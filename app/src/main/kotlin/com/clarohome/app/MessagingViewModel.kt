package com.clarohome.app

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarohome.feature.communication.ClaroContact
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
    )
    val state: StateFlow<MessagingUiState> = coordinator.state

    init {
        viewModelScope.launch {
            coordinator.state.collect { persist(coordinator.snapshot()) }
        }
        viewModelScope.launch {
            roleGateway.refresh()
            coordinator.openInbox()
        }
    }

    fun roleRequestIntent(): Intent? = roleGateway.createRoleRequest()
    fun refreshQualification() {
        viewModelScope.launch { roleGateway.refresh() }
    }

    fun openInbox() = coordinator.openInbox()
    fun searchInbox(query: String) = coordinator.searchInbox(query)
    fun openConversation(id: Long) = coordinator.openConversation(id)
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
    fun review() = coordinator.review()
    fun editAfterReview() = coordinator.editAfterReview()
    fun send() = coordinator.send()
    fun retry() = coordinator.retry()
    fun confirmDuplicateRiskAndSendAgain() = coordinator.confirmDuplicateRiskAndSendAgain()
    fun cancelFailure() = coordinator.cancelFailure()

    fun handleSendToIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SENDTO) return
        val number = intent.data?.schemeSpecificPart.orEmpty()
        coordinator.startNewMessage()
        coordinator.enterManualRecipient(number)
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let(coordinator::updateBody)
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
    }
}
