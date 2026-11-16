package com.clarohome.feature.onboarding.settings

import com.clarohome.core.domain.ClaroPreferencesRepository
import com.clarohome.core.model.AppLanguage
import com.clarohome.core.model.ClaroPreferences
import com.clarohome.core.model.CapabilityStatus
import com.clarohome.core.model.ReadinessCapability
import com.clarohome.core.model.defaultAppCapabilities
import com.clarohome.core.model.CriticalActivity
import com.clarohome.core.model.OnboardingStep
import com.clarohome.core.model.SecretVerifier
import com.clarohome.core.model.SettingsAccessMode
import com.clarohome.core.model.TextSize
import com.clarohome.core.model.VisualMode
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OnboardingSettingsManagerTest {
    @Test
    fun finishingSetupRequiresAllAvailableDefaultAppsToBeConfirmed() = runTest {
        val repository = FakePreferencesRepository()
        val manager = manager(repository)
        manager.advanceOnboarding(OnboardingStep.ROLE_READINESS)
        assertThat(manager.advanceOnboarding(OnboardingStep.COMPLETE)).isFalse()
        for (missing in defaultAppCapabilities) {
            val defaults = defaultAppCapabilities.associateWith {
                if (it == missing) CapabilityStatus.NEEDS_ATTENTION else CapabilityStatus.READY
            }
            assertThat(manager.advanceOnboarding(OnboardingStep.COMPLETE, defaults)).isFalse()
            assertThat(repository.current.onboarding.currentStep).isEqualTo(OnboardingStep.ROLE_READINESS)
        }
        assertThat(
            manager.advanceOnboarding(
                OnboardingStep.COMPLETE,
                defaultAppCapabilities.associateWith { CapabilityStatus.READY },
            ),
        ).isTrue()
        assertThat(repository.current.onboarding.currentStep).isEqualTo(OnboardingStep.COMPLETE)
    }

    @Test
    fun setupDoesNotRequirePhoneRolesThatAndroidReportsUnavailable() = runTest {
        val manager = manager(FakePreferencesRepository())
        assertThat(
            manager.advanceOnboarding(
                OnboardingStep.COMPLETE,
                mapOf(
                    ReadinessCapability.HOME to CapabilityStatus.READY,
                    ReadinessCapability.PHONE to CapabilityStatus.UNAVAILABLE,
                    ReadinessCapability.MESSAGES to CapabilityStatus.UNAVAILABLE,
                ),
            ),
        ).isTrue()
    }

    @Test
    fun newHomeStepUsesFlowOrderInsteadOfPersistedEnumOrdinals() = runTest {
        val repository = FakePreferencesRepository()
        val manager = manager(repository)
        manager.advanceOnboarding(OnboardingStep.LANGUAGE_AND_DISPLAY)
        manager.advanceOnboarding(OnboardingStep.HOME_TILES)
        manager.advanceOnboarding(OnboardingStep.LANGUAGE_AND_DISPLAY)
        assertThat(repository.current.onboarding.completedSteps).doesNotContain(OnboardingStep.HOME_TILES)

        manager.advanceOnboarding(OnboardingStep.HOME_TILES)
        manager.advanceOnboarding(OnboardingStep.SETTINGS_ACCESS)
        assertThat(repository.current.onboarding.completedSteps).contains(OnboardingStep.HOME_TILES)
        manager.advanceOnboarding(OnboardingStep.HOME_TILES)
        assertThat(repository.current.onboarding.currentStep).isEqualTo(OnboardingStep.HOME_TILES)
        assertThat(repository.current.onboarding.completedSteps).doesNotContain(OnboardingStep.SETTINGS_ACCESS)
    }

    @Test
    fun goingBackPersistsPreviousStepWithoutMarkingCurrentStepComplete() = runTest {
        val repository = FakePreferencesRepository()
        val manager = manager(repository)
        manager.advanceOnboarding(OnboardingStep.PHONE_STYLE)
        manager.chooseVisualMode(VisualMode.TOUCH)
        manager.advanceOnboarding(OnboardingStep.LANGUAGE_AND_DISPLAY)

        manager.advanceOnboarding(OnboardingStep.PHONE_STYLE)

        assertThat(repository.current.onboarding.currentStep).isEqualTo(OnboardingStep.PHONE_STYLE)
        assertThat(repository.current.onboarding.completedSteps)
            .containsExactly(OnboardingStep.WELCOME, OnboardingStep.PHONE_STYLE)
        assertThat(repository.current.visualMode).isEqualTo(VisualMode.TOUCH)
    }

    @Test
    fun modeSwitchDefersForCriticalActivityThenAppliesWithoutChangingSharedPreferences() = runTest {
        val repository = FakePreferencesRepository(
            ClaroPreferences(
                language = AppLanguage.HEBREW,
                textSize = TextSize.HUGE,
                highContrast = true,
            ),
        )
        val manager = manager(repository)

        val deferred = manager.requestModeSwitch(
            VisualMode.CLASSIC,
            CriticalActivity.entries.toSet(),
        )

        assertThat(deferred).isInstanceOf(ModeSwitchResult.Deferred::class.java)
        assertThat(repository.current.visualMode).isEqualTo(VisualMode.TOUCH)
        assertThat(repository.current.pendingVisualMode).isEqualTo(VisualMode.CLASSIC)

        val applied = manager.retryDeferredModeSwitch(emptySet())

        assertThat(applied).isEqualTo(ModeSwitchResult.Applied(VisualMode.CLASSIC))
        assertThat(repository.current.visualMode).isEqualTo(VisualMode.CLASSIC)
        assertThat(repository.current.pendingVisualMode).isNull()
        assertThat(repository.current.language).isEqualTo(AppLanguage.HEBREW)
        assertThat(repository.current.textSize).isEqualTo(TextSize.HUGE)
        assertThat(repository.current.highContrast).isTrue()
    }

    @Test
    fun hapticToggleAfterLanguageSelectionKeepsChosenLanguage() = runTest {
        val repository = FakePreferencesRepository()
        val manager = manager(repository)

        manager.setLanguageAndDisplay(AppLanguage.HEBREW, TextSize.LARGE, true, true)
        manager.setHapticFeedback(false)

        assertThat(repository.current.language).isEqualTo(AppLanguage.HEBREW)
        assertThat(repository.current.textSize).isEqualTo(TextSize.LARGE)
        assertThat(repository.current.highContrast).isTrue()
        assertThat(repository.current.hapticFeedback).isFalse()
    }

    @Test
    fun dateQuestionUsesExactCurrentZeroPaddedDateAndHasNoRecovery() = runTest {
        val repository = FakePreferencesRepository()
        val time = FakeTimeProvider(today = LocalDate.of(2028, 2, 29))
        val manager = manager(repository, time)

        manager.useDateQuestion()

        assertThat(manager.unlock("29/02/2028")).isEqualTo(AccessResult.Unlocked)
        manager.lock()
        assertThat(manager.unlock("29/2/2028")).isEqualTo(AccessResult.Incorrect)
        assertThat(
            manager.recoverPin("anything", "123456", "123456"),
        ).isEqualTo(AccessResult.Invalid("PIN recovery is available only in caregiver PIN mode."))
        assertThat(repository.current.accessProtection.mode)
            .isEqualTo(SettingsAccessMode.DATE_QUESTION)
    }

    @Test
    fun pinRecoveryReplacesPinWithoutDateFallback() = runTest {
        val repository = FakePreferencesRepository()
        val time = FakeTimeProvider(today = LocalDate.of(2026, 11, 11))
        val manager = manager(repository, time)

        assertThat(manager.configurePin("123456", "123456", "family recovery secret"))
            .isEqualTo(AccessResult.Unlocked)
        assertThat(manager.unlock("11/11/2026")).isEqualTo(AccessResult.Incorrect)
        assertThat(manager.recoverPin("wrong secret", "654321", "654321"))
            .isEqualTo(AccessResult.Incorrect)
        assertThat(manager.recoverPin("family recovery secret", "654321", "654321"))
            .isEqualTo(AccessResult.Unlocked)
        manager.lock()
        assertThat(manager.unlock("123456")).isEqualTo(AccessResult.Incorrect)
        assertThat(manager.unlock("654321")).isEqualTo(AccessResult.Unlocked)
    }

    @Test
    fun cooldownSurvivesManagerRecreationAndUsesElapsedTimeOnSameBoot() = runTest {
        val repository = FakePreferencesRepository()
        val time = FakeTimeProvider()
        var manager = manager(repository, time)
        manager.configurePin("123456", "123456", "family recovery secret")

        repeat(4) {
            assertThat(manager.unlock("000000")).isEqualTo(AccessResult.Incorrect)
        }
        assertThat(manager.unlock("000000")).isEqualTo(AccessResult.Cooldown(30))

        manager = manager(repository, time)
        assertThat(manager.unlock("123456")).isEqualTo(AccessResult.Cooldown(30))

        time.elapsedMillis += 30_000
        time.wallMillis -= 3_600_000
        assertThat(manager.unlock("123456")).isEqualTo(AccessResult.Unlocked)
    }

    @Test
    fun unlockLastsForTheSessionUntilExplicitLockAndNotAcrossProcessRecreation() = runTest {
        val repository = FakePreferencesRepository()
        val time = FakeTimeProvider()
        var manager = manager(repository, time)

        assertThat(manager.unlock("11/11/2026")).isEqualTo(AccessResult.Unlocked)
        time.elapsedMillis += 60 * 60_000
        assertThat(manager.isUnlocked()).isTrue()

        manager.lock()
        assertThat(manager.isUnlocked()).isFalse()

        assertThat(manager.unlock("11/11/2026")).isEqualTo(AccessResult.Unlocked)
        manager = manager(repository, time)
        assertThat(manager.isUnlocked()).isFalse()
    }

    private fun manager(
        repository: FakePreferencesRepository,
        time: FakeTimeProvider = FakeTimeProvider(),
    ) = OnboardingSettingsManager(repository, FakeSecretHasher(), time)
}

private class FakePreferencesRepository(
    initial: ClaroPreferences = ClaroPreferences(),
) : ClaroPreferencesRepository {
    private val state = MutableStateFlow(initial)
    override val preferences: Flow<ClaroPreferences> = state
    val current: ClaroPreferences get() = state.value

    override suspend fun save(preferences: ClaroPreferences) {
        state.value = preferences
    }
}

private class FakeTimeProvider(
    var wallMillis: Long = 1_700_000_000_000,
    var elapsedMillis: Long = 10_000,
    var bootId: String = "boot-1",
    var today: LocalDate = LocalDate.of(2026, 11, 11),
) : SettingsTimeProvider {
    override fun snapshot() = TimeSnapshot(wallMillis, elapsedMillis, bootId)
    override fun today(): LocalDate = today
}

private class FakeSecretHasher : SecretHasher {
    override fun create(secret: CharArray) = SecretVerifier(
        saltBase64 = "test",
        hashBase64 = secret.concatToString(),
        iterations = 1,
    )

    override fun matches(secret: CharArray, verifier: SecretVerifier): Boolean =
        secret.concatToString() == verifier.hashBase64
}
