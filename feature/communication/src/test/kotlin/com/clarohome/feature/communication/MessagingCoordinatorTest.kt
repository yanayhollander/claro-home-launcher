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
class MessagingCoordinatorTest {
    @Test
    fun roleLossDisablesSendAndExposesRepairWithoutFalseUnreadZero() = runTest {
        val fixture = fixture()
        fixture.role.mutableQualification.value = readySmsQualification().copy(
            roleHeld = false,
            readPermissionGranted = false,
        )
        runCurrent()

        fixture.coordinator.startNewMessage()
        fixture.coordinator.enterManualRecipient("+15550101")
        fixture.coordinator.updateBody("Hello")
        fixture.coordinator.review()

        assertThat(fixture.coordinator.state.value.route).isEqualTo(MessagingRoute.COMPOSE)
        assertThat(fixture.coordinator.state.value.statusMessage)
            .isEqualTo("Claro must be the default SMS app")
        assertThat(fixture.coordinator.state.value.actionsForMessaging(VisualMode.TOUCH))
            .contains(MessagingAction.HOME)
    }

    @Test
    fun providerReconciliationDeduplicatesRepeatedBroadcasts() = runTest {
        val repository = FakeMessageRepository()
        val message = ClaroMessage(
            providerId = 42,
            conversationId = 7,
            clientMessageId = null,
            direction = MessageDirection.INCOMING,
            senderOrRecipient = "+15550101",
            body = "Hello",
            timestampMillis = 1_000,
            isRead = false,
            deliveryState = MessageDeliveryState.RECEIVED,
        )

        repository.reconcileIncoming(listOf(message))
        repository.reconcileIncoming(listOf(message))

        assertThat(repository.storedMessages(7)).containsExactly(message)
    }

    @Test
    fun unicodeAndMultipartLengthAreReportedAccurately() {
        val hebrew = SmsLengthCalculator.calculate("א".repeat(71))
        val spanishGsm = SmsLengthCalculator.calculate("Hola señor")
        val extended = SmsLengthCalculator.calculate("^".repeat(81))

        assertThat(hebrew.encoding).isEqualTo(SmsEncoding.UNICODE)
        assertThat(hebrew.segments).isEqualTo(2)
        assertThat(spanishGsm.encoding).isEqualTo(SmsEncoding.GSM_7_BIT)
        assertThat(spanishGsm.segments).isEqualTo(1)
        assertThat(extended.segments).isEqualTo(2)
    }

    @Test
    fun draftSurvivesNavigationAndProcessRestorationWithoutSending() = runTest {
        val drafts = FakeMessageDraftStore()
        val first = fixture(drafts = drafts)
        first.coordinator.startNewMessage()
        first.coordinator.enterManualRecipient("+15550101")
        first.coordinator.updateBody("Saved across process death")
        runCurrent()
        val saved = first.coordinator.snapshot()

        val restored = fixture(drafts = drafts, savedState = saved)
        runCurrent()

        assertThat(restored.coordinator.state.value.draft.body)
            .isEqualTo("Saved across process death")
        assertThat(restored.coordinator.state.value.draft.recipient?.number)
            .isEqualTo("+15550101")
        assertThat(restored.transport.submissions).isEqualTo(0)
    }

    @Test
    fun recipientWithMultipleNumbersRequiresAndRetainsExplicitChoice() = runTest {
        val contact = ClaroContact(
            lookupKey = "mira",
            displayName = "Mira",
            numbers = listOf(
                ContactNumber("+15550101", "Mobile"),
                ContactNumber("+15550202", "Home"),
            ),
        )
        val fixture = fixture(contacts = FakeContactsGateway(listOf(contact)))
        fixture.coordinator.startNewMessage()
        fixture.coordinator.searchRecipients("Mira")
        runCurrent()

        fixture.coordinator.chooseContactNumber(contact, contact.numbers[1])

        assertThat(fixture.coordinator.state.value.draft.recipient?.number)
            .isEqualTo("+15550202")
        assertThat(fixture.coordinator.state.value.draft.recipient?.label).isEqualTo("Home")
    }

    @Test
    fun openingInboxDoesNotClearUnreadAndConversationClearsOnlyItself() = runTest {
        val repository = FakeMessageRepository(
            initialConversations = listOf(
                conversationFixture(1, unreadCount = 2),
                conversationFixture(2, unreadCount = 3),
            ),
        )
        val fixture = fixture(repository = repository)
        runCurrent()

        fixture.coordinator.openInbox()
        runCurrent()
        assertThat(repository.mutableUnreadCount.value).isEqualTo(5)

        fixture.coordinator.openConversation(1)
        runCurrent()
        assertThat(repository.mutableConversations.value.first { it.id == 1L }.unreadCount)
            .isEqualTo(0)
        assertThat(repository.mutableConversations.value.first { it.id == 2L }.unreadCount)
            .isEqualTo(3)
    }

    @Test
    fun unknownOutcomeRequiresDuplicateRiskConfirmationBeforeRetry() = runTest {
        val fixture = fixture()
        prepareSend(fixture)
        val draftId = fixture.coordinator.state.value.draft.id
        fixture.coordinator.send()
        runCurrent()
        fixture.transport.mutableEvents.value = SmsTransportEvent.Failed(
            clientMessageId = draftId,
            providerId = 10,
            failure = MessageFailure.UNKNOWN,
            outcomeMayBeUnknown = true,
        )
        runCurrent()

        fixture.coordinator.retry()
        assertThat(fixture.transport.submissions).isEqualTo(1)
        assertThat(fixture.coordinator.state.value.duplicateRiskConfirmationRequired).isTrue()

        fixture.coordinator.confirmDuplicateRiskAndSendAgain()
        runCurrent()
        assertThat(fixture.transport.submissions).isEqualTo(2)
    }

    @Test
    fun repeatedSendAndResultCallbacksAreIdempotent() = runTest {
        val fixture = fixture()
        prepareSend(fixture)
        val draftId = fixture.coordinator.state.value.draft.id

        fixture.coordinator.send()
        fixture.coordinator.send()
        runCurrent()
        assertThat(fixture.transport.submissions).isEqualTo(1)

        val sent = SmsTransportEvent.Sent(draftId, 10)
        fixture.transport.mutableEvents.value = sent
        runCurrent()
        fixture.transport.mutableEvents.value = null
        runCurrent()
        fixture.transport.mutableEvents.value = sent
        runCurrent()

        assertThat(fixture.repository.updates.count { it.second == MessageDeliveryState.SENT })
            .isEqualTo(1)
    }

    @Test
    fun successfulSendClearsDraftBodyAndRecipientQuery() = runTest {
        val fixture = fixture()
        prepareSend(fixture)
        val draftId = fixture.coordinator.state.value.draft.id

        fixture.coordinator.send()
        runCurrent()
        fixture.transport.mutableEvents.value = SmsTransportEvent.Sent(draftId, 10)
        runCurrent()

        assertThat(fixture.coordinator.state.value.draft.body).isEmpty()
        assertThat(fixture.coordinator.state.value.recipientQuery).isEmpty()
    }

    @Test
    fun touchAndClassicExposeEquivalentMessagingActions() = runTest {
        val fixture = fixture()
        fixture.coordinator.startNewMessage()
        fixture.coordinator.enterManualRecipient("+15550101")
        fixture.coordinator.updateBody("Hello")

        val state = fixture.coordinator.state.value
        assertThat(state.actionsForMessaging(VisualMode.TOUCH))
            .containsExactlyElementsIn(state.actionsForMessaging(VisualMode.CLASSIC))
    }

    @Test
    fun logsContainOnlyEnumeratedOutcomesNotRecipientOrMessageBody() = runTest {
        val fixture = fixture()
        prepareSend(fixture, body = "Private medical appointment")
        fixture.coordinator.send()
        runCurrent()

        val rendered = fixture.logger.events.joinToString()
        assertThat(rendered).doesNotContain("+15550101")
        assertThat(rendered).doesNotContain("Private medical appointment")
        assertThat(rendered).contains("submitted")
    }

    private fun TestScope.prepareSend(fixture: Fixture, body: String = "Hello") {
        fixture.coordinator.startNewMessage()
        fixture.coordinator.enterManualRecipient("+15550101")
        fixture.coordinator.updateBody(body)
        fixture.coordinator.review()
    }

    private fun TestScope.fixture(
        role: FakeSmsRoleGateway = FakeSmsRoleGateway(),
        repository: FakeMessageRepository = FakeMessageRepository(),
        drafts: FakeMessageDraftStore = FakeMessageDraftStore(),
        transport: FakeSmsTransport = FakeSmsTransport(),
        contacts: FakeContactsGateway = FakeContactsGateway(),
        savedState: MessagingSavedState = MessagingSavedState(),
    ): Fixture {
        val logger = FakeSensitiveDataSafeLogger()
        val coordinator = MessagingCoordinator(
            roleGateway = role,
            repository = repository,
            draftStore = drafts,
            transport = transport,
            contactsGateway = contacts,
            clock = FakeMessagingClock(),
            logger = logger,
            scope = TestScope(StandardTestDispatcher(testScheduler)),
            savedState = savedState,
        )
        runCurrent()
        return Fixture(coordinator, role, repository, transport, logger)
    }

    private data class Fixture(
        val coordinator: MessagingCoordinator,
        val role: FakeSmsRoleGateway,
        val repository: FakeMessageRepository,
        val transport: FakeSmsTransport,
        val logger: FakeSensitiveDataSafeLogger,
    )
}
