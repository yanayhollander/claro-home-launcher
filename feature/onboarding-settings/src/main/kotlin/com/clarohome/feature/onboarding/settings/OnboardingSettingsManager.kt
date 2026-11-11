package com.clarohome.feature.onboarding.settings

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.clarohome.core.domain.ClaroPreferencesRepository
import com.clarohome.core.model.AccessProtection
import com.clarohome.core.model.AppLanguage
import com.clarohome.core.model.CapabilityStatus
import com.clarohome.core.model.ClaroPreferences
import com.clarohome.core.model.CooldownState
import com.clarohome.core.model.CriticalActivity
import com.clarohome.core.model.HandoverCheck
import com.clarohome.core.model.OnboardingStep
import com.clarohome.core.model.ReadinessCapability
import com.clarohome.core.model.SecretVerifier
import com.clarohome.core.model.SettingsAccessMode
import com.clarohome.core.model.TextSize
import com.clarohome.core.model.VisualMode
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.security.SecureRandom
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TimeSnapshot(
    val wallClockMillis: Long,
    val elapsedRealtimeMillis: Long,
    val bootId: String,
)

interface SettingsTimeProvider {
    fun snapshot(): TimeSnapshot
    fun today(): LocalDate
}

@Singleton
class AndroidSettingsTimeProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsTimeProvider {
    private val clock = Clock.systemDefaultZone()

    override fun snapshot() = TimeSnapshot(
        wallClockMillis = clock.millis(),
        elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
        bootId = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0).toString(),
    )

    override fun today(): LocalDate = LocalDate.now(clock)
}

interface SecretHasher {
    fun create(secret: CharArray): SecretVerifier
    fun matches(secret: CharArray, verifier: SecretVerifier): Boolean
}

@Singleton
class Pbkdf2SecretHasher @Inject constructor() : SecretHasher {
    private val random = SecureRandom()

    override fun create(secret: CharArray): SecretVerifier {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val hash = derive(secret, salt, ITERATIONS)
        return SecretVerifier(
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            hashBase64 = Base64.getEncoder().encodeToString(hash),
            iterations = ITERATIONS,
        )
    }

    override fun matches(secret: CharArray, verifier: SecretVerifier): Boolean {
        val expected = Base64.getDecoder().decode(verifier.hashBase64)
        val actual = derive(
            secret,
            Base64.getDecoder().decode(verifier.saltBase64),
            verifier.iterations,
        )
        return java.security.MessageDigest.isEqual(expected, actual)
    }

    private fun derive(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private companion object {
        const val SALT_BYTES = 16
        const val ITERATIONS = 120_000
        const val KEY_BITS = 256
    }
}

sealed interface AccessResult {
    data object Unlocked : AccessResult
    data object Incorrect : AccessResult
    data class Cooldown(val remainingSeconds: Long) : AccessResult
    data class Invalid(val reason: String) : AccessResult
}

sealed interface ModeSwitchResult {
    data class Applied(val mode: VisualMode) : ModeSwitchResult
    data class Deferred(
        val mode: VisualMode,
        val blockers: Set<CriticalActivity>,
    ) : ModeSwitchResult
}

@Singleton
class OnboardingSettingsManager @Inject constructor(
    private val repository: ClaroPreferencesRepository,
    private val hasher: SecretHasher,
    private val timeProvider: SettingsTimeProvider,
) {
    private val mutex = Mutex()
    private var unlockedUntilElapsedMillis = 0L

    suspend fun advanceOnboarding(next: OnboardingStep) = update { current ->
        current.copy(
            onboarding = current.onboarding.copy(
                currentStep = next,
                completedSteps = current.onboarding.completedSteps + current.onboarding.currentStep,
            ),
        )
    }

    suspend fun chooseVisualMode(mode: VisualMode) = update {
        it.copy(visualMode = mode, pendingVisualMode = null)
    }

    suspend fun setLanguageAndDisplay(
        language: AppLanguage,
        textSize: TextSize,
        highContrast: Boolean,
    ) = update {
        it.copy(language = language, textSize = textSize, highContrast = highContrast)
    }

    suspend fun useDateQuestion() = update {
        it.copy(
            accessProtection = AccessProtection(mode = SettingsAccessMode.DATE_QUESTION),
        )
    }

    suspend fun configurePin(
        pin: String,
        confirmation: String,
        recoverySecret: String,
    ): AccessResult {
        if (!pin.matches(Regex("\\d{6}"))) {
            return AccessResult.Invalid("Use exactly 6 digits.")
        }
        if (pin != confirmation) {
            return AccessResult.Invalid("PINs do not match.")
        }
        if (recoverySecret.length < MIN_RECOVERY_LENGTH) {
            return AccessResult.Invalid("Recovery secret must be at least $MIN_RECOVERY_LENGTH characters.")
        }
        val pinVerifier = hasher.create(pin.toCharArray())
        val recoveryVerifier = hasher.create(recoverySecret.toCharArray())
        update {
            it.copy(
                accessProtection = AccessProtection(
                    mode = SettingsAccessMode.CAREGIVER_PIN,
                    pinVerifier = pinVerifier,
                    recoveryVerifier = recoveryVerifier,
                ),
            )
        }
        return AccessResult.Unlocked
    }

    suspend fun unlock(input: String): AccessResult = mutex.withLock {
        val current = repository.preferences.first()
        val now = timeProvider.snapshot()
        cooldownRemaining(current.accessProtection.unlockCooldown, now)?.let {
            return AccessResult.Cooldown(it)
        }
        val matches = when (current.accessProtection.mode) {
            SettingsAccessMode.DATE_QUESTION -> input == formattedToday()
            SettingsAccessMode.CAREGIVER_PIN ->
                current.accessProtection.pinVerifier?.let {
                    hasher.matches(input.toCharArray(), it)
                } == true
        }
        if (matches) {
            unlockedUntilElapsedMillis = now.elapsedRealtimeMillis + INACTIVITY_TIMEOUT_MILLIS
            repository.save(
                current.copy(
                    accessProtection = current.accessProtection.copy(
                        failedUnlockAttempts = 0,
                        unlockCooldown = CooldownState(),
                    ),
                ),
            )
            AccessResult.Unlocked
        } else {
            val attempts = current.accessProtection.failedUnlockAttempts + 1
            val cooldown = if (attempts >= ATTEMPTS_BEFORE_COOLDOWN) {
                newCooldown(attempts, now)
            } else {
                CooldownState()
            }
            repository.save(
                current.copy(
                    accessProtection = current.accessProtection.copy(
                        failedUnlockAttempts = attempts,
                        unlockCooldown = cooldown,
                    ),
                ),
            )
            cooldownRemaining(cooldown, now)?.let(AccessResult::Cooldown) ?: AccessResult.Incorrect
        }
    }

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class OnboardingSettingsBindingsModule {
        @Binds
        abstract fun bindSecretHasher(hasher: Pbkdf2SecretHasher): SecretHasher

        @Binds
        abstract fun bindTimeProvider(provider: AndroidSettingsTimeProvider): SettingsTimeProvider
    }

    suspend fun recoverPin(
        recoverySecret: String,
        newPin: String,
        confirmation: String,
    ): AccessResult = mutex.withLock {
        val current = repository.preferences.first()
        if (current.accessProtection.mode != SettingsAccessMode.CAREGIVER_PIN) {
            return AccessResult.Invalid("PIN recovery is available only in caregiver PIN mode.")
        }
        if (!newPin.matches(Regex("\\d{6}")) || newPin != confirmation) {
            return AccessResult.Invalid("Enter the same 6-digit PIN twice.")
        }
        val now = timeProvider.snapshot()
        cooldownRemaining(current.accessProtection.recoveryCooldown, now)?.let {
            return AccessResult.Cooldown(it)
        }
        val matches = current.accessProtection.recoveryVerifier?.let {
            hasher.matches(recoverySecret.toCharArray(), it)
        } == true
        if (!matches) {
            val attempts = current.accessProtection.failedRecoveryAttempts + 1
            val cooldown = if (attempts >= ATTEMPTS_BEFORE_COOLDOWN) {
                newCooldown(attempts, now)
            } else {
                CooldownState()
            }
            repository.save(
                current.copy(
                    accessProtection = current.accessProtection.copy(
                        failedRecoveryAttempts = attempts,
                        recoveryCooldown = cooldown,
                    ),
                ),
            )
            return cooldownRemaining(cooldown, now)?.let(AccessResult::Cooldown)
                ?: AccessResult.Incorrect
        }
        repository.save(
            current.copy(
                accessProtection = current.accessProtection.copy(
                    pinVerifier = hasher.create(newPin.toCharArray()),
                    failedRecoveryAttempts = 0,
                    recoveryCooldown = CooldownState(),
                ),
            ),
        )
        unlockedUntilElapsedMillis = now.elapsedRealtimeMillis + INACTIVITY_TIMEOUT_MILLIS
        AccessResult.Unlocked
    }

    fun isUnlocked(): Boolean =
        timeProvider.snapshot().elapsedRealtimeMillis < unlockedUntilElapsedMillis

    fun recordActivity() {
        if (isUnlocked()) {
            unlockedUntilElapsedMillis =
                timeProvider.snapshot().elapsedRealtimeMillis + INACTIVITY_TIMEOUT_MILLIS
        }
    }

    fun lock() {
        unlockedUntilElapsedMillis = 0
    }

    suspend fun requestModeSwitch(
        target: VisualMode,
        activeActivities: Set<CriticalActivity>,
    ): ModeSwitchResult {
        if (activeActivities.isNotEmpty()) {
            update { it.copy(pendingVisualMode = target) }
            return ModeSwitchResult.Deferred(target, activeActivities)
        }
        update { it.copy(visualMode = target, pendingVisualMode = null) }
        return ModeSwitchResult.Applied(target)
    }

    suspend fun retryDeferredModeSwitch(
        activeActivities: Set<CriticalActivity>,
    ): ModeSwitchResult? {
        val target = repository.preferences.first().pendingVisualMode ?: return null
        return requestModeSwitch(target, activeActivities)
    }

    suspend fun cancelDeferredModeSwitch() = update { it.copy(pendingVisualMode = null) }

    suspend fun resetAll() {
        mutex.withLock {
            unlockedUntilElapsedMillis = 0L
            repository.save(ClaroPreferences())
        }
    }

    suspend fun setReadiness(
        capability: ReadinessCapability,
        status: CapabilityStatus,
    ) = update {
        it.copy(
            onboarding = it.onboarding.copy(
                readiness = it.onboarding.readiness + (capability to status),
            ),
        )
    }

    suspend fun setHandoverCheck(check: HandoverCheck, checked: Boolean) = update {
        val checks = if (checked) {
            it.onboarding.handoverChecks + check
        } else {
            it.onboarding.handoverChecks - check
        }
        it.copy(onboarding = it.onboarding.copy(handoverChecks = checks))
    }

    suspend fun updateSetupAssignments(
        favoriteSlotsAssigned: Int,
        speedDialOneAssigned: Boolean,
        speedDialTwoAssigned: Boolean,
        sosRecipientAgreed: Boolean,
    ) = update {
        it.copy(
            onboarding = it.onboarding.copy(
                favoriteSlotsAssigned = favoriteSlotsAssigned.coerceIn(0, 4),
                speedDialOneAssigned = speedDialOneAssigned,
                speedDialTwoAssigned = speedDialTwoAssigned,
                sosRecipientAgreed = sosRecipientAgreed,
            ),
        )
    }

    fun formattedToday(): String = DATE_FORMATTER.format(timeProvider.today())

    private suspend fun update(transform: (ClaroPreferences) -> ClaroPreferences) {
        mutex.withLock {
            repository.save(transform(repository.preferences.first()))
        }
    }

    private fun newCooldown(attempts: Int, now: TimeSnapshot): CooldownState {
        val exponent = (attempts - ATTEMPTS_BEFORE_COOLDOWN).coerceAtLeast(0)
        val duration = min(BASE_COOLDOWN_MILLIS shl exponent.coerceAtMost(16), MAX_COOLDOWN_MILLIS)
        return CooldownState(
            wallClockDeadlineMillis = now.wallClockMillis + duration,
            elapsedRealtimeDeadlineMillis = now.elapsedRealtimeMillis + duration,
            bootId = now.bootId,
        )
    }

    private fun cooldownRemaining(cooldown: CooldownState, now: TimeSnapshot): Long? {
        val deadline = if (cooldown.bootId == now.bootId) {
            cooldown.elapsedRealtimeDeadlineMillis
        } else {
            cooldown.wallClockDeadlineMillis
        }
        val current = if (cooldown.bootId == now.bootId) {
            now.elapsedRealtimeMillis
        } else {
            now.wallClockMillis
        }
        val remaining = deadline - current
        return if (remaining > 0) (remaining + 999) / 1_000 else null
    }

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
        const val MIN_RECOVERY_LENGTH = 12
        const val ATTEMPTS_BEFORE_COOLDOWN = 5
        const val BASE_COOLDOWN_MILLIS = 30_000L
        const val MAX_COOLDOWN_MILLIS = 30 * 60_000L
        const val INACTIVITY_TIMEOUT_MILLIS = 2 * 60_000L
    }
}
