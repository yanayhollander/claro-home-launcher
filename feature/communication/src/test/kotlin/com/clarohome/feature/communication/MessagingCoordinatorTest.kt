package com.clarohome.feature.communication

import android.provider.Telephony
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import com.clarohome.core.model.VisualMode
import com.clarohome.core.domain.ClaroEvent
import com.clarohome.core.domain.EventOutcome
import com.clarohome.core.domain.EventTask
import com.clarohome.core.domain.ProductAnalytics
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
    fun deliveryTelemetryDoesNotRequireAMessagingScreenAndDeduplicatesCallbacks() = runTest {
        val transport = FakeSmsTransport()
        val events = mutableListOf<ClaroEvent>()
        observeSmsDeliveryTelemetry(transport, object : ProductAnalytics {
            override fun track(event: ClaroEvent) { events += event }
        }, null, backgroundScope)
        runCurrent()

        val sent = SmsTransportEvent.Sent("local-operation", 42)
        transport.mutableEvents.value = sent
        runCurrent()
        transport.mutableEvents.value = SmsTransportEvent.Delivered("local-operation", 42)
        runCurrent()
        transport.mutableEvents.value = sent
        runCurrent()

        assertThat(events).containsExactly(
            ClaroEvent.TaskFlowResult(EventTask.MESSAGE_DELIVERY, EventOutcome.COMPLETED),
            ClaroEvent.TaskFlowResult(EventTask.MESSAGE_DELIVERY, EventOutcome.VERIFIED),
        ).inOrder()
        assertThat(events.toString()).doesNotContain("local-operation")
    }

    @Test
    fun sendFromEditorSubmitsDirectlyAndRepeatedTapsDoNotDuplicate() = runTest {
        val fixture = fixture()
        fixture.coordinator.startNewMessage()
        fixture.coordinator.enterManualRecipient("+15550101")
        fixture.coordinator.updateBody("Hello")
        fixture.coordinator.send()
        assertThat(fixture.coordinator.state.value.route).isEqualTo(MessagingRoute.SENDING)
        fixture.coordinator.send()
        runCurrent()
        assertThat(fixture.transport.submissions).isEqualTo(1)
        assertThat(fixture.transport.sentBodies).containsExactly("Hello")
        assertThat(fixture.events).containsExactly(
            ClaroEvent.TaskFlowResult(EventTask.MESSAGE_SUBMISSION, EventOutcome.STARTED),
            ClaroEvent.TaskFlowResult(EventTask.MESSAGE_SUBMISSION, EventOutcome.SUBMITTED),
        ).inOrder()
    }

    @Test
    fun directSendReportsMissingRecipientOrEmptyBodyWithoutLosingDraft() = runTest {
        val fixture = fixture()
        fixture.coordinator.startNewMessage()
        fixture.coordinator.updateBody("Keep this draft")
        fixture.coordinator.send()
        runCurrent()
        assertThat(fixture.coordinator.state.value.sendFailure).isEqualTo(MessageFailure.INVALID_RECIPIENT)
        assertThat(fixture.coordinator.state.value.route).isEqualTo(MessagingRoute.COMPOSE)
        assertThat(fixture.coordinator.state.value.draft.body).isEqualTo("Keep this draft")
        fixture.coordinator.enterManualRecipient("+15550101")
        fixture.coordinator.updateBody("")
        fixture.coordinator.send()
        runCurrent()
        assertThat(fixture.coordinator.state.value.sendFailure).isEqualTo(MessageFailure.EMPTY_CONTENT)
        assertThat(fixture.transport.submissions).isEqualTo(0)
    }

    @Test
    fun deletingConversationRemovesOnlyItsMessagesAndDraftAndReturnsToInbox() = runTest {
        val conversations = listOf(7L, 8L).map {
            MessageConversation(it, MessageRecipient("202555010$it"), "Hello", 1_000, 1)
        }
        val repository = FakeMessageRepository(initialConversations = conversations)
        val drafts = FakeMessageDraftStore(
            listOf(
                MessageDraft("conversation-7", conversationId = 7, body = "Delete this"),
                MessageDraft("conversation-8", conversationId = 8, body = "Keep this"),
            ),
        )
        val fixture = fixture(repository = repository, drafts = drafts)
        fixture.coordinator.openConversation(7)
        runCurrent()
        fixture.coordinator.deleteConversation(7)
        fixture.coordinator.deleteConversation(7)
        runCurrent()

        assertThat(repository.deletionRequests).containsExactly(7L)
        assertThat(fixture.coordinator.state.value.route).isEqualTo(MessagingRoute.INBOX)
        assertThat(repository.conversations.value.map { it.id }).containsExactly(8L)
        assertThat(drafts.drafts).doesNotContainKey("conversation-7")
        assertThat(drafts.drafts["conversation-8"]?.body).isEqualTo("Keep this")
        assertThat(fixture.coordinator.state.value.deletingConversation).isFalse()
    }

    @Test
    fun failedDeletionKeepsConversationAndReplyDraftAndReportsFailure() = runTest {
        val repository = FakeMessageRepository(
            initialConversations = listOf(MessageConversation(7, MessageRecipient("2025550101"), "Hello", 1_000, 1)),
        ).apply { deletionResult = ConversationDeleteResult.NOT_ALLOWED }
        val drafts = FakeMessageDraftStore(listOf(MessageDraft("conversation-7", conversationId = 7, body = "Keep")))
        val fixture = fixture(repository = repository, drafts = drafts)
        fixture.coordinator.openConversation(7)
        runCurrent()
        fixture.coordinator.deleteConversation(8)
        assertThat(repository.deletionRequests).isEmpty()
        fixture.coordinator.deleteConversation(7)
        runCurrent()
        assertThat(fixture.coordinator.state.value.route).isEqualTo(MessagingRoute.CONVERSATION)
        assertThat(fixture.coordinator.state.value.statusMessage)
            .isEqualTo("Set Claro as default SMS app to delete conversations")
        assertThat(drafts.drafts["conversation-7"]?.body).isEqualTo("Keep")
        assertThat(repository.conversations.value).hasSize(1)
    }

    @Test
    fun messageTimestampIncludesDateTimeAndUsesTheLocalTimeZone() {
        val timestamp = Instant.parse("2026-11-15T11:40:00Z").toEpochMilli()
        val english = formatMessageTimestamp(timestamp, Locale.US, ZoneId.of("UTC"))
        assertThat(english).contains("Nov 15")
        assertThat(english).contains("2026")
        assertThat(english).contains("11:40")
        val hebrew = formatMessageTimestamp(timestamp, Locale.forLanguageTag("he"), ZoneId.of("Asia/Jerusalem"))
        assertThat(hebrew).contains("2026")
        assertThat(hebrew).contains("13:40")
        assertThat(formatMessageTimestamp(0, Locale.US)).isNull()
    }

    @Test
    fun incomingSmsDuplicateIdentityUsesSenderTimeNotChangingArrivalTime() {
        val message = ClaroMessage(
            providerId = 0,
            conversationId = 7,
            clientMessageId = null,
            direction = MessageDirection.INCOMING,
            senderOrRecipient = "+15550101",
            body = "Hello",
            timestampMillis = 20_000,
            isRead = false,
            deliveryState = MessageDeliveryState.RECEIVED,
            sentTimestampMillis = 10_000,
        )
        assertThat(incomingFingerprint(message.copy(timestampMillis = 30_000)))
            .isEqualTo(incomingFingerprint(message))
        assertThat(incomingFingerprint(message.copy(sentTimestampMillis = 11_000)))
            .isNotEqualTo(incomingFingerprint(message))
    }

    @Test
    fun inboxSmsIsReceivedRegardlessOfOutgoingDeliveryStatus() {
        assertThat(smsDeliveryState(Telephony.Sms.MESSAGE_TYPE_INBOX, Telephony.Sms.STATUS_NONE))
            .isEqualTo(MessageDeliveryState.RECEIVED)
        assertThat(smsDeliveryState(Telephony.Sms.MESSAGE_TYPE_INBOX, Telephony.Sms.STATUS_COMPLETE))
            .isEqualTo(MessageDeliveryState.RECEIVED)
        assertThat(smsDeliveryState(Telephony.Sms.MESSAGE_TYPE_SENT, Telephony.Sms.STATUS_NONE))
            .isEqualTo(MessageDeliveryState.SENT)
        assertThat(smsDeliveryState(Telephony.Sms.MESSAGE_TYPE_SENT, Telephony.Sms.STATUS_COMPLETE))
            .isEqualTo(MessageDeliveryState.DELIVERY_CONFIRMED)
        assertThat(smsDeliveryState(Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.STATUS_NONE))
            .isEqualTo(MessageDeliveryState.PENDING)
    }

    @Test
    fun notificationOpensConversationAfterLoadingAndReportsMissingThread() = runTest {
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
        val repository = FakeMessageRepository(initialMessages = mapOf(7L to listOf(message))).apply {
            conversationsOnRefresh = listOf(
                MessageConversation(7, MessageRecipient("+15550101", "Mira"), "Hello", 1_000, 1),
            )
        }
        val fixture = fixture(repository = repository)
        fixture.coordinator.searchInbox("another sender")
        fixture.coordinator.openConversation(7, refreshBeforeOpen = true)
        runCurrent()
        assertThat(fixture.coordinator.state.value.route).isEqualTo(MessagingRoute.CONVERSATION)
        assertThat(fixture.coordinator.state.value.selectedConversation?.id).isEqualTo(7)
        assertThat(fixture.coordinator.state.value.searchQuery).isEmpty()
        assertThat(fixture.coordinator.state.value.messages).containsExactly(message)
        assertThat(fixture.coordinator.state.value.selectedConversation?.unreadCount).isEqualTo(0)

        fixture.coordinator.openConversation(8, refreshBeforeOpen = true)
        runCurrent()
        assertThat(fixture.coordinator.state.value.route).isEqualTo(MessagingRoute.INBOX)
        assertThat(fixture.coordinator.state.value.statusMessage)
            .isEqualTo("Message conversation is no longer available")
        assertThat(fixture.transport.submissions).isEqualTo(0)
    }

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
        assertThat(fixture.events.count {
            it == ClaroEvent.TaskFlowResult(EventTask.MESSAGE_DELIVERY, EventOutcome.COMPLETED)
        }).isEqualTo(1)
        assertThat(fixture.events.toString()).doesNotContain("+15550101")
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
        val events = mutableListOf<ClaroEvent>()
        val analytics = object : ProductAnalytics {
            override fun track(event: ClaroEvent) { events += event }
        }
        observeSmsDeliveryTelemetry(transport, analytics, null, backgroundScope)
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
            analytics = analytics,
        )
        runCurrent()
        return Fixture(coordinator, role, repository, transport, logger, events)
    }

    private data class Fixture(
        val coordinator: MessagingCoordinator,
        val role: FakeSmsRoleGateway,
        val repository: FakeMessageRepository,
        val transport: FakeSmsTransport,
        val logger: FakeSensitiveDataSafeLogger,
        val events: List<ClaroEvent>,
    )
}
