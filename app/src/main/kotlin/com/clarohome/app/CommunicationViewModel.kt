package com.clarohome.app

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarohome.core.domain.ProductAnalytics
import com.clarohome.core.domain.CrashReporter
import com.clarohome.feature.communication.AudioRoute
import com.clarohome.feature.communication.AndroidTelecomGateway
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
import kotlinx.coroutines.Job

@HiltViewModel
class CommunicationViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val roleGateway: DialerRoleGateway,
    telecomGateway: TelecomGateway,
    contactsGateway: ContactsGateway,
    assignmentStore: PhoneAssignmentStore,
    historyStore: CallHistoryStore,
    clock: ElapsedRealtimeClock,
    analytics: ProductAnalytics,
    crashReporter: CrashReporter,
) : ViewModel() {
    private var contactsLoadJob: Job? = null
    private val coordinator = PhoneContactsCoordinator(
        roleGateway = roleGateway,
        telecomGateway = telecomGateway,
        contactsGateway = contactsGateway,
        assignmentStore = assignmentStore,
        historyStore = historyStore,
        clock = clock,
        scope = viewModelScope,
        savedState = restoredState(),
        analytics = analytics,
        crashReporter = crashReporter,
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
            coordinator.refreshHistory()
            coordinator.reconcileAssignments()
        }
    }

    fun roleRequestIntent(): Intent? = roleGateway.createRoleRequest()

    fun refreshQualification() {
        viewModelScope.launch {
            roleGateway.refresh()
            coordinator.refreshHistory()
        }
    }

    fun openDialer(showMissedCalls: Boolean = false) = coordinator.openDialer(showMissedCalls)
    fun openContacts(fromDialer: Boolean? = null) {
        coordinator.openContacts(fromDialer)
        searchContacts(state.value.contactQuery)
    }

    fun returnFromContactsToDialer() = coordinator.returnFromContactsToDialer()

    fun returnToActiveCall() = coordinator.returnToActiveCall()
    fun inputDigit(digit: Char) = coordinator.inputDigit(digit)
    fun backspace() = coordinator.backspace()
    fun requestEnteredNumberCall() = coordinator.requestEnteredNumberCall()
    fun requestCall(identity: CallerIdentity) = coordinator.requestCall(identity)
    fun selectDialerContact(identity: CallerIdentity) = coordinator.selectDialerContact(identity)
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
        contactsLoadJob?.cancel()
        contactsLoadJob = viewModelScope.launch { coordinator.loadContacts(query, restart = true) }
    }

    fun loadMoreContacts() {
        if (contactsLoadJob?.isActive == true) return
        contactsLoadJob = viewModelScope.launch {
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
                photoUri = contact.photoUri,
            ),
        )
    }

    fun openHistoryEntry(id: String) {
        viewModelScope.launch { coordinator.openHistoryEntry(id) }
    }

    fun saveAssignment(slot: String, contact: com.clarohome.feature.communication.ClaroContact, number: ContactNumber) {
        viewModelScope.launch { coordinator.saveAssignment(slot, contact, number) }
    }

    fun clearAssignment(slot: String) {
        viewModelScope.launch { coordinator.clearAssignment(slot) }
    }

    fun clearAllAssignments() {
        viewModelScope.launch {
            listOf("1", "2", "sos").forEach { coordinator.clearAssignment(it) }
        }
    }

    fun saveContact(
        existingLookupKey: String?,
        name: String,
        primaryNumber: String,
        secondaryNumber: String,
        pickedPhotoUri: String?,
        onResult: (com.clarohome.feature.communication.ContactWriteResult) -> Unit,
    ) {
        viewModelScope.launch {
            val existing = state.value.selectedContact?.takeIf { it.lookupKey == existingLookupKey }
            val numbers = buildList {
                if (primaryNumber.isNotBlank()) {
                    add(ContactNumber(primaryNumber.trim(), existing?.numbers?.getOrNull(0)?.label ?: "Mobile"))
                }
                if (secondaryNumber.isNotBlank()) {
                    add(ContactNumber(secondaryNumber.trim(), existing?.numbers?.getOrNull(1)?.label ?: "Home"))
                }
                addAll(existing?.numbers.orEmpty().drop(2))
            }
            val mutation = com.clarohome.feature.communication.ContactMutation(
                displayName = name.trim(),
                numbers = numbers,
                photoUri = pickedPhotoUri,
            )
            val result = if (existingLookupKey == null) {
                coordinator.addContact(mutation)
            } else {
                coordinator.editContact(existingLookupKey, mutation)
            }
            if (result is com.clarohome.feature.communication.ContactWriteResult.Saved) {
                if (existingLookupKey != null && state.value.selectedContact?.lookupKey == existingLookupKey &&
                    state.value.route == CommunicationRoute.CONTACT_DETAIL
                ) {
                    coordinator.openContact(result.lookupKey)
                }
                coordinator.loadContacts(state.value.contactQuery, restart = true)
            }
            onResult(result)
        }
    }

    fun deleteSelectedContact(
        lookupKey: String,
        onResult: (com.clarohome.feature.communication.ContactWriteResult) -> Unit,
    ) {
        viewModelScope.launch {
            val result = coordinator.deleteContact(lookupKey)
            if (result is com.clarohome.feature.communication.ContactWriteResult.Saved) {
                coordinator.loadContacts(state.value.contactQuery, restart = true)
            }
            onResult(result)
        }
    }

    fun handleDialIntent(intent: Intent?) {
        if (intent?.action == AndroidTelecomGateway.ACTION_RETURN_TO_CALL) {
            coordinator.returnToActiveCall()
            mutableOpenPhoneRequests.value += 1
            return
        }
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
                photoUri = savedStateHandle[KEY_PENDING_PHOTO],
            )
        },
        contactQuery = savedStateHandle[KEY_CONTACT_QUERY] ?: "",
        selectedContactLookupKey = savedStateHandle[KEY_SELECTED_CONTACT],
        contactsOpenedFromDialer = savedStateHandle[KEY_CONTACTS_FROM_DIALER] ?: false,
    )

    private fun persist(state: PhoneSavedState) {
        savedStateHandle[KEY_DIGITS] = state.digits
        savedStateHandle[KEY_ROUTE] = state.route.name
        savedStateHandle[KEY_PENDING_NAME] = state.pendingIdentity?.displayName
        savedStateHandle[KEY_PENDING_NUMBER] = state.pendingIdentity?.number
        savedStateHandle[KEY_PENDING_PRIVATE] = state.pendingIdentity?.isPrivate
        savedStateHandle[KEY_PENDING_PHOTO] = state.pendingIdentity?.photoUri
        savedStateHandle[KEY_CONTACT_QUERY] = state.contactQuery
        savedStateHandle[KEY_SELECTED_CONTACT] = state.selectedContactLookupKey
        savedStateHandle[KEY_CONTACTS_FROM_DIALER] = state.contactsOpenedFromDialer
    }

    companion object {
        private const val KEY_DIGITS = "phone.digits"
        private const val KEY_ROUTE = "phone.route"
        private const val KEY_PENDING_NAME = "phone.pending.name"
        private const val KEY_PENDING_NUMBER = "phone.pending.number"
        private const val KEY_PENDING_PRIVATE = "phone.pending.private"
        private const val KEY_PENDING_PHOTO = "phone.pending.photo"
        private const val KEY_CONTACT_QUERY = "phone.contacts.query"
        private const val KEY_SELECTED_CONTACT = "phone.contacts.selected"
        private const val KEY_CONTACTS_FROM_DIALER = "phone.contacts.fromDialer"
    }
}
