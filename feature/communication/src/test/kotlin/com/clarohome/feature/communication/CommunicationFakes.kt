package com.clarohome.feature.communication

import android.content.Intent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class FakeDialerRoleGateway(
    initial: DialerQualification = readyQualification(),
) : DialerRoleGateway {
    val mutableQualification = MutableStateFlow(initial)
    override val qualification: StateFlow<DialerQualification> = mutableQualification
    override fun createRoleRequest(): Intent? = null
    override suspend fun refresh() = Unit
}

class FakeTelecomGateway : TelecomGateway {
    class EventSource {
        private val mutable = MutableSharedFlow<TelecomEvent?>(replay = 1, extraBufferCapacity = 64)
        val flow: SharedFlow<TelecomEvent?> = mutable
        var value: TelecomEvent?
            get() = mutable.replayCache.lastOrNull()
            set(event) {
                check(mutable.tryEmit(event))
            }
    }

    val mutableEvents = EventSource()
    override val events: SharedFlow<TelecomEvent?> = mutableEvents.flow
    var placements = 0
    var emergencyHandoffs = 0
    var placementResult: CallPlacementResult = CallPlacementResult.SubmittedToTelecom
    var placementGate: CompletableDeferred<Unit>? = null
    val emergencyNumbers = mutableSetOf<String>()
    val actions = mutableListOf<String>()

    override suspend fun placeCall(number: String): CallPlacementResult {
        placements += 1
        actions += "place:$number"
        placementGate?.await()
        return placementResult
    }

    override suspend fun handoffEmergency(number: String): CallPlacementResult {
        emergencyHandoffs += 1
        actions += "emergency:$number"
        return CallPlacementResult.EmergencyHandoffStarted
    }

    override fun isEmergencyNumber(number: String): Boolean = number in emergencyNumbers
    override suspend fun answer(callId: String) {
        actions += "answer:$callId"
    }

    override suspend fun decline(callId: String) {
        actions += "decline:$callId"
    }

    override suspend fun disconnect(callId: String) {
        actions += "disconnect:$callId"
    }

    override suspend fun setMuted(muted: Boolean) {
        actions += "mute:$muted"
    }

    override suspend fun setAudioRoute(route: AudioRoute) {
        actions += "audio:$route"
    }

    override suspend fun setHeld(callId: String, held: Boolean) {
        actions += "hold:$callId:$held"
    }

    override suspend fun swap(callId: String) {
        actions += "swap:$callId"
    }

    override suspend fun playDtmf(callId: String, digit: Char) {
        actions += "dtmf:$callId:$digit"
    }
}

class FakeContactsGateway(
    contacts: List<ClaroContact> = emptyList(),
) : ContactsGateway {
    private val values = contacts.associateByTo(linkedMapOf()) { it.lookupKey }
    var largestRequestedPage = 0
    val matchingQueries = mutableListOf<String>()
    var matchingGate: CompletableDeferred<Unit>? = null

    override suspend fun matchingNumbers(digits: String, limit: Int): List<CallerIdentity> {
        matchingQueries += digits
        matchingGate?.await()
        return values.values.flatMap { contact ->
            contact.numbers.filter {
                PhoneContactsCoordinator.normalizeDialString(it.value).contains(digits)
            }.map { CallerIdentity(contact.displayName, it.value, photoUri = contact.photoUri) }
        }.take(limit)
    }

    override suspend fun page(query: String, offset: Int, limit: Int): ContactPage {
        largestRequestedPage = maxOf(largestRequestedPage, limit)
        val filtered = values.values.filter { contact ->
            query.isBlank() ||
                contact.displayName.contains(query, ignoreCase = true) ||
                contact.numbers.any { it.value.contains(query) }
        }
        val page = filtered.drop(offset).take(limit)
        return ContactPage(
            contacts = page,
            nextOffset = if (offset + page.size < filtered.size) offset + page.size else null,
            totalCount = filtered.size,
        )
    }

    override suspend fun get(lookupKey: String): ClaroContact? = values[lookupKey]

    override suspend fun add(mutation: ContactMutation): ContactWriteResult {
        val lookupKey = "contact-${values.size + 1}"
        values[lookupKey] = ClaroContact(
            lookupKey = lookupKey,
            displayName = mutation.displayName,
            photoUri = mutation.photoUri,
            numbers = mutation.numbers,
        )
        return ContactWriteResult.Saved(lookupKey)
    }

    override suspend fun edit(
        lookupKey: String,
        mutation: ContactMutation,
    ): ContactWriteResult {
        val current = values[lookupKey] ?: return ContactWriteResult.Failed("Missing")
        values[lookupKey] = current.copy(
            displayName = mutation.displayName,
            photoUri = mutation.photoUri,
            numbers = mutation.numbers,
        )
        return ContactWriteResult.Saved(lookupKey)
    }

    override suspend fun delete(lookupKey: String): ContactWriteResult =
        if (values.remove(lookupKey) != null) {
            ContactWriteResult.Saved(lookupKey)
        } else {
            ContactWriteResult.Failed("Missing")
        }
}

class FakePhoneAssignmentStore(
    initial: List<PhoneAssignment> = emptyList(),
) : PhoneAssignmentStore {
    val mutableAssignments = MutableStateFlow(initial)
    override val assignments: StateFlow<List<PhoneAssignment>> = mutableAssignments

    override suspend fun save(assignment: PhoneAssignment) {
        mutableAssignments.value =
            mutableAssignments.value.filterNot { it.slot == assignment.slot } + assignment
    }

    override suspend fun clear(slot: String) {
        mutableAssignments.value = mutableAssignments.value.filterNot { it.slot == slot }
    }
}

class FakeCallHistoryStore : CallHistoryStore {
    val mutableHistory = MutableStateFlow<List<CallHistoryEntry>>(emptyList())
    override val history: StateFlow<List<CallHistoryEntry>> = mutableHistory

    override suspend fun record(entry: CallHistoryEntry) {
        mutableHistory.value = listOf(entry) + mutableHistory.value.filterNot { it.id == entry.id }
    }

    override suspend fun markOpened(id: String) {
        mutableHistory.value = mutableHistory.value.map {
            if (it.id == id) it.copy(isOpened = true) else it
        }
    }
}

class FakeElapsedRealtimeClock(var elapsedMillis: Long = 0) : ElapsedRealtimeClock {
    override fun now(): Long = elapsedMillis
}

fun readyQualification() = DialerQualification(
    roleAvailable = true,
    roleHeld = true,
    telephonySupported = true,
    callPermissionGranted = true,
    callLogPermissionGranted = true,
    hasUsableCallingAccount = true,
    phoneStatePermissionGranted = true,
)

fun contactFixture(index: Int): ClaroContact = ClaroContact(
    lookupKey = "lookup-$index",
    displayName = "Person ${index.toString().padStart(4, '0')}",
    numbers = listOf(ContactNumber("+1555${index.toString().padStart(7, '0')}", "Mobile")),
)
