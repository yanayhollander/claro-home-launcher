package com.clarohome.feature.communication

import com.clarohome.core.model.VisualMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneContactsCoordinatorTest {
    @Test
    fun openingPhoneShowsUnreviewedMissedCallsWithoutMarkingThemRead() = runTest {
        val fixture = fixture()
        fixture.telecom.mutableEvents.value = endedCall().copy(
            direction = CallDirection.INCOMING,
            outcome = CallOutcome.MISSED_INCOMING,
        )
        runCurrent()
        fixture.coordinator.openDialer(showMissedCalls = true)
        runCurrent()

        assertThat(fixture.coordinator.state.value.route).isEqualTo(CommunicationRoute.DIALER)
        assertThat(fixture.coordinator.state.value.showMissedCalls).isTrue()
        assertThat(fixture.coordinator.state.value.missedIncomingCount).isEqualTo(1)
        assertThat(fixture.coordinator.state.value.history.single().isOpened).isFalse()

        fixture.coordinator.openHistoryEntry("call-1")
        runCurrent()
        assertThat(fixture.coordinator.state.value.missedIncomingCount).isEqualTo(0)
        assertThat(fixture.coordinator.state.value.history.single().isOpened).isTrue()
    }

    @Test
    fun restartingDialerWithSameFirstDigitRefreshesSuggestions() = runTest {
        val contacts = FakeContactsGateway(
            listOf(ClaroContact("mira", "Mira", numbers = listOf(ContactNumber("2125550101", "Mobile")))),
        )
        val fixture = fixture(contacts = contacts)
        fixture.coordinator.inputDigit('2')
        advanceTimeBy(200)
        runCurrent()

        fixture.coordinator.openDialer()
        fixture.coordinator.inputDigit('2')
        advanceTimeBy(200)
        runCurrent()

        assertThat(contacts.matchingQueries).containsExactly("2", "2").inOrder()
        assertThat(fixture.coordinator.state.value.dialerMatches.map { it.displayName }).containsExactly("Mira")
    }

    @Test
    fun typingNarrowsExistingMatchesImmediatelyAndBackspaceRestoresCachedRows() = runTest {
        val contacts = FakeContactsGateway(
            listOf(
                ClaroContact("mira", "Mira", numbers = listOf(ContactNumber("2125550101", "Mobile"))),
                ClaroContact("alex", "Alex", numbers = listOf(ContactNumber("2335550199", "Mobile"))),
            ),
        )
        val fixture = fixture(contacts = contacts)
        fixture.coordinator.inputDigit('2')
        advanceTimeBy(200)
        runCurrent()
        val original = fixture.coordinator.state.value.dialerMatches
        assertThat(original).hasSize(2)

        val gate = CompletableDeferred<Unit>()
        contacts.matchingGate = gate
        fixture.coordinator.inputDigit('1')
        assertThat(fixture.coordinator.state.value.dialerMatches.map { it.displayName }).containsExactly("Mira")
        advanceTimeBy(200)
        runCurrent()
        assertThat(fixture.coordinator.state.value.dialerMatches.map { it.displayName }).containsExactly("Mira")
        assertThat(fixture.coordinator.state.value.dialerMatchesLoading).isTrue()

        fixture.coordinator.backspace()
        assertThat(fixture.coordinator.state.value.dialerMatches).containsExactlyElementsIn(original).inOrder()
        gate.complete(Unit)
        advanceTimeBy(200)
        runCurrent()
        assertThat(fixture.coordinator.state.value.dialerMatches).containsExactlyElementsIn(original).inOrder()
        assertThat(fixture.coordinator.state.value.dialerMatchesLoading).isFalse()
    }

    @Test
    fun automaticAudioRequiresAFarSensorReadingAndPreservesHeadsetsAndManualChoice() {
        assertThat(automaticCallRoute(true, false)).isEqualTo(AudioRoute.SPEAKER)
        assertThat(automaticCallRoute(true, true)).isEqualTo(AudioRoute.EARPIECE)
        assertThat(automaticCallRoute(true, null)).isEqualTo(AudioRoute.EARPIECE)
        assertThat(automaticCallRoute(false, false)).isEqualTo(AudioRoute.EARPIECE)
        assertThat(automaticCallRoute(true, false, externalAudio = true)).isNull()
        assertThat(automaticCallRoute(true, false, manuallyControlled = true)).isNull()
    }

    @Test
    fun dialerMatchesPartialNumbersAndSelectingKeepsNamePhotoAndFullNumber() = runTest {
        val photo = "content://com.android.contacts/contacts/1/photo"
        val contacts = FakeContactsGateway(
            listOf(
                ClaroContact("mira", "Mira", photo, listOf(ContactNumber("202-555-0101", "Mobile"))),
                ClaroContact("alex", "Alex", numbers = listOf(ContactNumber("303 555 0199", "Mobile"))),
            ),
        )
        val fixture = fixture(contacts = contacts)
        "555".forEach(fixture.coordinator::inputDigit)
        advanceTimeBy(200)
        runCurrent()
        assertThat(fixture.coordinator.state.value.dialerMatches.map { it.displayName })
            .containsExactly("Mira", "Alex")
        val identity = fixture.coordinator.state.value.dialerMatches.first()
        fixture.coordinator.selectDialerContact(identity)
        runCurrent()
        assertThat(fixture.coordinator.state.value.digits).isEqualTo("2025550101")
        assertThat(fixture.coordinator.state.value.pendingIdentity?.photoUri).isEqualTo(photo)
        assertThat(fixture.telecom.actions).isEmpty()

        fixture.coordinator.inputDigit('9')
        assertThat(fixture.coordinator.state.value.pendingIdentity).isNull()
        fixture.coordinator.selectDialerContact(identity)
        fixture.coordinator.requestEnteredNumberCall()
        runCurrent()
        assertThat(fixture.coordinator.state.value.pendingIdentity?.displayName).isEqualTo("Mira")
        assertThat(fixture.coordinator.state.value.pendingIdentity?.photoUri).isEqualTo(photo)
        assertThat(fixture.telecom.actions).containsExactly("place:2025550101")
    }

    @Test
    fun clearingDigitsOrLeavingDialerDiscardsPendingNumberSuggestions() = runTest {
        val gate = CompletableDeferred<Unit>()
        val contacts = FakeContactsGateway(
            listOf(ClaroContact("mira", "Mira", numbers = listOf(ContactNumber("2025550101", "Mobile")))),
        ).apply { matchingGate = gate }
        val fixture = fixture(contacts = contacts)
        fixture.coordinator.inputDigit('2')
        advanceTimeBy(200)
        runCurrent()
        fixture.coordinator.backspace()
        gate.complete(Unit)
        runCurrent()
        assertThat(fixture.coordinator.state.value.dialerMatches).isEmpty()
        assertThat(fixture.coordinator.state.value.dialerMatchesLoading).isFalse()

        fixture.coordinator.inputDigit('2')
        fixture.coordinator.openContacts()
        advanceTimeBy(200)
        runCurrent()
        assertThat(fixture.coordinator.state.value.route).isEqualTo(CommunicationRoute.CONTACTS)
        assertThat(fixture.coordinator.state.value.dialerMatches).isEmpty()
        assertThat(contacts.matchingQueries).containsExactly("2")
    }

    @Test
    fun backFromDialerContactsRestoresTheEnteredNumberButDirectContactsDoesNotReturnToDialer() = runTest {
        val fixture = fixture()
        val coordinator = fixture.coordinator
        coordinator.openDialer()
        "202".forEach(coordinator::inputDigit)
        coordinator.openContacts(fromDialer = true)
        coordinator.returnFromContactsToDialer()
        assertThat(coordinator.state.value.route).isEqualTo(CommunicationRoute.DIALER)
        assertThat(coordinator.state.value.digits).isEqualTo("202")

        coordinator.openContacts(fromDialer = true)
        coordinator.openContacts(fromDialer = false)
        coordinator.returnFromContactsToDialer()
        assertThat(coordinator.state.value.route).isEqualTo(CommunicationRoute.CONTACTS)
    }

    @Test
    fun internationalPrefixIsKeptOnlyAtTheStartOfADialedNumber() = runTest {
        val coordinator = fixture().coordinator
        "+972+".forEach(coordinator::inputDigit)
        assertThat(coordinator.state.value.digits).isEqualTo("+972")
    }

    @Test
    fun inCallDigitsSendTonesWithoutSearchingContacts() = runTest {
        val fixture = fixture()
        fixture.telecom.mutableEvents.value = dialingCall().copy(
            phase = CallPhase.CONNECTED,
            capabilities = CallCapabilities(canSendDtmf = true),
        )
        runCurrent()
        fixture.coordinator.inputDigit('2')
        fixture.coordinator.inputDigit('+')
        advanceTimeBy(200)
        runCurrent()
        assertThat(fixture.contacts.matchingQueries).isEmpty()
        assertThat(fixture.telecom.actions).containsExactly("dtmf:outgoing:2")
    }

    @Test
    fun endingBeforeSubmissionCancelsWithoutPlacingOrRecordingCall() = runTest {
        val fixture = fixture()
        fixture.coordinator.requestCall(CallerIdentity("Mira", "2025550101"))
        fixture.coordinator.end()
        runCurrent()

        assertThat(fixture.telecom.placements).isEqualTo(0)
        assertThat(fixture.coordinator.state.value.route).isEqualTo(CommunicationRoute.DIALER)
        assertThat(fixture.coordinator.state.value.hasActiveCall).isFalse()
        assertThat(fixture.history.history.value).isEmpty()

        fixture.coordinator.requestCall(CallerIdentity("Mira", "2025550101"))
        runCurrent()
        assertThat(fixture.telecom.placements).isEqualTo(1)
    }

    @Test
    fun endingWhilePlacementIsPendingWaitsForMatchingOutgoingCall() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fixture = fixture(telecom = FakeTelecomGateway().apply { placementGate = gate })
        fixture.coordinator.requestCall(CallerIdentity("Mira", "2025550101"))
        runCurrent()
        fixture.coordinator.openContacts()
        fixture.coordinator.openDialer()
        assertThat(fixture.coordinator.state.value.route).isEqualTo(CommunicationRoute.ACTIVE_CALL)
        fixture.coordinator.end()

        fixture.telecom.mutableEvents.value = TelecomEvent.Incoming(
            callId = "incoming",
            identity = CallerIdentity("Alex", "2025550199"),
            capabilities = CallCapabilities(),
        )
        runCurrent()
        fixture.coordinator.end()
        runCurrent()
        assertThat(fixture.telecom.actions).doesNotContain("disconnect:incoming")

        gate.complete(Unit)
        runCurrent()
        fixture.telecom.mutableEvents.value = dialingCall()
        runCurrent()

        assertThat(fixture.telecom.actions)
            .containsExactly("place:2025550101", "disconnect:outgoing").inOrder()
        assertThat(fixture.coordinator.state.value.call?.phase).isEqualTo(CallPhase.DIALING)
        assertThat(fixture.history.history.value).isEmpty()

        fixture.telecom.mutableEvents.value = dialingCall().copy(audioRoute = AudioRoute.SPEAKER)
        runCurrent()
        assertThat(fixture.telecom.actions.count { it == "disconnect:outgoing" }).isEqualTo(1)
        fixture.telecom.mutableEvents.value = endedCall().copy(
            callId = "outgoing",
            outcome = CallOutcome.CANCELED_OUTGOING,
            connectedAtElapsedMillis = null,
        )
        runCurrent()
        assertThat(fixture.coordinator.state.value.hasActiveCall).isFalse()
        assertThat(fixture.history.history.value.single().outcome)
            .isEqualTo(CallOutcome.CANCELED_OUTGOING)
    }

    @Test
    fun endingAfterSubmissionQueuesHangupUntilCallIdArrives() = runTest {
        val fixture = fixture()
        fixture.coordinator.requestCall(CallerIdentity("Mira", "2025550101"))
        runCurrent()
        fixture.coordinator.end()
        runCurrent()
        assertThat(fixture.coordinator.state.value.hasActiveCall).isTrue()

        fixture.telecom.mutableEvents.value = dialingCall()
        runCurrent()
        assertThat(fixture.telecom.actions).contains("disconnect:outgoing")
    }

    @Test
    fun queuedHangupWorksWhenOutgoingHandleIsNotYetAvailable() = runTest {
        val fixture = fixture()
        fixture.coordinator.requestCall(CallerIdentity("Mira", "2025550101"))
        runCurrent()
        fixture.coordinator.end()
        fixture.telecom.mutableEvents.value = dialingCall().copy(identity = CallerIdentity(null, null))
        runCurrent()

        assertThat(fixture.telecom.actions).contains("disconnect:outgoing")
        assertThat(fixture.coordinator.state.value.awaitingOutgoingCall).isFalse()
        assertThat(fixture.coordinator.state.value.call?.identity)
            .isEqualTo(CallerIdentity("Mira", "2025550101"))
    }

    @Test
    fun disconnectKeepsSurvivingConnectedHeldAndIncomingCallsReachable() = runTest {
        for (phase in listOf(CallPhase.CONNECTED, CallPhase.ON_HOLD, CallPhase.INCOMING)) {
            val fixture = fixture()
            val survivor = CallSession(
                id = "survivor",
                direction = CallDirection.INCOMING,
                identity = CallerIdentity("Alex", "2025550199"),
                phase = phase,
                capabilities = CallCapabilities(canHold = true),
                audioRoute = AudioRoute.SPEAKER,
                isMuted = true,
                connectedAtElapsedMillis = 5_000,
            )
            fixture.telecom.mutableEvents.value = endedCall().copy(remainingCalls = listOf(survivor))
            runCurrent()

            assertThat(fixture.coordinator.state.value.call).isEqualTo(survivor)
            assertThat(fixture.coordinator.state.value.hasActiveCall).isTrue()
            assertThat(fixture.coordinator.state.value.route).isEqualTo(
                if (phase == CallPhase.INCOMING) CommunicationRoute.INCOMING_CALL
                else CommunicationRoute.ACTIVE_CALL,
            )
            assertThat(fixture.history.history.value.single().id).isEqualTo("call-1")
            if (phase == CallPhase.INCOMING) fixture.coordinator.answer() else fixture.coordinator.end()
            runCurrent()
            assertThat(fixture.telecom.actions).contains(
                if (phase == CallPhase.INCOMING) "answer:survivor" else "disconnect:survivor",
            )
        }
    }

    @Test
    fun consecutiveDisconnectsAreRecordedWithoutHidingRemainingCall() = runTest {
        val fixture = fixture()
        val survivor = CallSession(
            id = "survivor",
            direction = CallDirection.OUTGOING,
            identity = CallerIdentity("Alex", "2025550199"),
            phase = CallPhase.ON_HOLD,
        )
        fixture.telecom.mutableEvents.value = endedCall().copy(remainingCalls = listOf(survivor))
        fixture.telecom.mutableEvents.value = endedCall().copy(
            callId = "call-2",
            remainingCalls = listOf(survivor),
        )
        runCurrent()

        assertThat(fixture.history.history.value.map { it.id }).containsExactly("call-1", "call-2")
        assertThat(fixture.coordinator.state.value.call).isEqualTo(survivor)
    }

    @Test
    fun retainedDisconnectRestoresRemainingCallWithoutShowingEnded() = runTest {
        val survivor = CallSession(
            id = "survivor",
            direction = CallDirection.OUTGOING,
            identity = CallerIdentity("Alex", "2025550199"),
            phase = CallPhase.CONNECTED,
        )
        val fixture = fixture(
            telecom = FakeTelecomGateway().apply {
                mutableEvents.value = endedCall().copy(remainingCalls = listOf(survivor))
            },
        )
        assertThat(fixture.coordinator.state.value.call).isEqualTo(survivor)
        assertThat(fixture.coordinator.state.value.route).isEqualTo(CommunicationRoute.ACTIVE_CALL)
    }

    @Test
    fun missingPhoneStatePermissionIsNotReportedAsMissingAccount() = runTest {
        val fixture = fixture()
        fixture.role.mutableQualification.value = readyQualification().copy(
            phoneStatePermissionGranted = false,
            hasUsableCallingAccount = false,
        )
        runCurrent()
        fixture.coordinator.requestCall(CallerIdentity("Mira", "2025550101"))
        runCurrent()

        assertThat(fixture.coordinator.state.value.failure).isEqualTo(CallFailure.PERMISSION_DENIED)
        assertThat(fixture.telecom.placements).isEqualTo(0)

        fixture.role.mutableQualification.value = readyQualification().copy(
            hasUsableCallingAccount = false,
        )
        runCurrent()
        fixture.coordinator.requestCall(CallerIdentity("Mira", "2025550101"))
        runCurrent()
        assertThat(fixture.coordinator.state.value.failure).isEqualTo(CallFailure.ACCOUNT_UNAVAILABLE)
    }

    @Test
    fun callerPhotoUpdatesDoNotSwitchCallsOrLeaveContacts() = runTest {
        val fixture = fixture()
        fixture.telecom.mutableEvents.value = dialingCall().copy(phase = CallPhase.CONNECTED)
        runCurrent()
        fixture.coordinator.openContacts()

        fixture.telecom.mutableEvents.value = dialingCall().copy(
            callId = "held",
            phase = CallPhase.ON_HOLD,
            foreground = false,
        )
        runCurrent()
        assertThat(fixture.coordinator.state.value.call?.id).isEqualTo("outgoing")
        assertThat(fixture.coordinator.state.value.route).isEqualTo(CommunicationRoute.CONTACTS)

        val photo = "content://com.android.contacts/contacts/1/photo"
        fixture.telecom.mutableEvents.value = dialingCall().copy(
            phase = CallPhase.CONNECTED,
            identity = CallerIdentity("Mira", "2025550101", photoUri = photo),
            foreground = false,
        )
        runCurrent()
        assertThat(fixture.coordinator.state.value.call?.identity?.photoUri).isEqualTo(photo)
        assertThat(fixture.coordinator.state.value.route).isEqualTo(CommunicationRoute.CONTACTS)
    }

    @Test
    fun contactPhotoSurvivesTelecomIdentityUpdatesAndDisconnectHistory() = runTest {
        val fixture = fixture()
        val identity = CallerIdentity(
            "Mira",
            "2025550101",
            photoUri = "content://com.android.contacts/contacts/1/photo",
        )
        fixture.coordinator.requestCall(identity)
        runCurrent()
        fixture.telecom.mutableEvents.value = dialingCall().copy(
            identity = CallerIdentity(null, "2025550101"),
        )
        runCurrent()
        assertThat(fixture.coordinator.state.value.call?.identity).isEqualTo(identity)

        fixture.telecom.mutableEvents.value = endedCall().copy(callId = "outgoing")
        runCurrent()
        assertThat(fixture.history.history.value.single().identity.photoUri).isEqualTo(identity.photoUri)
    }

    private fun dialingCall() = TelecomEvent.StateChanged(
        callId = "outgoing",
        direction = CallDirection.OUTGOING,
        identity = CallerIdentity("Mira", "2025550101"),
        phase = CallPhase.DIALING,
        capabilities = CallCapabilities(),
        audioRoute = AudioRoute.EARPIECE,
        isMuted = false,
        connectedAtElapsedMillis = null,
    )

    @Test
    fun openingPhoneWhileConnectedRestoresCallInsteadOfOpeningFreshDialer() = runTest {
        val fixture = fixture()
        fixture.telecom.mutableEvents.value = TelecomEvent.StateChanged(
            callId = "active",
            direction = CallDirection.OUTGOING,
            identity = CallerIdentity("Mira", "2025550101"),
            phase = CallPhase.CONNECTED,
            capabilities = CallCapabilities(),
            audioRoute = AudioRoute.EARPIECE,
            isMuted = false,
            connectedAtElapsedMillis = 1_000,
        )
        runCurrent()

        fixture.coordinator.openContacts()
        fixture.coordinator.openDialer()
        runCurrent()

        assertThat(fixture.coordinator.state.value.route)
            .isEqualTo(CommunicationRoute.ACTIVE_CALL)
        assertThat(fixture.coordinator.state.value.call?.id).isEqualTo("active")
        assertThat(fixture.telecom.actions).isEmpty()
    }

    @Test
    fun reopeningDialerClearsPreviousNumberBeforeNewEntry() = runTest {
        val fixture = fixture()
        "2025550101".forEach(fixture.coordinator::inputDigit)

        fixture.coordinator.openDialer()

        assertThat(fixture.coordinator.state.value.digits).isEmpty()
        fixture.coordinator.inputDigit('5')
        assertThat(fixture.coordinator.state.value.digits).isEqualTo("5")
    }

    @Test
    fun reopeningDialerClearsPendingCallWithoutPlacingIt() = runTest {
        val fixture = fixture(
            savedState = PhoneSavedState(
                route = CommunicationRoute.CALL_CONFIRMATION,
                digits = "2025550101",
                pendingIdentity = CallerIdentity("Mira", "2025550101"),
            ),
        )

        fixture.coordinator.openDialer()
        runCurrent()

        assertThat(fixture.coordinator.state.value.route).isEqualTo(CommunicationRoute.DIALER)
        assertThat(fixture.coordinator.state.value.digits).isEmpty()
        assertThat(fixture.coordinator.state.value.pendingIdentity).isNull()
        assertThat(fixture.coordinator.snapshot().digits).isEmpty()
        assertThat(fixture.telecom.placements).isEqualTo(0)
    }

    @Test
    fun callActionDialsImmediatelyAndRepeatedTapsSubmitOneCall() = runTest {
        val fixture = fixture()
        fixture.coordinator.requestCall(CallerIdentity("Mira", "202-555-0101"))
        fixture.coordinator.requestCall(CallerIdentity("Mira", "202-555-0101"))

        assertThat(fixture.coordinator.state.value.route)
            .isEqualTo(CommunicationRoute.ACTIVE_CALL)

        runCurrent()
        fixture.coordinator.requestCall(CallerIdentity("Mira", "202-555-0101"))
        runCurrent()

        assertThat(fixture.telecom.placements).isEqualTo(1)
        assertThat(fixture.telecom.actions).containsExactly("place:2025550101")
    }

    @Test
    fun enteredNumberDialsWithoutConfirmation() = runTest {
        val fixture = fixture()
        "2025550101".forEach(fixture.coordinator::inputDigit)

        fixture.coordinator.requestEnteredNumberCall()
        runCurrent()

        assertThat(fixture.coordinator.state.value.route)
            .isEqualTo(CommunicationRoute.ACTIVE_CALL)
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

        fixture.telecom.mutableEvents.value = endedCall()
        runCurrent()
        fixture.telecom.emergencyNumbers += "112"
        fixture.coordinator.requestCall(CallerIdentity(null, "112"))
        runCurrent()

        assertThat(fixture.telecom.emergencyHandoffs).isEqualTo(1)
        assertThat(fixture.coordinator.state.value.route)
            .isEqualTo(CommunicationRoute.EXTERNAL_HANDOFF)
    }

    @Test
    fun redialClearsDisconnectedSessionUntilNewTelecomEventsArrive() = runTest {
        val fixture = fixture()
        fixture.telecom.mutableEvents.value = endedCall()
        runCurrent()
        assertThat(fixture.coordinator.state.value.route)
            .isEqualTo(CommunicationRoute.CALL_ENDED)

        val nextCaller = CallerIdentity("Alex", "2025550199")
        fixture.coordinator.requestCall(nextCaller)
        runCurrent()

        assertThat(fixture.coordinator.state.value.route)
            .isEqualTo(CommunicationRoute.ACTIVE_CALL)
        assertThat(fixture.coordinator.state.value.call).isNull()
        assertThat(fixture.coordinator.state.value.pendingIdentity).isEqualTo(nextCaller)
        assertThat(fixture.coordinator.state.value.failure).isNull()
        assertThat(fixture.telecom.actions).containsExactly("place:2025550199")
    }

    @Test
    fun reopeningDialerAndHistoryDoNotRedisplayEndedCall() = runTest {
        val fixture = fixture()
        fixture.telecom.mutableEvents.value = endedCall()
        runCurrent()

        fixture.coordinator.openDialer()
        assertThat(fixture.coordinator.state.value.route).isEqualTo(CommunicationRoute.DIALER)
        assertThat(fixture.coordinator.state.value.call).isNull()

        fixture.coordinator.openHistoryEntry("call-1")

        assertThat(fixture.coordinator.state.value.route).isEqualTo(CommunicationRoute.DIALER)
        assertThat(fixture.coordinator.state.value.digits).isEqualTo("2025550101")
        assertThat(fixture.coordinator.state.value.call).isNull()
        assertThat(fixture.telecom.placements).isEqualTo(0)
    }

    @Test
    fun restoringEndedScreenAndRetainedDisconnectDoesNotShowEndedAgain() = runTest {
        val fixture = fixture(
            telecom = FakeTelecomGateway().apply { mutableEvents.value = endedCall() },
            savedState = PhoneSavedState(route = CommunicationRoute.CALL_ENDED),
        )
        runCurrent()

        assertThat(fixture.coordinator.state.value.route).isEqualTo(CommunicationRoute.DIALER)
        assertThat(fixture.coordinator.state.value.call).isNull()
        assertThat(fixture.telecom.placements).isEqualTo(0)
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

    private fun endedCall() = TelecomEvent.Disconnected(
        callId = "call-1",
        direction = CallDirection.OUTGOING,
        identity = CallerIdentity("Mira", "2025550101"),
        outcome = CallOutcome.ENDED,
        failure = null,
        connectedAtElapsedMillis = 1_000,
        disconnectedAtElapsedMillis = 61_000,
    )

    private fun TestScope.fixture(
        clock: FakeElapsedRealtimeClock = FakeElapsedRealtimeClock(),
        contacts: FakeContactsGateway = FakeContactsGateway(),
        assignments: FakePhoneAssignmentStore = FakePhoneAssignmentStore(),
        savedState: PhoneSavedState = PhoneSavedState(),
        telecom: FakeTelecomGateway = FakeTelecomGateway(),
    ): Fixture {
        val role = FakeDialerRoleGateway()
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
