package com.clarohome.core.data

import com.clarohome.core.model.AppLanguage
import com.clarohome.core.model.CapabilityStatus
import com.clarohome.core.model.HandoverCheck
import com.clarohome.core.model.OnboardingStep
import com.clarohome.core.model.ReadinessCapability
import com.clarohome.core.model.SettingsAccessMode
import com.clarohome.core.model.TextSize
import com.clarohome.core.model.VisualMode
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProtoSettingsSerializerTest {
    @Test
    fun callAudioDefaultsAndExplicitVolumeChoiceSurvivePersistence() = runTest {
        val defaults = ClaroSettings().toPreferences()
        assertThat(defaults.easyCallAudio).isTrue()
        assertThat(defaults.maximumSpeakerVolume).isFalse()
        val settings = defaults.copy(easyCallAudio = false, maximumSpeakerVolume = true).toSettings()
        val output = ByteArrayOutputStream()
        ProtoSettingsSerializer.writeTo(settings, output)
        val restored = ProtoSettingsSerializer.readFrom(ByteArrayInputStream(output.toByteArray())).toPreferences()
        assertThat(restored.easyCallAudio).isFalse()
        assertThat(restored.maximumSpeakerVolume).isTrue()
    }

    @Test
    fun newHomeStepRoundTripsWithoutChangingLegacyCompletedSetup() = runTest {
        val legacy = ProtoSettingsSerializer.readFrom(ByteArrayInputStream(byteArrayOf(0x30, 0x08)))
        assertThat(legacy.toPreferences().onboarding.currentStep).isEqualTo(OnboardingStep.COMPLETE)

        val settings = ClaroSettings(
            onboardingStep = OnboardingStep.HOME_TILES,
            completedOnboardingSteps = setOf(OnboardingStep.LANGUAGE_AND_DISPLAY),
        )
        val output = ByteArrayOutputStream()
        ProtoSettingsSerializer.writeTo(settings, output)
        assertThat(ProtoSettingsSerializer.readFrom(ByteArrayInputStream(output.toByteArray())))
            .isEqualTo(settings)
    }

    @Test
    fun newSetupUsesDeviceLanguageButPreservesSavedAndLegacyChoices() {
        assertThat(ClaroSettings().toPreferences(AppLanguage.HEBREW).language)
            .isEqualTo(AppLanguage.HEBREW)
        assertThat(
            ClaroSettings(language = AppLanguage.SPANISH)
                .toPreferences(AppLanguage.HEBREW).language,
        ).isEqualTo(AppLanguage.SPANISH)
        assertThat(
            ClaroSettings(completedOnboardingSteps = setOf(OnboardingStep.LANGUAGE_AND_DISPLAY))
                .toPreferences(AppLanguage.HEBREW).language,
        ).isEqualTo(AppLanguage.ENGLISH)
    }

    @Test
    fun explicitEnglishChoiceSurvivesPersistenceOnHebrewDevice() = runTest {
        val settings = ClaroSettings().toPreferences(AppLanguage.ENGLISH).toSettings()
        val output = ByteArrayOutputStream()
        ProtoSettingsSerializer.writeTo(settings, output)

        val restored = ProtoSettingsSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))

        assertThat(restored.toPreferences(AppLanguage.HEBREW).language)
            .isEqualTo(AppLanguage.ENGLISH)
    }

    @Test
    fun deviceLanguageUsesFirstSupportedLanguageAndEnglishFallback() {
        assertThat(appLanguageFor(listOf("fr", "es", "en"))).isEqualTo(AppLanguage.SPANISH)
        assertThat(appLanguageFor(listOf("he", "en"))).isEqualTo(AppLanguage.HEBREW)
        assertThat(appLanguageFor(listOf("iw"))).isEqualTo(AppLanguage.HEBREW)
        assertThat(appLanguageFor(listOf("en", "es"))).isEqualTo(AppLanguage.ENGLISH)
        assertThat(appLanguageFor(listOf("fr"))).isEqualTo(AppLanguage.ENGLISH)
    }

    @Test
    fun removedHandoverStepResumesAtPermissions() {
        assertThat(
            ClaroSettings(onboardingStep = OnboardingStep.HANDOVER)
                .toPreferences().onboarding.currentStep,
        ).isEqualTo(OnboardingStep.ROLE_READINESS)
    }

    @Test
    fun settingsRoundTripPreservesOnboardingAndProtectedSettingsState() = runTest {
        val output = ByteArrayOutputStream()
        val settings = ClaroSettings(
            visualMode = VisualMode.CLASSIC,
            pendingVisualMode = VisualMode.TOUCH,
            language = AppLanguage.HEBREW,
            textSize = TextSize.HUGE,
            highContrast = true,
            onboardingStep = OnboardingStep.HANDOVER,
            completedOnboardingSteps = setOf(
                OnboardingStep.WELCOME,
                OnboardingStep.PHONE_STYLE,
            ),
            favoriteSlotsAssigned = 4,
            speedDialOneAssigned = true,
            speedDialTwoAssigned = true,
            sosRecipientAgreed = true,
            readiness = mapOf(ReadinessCapability.HOME to CapabilityStatus.READY),
            handoverChecks = setOf(HandoverCheck.OUTGOING_CALL),
            accessMode = SettingsAccessMode.CAREGIVER_PIN,
            pinVerifier = PersistedSecretVerifier("pin-salt", "pin-hash", 120_000),
            recoveryVerifier = PersistedSecretVerifier("recovery-salt", "recovery-hash", 120_000),
            failedUnlockAttempts = 5,
            unlockCooldown = PersistedCooldown(2_000, 1_000, "boot"),
            hapticFeedback = false,
        )

        ProtoSettingsSerializer.writeTo(settings, output)

        val restored = ProtoSettingsSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
        assertThat(restored).isEqualTo(settings)
        assertThat(ClaroSettings().toPreferences().hapticFeedback).isTrue()
    }
}
