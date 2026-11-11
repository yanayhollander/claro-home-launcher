package com.clarohome.feature.communication

import android.content.Intent
import com.clarohome.core.model.VisualMode
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

enum class CommunicationRoute {
    DIALER,
    CONTACTS,
    CONTACT_DETAIL,
    CALL_CONFIRMATION,
    INCOMING_CALL,
    ACTIVE_CALL,
    CALL_ENDED,
    MISSED_CALL,
    CALL_UNANSWERED,
    CALL_FAILED,
    EXTERNAL_HANDOFF,
}

enum class CallDirection {
    INCOMING,
    OUTGOING,
}

enum class CallPhase {
    IDLE,
    DIALING,
    RINGING,
    INCOMING,
    CONNECTED,
    ON_HOLD,
    DISCONNECTED,
}

enum class CallOutcome {
    ENDED,
    MISSED_INCOMING,
    DECLINED_INCOMING,
    CANCELED_OUTGOING,
    UNANSWERED_OUTGOING,
    FAILED,
}

enum class CallFailure {
    INVALID_NUMBER,
    NO_SIM,
    NO_SERVICE,
    ROLE_LOST,
    PERMISSION_DENIED,
    ACCOUNT_UNAVAILABLE,
    NETWORK_FAILURE,
    UNKNOWN,
}

enum class AudioRoute {
    EARPIECE,
    SPEAKER,
    WIRED_HEADSET,
    BLUETOOTH,
    UNKNOWN,
}

data class CallerIdentity(
    val displayName: String?,
    val number: String?,
    val isPrivate: Boolean = false,
) {
    val accessibleName: String
        get() = when {
            isPrivate -> "Private number"
            !displayName.isNullOrBlank() -> displayName
            !number.isNullOrBlank() -> number
            else -> "Unknown caller"
        }

    val canReturnCall: Boolean
        get() = !isPrivate && !number.isNullOrBlank()
}

data class CallCapabilities(
    val canMute: Boolean = false,
    val canChangeAudioRoute: Boolean = false,
    val canSendDtmf: Boolean = false,
    val canHold: Boolean = false,
    val canSwap: Boolean = false,
    val canMerge: Boolean = false,
)

data class CallSession(
    val id: String,
    val direction: CallDirection,
    val identity: CallerIdentity,
    val phase: CallPhase,
    val capabilities: CallCapabilities = CallCapabilities(),
    val audioRoute: AudioRoute = AudioRoute.UNKNOWN,
    val isMuted: Boolean = false,
    val connectedAtElapsedMillis: Long? = null,
    val disconnectedAtElapsedMillis: Long? = null,
    val outcome: CallOutcome? = null,
    val failure: CallFailure? = null,
) {
    fun connectedDurationMillis(nowElapsedMillis: Long): Long? {
        val connectedAt = connectedAtElapsedMillis ?: return null
        val end = disconnectedAtElapsedMillis ?: nowElapsedMillis
        return (end - connectedAt).coerceAtLeast(0)
    }
}

data class ContactNumber(
    val value: String,
    val label: String,
)

data class ClaroContact(
    val lookupKey: String,
    val displayName: String,
    val photoUri: String? = null,
    val numbers: List<ContactNumber>,
    val canEdit: Boolean = true,
    val canDelete: Boolean = true,
)

data class ContactPage(
    val contacts: List<ClaroContact>,
    val nextOffset: Int?,
    val totalCount: Int?,
)

data class ContactMutation(
    val displayName: String,
    val numbers: List<ContactNumber>,
    val photoUri: String? = null,
)

sealed interface ContactWriteResult {
    data class Saved(val lookupKey: String) : ContactWriteResult
    data object PermissionDenied : ContactWriteResult
    data object AccountReadOnly : ContactWriteResult
    data class Failed(val reason: String) : ContactWriteResult
}

data class PhoneAssignment(
    val slot: String,
    val contactLookupKey: String,
    val selectedNumberSnapshot: String,
    val displayNameSnapshot: String,
    val status: AssignmentStatus = AssignmentStatus.RESOLVED,
)

enum class AssignmentStatus {
    RESOLVED,
    CONTACT_MISSING,
    NUMBER_CHANGED,
}

data class CallHistoryEntry(
    val id: String,
    val identity: CallerIdentity,
    val direction: CallDirection,
    val outcome: CallOutcome,
    val timestampMillis: Long,
    val connectedDurationMillis: Long?,
    val isOpened: Boolean = false,
)

data class DialerQualification(
    val roleAvailable: Boolean,
    val roleHeld: Boolean,
    val telephonySupported: Boolean,
    val callPermissionGranted: Boolean,
    val callLogPermissionGranted: Boolean,
    val hasUsableCallingAccount: Boolean,
) {
    val canOwnCalls: Boolean
        get() = roleAvailable &&
            roleHeld &&
            telephonySupported &&
            callPermissionGranted &&
            hasUsableCallingAccount

    val needsRepair: Boolean
        get() = !canOwnCalls || !callLogPermissionGranted
}

sealed interface CallPlacementResult {
    data object SubmittedToTelecom : CallPlacementResult
    data object EmergencyHandoffStarted : CallPlacementResult
    data class Rejected(val failure: CallFailure) : CallPlacementResult
}

sealed interface TelecomEvent {
    data class Incoming(
        val callId: String,
        val identity: CallerIdentity,
        val capabilities: CallCapabilities,
    ) : TelecomEvent

    data class StateChanged(
        val callId: String,
        val direction: CallDirection,
        val identity: CallerIdentity,
        val phase: CallPhase,
        val capabilities: CallCapabilities,
        val audioRoute: AudioRoute,
        val isMuted: Boolean,
        val connectedAtElapsedMillis: Long?,
    ) : TelecomEvent

    data class Disconnected(
        val callId: String,
        val direction: CallDirection,
        val identity: CallerIdentity,
        val outcome: CallOutcome,
        val failure: CallFailure?,
        val connectedAtElapsedMillis: Long?,
        val disconnectedAtElapsedMillis: Long,
    ) : TelecomEvent
}

interface DialerRoleGateway {
    val qualification: StateFlow<DialerQualification>
    fun createRoleRequest(): Intent?
    suspend fun refresh()
}

interface TelecomGateway {
    val events: StateFlow<TelecomEvent?>
    suspend fun placeCall(number: String): CallPlacementResult
    suspend fun handoffEmergency(number: String): CallPlacementResult
    fun isEmergencyNumber(number: String): Boolean
    suspend fun answer(callId: String)
    suspend fun decline(callId: String)
    suspend fun disconnect(callId: String)
    suspend fun setMuted(muted: Boolean)
    suspend fun setAudioRoute(route: AudioRoute)
    suspend fun setHeld(callId: String, held: Boolean)
    suspend fun swap(callId: String)
    suspend fun playDtmf(callId: String, digit: Char)
}

interface ContactsGateway {
    suspend fun page(query: String, offset: Int, limit: Int): ContactPage
    suspend fun get(lookupKey: String): ClaroContact?
    suspend fun add(mutation: ContactMutation): ContactWriteResult
    suspend fun edit(lookupKey: String, mutation: ContactMutation): ContactWriteResult
    suspend fun delete(lookupKey: String): ContactWriteResult
}

interface PhoneAssignmentStore {
    val assignments: StateFlow<List<PhoneAssignment>>
    suspend fun save(assignment: PhoneAssignment)
    suspend fun clear(slot: String)
}

interface CallHistoryStore {
    val history: StateFlow<List<CallHistoryEntry>>
    suspend fun record(entry: CallHistoryEntry)
    suspend fun markOpened(id: String)
    suspend fun refresh() = Unit
}

interface ElapsedRealtimeClock {
    fun now(): Long
}

data class PhoneSavedState(
    val digits: String = "",
    val route: CommunicationRoute = CommunicationRoute.DIALER,
    val pendingIdentity: CallerIdentity? = null,
    val contactQuery: String = "",
    val selectedContactLookupKey: String? = null,
)

data class CommunicationUiState(
    val route: CommunicationRoute = CommunicationRoute.DIALER,
    val digits: String = "",
    val pendingIdentity: CallerIdentity? = null,
    val call: CallSession? = null,
    val qualification: DialerQualification,
    val contacts: List<ClaroContact> = emptyList(),
    val contactQuery: String = "",
    val contactsLoading: Boolean = false,
    val contactsNextOffset: Int? = 0,
    val selectedContact: ClaroContact? = null,
    val assignments: List<PhoneAssignment> = emptyList(),
    val history: List<CallHistoryEntry> = emptyList(),
    val failure: CallFailure? = null,
    val message: String? = null,
) {
    val missedIncomingCount: Int?
        get() = if (!qualification.roleHeld || !qualification.callLogPermissionGranted) {
            null
        } else {
            history.count {
                it.outcome == CallOutcome.MISSED_INCOMING && !it.isOpened
            }
        }

    val hasActiveCall: Boolean
        get() = call?.phase in setOf(
            CallPhase.DIALING,
            CallPhase.RINGING,
            CallPhase.INCOMING,
            CallPhase.CONNECTED,
            CallPhase.ON_HOLD,
        )
}

enum class CommunicationAction {
    ENTER_DIGIT,
    OPEN_CONTACTS,
    PLACE_CALL,
    ANSWER,
    DECLINE,
    END,
    MUTE,
    CHANGE_AUDIO_ROUTE,
    HOLD,
    SWAP,
    HOME,
    REPAIR,
}

fun CommunicationUiState.actionsFor(@Suppress("UNUSED_PARAMETER") mode: VisualMode): Set<CommunicationAction> =
    buildSet {
        when (route) {
            CommunicationRoute.DIALER -> {
                add(CommunicationAction.ENTER_DIGIT)
                add(CommunicationAction.OPEN_CONTACTS)
                add(CommunicationAction.PLACE_CALL)
                add(CommunicationAction.HOME)
            }

            CommunicationRoute.CONTACTS,
            CommunicationRoute.CONTACT_DETAIL,
            -> {
                add(CommunicationAction.PLACE_CALL)
                add(CommunicationAction.HOME)
            }

            CommunicationRoute.CALL_CONFIRMATION -> {
                add(CommunicationAction.PLACE_CALL)
                add(CommunicationAction.HOME)
            }

            CommunicationRoute.INCOMING_CALL -> {
                add(CommunicationAction.ANSWER)
                add(CommunicationAction.DECLINE)
            }

            CommunicationRoute.ACTIVE_CALL -> {
                add(CommunicationAction.END)
                call?.capabilities?.let { capabilities ->
                    if (capabilities.canMute) add(CommunicationAction.MUTE)
                    if (capabilities.canChangeAudioRoute) {
                        add(CommunicationAction.CHANGE_AUDIO_ROUTE)
                    }
                    if (capabilities.canHold) add(CommunicationAction.HOLD)
                    if (capabilities.canSwap) add(CommunicationAction.SWAP)
                }
                if (call?.capabilities?.canSendDtmf == true) {
                    add(CommunicationAction.ENTER_DIGIT)
                }
                add(CommunicationAction.HOME)
            }

            CommunicationRoute.CALL_ENDED,
            CommunicationRoute.MISSED_CALL,
            CommunicationRoute.CALL_UNANSWERED,
            CommunicationRoute.EXTERNAL_HANDOFF,
            -> add(CommunicationAction.HOME)

            CommunicationRoute.CALL_FAILED -> {
                add(CommunicationAction.HOME)
                if (qualification.needsRepair) add(CommunicationAction.REPAIR)
            }
        }
    }

class PhoneContactsCoordinator(
    private val roleGateway: DialerRoleGateway,
    private val telecomGateway: TelecomGateway,
    private val contactsGateway: ContactsGateway,
    private val assignmentStore: PhoneAssignmentStore,
    private val historyStore: CallHistoryStore,
    private val clock: ElapsedRealtimeClock,
    scope: CoroutineScope,
    savedState: PhoneSavedState = PhoneSavedState(),
) {
    private val placingCall = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(
        CommunicationUiState(
            route = savedState.route,
            digits = savedState.digits,
            pendingIdentity = savedState.pendingIdentity,
            qualification = roleGateway.qualification.value,
            contactQuery = savedState.contactQuery,
        ),
    )
    val state: StateFlow<CommunicationUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            roleGateway.qualification.collect { qualification ->
                update {
                    copy(
                        qualification = qualification,
                        failure = if (!qualification.roleHeld && hasActiveCall) {
                            CallFailure.ROLE_LOST
                        } else {
                            failure
                        },
                    )
                }
            }
        }
        scope.launch {
            telecomGateway.events.collect { event ->
                event?.let(::onTelecomEvent)
            }
        }
        scope.launch {
            assignmentStore.assignments.collect { assignments ->
                update { copy(assignments = assignments) }
            }
        }
        scope.launch {
            historyStore.history.collect { history ->
                update { copy(history = history) }
            }
        }
        if (savedState.selectedContactLookupKey != null) {
            scope.launch { openContact(savedState.selectedContactLookupKey) }
        }
    }

    fun snapshot(): PhoneSavedState = PhoneSavedState(
        digits = state.value.digits,
        route = when (state.value.route) {
            CommunicationRoute.CALL_CONFIRMATION -> CommunicationRoute.DIALER
            else -> state.value.route
        },
        pendingIdentity = state.value.pendingIdentity,
        contactQuery = state.value.contactQuery,
        selectedContactLookupKey = state.value.selectedContact?.lookupKey,
    )

    suspend fun refreshHistory() = historyStore.refresh()

    fun openDialer() = update {
        copy(route = CommunicationRoute.DIALER, failure = null, message = null)
    }

    fun openContacts() {
        update { copy(route = CommunicationRoute.CONTACTS, failure = null, message = null) }
    }

    fun returnToActiveCall() {
        if (state.value.hasActiveCall) {
            update {
                copy(
                    route = if (call?.phase == CallPhase.INCOMING) {
                        CommunicationRoute.INCOMING_CALL
                    } else {
                        CommunicationRoute.ACTIVE_CALL
                    },
                )
            }
        }
    }

    fun inputDigit(digit: Char) {
        if (digit !in "0123456789*#") return
        val current = state.value
        val active = current.call
        if (active != null && active.phase in setOf(CallPhase.CONNECTED, CallPhase.ON_HOLD)) {
            if (active.capabilities.canSendDtmf) {
                launchAction { telecomGateway.playDtmf(active.id, digit) }
            }
            return
        }
        if (active?.phase in setOf(CallPhase.INCOMING, CallPhase.DIALING, CallPhase.RINGING)) return
        update {
            copy(
                route = CommunicationRoute.DIALER,
                digits = (digits + digit).take(MAX_DIAL_LENGTH),
                failure = null,
                message = null,
            )
        }
    }

    fun backspace() = update {
        copy(digits = digits.dropLast(1), failure = null, message = null)
    }

    fun requestEnteredNumberCall() {
        val number = normalizeDialString(state.value.digits)
        if (number.isEmpty()) {
            fail(CallFailure.INVALID_NUMBER, "Enter a phone number")
            return
        }
        requestCall(CallerIdentity(displayName = null, number = number))
    }

    fun requestCall(identity: CallerIdentity) {
        val number = normalizeDialString(identity.number.orEmpty())
        if (identity.isPrivate || number.isEmpty()) {
            fail(CallFailure.INVALID_NUMBER, "This caller cannot be called back")
            return
        }
        update {
            copy(
                route = CommunicationRoute.CALL_CONFIRMATION,
                pendingIdentity = identity.copy(number = number),
                failure = null,
                message = null,
            )
        }
    }

    fun cancelConfirmation() = update {
        copy(
            route = CommunicationRoute.DIALER,
            pendingIdentity = null,
            message = "Call canceled",
        )
    }

    fun confirmOutgoingCall() {
        val identity = state.value.pendingIdentity ?: return
        submitCall(identity, direct = false)
    }

    fun directCallAssignment(slot: String) {
        val assignment = state.value.assignments.firstOrNull { it.slot == slot }
        if (assignment == null) {
            update { copy(message = "Hold $slot is not set") }
            return
        }
        if (assignment.status != AssignmentStatus.RESOLVED) {
            update { copy(message = "Ask your caregiver to repair Hold $slot") }
            return
        }
        submitCall(
            CallerIdentity(
                displayName = assignment.displayNameSnapshot,
                number = assignment.selectedNumberSnapshot,
            ),
            direct = true,
        )
    }

    private fun submitCall(identity: CallerIdentity, direct: Boolean) {
        val number = normalizeDialString(identity.number.orEmpty())
        if (number.isEmpty()) {
            fail(CallFailure.INVALID_NUMBER, "Enter a valid phone number")
            return
        }
        if (!placingCall.compareAndSet(false, true)) return
        launchAction {
            val result = try {
                if (telecomGateway.isEmergencyNumber(number)) {
                    telecomGateway.handoffEmergency(number)
                } else if (!state.value.qualification.canOwnCalls) {
                    CallPlacementResult.Rejected(qualificationFailure(state.value.qualification))
                } else {
                    telecomGateway.placeCall(number)
                }
            } finally {
                placingCall.set(false)
            }
            when (result) {
                CallPlacementResult.SubmittedToTelecom -> update {
                    copy(
                        route = CommunicationRoute.ACTIVE_CALL,
                        pendingIdentity = identity,
                        message = if (direct) {
                            "Starting call to ${identity.accessibleName}"
                        } else {
                            "Waiting for phone service"
                        },
                    )
                }

                CallPlacementResult.EmergencyHandoffStarted -> update {
                    copy(
                        route = CommunicationRoute.EXTERNAL_HANDOFF,
                        pendingIdentity = null,
                        message = "Continue in the system emergency dialer",
                    )
                }

                is CallPlacementResult.Rejected -> fail(
                    result.failure,
                    failureMessage(result.failure),
                )
            }
        }
    }

    suspend fun loadContacts(query: String = state.value.contactQuery, restart: Boolean = true) {
        val normalizedQuery = query.trim()
        val offset = if (restart) 0 else state.value.contactsNextOffset ?: return
        update {
            copy(
                route = CommunicationRoute.CONTACTS,
                contactQuery = normalizedQuery,
                contactsLoading = true,
                contacts = if (restart) emptyList() else contacts,
                contactsNextOffset = if (restart) 0 else contactsNextOffset,
            )
        }
        val page = contactsGateway.page(normalizedQuery, offset, CONTACT_PAGE_SIZE)
        update {
            copy(
                contacts = if (restart) page.contacts else (contacts + page.contacts).distinctBy {
                    it.lookupKey
                },
                contactsLoading = false,
                contactsNextOffset = page.nextOffset,
            )
        }
    }

    suspend fun openContact(lookupKey: String) {
        val contact = contactsGateway.get(lookupKey)
        update {
            copy(
                route = CommunicationRoute.CONTACT_DETAIL,
                selectedContact = contact,
                message = if (contact == null) "Contact is no longer available" else null,
            )
        }
    }

    suspend fun reconcileAssignments() {
        state.value.assignments.forEach { assignment ->
            val contact = contactsGateway.get(assignment.contactLookupKey)
            val status = when {
                contact == null -> AssignmentStatus.CONTACT_MISSING
                contact.numbers.none {
                    normalizeDialString(it.value) ==
                        normalizeDialString(assignment.selectedNumberSnapshot)
                } -> AssignmentStatus.NUMBER_CHANGED
                else -> AssignmentStatus.RESOLVED
            }
            if (status != assignment.status) {
                assignmentStore.save(assignment.copy(status = status))
            }
        }
    }

    suspend fun saveAssignment(
        slot: String,
        contact: ClaroContact,
        number: ContactNumber,
    ) {
        assignmentStore.save(
            PhoneAssignment(
                slot = slot,
                contactLookupKey = contact.lookupKey,
                selectedNumberSnapshot = normalizeDialString(number.value),
                displayNameSnapshot = contact.displayName,
            ),
        )
    }

    suspend fun clearAssignment(slot: String) = assignmentStore.clear(slot)

    suspend fun addContact(mutation: ContactMutation): ContactWriteResult =
        contactsGateway.add(mutation)

    suspend fun editContact(
        lookupKey: String,
        mutation: ContactMutation,
    ): ContactWriteResult {
        val result = contactsGateway.edit(lookupKey, mutation)
        if (result is ContactWriteResult.Saved) reconcileAssignments()
        return result
    }

    suspend fun deleteContact(lookupKey: String): ContactWriteResult {
        val linked = state.value.assignments.filter { it.contactLookupKey == lookupKey }
        if (linked.any { it.slot.equals("sos", ignoreCase = true) }) {
            return ContactWriteResult.Failed("Replace the SOS person before deleting this contact")
        }
        val result = contactsGateway.delete(lookupKey)
        if (result is ContactWriteResult.Saved) reconcileAssignments()
        return result
    }

    fun answer() {
        val call = state.value.call ?: return
        launchAction { telecomGateway.answer(call.id) }
    }

    fun decline() {
        val call = state.value.call ?: return
        launchAction { telecomGateway.decline(call.id) }
    }

    fun end() {
        val call = state.value.call ?: return
        launchAction { telecomGateway.disconnect(call.id) }
    }

    fun toggleMute() {
        val call = state.value.call ?: return
        if (!call.capabilities.canMute) return
        launchAction { telecomGateway.setMuted(!call.isMuted) }
    }

    fun selectAudioRoute(route: AudioRoute) {
        val call = state.value.call ?: return
        if (!call.capabilities.canChangeAudioRoute) return
        launchAction { telecomGateway.setAudioRoute(route) }
    }

    fun toggleHold() {
        val call = state.value.call ?: return
        if (!call.capabilities.canHold) return
        launchAction {
            telecomGateway.setHeld(call.id, call.phase != CallPhase.ON_HOLD)
        }
    }

    fun swapCall() {
        val call = state.value.call ?: return
        if (!call.capabilities.canSwap) return
        launchAction { telecomGateway.swap(call.id) }
    }

    suspend fun openHistoryEntry(id: String) {
        historyStore.markOpened(id)
        val entry = historyStore.history.value.firstOrNull { it.id == id } ?: return
        update {
            copy(
                route = if (entry.outcome == CallOutcome.MISSED_INCOMING) {
                    CommunicationRoute.MISSED_CALL
                } else {
                    CommunicationRoute.CALL_ENDED
                },
                pendingIdentity = entry.identity,
            )
        }
    }

    private fun onTelecomEvent(event: TelecomEvent) {
        when (event) {
            is TelecomEvent.Incoming -> update {
                copy(
                    route = CommunicationRoute.INCOMING_CALL,
                    call = CallSession(
                        id = event.callId,
                        direction = CallDirection.INCOMING,
                        identity = event.identity,
                        phase = CallPhase.INCOMING,
                        capabilities = event.capabilities,
                    ),
                    failure = null,
                )
            }

            is TelecomEvent.StateChanged -> update {
                val connectedAt = event.connectedAtElapsedMillis
                    ?: call?.connectedAtElapsedMillis
                copy(
                    route = if (event.phase == CallPhase.INCOMING) {
                        CommunicationRoute.INCOMING_CALL
                    } else {
                        CommunicationRoute.ACTIVE_CALL
                    },
                    call = CallSession(
                        id = event.callId,
                        direction = event.direction,
                        identity = event.identity,
                        phase = event.phase,
                        capabilities = event.capabilities,
                        audioRoute = event.audioRoute,
                        isMuted = event.isMuted,
                        connectedAtElapsedMillis = connectedAt,
                    ),
                    pendingIdentity = null,
                    failure = null,
                )
            }

            is TelecomEvent.Disconnected -> {
                val session = CallSession(
                    id = event.callId,
                    direction = event.direction,
                    identity = event.identity,
                    phase = CallPhase.DISCONNECTED,
                    connectedAtElapsedMillis = event.connectedAtElapsedMillis,
                    disconnectedAtElapsedMillis = event.disconnectedAtElapsedMillis,
                    outcome = event.outcome,
                    failure = event.failure,
                )
                update {
                    copy(
                        route = routeForOutcome(event.outcome),
                        call = session,
                        pendingIdentity = event.identity,
                        failure = event.failure,
                        message = if (event.failure != null) {
                            failureMessage(event.failure)
                        } else {
                            null
                        },
                    )
                }
                launchAction {
                    historyStore.record(
                        CallHistoryEntry(
                            id = event.callId,
                            identity = event.identity,
                            direction = event.direction,
                            outcome = event.outcome,
                            timestampMillis = System.currentTimeMillis(),
                            connectedDurationMillis = session.connectedDurationMillis(clock.now()),
                        ),
                    )
                }
            }
        }
    }

    private fun routeForOutcome(outcome: CallOutcome): CommunicationRoute = when (outcome) {
        CallOutcome.MISSED_INCOMING -> CommunicationRoute.MISSED_CALL
        CallOutcome.UNANSWERED_OUTGOING -> CommunicationRoute.CALL_UNANSWERED
        CallOutcome.FAILED -> CommunicationRoute.CALL_FAILED
        else -> CommunicationRoute.CALL_ENDED
    }

    private fun fail(failure: CallFailure, message: String) = update {
        copy(
            route = CommunicationRoute.CALL_FAILED,
            failure = failure,
            message = message,
        )
    }

    private fun update(transform: CommunicationUiState.() -> CommunicationUiState) {
        mutableState.value = mutableState.value.transform()
    }

    private fun launchAction(block: suspend () -> Unit) {
        actionScope.launch { block() }
    }

    private val actionScope = scope

    companion object {
        const val CONTACT_PAGE_SIZE = 50
        const val MAX_DIAL_LENGTH = 64

        fun normalizeDialString(value: String): String = buildString {
            value.forEachIndexed { index, char ->
                when {
                    char.isDigit() || char == '*' || char == '#' -> append(char)
                    char == '+' && index == 0 -> append(char)
                }
            }
        }

        private fun qualificationFailure(qualification: DialerQualification): CallFailure = when {
            !qualification.roleHeld -> CallFailure.ROLE_LOST
            !qualification.callPermissionGranted -> CallFailure.PERMISSION_DENIED
            !qualification.telephonySupported -> CallFailure.NO_SIM
            !qualification.hasUsableCallingAccount -> CallFailure.ACCOUNT_UNAVAILABLE
            else -> CallFailure.UNKNOWN
        }

        fun failureMessage(failure: CallFailure): String = when (failure) {
            CallFailure.INVALID_NUMBER -> "Enter a valid phone number"
            CallFailure.NO_SIM -> "No SIM is ready for calls"
            CallFailure.NO_SERVICE -> "No mobile service"
            CallFailure.ROLE_LOST -> "Claro is not the default phone app"
            CallFailure.PERMISSION_DENIED -> "Phone permission is needed"
            CallFailure.ACCOUNT_UNAVAILABLE -> "Choose a calling account in system settings"
            CallFailure.NETWORK_FAILURE -> "The call could not connect"
            CallFailure.UNKNOWN -> "The call did not connect"
        }
    }
}

fun Long.formatCallDuration(): String {
    val totalSeconds = (this / 1_000).coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}
