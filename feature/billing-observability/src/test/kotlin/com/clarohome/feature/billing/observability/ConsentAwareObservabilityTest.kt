package com.clarohome.feature.billing.observability

import com.clarohome.core.domain.ClaroEvent
import com.clarohome.core.domain.DiagnosticOperation
import com.clarohome.core.domain.EventOutcome
import com.clarohome.core.domain.EventTask
import com.clarohome.core.domain.HomeTelemetryContext
import com.clarohome.core.domain.HomeAction
import com.clarohome.core.domain.TelemetryScreen
import com.clarohome.core.domain.TelemetryProduct
import com.clarohome.core.domain.ConversionKind
import com.clarohome.core.domain.TelemetryMoney
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind
import com.clarohome.core.model.TileAccent
import com.clarohome.core.model.OnboardingStep
import java.math.BigDecimal
import java.util.Currency
import com.clarohome.core.model.ReadinessCapability
import com.clarohome.core.model.TextSize
import com.clarohome.core.model.VisualMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ConsentAwareObservabilityTest {
    @Test
    fun sharedIdentityRespectsIndependentConsentAndNeverReachesMeta() = runTest {
        val fixture = Fixture()
        fixture.controller.update(DiagnosticsConsent(analytics = true, crashReports = true, marketing = true))
        val identity = fixture.mixpanel.userId
        assertThat(identity).isNotNull()
        assertThat(fixture.firebase.userId).isEqualTo(identity)
        assertThat(fixture.crash.userId).isEqualTo(identity)
        assertThat(fixture.meta.userId).isNull()

        fixture.controller.update(DiagnosticsConsent(crashReports = true, marketing = true))
        assertThat(fixture.mixpanel.userId).isNull()
        assertThat(fixture.firebase.userId).isNull()
        assertThat(fixture.crash.userId).isEqualTo(identity)

        fixture.controller.update(DiagnosticsConsent(analytics = true, crashReports = true))
        assertThat(fixture.mixpanel.userId).isEqualTo(identity)
        fixture.controller.withdrawAll()
        assertThat(fixture.crash.userId).isNull()
        fixture.controller.update(DiagnosticsConsent(analytics = true))
        assertThat(fixture.mixpanel.userId).isNotEqualTo(identity)
        assertThat(fixture.firebase.userId).isEqualTo(fixture.mixpanel.userId)
    }

    @Test
    fun marketingOnlyDoesNotCreateASharedIdentityAndPersistenceFailureFailsClosed() = runTest {
        val fixture = Fixture()
        fixture.controller.update(DiagnosticsConsent(marketing = true))
        assertThat(fixture.identity.creationCount).isEqualTo(0)
        fixture.identity.failPersistence = true
        fixture.controller.update(DiagnosticsConsent(analytics = true, crashReports = true, marketing = true))
        assertThat(fixture.mixpanel.enabled).isFalse()
        assertThat(fixture.firebase.enabled).isFalse()
        assertThat(fixture.crash.enabled).isFalse()
        assertThat(fixture.meta.enabled).isTrue()
        assertThat(fixture.meta.userId).isNull()
    }

    @Test
    fun immediateConsentObserverSeesReadyVendorsBeforeStartingSession() = runTest {
        val fixture = Fixture()
        val observer = backgroundScope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            fixture.controller.consent.collect {
                fixture.observability.foregroundSession(1)
            }
        }

        fixture.controller.update(DiagnosticsConsent(analytics = true, marketing = true))

        assertThat(fixture.mixpanel.events.map { it.first }).containsExactly("session_started")
        assertThat(fixture.meta.events.map { it.first }).containsExactly("session_started")
        observer.cancel()
    }

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
    fun everyCustomEventIsMirroredExactlyOnceWithMatchingProperties() = runTest {
        val fixture = Fixture()
        fixture.controller.update(DiagnosticsConsent(analytics = true))
        val events = listOf(
            ClaroEvent.SessionStarted,
            ClaroEvent.ScreenView(TelemetryScreen.CONTACTS),
            ClaroEvent.SetupStepResult(OnboardingStep.COMPLETE, EventOutcome.COMPLETED),
            ClaroEvent.SetupCompleted,
            ClaroEvent.MembershipScreenView(),
            ClaroEvent.VisualModeSelected(VisualMode.CLASSIC),
            ClaroEvent.AccessibilityPresetSelected(TextSize.HUGE),
            ClaroEvent.HomeChanged(HomeAction.SET, HomeTileKind.CONTACT),
            ClaroEvent.HomeTileOpened(HomeTileKind.CONTACT, 2),
            ClaroEvent.CheckoutStarted(TelemetryProduct.MONTHLY),
            ClaroEvent.Conversion(ConversionKind.TRIAL, TelemetryProduct.MONTHLY),
            ClaroEvent.Conversion(ConversionKind.SUBSCRIPTION, TelemetryProduct.YEARLY),
            ClaroEvent.Conversion(ConversionKind.PURCHASE, TelemetryProduct.LIFETIME,
                TelemetryMoney(BigDecimal("12.50"), Currency.getInstance("USD"))),
            ClaroEvent.TaskFlowResult(EventTask.STARTUP_RECOVERY, EventOutcome.COMPLETED),
            ClaroEvent.TaskFlowResult(EventTask.SOS_HANDOFF, EventOutcome.STARTED, testMode = true),
            ClaroEvent.RoleReadiness(
                capability = ReadinessCapability.PHONE,
                outcome = EventOutcome.READY,
            ),
        )
        events.forEach(fixture.observability::track)

        assertThat(fixture.mixpanel.events).hasSize(events.size)
        assertThat(fixture.firebase.events).containsExactlyElementsIn(fixture.mixpanel.events).inOrder()
        assertThat(fixture.meta.events).isEmpty()
        assertThat(fixture.inspector.envelopes).hasSize(events.size * 2)
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

        val payload = fixture.inspector.envelopes.first().properties
        assertThat(payload.keys).containsExactly("outcome", "task", "schema_version", "environment")
        assertThat(payload.toString()).doesNotContain("name")
        assertThat(payload.toString()).doesNotContain("number")
        assertThat(payload.toString()).doesNotContain("message")
        assertThat(payload.toString()).doesNotContain("recipient")
        assertThat(payload.toString()).doesNotContain("destination")
    }

    @Test
    fun homeCountsExcludeEmptySlotsAndSharedContextRefreshesAfterConsentAndEdits() = runTest {
        val fixture = Fixture()
        val slots = listOf(
            null,
            HomeTile("contact", "Private name", TileAccent.BLUE, HomeTileKind.CONTACT),
            HomeTile("app", "Private app", TileAccent.BLUE, HomeTileKind.INSTALLED_APP),
            HomeTile("phone", "Phone", TileAccent.GREEN, HomeTileKind.PHONE),
            HomeTile("widget", "Private widget", TileAccent.BLUE, HomeTileKind.WIDGET),
            null,
        )
        val home = HomeTelemetryContext.from(slots)
        fixture.observability.updateContext(VisualMode.TOUCH, TextSize.HUGE, home)
        assertThat(fixture.mixpanel.sharedContext).isEmpty()
        fixture.controller.update(DiagnosticsConsent(analytics = true))
        fixture.observability.track(ClaroEvent.HomeTileOpened(HomeTileKind.CONTACT, 0))
        val initial = fixture.mixpanel.events.single().second
        assertThat(initial["home_tile_count"]).isEqualTo("4")
        assertThat(initial["home_contact_tile_count"]).isEqualTo("1")
        assertThat(initial["home_app_tile_count"]).isEqualTo("1")
        assertThat(initial["home_shortcut_tile_count"]).isEqualTo("1")
        assertThat(initial["home_widget_tile_count"]).isEqualTo("1")
        assertThat(initial["tile_position"]).isEqualTo("0")
        assertThat(initial.toString()).doesNotContain("Private")
        assertThat(fixture.firebase.sharedContext).isEqualTo(fixture.mixpanel.sharedContext)
        fixture.observability.updateContext(VisualMode.CLASSIC, TextSize.HUGE, HomeTelemetryContext.from(emptyList()))
        fixture.observability.track(ClaroEvent.SessionStarted)
        assertThat(fixture.firebase.events.last().second["home_tile_count"]).isEqualTo("0")
        assertThat(fixture.firebase.sharedContext["visual_mode"]).isEqualTo("classic")
        fixture.controller.withdrawAll()
        assertThat(fixture.mixpanel.sharedContext).isEmpty()
        assertThat(fixture.firebase.sharedContext).isEmpty()
    }

    @Test
    fun analyticsCountsAndRevenueUseNumericTypesWithoutConvertingIdentifiers() {
        assertThat(analyticsValue("home_tile_count", "12")).isEqualTo(12L)
        assertThat(analyticsValue("tile_position", "0")).isEqualTo(0L)
        assertThat(analyticsValue("amount", "12.50")).isEqualTo(BigDecimal("12.50"))
        assertThat(analyticsValue("test_mode", "true")).isEqualTo(true)
        assertThat(analyticsValue("product", "monthly")).isEqualTo("monthly")
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
        assertThat(recorded.cause).isNull()
        assertThat(recorded.suppressed).isEmpty()
    }

    @Test
    fun repeatedHandledFailuresAreLimitedPerOperationAndForegroundSession() = runTest {
        val fixture = Fixture()
        fixture.controller.update(DiagnosticsConsent(crashReports = true))
        val failure = IllegalStateException("private")
        fixture.observability.foregroundSession(1)

        fixture.observability.record(failure, DiagnosticOperation.STARTUP)
        fixture.observability.record(failure, DiagnosticOperation.STARTUP)
        fixture.observability.record(failure, DiagnosticOperation.BILLING)
        assertThat(fixture.crash.recorded).hasSize(2)

        fixture.observability.foregroundSession(2)
        fixture.observability.record(failure, DiagnosticOperation.STARTUP)
        assertThat(fixture.crash.recorded).hasSize(3)
    }

    @Test
    fun allEightConsentCombinationsGateEachDestinationIndependently() = runTest {
        for (analytics in listOf(false, true)) {
            for (crashes in listOf(false, true)) {
                for (marketing in listOf(false, true)) {
                    val fixture = Fixture()
                    fixture.controller.update(DiagnosticsConsent(analytics, crashes, marketing))
                    fixture.observability.track(ClaroEvent.MembershipScreenView())
                    fixture.observability.track(ClaroEvent.RoleReadiness(
                        ReadinessCapability.HOME, EventOutcome.READY,
                    ))
                    fixture.observability.track(ClaroEvent.TaskFlowResult(EventTask.SOS_HANDOFF, EventOutcome.FAILED))
                    fixture.observability.record(IllegalStateException("private"))

                    assertThat(fixture.mixpanel.events.isNotEmpty()).isEqualTo(analytics)
                    assertThat(fixture.firebase.events.isNotEmpty()).isEqualTo(analytics)
                    assertThat(fixture.crash.recorded.isNotEmpty()).isEqualTo(crashes)
                    assertThat(fixture.meta.events.map { it.first }).containsExactlyElementsIn(
                        if (marketing) listOf("membership_screen_view") else emptyList(),
                    )
                }
            }
        }
    }

    @Test
    fun metaNeverReceivesDiagnosticOrAccessibilityContext() = runTest {
        val fixture = Fixture()
        fixture.controller.update(DiagnosticsConsent(analytics = true, marketing = true))
        fixture.observability.updateContext(VisualMode.CLASSIC, TextSize.HUGE)
        fixture.observability.track(ClaroEvent.MembershipScreenView())
        fixture.observability.track(ClaroEvent.AccessibilityPresetSelected(TextSize.HUGE))

        assertThat(fixture.meta.events).hasSize(1)
        assertThat(fixture.meta.events.single().second.keys).containsExactly("outcome")
        assertThat(fixture.mixpanel.events.first().second["visual_mode"]).isEqualTo("classic")
    }

    @Test
    fun sessionDeduplicatesPerDestinationAndDoesNotBackfillOtherEventsOnConsent() = runTest {
        val fixture = Fixture()
        fixture.controller.update(DiagnosticsConsent(analytics = true))
        fixture.observability.foregroundSession(1)
        fixture.observability.foregroundSession(1)
        fixture.controller.update(DiagnosticsConsent(analytics = true, marketing = true))
        fixture.observability.foregroundSession(1)

        assertThat(fixture.mixpanel.events.map { it.first }).containsExactly("session_started")
        assertThat(fixture.meta.events.map { it.first }).containsExactly("session_started")
        fixture.observability.foregroundSession(2)
        assertThat(fixture.meta.events).hasSize(2)
    }

    @Test
    fun withdrawalStopsMetaAndCrashOnlyConsentStillGetsBreadcrumbs() = runTest {
        val fixture = Fixture()
        fixture.controller.update(DiagnosticsConsent(marketing = true))
        fixture.observability.track(ClaroEvent.SetupCompleted)
        fixture.controller.withdrawAll()
        fixture.observability.track(ClaroEvent.SetupCompleted)
        assertThat(fixture.meta.events).isEmpty()
        assertThat(fixture.meta.enabled).isFalse()
        fixture.controller.update(DiagnosticsConsent(crashReports = true))
        fixture.observability.track(ClaroEvent.TaskFlowResult(EventTask.MESSAGE_SUBMISSION, EventOutcome.FAILED))
        assertThat(fixture.crash.breadcrumbs).containsExactly("task_flow_result")
        assertThat(fixture.mixpanel.events).isEmpty()
    }

    private class Fixture {
        val store = MemoryConsentStore()
        val mixpanel = FakeAnalyticsVendor(AnalyticsDestination.MIXPANEL)
        val firebase = FakeAnalyticsVendor(AnalyticsDestination.FIREBASE)
        val meta = FakeAnalyticsVendor(AnalyticsDestination.META)
        val crash = FakeCrashVendor()
        val inspector = RecordingInspector()
        val identity = MemoryIdentityStore()
        val controller = DefaultConsentController(store, setOf(mixpanel, firebase, meta), crash, identity)
        val observability =
            ConsentAwareObservability(controller, setOf(mixpanel, firebase, meta), crash, inspector)
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
    var userId: String? = null
    val events = mutableListOf<Pair<String, Map<String, String>>>()
    var sharedContext: Map<String, String> = emptyMap()

    override fun context(properties: Map<String, String>) {
        if (enabled) sharedContext = properties
    }

    override fun setCollectionEnabled(enabled: Boolean, userId: String?) {
        this.enabled = enabled
        this.userId = userId
    }

    override fun track(name: String, properties: Map<String, String>) {
        if (enabled) events += name to properties
    }

    override fun reset() {
        resetCount += 1
        events.clear()
        sharedContext = emptyMap()
    }
}

private class FakeCrashVendor : CrashVendor {
    var enabled = true
    var deleteCount = 0
    val recorded = mutableListOf<Throwable>()
    val breadcrumbs = mutableListOf<String>()
    var userId: String? = null

    override fun breadcrumb(name: String, properties: Map<String, String>) {
        if (enabled) breadcrumbs += name
    }

    override fun setCollectionEnabled(enabled: Boolean, userId: String?) {
        this.enabled = enabled
        this.userId = userId
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

private class MemoryIdentityStore : TelemetryIdentityStore {
    var creationCount = 0
        private set
    var failPersistence = false
    private var id: String? = null

    override fun getExisting(): String? = id

    override fun getOrCreate(): String? {
        if (failPersistence) return null
        return id ?: "installation-${++creationCount}".also { id = it }
    }

    override fun clear() {
        id = null
    }
}
