package com.clarohome.feature.communication

import com.clarohome.core.model.VisualMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneContactsCoordinatorTest {
    @Test
    fun repeatedConfirmationTapsSubmitOneCall() = runTest {
        val fixture = fixture()
        fixture.coordinator.requestCall(CallerIdentity("Mira", "202-555-0101"))
        fixture.coordinator.confirmOutgoingCall()
        fixture.coordinator.confirmOutgoingCall()

        runCurrent()

        assertThat(fixture.telecom.placements).isEqualTo(1)
        assertThat(fixture.telecom.actions).containsExactly("place:2025550101")
    }

    @Test
    fun directSpeedDialPlacesOneCallAndEmergencyUsesSystemHandoff() = runTest {
        val assignment = PhoneAssignment(
            slot = "1",
            contactLookupKey = "mira",
            selectedNumberSnapshot = "2025550101",
            displayNameSnapshot = "Mira",
        )
        val fixture = fixture(assignments = FakePhoneAssignmentStore(listOf(assignment)))
        runCurrent()

        fixture.coordinator.directCallAssignment("1")
        fixture.coordinator.directCallAssignment("1")
        runCurrent()

        assertThat(fixture.telecom.placements).isEqualTo(1)

        fixture.telecom.emergencyNumbers += "112"
        fixture.coordinator.requestCall(CallerIdentity(null, "112"))
        fixture.coordinator.confirmOutgoingCall()
        runCurrent()

        assertThat(fixture.telecom.emergencyHandoffs).isEqualTo(1)
        assertThat(fixture.coordinator.state.value.route)
            .isEqualTo(CommunicationRoute.EXTERNAL_HANDOFF)
    }

    @Test
    fun durationStartsOnlyAfterAuthoritativeConnection() = runTest {
        val fixture = fixture(clock = FakeElapsedRealtimeClock(61_000))
        val identity = CallerIdentity("Mira", "2025550101")

        fixture.telecom.mutableEvents.value = TelecomEvent.StateChanged(
            callId = "call-1",
            direction = CallDirection.OUTGOING,
            identity = identity,
            phase = CallPhase.DIALING,
            capabilities = CallCapabilities(),
            audioRoute = AudioRoute.EARPIECE,
            isMuted = false,
            connectedAtElapsedMillis = null,
        )
        runCurrent()
        assertThat(fixture.coordinator.state.value.call?.connectedDurationMillis(20_000)).isNull()

        fixture.telecom.mutableEvents.value = TelecomEvent.StateChanged(
            callId = "call-1",
            direction = CallDirection.OUTGOING,
            identity = identity,
            phase = CallPhase.CONNECTED,
            capabilities = CallCapabilities(canMute = true),
            audioRoute = AudioRoute.EARPIECE,
            isMuted = false,
            connectedAtElapsedMillis = 1_000,
        )
        fixture.telecom.mutableEvents.value = TelecomEvent.Disconnected(
            callId = "call-1",
            direction = CallDirection.OUTGOING,
            identity = identity,
            outcome = CallOutcome.ENDED,
            failure = null,
            connectedAtElapsedMillis = 1_000,
            disconnectedAtElapsedMillis = 61_000,
        )
        runCurrent()

        assertThat(fixture.coordinator.state.value.call?.connectedDurationMillis(100_000))
            .isEqualTo(60_000)
        assertThat(fixture.history.history.value.single().connectedDurationMillis).isEqualTo(60_000)
    }

    @Test
    fun missedIncomingAndOutgoingNoAnswerStayDistinct() = runTest {
        val fixture = fixture()
        val identity = CallerIdentity("Mira", "2025550101")

        fixture.telecom.mutableEvents.value = TelecomEvent.Disconnected(
            callId = "incoming",
            direction = CallDirection.INCOMING,
            identity = identity,
            outcome = CallOutcome.MISSED_INCOMING,
            failure = null,
            connectedAtElapsedMillis = null,
            disconnectedAtElapsedMillis = 1_000,
        )
        runCurrent()
        fixture.telecom.mutableEvents.value = TelecomEvent.Disconnected(
            callId = "outgoing",
            direction = CallDirection.OUTGOING,
            identity = identity,
            outcome = CallOutcome.UNANSWERED_OUTGOING,
            failure = null,
            connectedAtElapsedMillis = null,
            disconnectedAtElapsedMillis = 2_000,
        )
        runCurrent()

        assertThat(fixture.coordinator.state.value.route)
            .isEqualTo(CommunicationRoute.CALL_UNANSWERED)
        assertThat(fixture.coordinator.state.value.missedIncomingCount).isEqualTo(1)
        assertThat(fixture.history.history.value.map { it.outcome })
            .containsExactly(CallOutcome.UNANSWERED_OUTGOING, CallOutcome.MISSED_INCOMING)
            .inOrder()
    }

    @Test
    fun privateCallerHasNoCallbackIdentity() = runTest {
        val fixture = fixture()
        val privateCaller = CallerIdentity(displayName = null, number = null, isPrivate = true)

        fixture.telecom.mutableEvents.value = TelecomEvent.Incoming(
            callId = "private",
            identity = privateCaller,
            capabilities = CallCapabilities(),
        )
        fixture.telecom.mutableEvents.value = TelecomEvent.Disconnected(
            callId = "private",
            direction = CallDirection.INCOMING,
            identity = privateCaller,
            outcome = CallOutcome.MISSED_INCOMING,
            failure = null,
            connectedAtElapsedMillis = null,
            disconnectedAtElapsedMillis = 5_000,
        )
        runCurrent()

        assertThat(fixture.coordinator.state.value.pendingIdentity?.accessibleName)
            .isEqualTo("Private number")
        assertThat(fixture.coordinator.state.value.pendingIdentity?.canReturnCall).isFalse()
    }

    @Test
    fun pagesAndSearchesTwoThousandContactsWithoutLoadingAllAtOnce() = runTest {
        val contacts = (0 until 2_000).map(::contactFixture)
        val fixture = fixture(contacts = FakeContactsGateway(contacts))

        fixture.coordinator.loadContacts()
        assertThat(fixture.coordinator.state.value.contacts).hasSize(50)
        assertThat(fixture.contacts.largestRequestedPage).isEqualTo(50)

        fixture.coordinator.loadContacts(query = "Person 1999")
        assertThat(fixture.coordinator.state.value.contacts.single().displayName)
            .isEqualTo("Person 1999")

        fixture.coordinator.loadContacts(query = "+15550000042")
        assertThat(fixture.coordinator.state.value.contacts.single().lookupKey)
            .isEqualTo("lookup-42")
    }

    @Test
    fun processRestorationNeverPlacesPendingCall() = runTest {
        val first = fixture()
        "2025550101".forEach(first.coordinator::inputDigit)
        first.coordinator.requestEnteredNumberCall()
        val saved = first.coordinator.snapshot()

        val restored = fixture(savedState = saved)
        runCurrent()

        assertThat(restored.coordinator.state.value.digits).isEqualTo("2025550101")
        assertThat(restored.coordinator.state.value.route).isEqualTo(CommunicationRoute.DIALER)
        assertThat(restored.telecom.placements).isEqualTo(0)
    }

    @Test
    fun roleLossDisablesNewCallsAndExposesRepair() = runTest {
        val fixture = fixture()
        fixture.role.mutableQualification.value = readyQualification().copy(roleHeld = false)
        runCurrent()

        fixture.coordinator.requestCall(CallerIdentity("Mira", "2025550101"))
        fixture.coordinator.confirmOutgoingCall()
        runCurrent()

        assertThat(fixture.telecom.placements).isEqualTo(0)
        assertThat(fixture.coordinator.state.value.failure).isEqualTo(CallFailure.ROLE_LOST)
        assertThat(fixture.coordinator.state.value.actionsFor(VisualMode.TOUCH))
            .contains(CommunicationAction.REPAIR)
    }

    @Test
    fun roleLossMakesMissedCountUnknownInsteadOfFalseZero() = runTest {
        val fixture = fixture()
        fixture.role.mutableQualification.value = readyQualification().copy(
            roleHeld = false,
            callLogPermissionGranted = false,
        )
        runCurrent()

        assertThat(fixture.coordinator.state.value.missedIncomingCount).isNull()
    }

    @Test
    fun assignmentReconciliationNeverRedirectsChangedNumber() = runTest {
        val contact = ClaroContact(
            lookupKey = "mira",
            displayName = "Mira",
            numbers = listOf(ContactNumber("2025550199", "Mobile")),
        )
        val assignment = PhoneAssignment(
            slot = "2",
            contactLookupKey = "mira",
            selectedNumberSnapshot = "2025550101",
            displayNameSnapshot = "Mira",
        )
        val assignments = FakePhoneAssignmentStore(listOf(assignment))
        val fixture = fixture(
            contacts = FakeContactsGateway(listOf(contact)),
            assignments = assignments,
        )

        fixture.coordinator.reconcileAssignments()
        runCurrent()
        fixture.coordinator.directCallAssignment("2")
        runCurrent()

        assertThat(assignments.assignments.value.single().status)
            .isEqualTo(AssignmentStatus.NUMBER_CHANGED)
        assertThat(fixture.telecom.placements).isEqualTo(0)
    }

    @Test
    fun touchAndClassicExposeEquivalentCallActions() = runTest {
        val fixture = fixture()
        fixture.telecom.mutableEvents.value = TelecomEvent.StateChanged(
            callId = "active",
            direction = CallDirection.OUTGOING,
            identity = CallerIdentity("Mira", "2025550101"),
            phase = CallPhase.CONNECTED,
            capabilities = CallCapabilities(
                canMute = true,
                canChangeAudioRoute = true,
                canSendDtmf = true,
                canHold = true,
                canSwap = true,
            ),
            audioRoute = AudioRoute.EARPIECE,
            isMuted = false,
            connectedAtElapsedMillis = 1_000,
        )
        runCurrent()

        val state = fixture.coordinator.state.value
        assertThat(state.actionsFor(VisualMode.TOUCH))
            .containsExactlyElementsIn(state.actionsFor(VisualMode.CLASSIC))
    }

    private fun TestScope.fixture(
        clock: FakeElapsedRealtimeClock = FakeElapsedRealtimeClock(),
        contacts: FakeContactsGateway = FakeContactsGateway(),
        assignments: FakePhoneAssignmentStore = FakePhoneAssignmentStore(),
        savedState: PhoneSavedState = PhoneSavedState(),
    ): Fixture {
        val role = FakeDialerRoleGateway()
        val telecom = FakeTelecomGateway()
        val history = FakeCallHistoryStore()
        val coordinator = PhoneContactsCoordinator(
            roleGateway = role,
            telecomGateway = telecom,
            contactsGateway = contacts,
            assignmentStore = assignments,
            historyStore = history,
            clock = clock,
            scope = TestScope(StandardTestDispatcher(testScheduler)),
            savedState = savedState,
        )
        runCurrent()
        return Fixture(
            coordinator = coordinator,
            role = role,
            telecom = telecom,
            contacts = contacts,
            history = history,
        )
    }

    private data class Fixture(
        val coordinator: PhoneContactsCoordinator,
        val role: FakeDialerRoleGateway,
        val telecom: FakeTelecomGateway,
        val contacts: FakeContactsGateway,
        val history: FakeCallHistoryStore,
    )
}
