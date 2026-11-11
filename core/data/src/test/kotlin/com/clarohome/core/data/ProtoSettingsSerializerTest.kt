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
        )

        ProtoSettingsSerializer.writeTo(settings, output)

        val restored = ProtoSettingsSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
        assertThat(restored).isEqualTo(settings)
    }
}
