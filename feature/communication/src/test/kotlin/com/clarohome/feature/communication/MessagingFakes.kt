package com.clarohome.feature.communication

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSmsRoleGateway(
    initial: SmsQualification = readySmsQualification(),
) : SmsRoleGateway {
    val mutableQualification = MutableStateFlow(initial)
    override val qualification: StateFlow<SmsQualification> = mutableQualification
    override fun createRoleRequest(): Intent? = null
    override suspend fun refresh() = Unit
}

class FakeMessageRepository(
    initialConversations: List<MessageConversation> = emptyList(),
    initialMessages: Map<Long, List<ClaroMessage>> = emptyMap(),
) : MessageRepository {
    val mutableConversations = MutableStateFlow(initialConversations)
    override val conversations: StateFlow<List<MessageConversation>> = mutableConversations
    val mutableUnreadCount = MutableStateFlow<Int?>(
        initialConversations.sumOf(MessageConversation::unreadCount),
    )
    override val unreadCount: StateFlow<Int?> = mutableUnreadCount
    private val messagesByConversation = initialMessages
        .mapValuesTo(mutableMapOf()) { it.value.toMutableList() }
    val updates = mutableListOf<Pair<String, MessageDeliveryState>>()
    var refreshes = 0

    override suspend fun refresh(query: String) {
        refreshes += 1
    }

    override suspend fun messages(conversationId: Long): List<ClaroMessage> =
        messagesByConversation[conversationId].orEmpty()

    override suspend fun markConversationRead(conversationId: Long) {
        mutableConversations.value = mutableConversations.value.map { conversation ->
            if (conversation.id == conversationId) conversation.copy(unreadCount = 0)
            else conversation
        }
        mutableUnreadCount.value = mutableConversations.value.sumOf(MessageConversation::unreadCount)
    }

    override suspend fun reconcileIncoming(messages: List<ClaroMessage>) {
        messages.groupBy(ClaroMessage::conversationId).forEach { (conversationId, incoming) ->
            val stored = messagesByConversation.getOrPut(conversationId) { mutableListOf() }
            val existing = stored.mapTo(mutableSetOf()) { it.providerId }
            stored += incoming.filter { existing.add(it.providerId) }
        }
    }

    override suspend fun updateOutgoingState(
        clientMessageId: String,
        providerId: Long?,
        state: MessageDeliveryState,
        failure: MessageFailure?,
    ) {
        updates += clientMessageId to state
    }

    fun storedMessages(conversationId: Long): List<ClaroMessage> =
        messagesByConversation[conversationId].orEmpty()
}

class FakeMessageDraftStore(
    initial: List<MessageDraft> = emptyList(),
) : MessageDraftStore {
    val drafts = initial.associateByTo(mutableMapOf(), MessageDraft::id)
    override suspend fun get(id: String): MessageDraft? = drafts[id]
    override suspend fun save(draft: MessageDraft) {
        drafts[draft.id] = draft
    }

    override suspend fun delete(id: String) {
        drafts.remove(id)
    }
}

class FakeSmsTransport : SmsTransport {
    val mutableEvents = MutableStateFlow<SmsTransportEvent?>(null)
    override val events: StateFlow<SmsTransportEvent?> = mutableEvents
    var submissions = 0
    val sentRecipients = mutableListOf<String>()
    val sentBodies = mutableListOf<String>()
    var result: SmsSubmissionResult = SmsSubmissionResult.Submitted(10)

    override suspend fun send(
        clientMessageId: String,
        recipient: String,
        body: String,
    ): SmsSubmissionResult {
        submissions += 1
        sentRecipients += recipient
        sentBodies += body
        return result
    }
}

class FakeMessagingClock(var now: Long = 1_000) : MessagingClock {
    override fun nowMillis(): Long = now
}

class FakeSensitiveDataSafeLogger : SensitiveDataSafeLogger {
    val events = mutableListOf<Pair<String, String>>()
    override fun event(name: String, outcome: String) {
        events += name to outcome
    }
}

fun readySmsQualification() = SmsQualification(
    roleAvailable = true,
    roleHeld = true,
    telephonySupported = true,
    sendPermissionGranted = true,
    readPermissionGranted = true,
    receivePermissionGranted = true,
    notificationPermissionGranted = true,
    hasUsableDefaultAccount = true,
)

fun conversationFixture(
    id: Long,
    unreadCount: Int,
    number: String = "+155500000$id",
) = MessageConversation(
    id = id,
    recipient = MessageRecipient(number = number, displayName = "Person $id"),
    snippet = "Message $id",
    timestampMillis = id * 1_000,
    unreadCount = unreadCount,
)
