package com.clarohome.feature.safety.media.apps

import com.clarohome.core.domain.EmergencyNumberDetector
import com.clarohome.core.domain.PhoneCallGateway
import com.clarohome.core.domain.PhoneCallRequest
import com.clarohome.core.domain.PhoneCallRequestResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SosCoordinatorTest {
    private val contact = TrustedContact(
        contactId = "mira",
        displayName = "Mira",
        destination = "2025550101",
        recipientConsented = true,
    )

    @Test
    fun setupRequiresAValidNumberAndRecipientAgreement() {
        assertThat(normalizeSosDestination("+972 (50) 123-4567")).isEqualTo("+972501234567")
        assertThat(normalizeSosDestination("")).isNull()
        assertThat(normalizeSosDestination("abc")).isNull()
        assertThat(normalizeSosDestination("050+123")).isNull()
        val gateway = RecordingPhoneGateway()
        val coordinator = coordinator(gateway)
        assertThat(coordinator.configure(contact.copy(destination = ""))).isEqualTo(SosReadiness.NotConfigured)
        assertThat(coordinator.start(0)).isFalse()
        assertThat(coordinator.configure(contact.copy(recipientConsented = false)))
            .isEqualTo(SosReadiness.RecipientConsentMissing)
        assertThat(coordinator.start(0)).isFalse()
        assertThat(gateway.requests).isEmpty()
    }

    @Test
    fun activeSosCallCannotBeRetriedAndEndedCallRequiresANewCountdown() = runTest {
        val gateway = RecordingPhoneGateway()
        val coordinator = coordinator(gateway)
        coordinator.configure(contact)
        coordinator.start(0)
        coordinator.advance(5_000)
        assertThat(coordinator.start(6_000)).isFalse()
        assertThat(gateway.requests).hasSize(1)
        coordinator.onCallOutcome(SosCallOutcome.ENDED)
        assertThat(coordinator.start(7_000)).isTrue()
        assertThat(gateway.requests).hasSize(1)
        coordinator.advance(12_000)
        assertThat(gateway.requests).hasSize(2)
    }

    @Test
    fun cancelBeforeDeadlinePlacesNoCall() = runTest {
        val gateway = RecordingPhoneGateway()
        val coordinator = coordinator(gateway)
        coordinator.configure(contact)

        assertThat(coordinator.start(nowElapsedMillis = 1_000)).isTrue()
        assertThat(coordinator.cancel()).isTrue()
        coordinator.advance(nowElapsedMillis = 7_000)

        assertThat(gateway.requests).isEmpty()
        assertThat(coordinator.state).isEqualTo(SosState.Canceled(contact, testMode = false))
    }

    @Test
    fun repeatedDeadlineChecksPlaceExactlyOneCall() = runTest {
        val gateway = RecordingPhoneGateway()
        val coordinator = coordinator(gateway)
        coordinator.configure(contact)
        coordinator.start(nowElapsedMillis = 1_000)

        coordinator.advance(nowElapsedMillis = 6_000)
        coordinator.advance(nowElapsedMillis = 7_000)
        coordinator.advance(nowElapsedMillis = 8_000)

        assertThat(gateway.requests).hasSize(1)
        assertThat(coordinator.state).isEqualTo(
            SosState.Calling(
                contact = contact,
                activationId = 1,
                outcome = SosCallOutcome.DIALING,
                testMode = false,
            ),
        )
    }

    @Test
    fun restoringCountdownRequiresExplicitRestart() = runTest {
        val gateway = RecordingPhoneGateway()
        val coordinator = coordinator(gateway)

        coordinator.restore(
            PersistedSosState(
                contact = contact,
                countdownDeadlineElapsedMillis = 5_000,
                activationId = 17,
                testMode = false,
            ),
        )
        coordinator.advance(nowElapsedMillis = 10_000)

        assertThat(coordinator.state).isEqualTo(
            SosState.Interrupted(contact, activationId = 17, testMode = false),
        )
        assertThat(gateway.requests).isEmpty()
        assertThat(coordinator.start(nowElapsedMillis = 10_000)).isTrue()
    }

    @Test
    fun emergencyNumberCannotBecomeTrustedRecipient() {
        val coordinator = SosCoordinator(
            phoneCallGateway = RecordingPhoneGateway(),
            emergencyNumberDetector = EmergencyNumberDetector { it == "112" },
        )

        val readiness = coordinator.configure(contact.copy(destination = "112"))

        assertThat(readiness).isEqualTo(SosReadiness.NotConfigured)
        assertThat(coordinator.start(nowElapsedMillis = 0)).isFalse()
    }

    @Test
    fun knownNoServiceRequestProducesDistinctFailure() = runTest {
        val coordinator = coordinator(
            RecordingPhoneGateway(PhoneCallRequestResult.NoService),
        )
        coordinator.configure(contact)
        coordinator.start(nowElapsedMillis = 0)

        coordinator.advance(nowElapsedMillis = 5_000)

        assertThat(coordinator.state).isEqualTo(
            SosState.Failed(
                contact = contact,
                outcome = SosCallOutcome.NO_SERVICE,
                testMode = false,
            ),
        )
    }

    private fun coordinator(gateway: RecordingPhoneGateway) = SosCoordinator(
        phoneCallGateway = gateway,
        emergencyNumberDetector = EmergencyNumberDetector { false },
    )
}

private class RecordingPhoneGateway(
    private val result: PhoneCallRequestResult = PhoneCallRequestResult.Requested,
) : PhoneCallGateway {
    val requests = mutableListOf<PhoneCallRequest>()

    override suspend fun requestCall(request: PhoneCallRequest): PhoneCallRequestResult {
        requests += request
        return result
    }
}
