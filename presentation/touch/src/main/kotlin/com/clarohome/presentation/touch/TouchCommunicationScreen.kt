package com.clarohome.presentation.touch

import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.feature.communication.AudioRoute
import com.clarohome.feature.communication.CallPhase
import com.clarohome.feature.communication.CallerIdentity
import com.clarohome.feature.communication.CommunicationRoute
import com.clarohome.feature.communication.CommunicationUiState
import com.clarohome.feature.communication.ContactNumber
import com.clarohome.feature.communication.formatCallDuration
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TouchCommunicationScreen(
    state: CommunicationUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onCallEnteredNumber: () -> Unit,
    onDirectCall: (String) -> Unit,
    onOpenContacts: () -> Unit,
    onSearchContacts: (String) -> Unit,
    onLoadMoreContacts: () -> Unit,
    onOpenContact: (String) -> Unit,
    onChooseNumber: (ContactNumber) -> Unit,
    onConfirmCall: () -> Unit,
    onCancelCall: () -> Unit,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onAudioRoute: (AudioRoute) -> Unit,
    onToggleHold: () -> Unit,
    onSwap: () -> Unit,
    onRepairPhone: () -> Unit,
    onHome: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ClaroDimensions.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(routeTitle(state.route), color = Color.White)
        when (state.route) {
            CommunicationRoute.DIALER -> TouchDialer(
                state = state,
                onDigit = onDigit,
                onBackspace = onBackspace,
                onCall = onCallEnteredNumber,
                onDirectCall = onDirectCall,
                onOpenContacts = onOpenContacts,
            )

            CommunicationRoute.CONTACTS -> ContactList(
                state = state,
                onSearch = onSearchContacts,
                onLoadMore = onLoadMoreContacts,
                onOpenContact = onOpenContact,
            )

            CommunicationRoute.CONTACT_DETAIL -> ContactDetail(
                state = state,
                onChooseNumber = onChooseNumber,
            )

            CommunicationRoute.CALL_CONFIRMATION -> Confirmation(
                identity = state.pendingIdentity,
                onConfirm = onConfirmCall,
                onCancel = onCancelCall,
            )

            CommunicationRoute.INCOMING_CALL -> IncomingCall(
                identity = state.call?.identity,
                onAnswer = onAnswer,
                onDecline = onDecline,
            )

            CommunicationRoute.ACTIVE_CALL -> ActiveCall(
                state = state,
                onEnd = onEnd,
                onToggleMute = onToggleMute,
                onAudioRoute = onAudioRoute,
                onToggleHold = onToggleHold,
                onSwap = onSwap,
            )

            CommunicationRoute.CALL_ENDED,
            CommunicationRoute.MISSED_CALL,
            CommunicationRoute.CALL_UNANSWERED,
            -> CallOutcomeScreen(state, onHome)

            CommunicationRoute.CALL_FAILED -> FailureScreen(
                state = state,
                onRepairPhone = onRepairPhone,
                onHome = onHome,
            )

            CommunicationRoute.EXTERNAL_HANDOFF -> {
                Text(state.message.orEmpty(), color = Color.White)
                PrimaryButton("Home", onHome)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TouchDialer(
    state: CommunicationUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onCall: () -> Unit,
    onDirectCall: (String) -> Unit,
    onOpenContacts: () -> Unit,
) {
    Text(
        text = state.digits.ifBlank { "Enter a number" },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Phone number ${state.digits}" },
        color = Color.White,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("1", "2").forEach { slot ->
            val assignment = state.assignments.firstOrNull { it.slot == slot }
            OutlinedButton(
                onClick = { },
                modifier = Modifier
                    .weight(1f)
                    .height(ClaroDimensions.SecondaryTarget)
                    .combinedClickable(
                        onClick = { onDigit(slot.single()) },
                        onLongClick = { onDirectCall(slot) },
                    ),
            ) {
                Text("Hold $slot: ${assignment?.displayNameSnapshot ?: "Not set"}")
            }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#')) { digit ->
            Button(
                onClick = { onDigit(digit) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ClaroDimensions.PrimaryTarget),
                colors = ButtonDefaults.buttonColors(containerColor = ClaroColors.Surface),
            ) {
                Text(digit.toString(), color = ClaroColors.Charcoal)
            }
        }
    }
    PrimaryButton("Contacts", onOpenContacts, containerColor = ClaroColors.Blue)
    PrimaryButton("Call", onCall)
    PrimaryButton("Backspace", onBackspace, containerColor = ClaroColors.Surface, contentColor = ClaroColors.Charcoal)
    state.message?.let { Text(it, color = Color.White) }
}

@Composable
private fun ContactList(
    state: CommunicationUiState,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenContact: (String) -> Unit,
) {
    OutlinedTextField(
        value = state.contactQuery,
        onValueChange = onSearch,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Search name or number") },
        singleLine = true,
    )
    if (!state.qualification.roleHeld) {
        Text("Phone role needs repair. Contacts can still be browsed.", color = Color.White)
    }
    LazyColumn(
        modifier = Modifier.height(400.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!state.contactsLoading && state.contacts.isEmpty()) {
            item {
                Text(
                    if (state.contactQuery.isBlank()) "No contacts available" else "No contacts found",
                    color = Color.White,
                )
            }
        }
        items(state.contacts, key = { it.lookupKey }) { contact ->
            OutlinedButton(
                onClick = { onOpenContact(contact.lookupKey) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ClaroDimensions.PrimaryTarget),
            ) {
                Text("${contact.displayName} · ${contact.numbers.firstOrNull()?.value.orEmpty()}")
            }
        }
        if (state.contactsNextOffset != null && !state.contactsLoading) {
            item { PrimaryButton("Load more", onLoadMore) }
        }
        if (state.contactsLoading) {
            item { Text("Loading contacts", color = Color.White) }
        }
    }
}

@Composable
private fun ContactDetail(
    state: CommunicationUiState,
    onChooseNumber: (ContactNumber) -> Unit,
) {
    val contact = state.selectedContact
    if (contact == null) {
        Text(state.message ?: "Contact not available", color = Color.White)
        return
    }
    Text(contact.displayName, color = Color.White)
    contact.numbers.forEach { number ->
        PrimaryButton(
            label = "${number.label}: ${number.value}",
            onClick = { onChooseNumber(number) },
        )
    }
    Text("Add, edit and delete require caregiver unlock.", color = Color.White)
}

@Composable
private fun Confirmation(
    identity: CallerIdentity?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(identity?.accessibleName ?: "Unknown number", color = Color.White)
    identity?.number?.let { Text(it, color = Color.White) }
    PrimaryButton("Call", onConfirm)
    PrimaryButton("Cancel", onCancel, containerColor = ClaroColors.Surface, contentColor = ClaroColors.Charcoal)
}

@Composable
private fun IncomingCall(
    identity: CallerIdentity?,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
) {
    Text(identity?.accessibleName ?: "Unknown caller", color = Color.White)
    identity?.number?.let { Text(it, color = Color.White) }
    PrimaryButton("Answer", onAnswer)
    PrimaryButton("Decline", onDecline, containerColor = ClaroColors.Red)
}

@Composable
private fun ActiveCall(
    state: CommunicationUiState,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onAudioRoute: (AudioRoute) -> Unit,
    onToggleHold: () -> Unit,
    onSwap: () -> Unit,
) {
    val call = state.call
    if (call == null) {
        Text(state.pendingIdentity?.accessibleName.orEmpty(), color = Color.White)
        Text(state.message ?: "Waiting for phone service", color = Color.White)
        return
    }
    Text(call.identity.accessibleName, color = Color.White)
    call.identity.number?.let { Text(it, color = Color.White) }
    Text(call.phase.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase), color = Color.White)
    val duration = rememberConnectedDuration(call.connectedAtElapsedMillis)
    if (call.phase in setOf(CallPhase.CONNECTED, CallPhase.ON_HOLD) && duration != null) {
        Text(duration.formatCallDuration(), color = Color.White)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SmallControl(
            if (call.isMuted) "Unmute" else "Mute",
            call.capabilities.canMute,
            onToggleMute,
            Modifier.fillMaxWidth(0.5f),
        )
        SmallControl(
            if (call.audioRoute == AudioRoute.SPEAKER) "Earpiece" else "Speaker",
            call.capabilities.canChangeAudioRoute,
            {
                onAudioRoute(
                    if (call.audioRoute == AudioRoute.SPEAKER) {
                        AudioRoute.EARPIECE
                    } else {
                        AudioRoute.SPEAKER
                    },
                )
            },
            Modifier.fillMaxWidth(0.5f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SmallControl(
            if (call.phase == CallPhase.ON_HOLD) "Resume" else "Hold",
            call.capabilities.canHold,
            onToggleHold,
            Modifier.fillMaxWidth(0.5f),
        )
        SmallControl("Swap", call.capabilities.canSwap, onSwap, Modifier.fillMaxWidth(0.5f))
    }
    Text("Use the keypad for tones during a connected call.", color = Color.White)
    PrimaryButton("End call", onEnd, containerColor = ClaroColors.Red)
}

@Composable
private fun CallOutcomeScreen(state: CommunicationUiState, onHome: () -> Unit) {
    val call = state.call
    Text(call?.identity?.accessibleName ?: state.pendingIdentity?.accessibleName.orEmpty(), color = Color.White)
    val duration = call?.connectedDurationMillis(SystemClock.elapsedRealtime())
    if (state.route == CommunicationRoute.CALL_ENDED && duration != null) {
        Text("Call duration ${duration.formatCallDuration()}", color = Color.White)
    }
    Text(
        when (state.route) {
            CommunicationRoute.MISSED_CALL -> "Missed incoming call"
            CommunicationRoute.CALL_UNANSWERED -> "No answer"
            else -> "Call ended"
        },
        color = Color.White,
    )
    PrimaryButton("Home", onHome)
}

@Composable
private fun FailureScreen(
    state: CommunicationUiState,
    onRepairPhone: () -> Unit,
    onHome: () -> Unit,
) {
    Text(state.message ?: "The call did not connect", color = Color.White)
    if (state.qualification.needsRepair) {
        PrimaryButton("Repair phone access", onRepairPhone)
    }
    PrimaryButton("Home", onHome, containerColor = ClaroColors.Surface, contentColor = ClaroColors.Charcoal)
}

@Composable
private fun SmallControl(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(ClaroDimensions.SecondaryTarget),
    ) {
        Text(label)
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    containerColor: Color = ClaroColors.Green,
    contentColor: Color = Color.White,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(ClaroDimensions.PrimaryTarget),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Text(label)
    }
}

@Composable
private fun rememberConnectedDuration(connectedAt: Long?): Long? {
    var now by remember(connectedAt) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(connectedAt) {
        while (connectedAt != null) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    return connectedAt?.let { (now - it).coerceAtLeast(0) }
}

private fun routeTitle(route: CommunicationRoute): String = when (route) {
    CommunicationRoute.DIALER -> "Phone"
    CommunicationRoute.CONTACTS -> "Contacts"
    CommunicationRoute.CONTACT_DETAIL -> "Contact"
    CommunicationRoute.CALL_CONFIRMATION -> "Call this person?"
    CommunicationRoute.INCOMING_CALL -> "Incoming call"
    CommunicationRoute.ACTIVE_CALL -> "Call"
    CommunicationRoute.CALL_ENDED -> "Call ended"
    CommunicationRoute.MISSED_CALL -> "Missed call"
    CommunicationRoute.CALL_UNANSWERED -> "No answer"
    CommunicationRoute.CALL_FAILED -> "Call failed"
    CommunicationRoute.EXTERNAL_HANDOFF -> "System dialer"
}
