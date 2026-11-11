package com.clarohome.app

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarohome.feature.communication.AudioRoute
import com.clarohome.feature.communication.CallHistoryStore
import com.clarohome.feature.communication.CallerIdentity
import com.clarohome.feature.communication.CommunicationRoute
import com.clarohome.feature.communication.CommunicationUiState
import com.clarohome.feature.communication.ContactNumber
import com.clarohome.feature.communication.ContactsGateway
import com.clarohome.feature.communication.DialerRoleGateway
import com.clarohome.feature.communication.ElapsedRealtimeClock
import com.clarohome.feature.communication.PhoneAssignmentStore
import com.clarohome.feature.communication.PhoneContactsCoordinator
import com.clarohome.feature.communication.PhoneSavedState
import com.clarohome.feature.communication.TelecomGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CommunicationViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val roleGateway: DialerRoleGateway,
    telecomGateway: TelecomGateway,
    contactsGateway: ContactsGateway,
    assignmentStore: PhoneAssignmentStore,
    historyStore: CallHistoryStore,
    clock: ElapsedRealtimeClock,
) : ViewModel() {
    private val coordinator = PhoneContactsCoordinator(
        roleGateway = roleGateway,
        telecomGateway = telecomGateway,
        contactsGateway = contactsGateway,
        assignmentStore = assignmentStore,
        historyStore = historyStore,
        clock = clock,
        scope = viewModelScope,
        savedState = restoredState(),
    )
    val state: StateFlow<CommunicationUiState> = coordinator.state
    private val mutableOpenPhoneRequests = MutableStateFlow(0L)
    val openPhoneRequests: StateFlow<Long> = mutableOpenPhoneRequests.asStateFlow()

    init {
        viewModelScope.launch {
            coordinator.state.collect { persist(coordinator.snapshot()) }
        }
        viewModelScope.launch {
            roleGateway.refresh()
            coordinator.reconcileAssignments()
        }
    }

    fun roleRequestIntent(): Intent? = roleGateway.createRoleRequest()

    fun refreshQualification() {
        viewModelScope.launch { roleGateway.refresh() }
    }

    fun openDialer() = coordinator.openDialer()
    fun openContacts() {
        coordinator.openContacts()
        viewModelScope.launch { coordinator.loadContacts() }
    }

    fun returnToActiveCall() = coordinator.returnToActiveCall()
    fun inputDigit(digit: Char) = coordinator.inputDigit(digit)
    fun backspace() = coordinator.backspace()
    fun requestEnteredNumberCall() = coordinator.requestEnteredNumberCall()
    fun requestCall(identity: CallerIdentity) = coordinator.requestCall(identity)
    fun cancelConfirmation() = coordinator.cancelConfirmation()
    fun confirmOutgoingCall() = coordinator.confirmOutgoingCall()
    fun directCallAssignment(slot: String) = coordinator.directCallAssignment(slot)
    fun answer() = coordinator.answer()
    fun decline() = coordinator.decline()
    fun end() = coordinator.end()
    fun toggleMute() = coordinator.toggleMute()
    fun selectAudioRoute(route: AudioRoute) = coordinator.selectAudioRoute(route)
    fun toggleHold() = coordinator.toggleHold()
    fun swapCall() = coordinator.swapCall()

    fun searchContacts(query: String) {
        viewModelScope.launch { coordinator.loadContacts(query, restart = true) }
    }

    fun loadMoreContacts() {
        viewModelScope.launch {
            coordinator.loadContacts(state.value.contactQuery, restart = false)
        }
    }

    fun openContact(lookupKey: String) {
        viewModelScope.launch { coordinator.openContact(lookupKey) }
    }

    fun chooseContactNumber(number: ContactNumber) {
        val contact = state.value.selectedContact ?: return
        coordinator.requestCall(
            CallerIdentity(
                displayName = contact.displayName,
                number = number.value,
            ),
        )
    }

    fun openHistoryEntry(id: String) {
        viewModelScope.launch { coordinator.openHistoryEntry(id) }
    }

    fun handleDialIntent(intent: Intent?) {
        if (intent?.action !in setOf(Intent.ACTION_DIAL, Intent.ACTION_VIEW)) return
        val number = intent?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart.orEmpty()
        coordinator.openDialer()
        number.forEach(coordinator::inputDigit)
        mutableOpenPhoneRequests.value += 1
    }

    private fun restoredState(): PhoneSavedState = PhoneSavedState(
        digits = savedStateHandle[KEY_DIGITS] ?: "",
        route = savedStateHandle.get<String>(KEY_ROUTE)?.let { route ->
            runCatching { CommunicationRoute.valueOf(route) }.getOrNull()
        } ?: CommunicationRoute.DIALER,
        pendingIdentity = savedStateHandle.get<String>(KEY_PENDING_NUMBER)?.let { number ->
            CallerIdentity(
                displayName = savedStateHandle[KEY_PENDING_NAME],
                number = number,
                isPrivate = savedStateHandle[KEY_PENDING_PRIVATE] ?: false,
            )
        },
        contactQuery = savedStateHandle[KEY_CONTACT_QUERY] ?: "",
        selectedContactLookupKey = savedStateHandle[KEY_SELECTED_CONTACT],
    )

    private fun persist(state: PhoneSavedState) {
        savedStateHandle[KEY_DIGITS] = state.digits
        savedStateHandle[KEY_ROUTE] = state.route.name
        savedStateHandle[KEY_PENDING_NAME] = state.pendingIdentity?.displayName
        savedStateHandle[KEY_PENDING_NUMBER] = state.pendingIdentity?.number
        savedStateHandle[KEY_PENDING_PRIVATE] = state.pendingIdentity?.isPrivate
        savedStateHandle[KEY_CONTACT_QUERY] = state.contactQuery
        savedStateHandle[KEY_SELECTED_CONTACT] = state.selectedContactLookupKey
    }

    companion object {
        private const val KEY_DIGITS = "phone.digits"
        private const val KEY_ROUTE = "phone.route"
        private const val KEY_PENDING_NAME = "phone.pending.name"
        private const val KEY_PENDING_NUMBER = "phone.pending.number"
        private const val KEY_PENDING_PRIVATE = "phone.pending.private"
        private const val KEY_CONTACT_QUERY = "phone.contacts.query"
        private const val KEY_SELECTED_CONTACT = "phone.contacts.selected"
    }
}
