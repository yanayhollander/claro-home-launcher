package com.clarohome.feature.communication

import android.content.Intent
import com.clarohome.core.model.VisualMode
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MessagingRoute {
    INBOX,
    CONVERSATION,
    RECIPIENTS,
    COMPOSE,
    CONFIRMATION,
    SENDING,
    FAILED,
}

enum class MessageDirection {
    INCOMING,
    OUTGOING,
}

enum class MessageDeliveryState {
    RECEIVED,
    DRAFT,
    PENDING,
    SENT,
    DELIVERY_CONFIRMED,
    DELIVERY_UNKNOWN,
    KNOWN_FAILURE,
}

enum class MessageKind {
    SMS,
    MMS,
}

enum class AttachmentState {
    NONE,
    AVAILABLE,
    DOWNLOAD_REQUIRED,
    DOWNLOAD_FAILED,
    UNSUPPORTED,
}

enum class MessageFailure {
    INVALID_RECIPIENT,
    EMPTY_CONTENT,
    ROLE_LOST,
    PERMISSION_DENIED,
    NO_SIM,
    NO_SERVICE,
    ACCOUNT_UNAVAILABLE,
    CARRIER_REJECTED,
    UNKNOWN,
}

data class MessageRecipient(
    val number: String,
    val displayName: String? = null,
    val contactLookupKey: String? = null,
    val label: String? = null,
) {
    val accessibleName: String
        get() = displayName?.ifBlank { null } ?: "\u2066$number\u2069"
}

data class MessageAttachment(
    val contentType: String?,
    val displayName: String?,
    val state: AttachmentState,
)

data class ClaroMessage(
    val providerId: Long,
    val conversationId: Long,
    val clientMessageId: String?,
    val direction: MessageDirection,
    val senderOrRecipient: String,
    val body: String,
    val timestampMillis: Long,
    val isRead: Boolean,
    val deliveryState: MessageDeliveryState,
    val kind: MessageKind = MessageKind.SMS,
    val attachments: List<MessageAttachment> = emptyList(),
    val groupParticipants: List<String> = emptyList(),
)

data class MessageConversation(
    val id: Long,
    val recipient: MessageRecipient,
    val snippet: String,
    val timestampMillis: Long,
    val unreadCount: Int,
    val kind: MessageKind = MessageKind.SMS,
    val groupSize: Int = 1,
)

data class SmsQualification(
    val roleAvailable: Boolean,
    val roleHeld: Boolean,
    val telephonySupported: Boolean,
    val sendPermissionGranted: Boolean,
    val readPermissionGranted: Boolean,
    val receivePermissionGranted: Boolean,
    val notificationPermissionGranted: Boolean,
    val hasUsableDefaultAccount: Boolean,
) {
    val canRead: Boolean
        get() = roleAvailable && roleHeld && telephonySupported && readPermissionGranted

    val canSend: Boolean
        get() = canRead && sendPermissionGranted && hasUsableDefaultAccount

    val needsRepair: Boolean
        get() = !canRead || !receivePermissionGranted || !notificationPermissionGranted
}

data class SmsEncodingInfo(
    val encoding: SmsEncoding,
    val segments: Int,
    val unitsUsed: Int,
    val unitsRemainingInSegment: Int,
) {
    val isMultipart: Boolean get() = segments > 1
}

enum class SmsEncoding {
    GSM_7_BIT,
    UNICODE,
}

object SmsLengthCalculator {
    private const val GSM_SINGLE_LIMIT = 160
    private const val GSM_MULTI_LIMIT = 153
    private const val UNICODE_SINGLE_LIMIT = 70
    private const val UNICODE_MULTI_LIMIT = 67
    private val gsmBasic =
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ !\"#¤%&'()*+,-./0123456789:;<=>?" +
            "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    private val gsmExtended = "^{}\\[~]|€"

    fun calculate(text: String): SmsEncodingInfo {
        val gsmUnits = text.sumOf { character ->
            when {
                character in gsmBasic -> 1
                character in gsmExtended -> 2
                else -> return unicode(text)
            }
        }
        return result(SmsEncoding.GSM_7_BIT, gsmUnits, GSM_SINGLE_LIMIT, GSM_MULTI_LIMIT)
    }

    private fun unicode(text: String): SmsEncodingInfo =
        result(SmsEncoding.UNICODE, text.length, UNICODE_SINGLE_LIMIT, UNICODE_MULTI_LIMIT)

    private fun result(
        encoding: SmsEncoding,
        units: Int,
        singleLimit: Int,
        multipartLimit: Int,
    ): SmsEncodingInfo {
        val segments = when {
            units == 0 -> 1
            units <= singleLimit -> 1
            else -> (units + multipartLimit - 1) / multipartLimit
        }
        val limit = if (segments == 1) singleLimit else multipartLimit
        val remaining = (segments * limit - units).coerceAtLeast(0)
        return SmsEncodingInfo(encoding, segments, units, remaining)
    }
}

data class MessageDraft(
    val id: String,
    val conversationId: Long? = null,
    val recipient: MessageRecipient? = null,
    val body: String = "",
    val updatedAtMillis: Long = 0,
)

data class MessagingSavedState(
    val route: MessagingRoute = MessagingRoute.INBOX,
    val conversationId: Long? = null,
    val draftId: String? = null,
    val searchQuery: String = "",
)

sealed interface SmsSubmissionResult {
    data class Submitted(val providerId: Long?) : SmsSubmissionResult
    data class NeedsDefaultAccountSetup(val setupIntent: Intent?) : SmsSubmissionResult
    data class Rejected(val failure: MessageFailure) : SmsSubmissionResult
}

sealed interface SmsTransportEvent {
    val clientMessageId: String

    data class Sent(
        override val clientMessageId: String,
        val providerId: Long?,
    ) : SmsTransportEvent

    data class Delivered(
        override val clientMessageId: String,
        val providerId: Long?,
    ) : SmsTransportEvent

    data class Failed(
        override val clientMessageId: String,
        val providerId: Long?,
        val failure: MessageFailure,
        val outcomeMayBeUnknown: Boolean,
    ) : SmsTransportEvent
}

interface SmsRoleGateway {
    val qualification: StateFlow<SmsQualification>
    fun createRoleRequest(): Intent?
    suspend fun refresh()
}

interface MessageRepository {
    val conversations: StateFlow<List<MessageConversation>>
    val unreadCount: StateFlow<Int?>
    suspend fun refresh(query: String = "")
    suspend fun messages(conversationId: Long): List<ClaroMessage>
    suspend fun markConversationRead(conversationId: Long)
    suspend fun reconcileIncoming(messages: List<ClaroMessage>)
    suspend fun updateOutgoingState(
        clientMessageId: String,
        providerId: Long?,
        state: MessageDeliveryState,
        failure: MessageFailure? = null,
    )
}

interface MessageDraftStore {
    suspend fun get(id: String): MessageDraft?
    suspend fun save(draft: MessageDraft)
    suspend fun delete(id: String)
    suspend fun clearAll() = Unit
}

interface SmsTransport {
    val events: StateFlow<SmsTransportEvent?>
    suspend fun send(
        clientMessageId: String,
        recipient: String,
        body: String,
    ): SmsSubmissionResult
}

interface MessagingClock {
    fun nowMillis(): Long
}

interface SensitiveDataSafeLogger {
    fun event(name: String, outcome: String)
}

data class MessagingUiState(
    val route: MessagingRoute = MessagingRoute.INBOX,
    val qualification: SmsQualification,
    val conversations: List<MessageConversation> = emptyList(),
    val searchQuery: String = "",
    val selectedConversation: MessageConversation? = null,
    val messages: List<ClaroMessage> = emptyList(),
    val draft: MessageDraft = MessageDraft(id = ""),
    val contacts: List<ClaroContact> = emptyList(),
    val contactsLoading: Boolean = false,
    val contactsNextOffset: Int? = 0,
    val recipientQuery: String = "",
    val sendFailure: MessageFailure? = null,
    val unknownOutcome: Boolean = false,
    val duplicateRiskConfirmationRequired: Boolean = false,
    val statusMessage: String? = null,
) {
    val encoding: SmsEncodingInfo get() = SmsLengthCalculator.calculate(draft.body)
    val canConfirm: Boolean
        get() = draft.recipient != null && draft.body.isNotBlank() && qualification.canSend
}

enum class MessagingAction {
    OPEN_INBOX,
    OPEN_CONVERSATION,
    SEARCH,
    NEW_MESSAGE,
    SELECT_RECIPIENT,
    EDIT_BODY,
    REVIEW,
    SEND,
    RETRY,
    CONFIRM_DUPLICATE_RISK,
    CANCEL,
    REPAIR,
    HOME,
}

fun MessagingUiState.actionsForMessaging(
    @Suppress("UNUSED_PARAMETER") mode: VisualMode,
): Set<MessagingAction> = buildSet {
    add(MessagingAction.HOME)
    when (route) {
        MessagingRoute.INBOX -> {
            add(MessagingAction.SEARCH)
            add(MessagingAction.NEW_MESSAGE)
            add(MessagingAction.OPEN_CONVERSATION)
            if (qualification.needsRepair) add(MessagingAction.REPAIR)
        }

        MessagingRoute.CONVERSATION -> {
            add(MessagingAction.EDIT_BODY)
            add(MessagingAction.REVIEW)
        }

        MessagingRoute.RECIPIENTS -> {
            add(MessagingAction.SEARCH)
            add(MessagingAction.SELECT_RECIPIENT)
            add(MessagingAction.CANCEL)
        }

        MessagingRoute.COMPOSE -> {
            add(MessagingAction.SELECT_RECIPIENT)
            add(MessagingAction.EDIT_BODY)
            add(MessagingAction.REVIEW)
            add(MessagingAction.CANCEL)
        }

        MessagingRoute.CONFIRMATION -> {
            add(MessagingAction.SEND)
            add(MessagingAction.EDIT_BODY)
            add(MessagingAction.CANCEL)
        }

        MessagingRoute.SENDING -> add(MessagingAction.CANCEL)
        MessagingRoute.FAILED -> {
            add(MessagingAction.RETRY)
            add(MessagingAction.CANCEL)
            if (unknownOutcome) add(MessagingAction.CONFIRM_DUPLICATE_RISK)
        }
    }
}

class MessagingCoordinator(
    private val roleGateway: SmsRoleGateway,
    private val repository: MessageRepository,
    private val draftStore: MessageDraftStore,
    private val transport: SmsTransport,
    private val contactsGateway: ContactsGateway,
    private val clock: MessagingClock,
    private val logger: SensitiveDataSafeLogger,
    private val scope: CoroutineScope,
    savedState: MessagingSavedState = MessagingSavedState(),
) {
    private val sendInFlight = AtomicBoolean(false)
    private val handledEvents = mutableSetOf<String>()
    private val mutableState = MutableStateFlow(
        MessagingUiState(
            route = savedState.route.safeRestoredRoute(),
            qualification = roleGateway.qualification.value,
            searchQuery = savedState.searchQuery,
            draft = MessageDraft(savedState.draftId ?: newDraftId()),
        ),
    )
    val state: StateFlow<MessagingUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            roleGateway.qualification.collect { qualification ->
                mutableState.value = mutableState.value.copy(
                    qualification = qualification,
                    statusMessage = if (!qualification.roleHeld) {
                        "Messages access needs repair"
                    } else {
                        mutableState.value.statusMessage
                    },
                )
            }
        }
        scope.launch {
            repository.conversations.collect { conversations ->
                val selectedId = mutableState.value.selectedConversation?.id
                mutableState.value = mutableState.value.copy(
                    conversations = conversations,
                    selectedConversation = conversations.firstOrNull { it.id == selectedId },
                )
            }
        }
        scope.launch {
            transport.events.collect { event ->
                if (event != null) handleTransportEvent(event)
            }
        }
        scope.launch {
            val draftId = savedState.draftId ?: return@launch
            draftStore.get(draftId)?.let { draft ->
                mutableState.value = mutableState.value.copy(draft = draft)
            }
        }
    }

    fun snapshot(): MessagingSavedState = MessagingSavedState(
        route = mutableState.value.route,
        conversationId = mutableState.value.selectedConversation?.id,
        draftId = mutableState.value.draft.id,
        searchQuery = mutableState.value.searchQuery,
    )

    fun openInbox() {
        mutableState.value = mutableState.value.copy(route = MessagingRoute.INBOX)
        scope.launch { repository.refresh(mutableState.value.searchQuery) }
    }

    fun searchInbox(query: String) {
        mutableState.value = mutableState.value.copy(searchQuery = query)
        scope.launch { repository.refresh(query) }
    }

    fun openConversation(conversationId: Long) {
        scope.launch {
            val conversation = mutableState.value.conversations.firstOrNull { it.id == conversationId }
                ?: return@launch
            val messages = repository.messages(conversationId)
            repository.markConversationRead(conversationId)
            repository.refresh(mutableState.value.searchQuery)
            val draftId = "conversation-$conversationId"
            val savedDraft = draftStore.get(draftId)
            mutableState.value = mutableState.value.copy(
                route = MessagingRoute.CONVERSATION,
                selectedConversation = conversation.copy(unreadCount = 0),
                messages = messages,
                draft = savedDraft ?: MessageDraft(
                    id = draftId,
                    conversationId = conversationId,
                    recipient = conversation.recipient,
                ),
                sendFailure = null,
                unknownOutcome = false,
            )
        }
    }

    fun startNewMessage() {
        val existing = mutableState.value.draft
        val draft = if (existing.conversationId == null && existing.id.isNotBlank()) {
            existing
        } else {
            MessageDraft(id = newDraftId(), updatedAtMillis = clock.nowMillis())
        }
        mutableState.value = mutableState.value.copy(
            route = MessagingRoute.COMPOSE,
            selectedConversation = null,
            messages = emptyList(),
            draft = draft,
            recipientQuery = draft.recipient?.number.orEmpty(),
            contacts = emptyList(),
            sendFailure = null,
            unknownOutcome = false,
            statusMessage = null,
        )
        scope.launch { draftStore.save(draft) }
    }

    fun openRecipientPicker() {
        val query = mutableState.value.draft.recipient?.number
            ?: mutableState.value.recipientQuery
        mutableState.value = mutableState.value.copy(
            route = MessagingRoute.RECIPIENTS,
            recipientQuery = query,
            statusMessage = null,
        )
        searchRecipients(query)
    }

    fun cancelRecipientPicker() {
        mutableState.value = mutableState.value.copy(
            route = MessagingRoute.COMPOSE,
            statusMessage = null,
        )
    }

    fun updateRecipientInput(raw: String) {
        val trimmed = raw.trim()
        val recipient = if (trimmed.isBlank()) {
            null
        } else {
            MessageRecipient(number = normalizePhoneNumber(trimmed))
        }
        val draft = mutableState.value.draft.copy(
            recipient = recipient,
            updatedAtMillis = clock.nowMillis(),
        )
        mutableState.value = mutableState.value.copy(
            recipientQuery = raw,
            draft = draft,
            statusMessage = null,
        )
        persistDraft(draft)
    }

    fun clearAllDrafts() {
        val fresh = MessageDraft(id = newDraftId(), updatedAtMillis = clock.nowMillis())
        mutableState.value = mutableState.value.copy(
            route = MessagingRoute.INBOX,
            draft = fresh,
            recipientQuery = "",
            statusMessage = null,
        )
        scope.launch { draftStore.clearAll() }
    }

    fun searchRecipients(query: String) {
        mutableState.value = mutableState.value.copy(
            recipientQuery = query,
            contactsLoading = true,
        )
        scope.launch {
            val page = contactsGateway.page(query, 0, CONTACT_PAGE_SIZE)
            mutableState.value = mutableState.value.copy(
                contacts = page.contacts,
                contactsLoading = false,
                contactsNextOffset = page.nextOffset,
            )
        }
    }

    fun loadMoreRecipients() {
        val offset = mutableState.value.contactsNextOffset ?: return
        val query = mutableState.value.recipientQuery
        mutableState.value = mutableState.value.copy(contactsLoading = true)
        scope.launch {
            val page = contactsGateway.page(query, offset, CONTACT_PAGE_SIZE)
            mutableState.value = mutableState.value.copy(
                contacts = (mutableState.value.contacts + page.contacts).distinctBy { it.lookupKey },
                contactsLoading = false,
                contactsNextOffset = page.nextOffset,
            )
        }
    }

    fun chooseContactNumber(contact: ClaroContact, number: ContactNumber) {
        setRecipient(
            MessageRecipient(
                number = normalizePhoneNumber(number.value),
                displayName = contact.displayName,
                contactLookupKey = contact.lookupKey,
                label = number.label,
            ),
        )
    }

    fun enterManualRecipient(number: String) {
        val normalized = normalizePhoneNumber(number)
        if (!isValidSmsDestination(normalized)) {
            mutableState.value = mutableState.value.copy(
                statusMessage = "Enter a valid phone number",
            )
            return
        }
        setRecipient(MessageRecipient(number = normalized))
    }

    private fun setRecipient(recipient: MessageRecipient) {
        val draft = mutableState.value.draft.copy(
            recipient = recipient,
            updatedAtMillis = clock.nowMillis(),
        )
        mutableState.value = mutableState.value.copy(
            route = MessagingRoute.COMPOSE,
            draft = draft,
            statusMessage = null,
        )
        persistDraft(draft)
    }

    fun updateBody(body: String) {
        val draft = mutableState.value.draft.copy(
            body = body,
            updatedAtMillis = clock.nowMillis(),
        )
        mutableState.value = mutableState.value.copy(draft = draft, statusMessage = null)
        persistDraft(draft)
    }

    fun review() {
        val draft = mutableState.value.draft
        val failure = when {
            draft.recipient == null || !isValidSmsDestination(draft.recipient.number) ->
                MessageFailure.INVALID_RECIPIENT

            draft.body.isBlank() -> MessageFailure.EMPTY_CONTENT
            !mutableState.value.qualification.canSend -> MessageFailure.ROLE_LOST
            else -> null
        }
        mutableState.value = if (failure == null) {
            mutableState.value.copy(route = MessagingRoute.CONFIRMATION, statusMessage = null)
        } else {
            mutableState.value.copy(
                statusMessage = failure.userMessage(),
                sendFailure = failure,
            )
        }
    }

    fun editAfterReview() {
        mutableState.value = mutableState.value.copy(route = MessagingRoute.COMPOSE)
    }

    fun send() {
        val current = mutableState.value
        val recipient = current.draft.recipient ?: return
        if (!current.qualification.canSend || current.draft.body.isBlank()) {
            review()
            return
        }
        if (!sendInFlight.compareAndSet(false, true)) return
        mutableState.value = current.copy(
            route = MessagingRoute.SENDING,
            statusMessage = "Sending message",
            duplicateRiskConfirmationRequired = false,
        )
        val clientMessageId = "${current.draft.id}:${clock.nowMillis()}"
        scope.launch {
            when (
                val result = transport.send(
                    clientMessageId = clientMessageId,
                    recipient = recipient.number,
                    body = current.draft.body,
                )
            ) {
                is SmsSubmissionResult.Submitted -> {
                    repository.updateOutgoingState(
                        clientMessageId,
                        result.providerId,
                        MessageDeliveryState.PENDING,
                    )
                    logger.event("sms_send_result", "submitted")
                }

                is SmsSubmissionResult.NeedsDefaultAccountSetup -> {
                    sendInFlight.set(false)
                    mutableState.value = mutableState.value.copy(
                        route = MessagingRoute.COMPOSE,
                        statusMessage = "Choose a default SMS account in Android. Your draft is saved.",
                    )
                    logger.event("sms_send_result", "account_setup_required")
                }

                is SmsSubmissionResult.Rejected -> {
                    sendInFlight.set(false)
                    showFailure(result.failure, outcomeUnknown = false)
                }
            }
        }
    }

    fun retry() {
        if (mutableState.value.unknownOutcome) {
            mutableState.value = mutableState.value.copy(
                duplicateRiskConfirmationRequired = true,
                statusMessage = "This message may already have been sent. Send it again?",
            )
        } else {
            send()
        }
    }

    fun confirmDuplicateRiskAndSendAgain() {
        if (!mutableState.value.duplicateRiskConfirmationRequired) return
        val oldDraft = mutableState.value.draft
        val replacement = oldDraft.copy(
            id = newDraftId(),
            updatedAtMillis = clock.nowMillis(),
        )
        sendInFlight.set(false)
        mutableState.value = mutableState.value.copy(
            draft = replacement,
            route = MessagingRoute.CONFIRMATION,
            duplicateRiskConfirmationRequired = false,
            unknownOutcome = false,
            statusMessage = null,
        )
        persistDraft(replacement)
        send()
    }

    fun cancelFailure() {
        sendInFlight.set(false)
        val target = if (mutableState.value.selectedConversation != null) {
            MessagingRoute.CONVERSATION
        } else {
            MessagingRoute.INBOX
        }
        mutableState.value = mutableState.value.copy(
            route = target,
            sendFailure = null,
            unknownOutcome = false,
            duplicateRiskConfirmationRequired = false,
            statusMessage = null,
        )
    }

    private suspend fun handleTransportEvent(event: SmsTransportEvent) {
        val eventKey = "${event.clientMessageId}:${event::class.simpleName}"
        if (!handledEvents.add(eventKey)) return
        when (event) {
            is SmsTransportEvent.Sent -> {
                repository.updateOutgoingState(
                    event.clientMessageId,
                    event.providerId,
                    MessageDeliveryState.SENT,
                )
                sendInFlight.set(false)
                val draft = mutableState.value.draft
                draftStore.delete(draft.id)
                val recipient = draft.recipient
                val conversation = mutableState.value.selectedConversation
                    ?: recipient?.let {
                        MessageConversation(
                            id = event.providerId ?: -1,
                            recipient = it,
                            snippet = draft.body,
                            timestampMillis = clock.nowMillis(),
                            unreadCount = 0,
                        )
                    }
                val sentMessage = recipient?.let {
                    ClaroMessage(
                        providerId = event.providerId ?: -1,
                        conversationId = conversation?.id ?: -1,
                        clientMessageId = event.clientMessageId,
                        direction = MessageDirection.OUTGOING,
                        senderOrRecipient = it.number,
                        body = draft.body,
                        timestampMillis = clock.nowMillis(),
                        isRead = true,
                        deliveryState = MessageDeliveryState.SENT,
                    )
                }
                val clearedDraft = MessageDraft(
                    id = conversation?.let { "conversation-${it.id}" } ?: newDraftId(),
                    conversationId = conversation?.id,
                    recipient = draft.recipient,
                    updatedAtMillis = clock.nowMillis(),
                )
                mutableState.value = mutableState.value.copy(
                    route = MessagingRoute.CONVERSATION,
                    selectedConversation = conversation,
                    draft = clearedDraft,
                    recipientQuery = "",
                    messages = if (sentMessage == null) {
                        mutableState.value.messages
                    } else {
                        (mutableState.value.messages + sentMessage)
                            .distinctBy { it.clientMessageId ?: "provider:${it.providerId}" }
                    },
                    statusMessage = "Sent. Delivery is not confirmed.",
                    sendFailure = null,
                    unknownOutcome = false,
                )
                logger.event("sms_send_result", "sent")
            }

            is SmsTransportEvent.Delivered -> {
                repository.updateOutgoingState(
                    event.clientMessageId,
                    event.providerId,
                    MessageDeliveryState.DELIVERY_CONFIRMED,
                )
                mutableState.value = mutableState.value.copy(
                    statusMessage = "Delivery confirmed by the carrier",
                )
                logger.event("sms_delivery_result", "confirmed")
            }

            is SmsTransportEvent.Failed -> {
                repository.updateOutgoingState(
                    event.clientMessageId,
                    event.providerId,
                    if (event.outcomeMayBeUnknown) {
                        MessageDeliveryState.DELIVERY_UNKNOWN
                    } else {
                        MessageDeliveryState.KNOWN_FAILURE
                    },
                    event.failure,
                )
                sendInFlight.set(false)
                showFailure(event.failure, event.outcomeMayBeUnknown)
            }
        }
    }

    private fun showFailure(failure: MessageFailure, outcomeUnknown: Boolean) {
        mutableState.value = mutableState.value.copy(
            route = MessagingRoute.FAILED,
            sendFailure = failure,
            unknownOutcome = outcomeUnknown,
            duplicateRiskConfirmationRequired = false,
            statusMessage = if (outcomeUnknown) {
                "The carrier did not confirm the result. It may already have been sent."
            } else {
                failure.userMessage()
            },
        )
        logger.event(
            "sms_send_result",
            if (outcomeUnknown) "unknown" else failure.name.lowercase(),
        )
    }

    private fun persistDraft(draft: MessageDraft) {
        scope.launch { draftStore.save(draft) }
    }

    companion object {
        private const val CONTACT_PAGE_SIZE = 50
        private fun newDraftId(): String = UUID.randomUUID().toString()
    }
}

fun normalizePhoneNumber(value: String): String {
    val trimmed = value.trim()
    val prefix = if (trimmed.startsWith("+")) "+" else ""
    return prefix + trimmed.filter(Char::isDigit)
}

fun isValidSmsDestination(value: String): Boolean {
    val normalized = normalizePhoneNumber(value)
    val digitCount = normalized.count(Char::isDigit)
    return digitCount in 3..15
}

private fun MessagingRoute.safeRestoredRoute(): MessagingRoute = when (this) {
    MessagingRoute.SENDING,
    MessagingRoute.CONFIRMATION,
    MessagingRoute.FAILED,
    -> MessagingRoute.COMPOSE

    else -> this
}

private fun MessageFailure.userMessage(): String = when (this) {
    MessageFailure.INVALID_RECIPIENT -> "Enter a valid recipient"
    MessageFailure.EMPTY_CONTENT -> "Write a message before sending"
    MessageFailure.ROLE_LOST -> "Claro must be the default SMS app"
    MessageFailure.PERMISSION_DENIED -> "Messages permission needs repair"
    MessageFailure.NO_SIM -> "No SIM is available for messages"
    MessageFailure.NO_SERVICE -> "No cellular service is available"
    MessageFailure.ACCOUNT_UNAVAILABLE -> "Choose a default SMS account in Android"
    MessageFailure.CARRIER_REJECTED -> "The carrier could not send this message"
    MessageFailure.UNKNOWN -> "The message result is unknown"
}
