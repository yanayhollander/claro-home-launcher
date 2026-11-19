package com.clarohome.feature.communication

import android.content.Intent
import android.database.sqlite.SQLiteException
import android.util.Log
import com.clarohome.core.model.VisualMode
import com.clarohome.core.domain.ClaroEvent
import com.clarohome.core.domain.CrashReporter
import com.clarohome.core.domain.DiagnosticOperation
import com.clarohome.core.domain.EventOutcome
import com.clarohome.core.domain.EventTask
import com.clarohome.core.domain.NoOpProductAnalytics
import com.clarohome.core.domain.ProductAnalytics
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
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

private fun CommunicationRoute.restorableRoute(): CommunicationRoute = when (this) {
    CommunicationRoute.CALL_CONFIRMATION,
    CommunicationRoute.INCOMING_CALL,
    CommunicationRoute.ACTIVE_CALL,
    CommunicationRoute.CALL_ENDED,
    CommunicationRoute.CALL_UNANSWERED,
    -> CommunicationRoute.DIALER
    else -> this
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
    val photoUri: String? = null,
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
    // Null leaves an existing photo unchanged.
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
    val phoneStatePermissionGranted: Boolean,
) {
    val canOwnCalls: Boolean
        get() = roleAvailable &&
            roleHeld &&
            telephonySupported &&
            callPermissionGranted &&
            phoneStatePermissionGranted &&
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
        val foreground: Boolean = true,
    ) : TelecomEvent

    data class Disconnected(
        val callId: String,
        val direction: CallDirection,
        val identity: CallerIdentity,
        val outcome: CallOutcome,
        val failure: CallFailure?,
        val connectedAtElapsedMillis: Long?,
        val disconnectedAtElapsedMillis: Long,
        val remainingCalls: List<CallSession> = emptyList(),
    ) : TelecomEvent
}

interface DialerRoleGateway {
    val qualification: StateFlow<DialerQualification>
    fun createRoleRequest(): Intent?
    suspend fun refresh()
}

interface TelecomGateway {
    val events: SharedFlow<TelecomEvent?>
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
    suspend fun matchingNumbers(digits: String, limit: Int): List<CallerIdentity>
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
    val contactsOpenedFromDialer: Boolean = false,
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
    val awaitingOutgoingCall: Boolean = false,
    val dialerMatches: List<CallerIdentity> = emptyList(),
    val dialerMatchesLoading: Boolean = false,
    val dialerSearchGeneration: Long = 0,
    val showMissedCalls: Boolean = false,
    val contactsOpenedFromDialer: Boolean = false,
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
        get() = awaitingOutgoingCall || call?.phase in setOf(
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
    private val analytics: ProductAnalytics = NoOpProductAnalytics,
    private val crashReporter: CrashReporter? = null,
) {
    private val recentDialerMatches = LinkedHashMap<String, List<CallerIdentity>>()
    private val placingCall = AtomicBoolean(false)
    private var pendingCallNumber: String? = null
    private var cancelPendingCall = false
    private val mutableState = MutableStateFlow(
        CommunicationUiState(
            route = savedState.route.restorableRoute(),
            digits = savedState.digits,
            pendingIdentity = savedState.pendingIdentity,
            qualification = roleGateway.qualification.value,
            contactQuery = savedState.contactQuery,
            contactsOpenedFromDialer = savedState.contactsOpenedFromDialer,
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
        val previousDisconnect = telecomGateway.events.replayCache.lastOrNull() as? TelecomEvent.Disconnected
        scope.launch {
            telecomGateway.events.collect { event ->
                // A retained disconnect is history, not a newly ended call.
                event?.takeUnless {
                    it === previousDisconnect && previousDisconnect?.remainingCalls.isNullOrEmpty()
                }?.let(::onTelecomEvent)
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
        scope.launch {
            state.map {
                it.dialerSearchGeneration to it.digits.takeIf { digits ->
                    it.route == CommunicationRoute.DIALER && !it.hasActiveCall &&
                        it.pendingIdentity == null && digits.isNotEmpty() && digits.all(Char::isDigit)
                }
            }.distinctUntilChanged().collectLatest { (generation, query) ->
                if (query == null) {
                    recentDialerMatches.clear()
                    update { copy(dialerMatches = emptyList(), dialerMatchesLoading = false) }
                    return@collectLatest
                }
                update { copy(dialerMatchesLoading = true) }
                val digits = query ?: return@collectLatest
                delay(100)
                try {
                    val matches = contactsGateway.matchingNumbers(digits, DIALER_MATCH_LIMIT)
                    if (isCurrentDialerQuery(digits, generation)) {
                        recentDialerMatches[digits] = matches
                        if (recentDialerMatches.size > MAX_CACHED_DIALER_QUERIES) {
                            recentDialerMatches.remove(recentDialerMatches.keys.first())
                        }
                        update { copy(dialerMatches = matches, dialerMatchesLoading = false) }
                    }
                } catch (_: SecurityException) {
                    Log.w("ClaroDialer", "Contact access denied for number suggestions.")
                    if (isCurrentDialerQuery(digits, generation)) {
                        update {
                            copy(dialerMatchesLoading = false, message = "Contacts permission is needed")
                        }
                    }
                } catch (_: SQLiteException) {
                    Log.w("ClaroDialer", "Contact lookup failed for number suggestions.")
                    if (isCurrentDialerQuery(digits, generation)) {
                        update {
                            copy(dialerMatchesLoading = false, message = "Contacts could not be loaded")
                        }
                    }
                }
            }
        }
        if (savedState.selectedContactLookupKey != null) {
            scope.launch { openContact(savedState.selectedContactLookupKey) }
        }
    }

    fun snapshot(): PhoneSavedState = PhoneSavedState(
        digits = state.value.digits,
        route = state.value.route.restorableRoute(),
        pendingIdentity = state.value.pendingIdentity,
        contactQuery = state.value.contactQuery,
        selectedContactLookupKey = state.value.selectedContact?.lookupKey,
        contactsOpenedFromDialer = state.value.contactsOpenedFromDialer,
    )

    suspend fun refreshHistory() = historyStore.refresh()

    private fun isCurrentDialerQuery(digits: String, generation: Long): Boolean =
        state.value.let {
            it.route == CommunicationRoute.DIALER && it.digits == digits &&
                !it.hasActiveCall && it.pendingIdentity == null && it.dialerSearchGeneration == generation
        }

    private fun dialerMatchesFor(digits: String): List<CallerIdentity> {
        if (digits.isEmpty() || !digits.all(Char::isDigit)) return emptyList()
        // Cached rows are previews; each query is still revalidated against Contacts.
        return recentDialerMatches[digits] ?: state.value.dialerMatches.filter {
            normalizeDialString(it.number.orEmpty()).contains(digits)
        }
    }

    fun openDialer(showMissedCalls: Boolean = false) {
        if (state.value.hasActiveCall) {
            returnToActiveCall()
            return
        }
        recentDialerMatches.clear()
        update {
            copy(
                route = CommunicationRoute.DIALER,
                digits = "",
                dialerSearchGeneration = dialerSearchGeneration + 1,
                showMissedCalls = showMissedCalls,
                contactsOpenedFromDialer = false,
                pendingIdentity = null,
                call = null,
                failure = null,
                message = null,
                dialerMatches = emptyList(),
                dialerMatchesLoading = false,
            )
        }
    }

    fun openContacts(fromDialer: Boolean? = null) = update {
        copy(
            route = CommunicationRoute.CONTACTS,
            contactsOpenedFromDialer = fromDialer ?: when (route) {
                CommunicationRoute.CONTACTS, CommunicationRoute.CONTACT_DETAIL -> contactsOpenedFromDialer
                else -> false
            },
            failure = null,
            message = null,
        )
    }

    fun returnFromContactsToDialer() = update {
        if (route == CommunicationRoute.CONTACTS && contactsOpenedFromDialer) {
            copy(
                route = CommunicationRoute.DIALER,
                contactsOpenedFromDialer = false,
                selectedContact = null,
                failure = null,
                message = null,
            )
        } else {
            this
        }
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
        if (digit !in "0123456789*#+") return
        val current = state.value
        if (current.awaitingOutgoingCall) return
        if (digit == '+' && (current.hasActiveCall || current.digits.isNotEmpty())) return
        val active = current.call
        if (active != null && active.phase in setOf(CallPhase.CONNECTED, CallPhase.ON_HOLD)) {
            if (active.capabilities.canSendDtmf) {
                launchAction { telecomGateway.playDtmf(active.id, digit) }
            }
            return
        }
        if (active?.phase in setOf(CallPhase.INCOMING, CallPhase.DIALING, CallPhase.RINGING)) return
        update {
            val nextDigits = (digits + digit).take(MAX_DIAL_LENGTH)
            copy(
                route = CommunicationRoute.DIALER,
                digits = nextDigits,
                failure = null,
                message = null,
                pendingIdentity = null,
                dialerMatches = dialerMatchesFor(nextDigits),
            )
        }
    }

    fun backspace() = update {
        val nextDigits = digits.dropLast(1)
        copy(
            digits = nextDigits,
            pendingIdentity = null,
            dialerMatches = dialerMatchesFor(nextDigits),
            failure = null,
            message = null,
        )
    }

    fun selectDialerContact(identity: CallerIdentity) {
        if (state.value.hasActiveCall) return
        val number = normalizeDialString(identity.number.orEmpty())
        if (!identity.canReturnCall || number.isEmpty()) {
            fail(CallFailure.INVALID_NUMBER, "This caller cannot be called back")
            return
        }
        update {
            copy(
                route = CommunicationRoute.DIALER,
                digits = number,
                pendingIdentity = identity.copy(number = number),
                call = null,
                dialerMatches = emptyList(),
                dialerMatchesLoading = false,
                failure = null,
                message = null,
            )
        }
    }

    fun requestEnteredNumberCall() {
        val number = normalizeDialString(state.value.digits)
        if (number.isEmpty()) {
            fail(CallFailure.INVALID_NUMBER, "Enter a phone number")
            return
        }
        val identity = state.value.pendingIdentity?.takeIf {
            it.canReturnCall && normalizeDialString(it.number.orEmpty()) == number
        } ?: CallerIdentity(displayName = null, number = number)
        requestCall(identity)
    }

    fun requestCall(identity: CallerIdentity) {
        val number = normalizeDialString(identity.number.orEmpty())
        if (identity.isPrivate || number.isEmpty()) {
            fail(CallFailure.INVALID_NUMBER, "This caller cannot be called back")
            return
        }
        submitCall(identity.copy(number = number), direct = false)
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
        if (state.value.hasActiveCall || state.value.route == CommunicationRoute.ACTIVE_CALL) return
        val number = normalizeDialString(identity.number.orEmpty())
        if (number.isEmpty()) {
            fail(CallFailure.INVALID_NUMBER, "Enter a valid phone number")
            return
        }
        if (!placingCall.compareAndSet(false, true)) return
        trackPlacement(EventOutcome.STARTED)
        pendingCallNumber = number
        cancelPendingCall = false
        update {
            copy(
                route = CommunicationRoute.ACTIVE_CALL,
                call = null,
                awaitingOutgoingCall = true,
                pendingIdentity = identity.copy(number = number),
                failure = null,
                message = if (direct) {
                    "Starting call to ${identity.accessibleName}"
                } else {
                    "Waiting for phone service"
                },
            )
        }
        launchAction {
            if (cancelPendingCall) {
                trackPlacement(EventOutcome.CANCELED)
                placingCall.set(false)
                clearPendingCall()
                update {
                    copy(
                        route = CommunicationRoute.DIALER,
                        awaitingOutgoingCall = false,
                        pendingIdentity = null,
                        message = "Call canceled",
                    )
                }
                return@launchAction
            }
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
                CallPlacementResult.SubmittedToTelecom -> trackPlacement(EventOutcome.SUBMITTED)

                CallPlacementResult.EmergencyHandoffStarted -> {
                    trackPlacement(EventOutcome.SUBMITTED)
                    clearPendingCall()
                    update {
                        copy(
                            route = CommunicationRoute.EXTERNAL_HANDOFF,
                            awaitingOutgoingCall = false,
                            pendingIdentity = null,
                            message = "Continue in the system emergency dialer",
                        )
                    }
                }

                is CallPlacementResult.Rejected -> {
                    trackPlacement(EventOutcome.FAILED)
                    if (result.failure == CallFailure.UNKNOWN) {
                        crashReporter?.record(
                            IllegalStateException("Unexpected call placement failure"),
                            DiagnosticOperation.CALL_PLACEMENT,
                        )
                    }
                    clearPendingCall()
                    update { copy(awaitingOutgoingCall = false) }
                    fail(result.failure, failureMessage(result.failure))
                }
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
        val call = state.value.call
        if (state.value.awaitingOutgoingCall) {
            if (call?.direction != CallDirection.OUTGOING) {
                cancelPendingCall = true
                return
            }
            clearPendingCall()
            update { copy(awaitingOutgoingCall = false) }
        }
        if (call == null) return
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
                    CommunicationRoute.DIALER
                },
                digits = normalizeDialString(entry.identity.number.orEmpty()),
                call = call.takeIf { hasActiveCall },
                pendingIdentity = entry.identity,
                dialerMatches = emptyList(),
                dialerMatchesLoading = false,
                failure = null,
                message = null,
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

            is TelecomEvent.StateChanged -> {
                if (!event.foreground && state.value.hasActiveCall && state.value.call?.id != event.callId) {
                    return
                }
                val pendingOutgoing = matchesPendingCall(event.direction, event.identity)
                val shouldCancel = pendingOutgoing && cancelPendingCall
                if (pendingOutgoing) clearPendingCall()
                update {
                    val knownIdentity = call?.takeIf {
                        it.id == event.callId && it.identity.number == event.identity.number
                    }?.identity ?: pendingIdentity?.takeIf { pendingOutgoing }
                    val connectedAt = event.connectedAtElapsedMillis
                        ?: call?.takeIf { it.id == event.callId }?.connectedAtElapsedMillis
                    copy(
                        route = if (!event.foreground && call?.id == event.callId) {
                            route
                        } else if (event.phase == CallPhase.INCOMING) {
                            CommunicationRoute.INCOMING_CALL
                        } else {
                            CommunicationRoute.ACTIVE_CALL
                        },
                        call = CallSession(
                            id = event.callId,
                            direction = event.direction,
                            identity = event.identity.copy(
                                displayName = event.identity.displayName ?: knownIdentity?.displayName,
                                number = event.identity.number ?: knownIdentity?.number,
                                photoUri = event.identity.photoUri ?: knownIdentity?.photoUri,
                            ),
                            phase = event.phase,
                            capabilities = event.capabilities,
                            audioRoute = event.audioRoute,
                            isMuted = event.isMuted,
                            connectedAtElapsedMillis = connectedAt,
                        ),
                        pendingIdentity = if (pendingOutgoing || !awaitingOutgoingCall) null else pendingIdentity,
                        awaitingOutgoingCall = awaitingOutgoingCall && !pendingOutgoing,
                        failure = null,
                    )
                }
                if (shouldCancel) launchAction { telecomGateway.disconnect(event.callId) }
            }

            is TelecomEvent.Disconnected -> {
                val pendingOutgoing = matchesPendingCall(event.direction, event.identity)
                val knownIdentity = state.value.call?.takeIf {
                    it.id == event.callId && it.identity.number == event.identity.number
                }?.identity ?: state.value.pendingIdentity?.takeIf { pendingOutgoing }
                val identity = event.identity.copy(
                    displayName = event.identity.displayName ?: knownIdentity?.displayName,
                    photoUri = event.identity.photoUri ?: knownIdentity?.photoUri,
                )
                if (pendingOutgoing) clearPendingCall()
                val session = CallSession(
                    id = event.callId,
                    direction = event.direction,
                    identity = identity,
                    phase = CallPhase.DISCONNECTED,
                    connectedAtElapsedMillis = event.connectedAtElapsedMillis,
                    disconnectedAtElapsedMillis = event.disconnectedAtElapsedMillis,
                    outcome = event.outcome,
                    failure = event.failure,
                )
                update {
                    val remaining = event.remainingCalls.firstOrNull { it.id == call?.id }
                        ?: event.remainingCalls.firstOrNull { it.phase == CallPhase.INCOMING }
                        ?: event.remainingCalls.firstOrNull { it.phase == CallPhase.CONNECTED }
                        ?: event.remainingCalls.firstOrNull()
                    val stillWaiting = awaitingOutgoingCall && !pendingOutgoing
                    copy(
                        route = when {
                            remaining?.phase == CallPhase.INCOMING -> CommunicationRoute.INCOMING_CALL
                            remaining != null || stillWaiting -> CommunicationRoute.ACTIVE_CALL
                            else -> routeForOutcome(event.outcome)
                        },
                        call = remaining ?: session.takeUnless { stillWaiting },
                        awaitingOutgoingCall = stillWaiting,
                        pendingIdentity = when {
                            stillWaiting -> pendingIdentity
                            remaining != null -> null
                            else -> identity
                        },
                        failure = event.failure.takeIf { remaining == null && !stillWaiting },
                        message = if (remaining != null || stillWaiting) {
                            null
                        } else if (event.failure != null) {
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
                            identity = identity,
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

    private fun matchesPendingCall(direction: CallDirection, identity: CallerIdentity): Boolean =
        pendingCallNumber != null && direction == CallDirection.OUTGOING &&
            (identity.number.isNullOrBlank() ||
                normalizeDialString(identity.number) == pendingCallNumber)

    private fun clearPendingCall() {
        pendingCallNumber = null
        cancelPendingCall = false
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

    private fun trackPlacement(outcome: EventOutcome) {
        analytics.track(ClaroEvent.TaskFlowResult(EventTask.CALL_PLACEMENT, outcome))
    }

    private val actionScope = scope

    companion object {
        const val CONTACT_PAGE_SIZE = 50
        const val DIALER_MATCH_LIMIT = 5
        private const val MAX_CACHED_DIALER_QUERIES = 16
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
            !qualification.callPermissionGranted || !qualification.phoneStatePermissionGranted ->
                CallFailure.PERMISSION_DENIED
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
