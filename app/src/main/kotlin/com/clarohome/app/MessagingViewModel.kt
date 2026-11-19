package com.clarohome.app

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarohome.core.domain.ProductAnalytics
import com.clarohome.core.domain.CrashReporter
import com.clarohome.feature.communication.ClaroContact
import com.clarohome.feature.communication.ACTION_OPEN_MESSAGES
import com.clarohome.feature.communication.EXTRA_CONVERSATION_ID
import com.clarohome.feature.communication.ContactNumber
import com.clarohome.feature.communication.ContactsGateway
import com.clarohome.feature.communication.MessageDraftStore
import com.clarohome.feature.communication.MessageRepository
import com.clarohome.feature.communication.MessagingClock
import com.clarohome.feature.communication.MessagingCoordinator
import com.clarohome.feature.communication.MessagingRoute
import com.clarohome.feature.communication.MessagingSavedState
import com.clarohome.feature.communication.MessagingUiState
import com.clarohome.feature.communication.SensitiveDataSafeLogger
import com.clarohome.feature.communication.SmsRoleGateway
import com.clarohome.feature.communication.SmsTransport
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MessagingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val roleGateway: SmsRoleGateway,
    repository: MessageRepository,
    draftStore: MessageDraftStore,
    transport: SmsTransport,
    contactsGateway: ContactsGateway,
    clock: MessagingClock,
    logger: SensitiveDataSafeLogger,
    analytics: ProductAnalytics,
    crashReporter: CrashReporter,
) : ViewModel() {
    private val coordinator = MessagingCoordinator(
        roleGateway = roleGateway,
        repository = repository,
        draftStore = draftStore,
        transport = transport,
        contactsGateway = contactsGateway,
        clock = clock,
        logger = logger,
        scope = viewModelScope,
        savedState = restoredState(),
        analytics = analytics,
        crashReporter = crashReporter,
    )
    val state: StateFlow<MessagingUiState> = coordinator.state
    private var externalNavigationRequested = false
    private val mutableOpenMessageRequests = MutableStateFlow(
        savedStateHandle.get<Long>(KEY_OPEN_REQUESTS) ?: 0L,
    )
    val openMessageRequests: StateFlow<Long> = mutableOpenMessageRequests.asStateFlow()

    init {
        viewModelScope.launch {
            coordinator.state.collect { persist(coordinator.snapshot()) }
        }
        viewModelScope.launch {
            roleGateway.refresh()
            if (!externalNavigationRequested) coordinator.openInbox()
        }
    }

    fun roleRequestIntent(): Intent? = roleGateway.createRoleRequest()
    fun refreshQualification() {
        viewModelScope.launch { roleGateway.refresh() }
    }

    fun openInbox() = coordinator.openInbox()
    fun searchInbox(query: String) = coordinator.searchInbox(query)
    fun openConversation(id: Long) = coordinator.openConversation(id)
    fun deleteConversation(id: Long) = coordinator.deleteConversation(id)
    fun startNewMessage() = coordinator.startNewMessage()
    fun openRecipientPicker() = coordinator.openRecipientPicker()
    fun cancelRecipientPicker() = coordinator.cancelRecipientPicker()
    fun updateRecipientInput(raw: String) = coordinator.updateRecipientInput(raw)
    fun clearAllDrafts() = coordinator.clearAllDrafts()
    fun searchRecipients(query: String) = coordinator.searchRecipients(query)
    fun loadMoreRecipients() = coordinator.loadMoreRecipients()
    fun chooseContactNumber(contact: ClaroContact, number: ContactNumber) =
        coordinator.chooseContactNumber(contact, number)

    fun enterManualRecipient(number: String) = coordinator.enterManualRecipient(number)
    fun updateBody(body: String) = coordinator.updateBody(body)
    fun sendDraft() = coordinator.send()
    fun editAfterReview() = coordinator.editAfterReview()
    fun send() = coordinator.send()
    fun retry() = coordinator.retry()
    fun confirmDuplicateRiskAndSendAgain() = coordinator.confirmDuplicateRiskAndSendAgain()
    fun cancelFailure() = coordinator.cancelFailure()

    fun handleSendToIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_MESSAGES) {
            externalNavigationRequested = true
            val conversationId = intent.getLongExtra(EXTRA_CONVERSATION_ID, -1L)
            if (conversationId > 0) {
                coordinator.openConversation(conversationId, refreshBeforeOpen = true)
            } else {
                coordinator.openInbox()
            }
            requestOpenMessages()
            return
        }
        if (intent?.action != Intent.ACTION_SENDTO) return
        externalNavigationRequested = true
        val number = intent.data?.schemeSpecificPart.orEmpty()
        coordinator.startNewMessage()
        coordinator.enterManualRecipient(number)
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let(coordinator::updateBody)
        requestOpenMessages()
    }

    private fun requestOpenMessages() {
        val next = mutableOpenMessageRequests.value + 1
        savedStateHandle[KEY_OPEN_REQUESTS] = next
        mutableOpenMessageRequests.value = next
    }

    private fun restoredState(): MessagingSavedState = MessagingSavedState(
        route = savedStateHandle.get<String>(KEY_ROUTE)?.let { route ->
            runCatching { MessagingRoute.valueOf(route) }.getOrNull()
        } ?: MessagingRoute.INBOX,
        conversationId = savedStateHandle[KEY_CONVERSATION],
        draftId = savedStateHandle[KEY_DRAFT],
        searchQuery = savedStateHandle[KEY_SEARCH] ?: "",
    )

    private fun persist(state: MessagingSavedState) {
        savedStateHandle[KEY_ROUTE] = state.route.name
        savedStateHandle[KEY_CONVERSATION] = state.conversationId
        savedStateHandle[KEY_DRAFT] = state.draftId
        savedStateHandle[KEY_SEARCH] = state.searchQuery
    }

    companion object {
        private const val KEY_ROUTE = "messaging.route"
        private const val KEY_CONVERSATION = "messaging.conversation"
        private const val KEY_DRAFT = "messaging.draft"
        private const val KEY_SEARCH = "messaging.search"
        private const val KEY_OPEN_REQUESTS = "messaging.open.requests"
    }
}
