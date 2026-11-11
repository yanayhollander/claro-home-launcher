package com.clarohome.feature.billing.observability

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ConsentAwareObservabilityTest {
    @Test
    fun coldStartDoesNotUploadAndDisablesEveryVendor() {
        val fixture = Fixture()

        fixture.observability.track(ClaroEvent.MembershipScreenView())
        fixture.observability.record(IllegalStateException("Mira +1 555 0100"))

        assertThat(fixture.mixpanel.enabled).isFalse()
        assertThat(fixture.firebase.enabled).isFalse()
        assertThat(fixture.crash.enabled).isFalse()
        assertThat(fixture.inspector.envelopes).isEmpty()
    }

    @Test
    fun routingDoesNotDuplicateBusinessEvents() = runTest {
        val fixture = Fixture()
        fixture.controller.update(DiagnosticsConsent(analytics = true))

        fixture.observability.track(ClaroEvent.MembershipScreenView())
        fixture.observability.track(
            ClaroEvent.RoleReadiness(
                capability = EventCapability.PHONE,
                outcome = EventOutcome.READY,
            ),
        )

        assertThat(fixture.mixpanel.events.map { it.first })
            .containsExactly("membership_screen_view")
        assertThat(fixture.firebase.events.map { it.first })
            .containsExactly("role_readiness")
        assertThat(fixture.inspector.envelopes).hasSize(2)
    }

    @Test
    fun allowlistedEventsCannotContainSensitiveUserContent() = runTest {
        val fixture = Fixture()
        fixture.controller.update(DiagnosticsConsent(analytics = true))

        fixture.observability.track(
            ClaroEvent.TaskFlowResult(
                task = EventTask.OPEN_SOS,
                outcome = EventOutcome.FAILED,
            ),
        )

        val payload = fixture.inspector.envelopes.single().properties
        assertThat(payload.keys).containsExactly("outcome", "task")
        assertThat(payload.toString()).doesNotContain("name")
        assertThat(payload.toString()).doesNotContain("number")
        assertThat(payload.toString()).doesNotContain("message")
        assertThat(payload.toString()).doesNotContain("recipient")
        assertThat(payload.toString()).doesNotContain("destination")
    }

    @Test
    fun withdrawalResetsAnalyticsAndDeletesQueuedCrashes() = runTest {
        val fixture = Fixture()
        fixture.controller.update(DiagnosticsConsent(analytics = true, crashReports = true))

        fixture.controller.withdrawAll()
        fixture.observability.track(ClaroEvent.MembershipScreenView())
        fixture.observability.record(IllegalStateException("secret"))

        assertThat(fixture.controller.consent.value).isEqualTo(DiagnosticsConsent())
        assertThat(fixture.mixpanel.resetCount).isEqualTo(1)
        assertThat(fixture.firebase.resetCount).isEqualTo(1)
        assertThat(fixture.crash.deleteCount).isEqualTo(1)
        assertThat(fixture.inspector.envelopes).isEmpty()
    }

    @Test
    fun crashReportsDiscardExceptionMessages() = runTest {
        val fixture = Fixture()
        fixture.controller.update(DiagnosticsConsent(crashReports = true))

        fixture.observability.record(
            IllegalStateException("Call Mira at +1 555 0100 about the recovery phrase"),
        )

        val recorded = fixture.crash.recorded.single()
        assertThat(recorded).isInstanceOf(SanitizedException::class.java)
        assertThat(recorded.message).doesNotContain("Mira")
        assertThat(recorded.message).doesNotContain("555")
        assertThat(recorded.message).doesNotContain("recovery phrase")
    }

    private class Fixture {
        val store = MemoryConsentStore()
        val mixpanel = FakeAnalyticsVendor(AnalyticsDestination.MIXPANEL)
        val firebase = FakeAnalyticsVendor(AnalyticsDestination.FIREBASE)
        val crash = FakeCrashVendor()
        val inspector = RecordingInspector()
        val controller = DefaultConsentController(store, setOf(mixpanel, firebase), crash)
        val observability =
            ConsentAwareObservability(controller, setOf(mixpanel, firebase), crash, inspector)
    }
}

private class MemoryConsentStore : ConsentStore {
    private var value = DiagnosticsConsent()

    override fun read() = value

    override fun write(consent: DiagnosticsConsent) {
        value = consent
    }

    override fun clear() {
        value = DiagnosticsConsent()
    }
}

private class FakeAnalyticsVendor(
    override val destination: AnalyticsDestination,
) : AnalyticsVendor {
    var enabled = true
    var resetCount = 0
    val events = mutableListOf<Pair<String, Map<String, String>>>()

    override fun setCollectionEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun track(name: String, properties: Map<String, String>) {
        if (enabled) events += name to properties
    }

    override fun reset() {
        resetCount += 1
        events.clear()
    }
}

private class FakeCrashVendor : CrashVendor {
    var enabled = true
    var deleteCount = 0
    val recorded = mutableListOf<Throwable>()

    override fun setCollectionEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun record(exception: Throwable) {
        if (enabled) recorded += exception
    }

    override fun deleteUnsentReports() {
        deleteCount += 1
        recorded.clear()
    }
}

private class RecordingInspector : VendorNetworkInspector {
    val envelopes = mutableListOf<InspectedVendorEnvelope>()

    override fun onEnvelope(envelope: InspectedVendorEnvelope) {
        envelopes += envelope
    }
}
