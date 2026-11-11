package com.clarohome.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.clarohome.core.domain.ClaroPreferencesRepository
import com.clarohome.core.domain.VisualModeRepository
import com.clarohome.core.model.AccessProtection
import com.clarohome.core.model.AppLanguage
import com.clarohome.core.model.CapabilityStatus
import com.clarohome.core.model.ClaroPreferences
import com.clarohome.core.model.CooldownState
import com.clarohome.core.model.HandoverCheck
import com.clarohome.core.model.OnboardingProgress
import com.clarohome.core.model.OnboardingStep
import com.clarohome.core.model.ReadinessCapability
import com.clarohome.core.model.SecretVerifier
import com.clarohome.core.model.SettingsAccessMode
import com.clarohome.core.model.TextSize
import com.clarohome.core.model.VisualMode
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf

@Serializable
data class ClaroSettings(
    val visualMode: VisualMode = VisualMode.TOUCH,
    val pendingVisualMode: VisualMode? = null,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val textSize: TextSize = TextSize.STANDARD,
    val highContrast: Boolean = false,
    val onboardingStep: OnboardingStep = OnboardingStep.WELCOME,
    val completedOnboardingSteps: Set<OnboardingStep> = emptySet(),
    val favoriteSlotsAssigned: Int = 0,
    val speedDialOneAssigned: Boolean = false,
    val speedDialTwoAssigned: Boolean = false,
    val sosRecipientAgreed: Boolean = false,
    val readiness: Map<ReadinessCapability, CapabilityStatus> =
        ReadinessCapability.entries.associateWith { CapabilityStatus.NEEDS_ATTENTION },
    val handoverChecks: Set<HandoverCheck> = emptySet(),
    val accessMode: SettingsAccessMode = SettingsAccessMode.DATE_QUESTION,
    val pinVerifier: PersistedSecretVerifier? = null,
    val recoveryVerifier: PersistedSecretVerifier? = null,
    val failedUnlockAttempts: Int = 0,
    val failedRecoveryAttempts: Int = 0,
    val unlockCooldown: PersistedCooldown = PersistedCooldown(),
    val recoveryCooldown: PersistedCooldown = PersistedCooldown(),
)

@Serializable
data class PersistedSecretVerifier(
    val saltBase64: String,
    val hashBase64: String,
    val iterations: Int,
)

@Serializable
data class PersistedCooldown(
    val wallClockDeadlineMillis: Long = 0,
    val elapsedRealtimeDeadlineMillis: Long = 0,
    val bootId: String = "",
)

fun ClaroSettings.toPreferences() = ClaroPreferences(
    visualMode = visualMode,
    pendingVisualMode = pendingVisualMode,
    language = language,
    textSize = textSize,
    highContrast = highContrast,
    onboarding = OnboardingProgress(
        currentStep = onboardingStep,
        completedSteps = completedOnboardingSteps,
        favoriteSlotsAssigned = favoriteSlotsAssigned,
        speedDialOneAssigned = speedDialOneAssigned,
        speedDialTwoAssigned = speedDialTwoAssigned,
        sosRecipientAgreed = sosRecipientAgreed,
        readiness = readiness,
        handoverChecks = handoverChecks,
    ),
    accessProtection = AccessProtection(
        mode = accessMode,
        pinVerifier = pinVerifier?.toModel(),
        recoveryVerifier = recoveryVerifier?.toModel(),
        failedUnlockAttempts = failedUnlockAttempts,
        failedRecoveryAttempts = failedRecoveryAttempts,
        unlockCooldown = unlockCooldown.toModel(),
        recoveryCooldown = recoveryCooldown.toModel(),
    ),
)

fun ClaroPreferences.toSettings() = ClaroSettings(
    visualMode = visualMode,
    pendingVisualMode = pendingVisualMode,
    language = language,
    textSize = textSize,
    highContrast = highContrast,
    onboardingStep = onboarding.currentStep,
    completedOnboardingSteps = onboarding.completedSteps,
    favoriteSlotsAssigned = onboarding.favoriteSlotsAssigned,
    speedDialOneAssigned = onboarding.speedDialOneAssigned,
    speedDialTwoAssigned = onboarding.speedDialTwoAssigned,
    sosRecipientAgreed = onboarding.sosRecipientAgreed,
    readiness = onboarding.readiness,
    handoverChecks = onboarding.handoverChecks,
    accessMode = accessProtection.mode,
    pinVerifier = accessProtection.pinVerifier?.toPersisted(),
    recoveryVerifier = accessProtection.recoveryVerifier?.toPersisted(),
    failedUnlockAttempts = accessProtection.failedUnlockAttempts,
    failedRecoveryAttempts = accessProtection.failedRecoveryAttempts,
    unlockCooldown = accessProtection.unlockCooldown.toPersisted(),
    recoveryCooldown = accessProtection.recoveryCooldown.toPersisted(),
)

private fun PersistedSecretVerifier.toModel() = SecretVerifier(saltBase64, hashBase64, iterations)
private fun SecretVerifier.toPersisted() = PersistedSecretVerifier(saltBase64, hashBase64, iterations)
private fun PersistedCooldown.toModel() =
    CooldownState(wallClockDeadlineMillis, elapsedRealtimeDeadlineMillis, bootId)
private fun CooldownState.toPersisted() =
    PersistedCooldown(wallClockDeadlineMillis, elapsedRealtimeDeadlineMillis, bootId)

@OptIn(ExperimentalSerializationApi::class)
object ProtoSettingsSerializer : Serializer<ClaroSettings> {
    override val defaultValue = ClaroSettings()

    override suspend fun readFrom(input: InputStream): ClaroSettings =
        ProtoBuf.decodeFromByteArray(ClaroSettings.serializer(), input.readBytes())

    override suspend fun writeTo(t: ClaroSettings, output: OutputStream) {
        output.write(ProtoBuf.encodeToByteArray(ClaroSettings.serializer(), t))
    }
}

private val Context.claroSettings: DataStore<ClaroSettings> by dataStore(
    fileName = "claro_settings.pb",
    serializer = ProtoSettingsSerializer,
)

@Singleton
class ProtoVisualModeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : VisualModeRepository, ClaroPreferencesRepository {
    override val visualMode: Flow<VisualMode> = context.claroSettings.data
        .catch { emit(ClaroSettings()) }
        .map { it.visualMode }

    override val preferences: Flow<ClaroPreferences> = context.claroSettings.data
        .catch { emit(ClaroSettings()) }
        .map(ClaroSettings::toPreferences)

    override suspend fun setVisualMode(mode: VisualMode) {
        context.claroSettings.updateData { it.copy(visualMode = mode) }
    }

    override suspend fun save(preferences: ClaroPreferences) {
        context.claroSettings.updateData { preferences.toSettings() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VisualModeDataModule {
    @Binds
    abstract fun bindVisualModeRepository(
        repository: ProtoVisualModeRepository,
    ): VisualModeRepository

    @Binds
    abstract fun bindPreferencesRepository(
        repository: ProtoVisualModeRepository,
    ): ClaroPreferencesRepository
}
