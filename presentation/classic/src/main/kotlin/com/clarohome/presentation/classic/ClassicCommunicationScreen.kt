package com.clarohome.presentation.classic

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.feature.communication.AudioRoute
import com.clarohome.feature.communication.CallPhase
import com.clarohome.feature.communication.CommunicationRoute
import com.clarohome.feature.communication.CommunicationUiState
import com.clarohome.feature.communication.ContactNumber
import com.clarohome.feature.communication.formatCallDuration

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClassicCommunicationScreen(
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
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(classicTitle(state.route), color = Color.White)
        ClassicDisplay(
            state = state,
            onOpenContacts = onOpenContacts,
            onSearchContacts = onSearchContacts,
            onLoadMoreContacts = onLoadMoreContacts,
            onOpenContact = onOpenContact,
            onChooseNumber = onChooseNumber,
            onToggleMute = onToggleMute,
            onAudioRoute = onAudioRoute,
            onToggleHold = onToggleHold,
            onSwap = onSwap,
            onRepairPhone = onRepairPhone,
        )
        ClassicControlDeck(
            state = state,
            onBack = onBackspace,
            onOptions = onOpenContacts,
            onGreen = {
                when (state.route) {
                    CommunicationRoute.INCOMING_CALL -> onAnswer()
                    CommunicationRoute.CALL_CONFIRMATION -> onConfirmCall()
                    CommunicationRoute.DIALER -> onCallEnteredNumber()
                    else -> state.pendingIdentity?.takeIf { it.canReturnCall }?.let {
                        onCallEnteredNumber()
                    }
                }
            },
            onRed = {
                when (state.route) {
                    CommunicationRoute.INCOMING_CALL -> onDecline()
                    CommunicationRoute.ACTIVE_CALL -> onEnd()
                    CommunicationRoute.CALL_CONFIRMATION -> onCancelCall()
                    else -> onHome()
                }
            },
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .height(268.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#')) { digit ->
                Button(
                    onClick = { onDigit(digit) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .combinedClickable(
                            onClick = { onDigit(digit) },
                            onLongClick = {
                                if (digit == '1' || digit == '2') onDirectCall(digit.toString())
                            },
                        ),
                    colors = ButtonDefaults.buttonColors(containerColor = ClaroColors.Surface),
                ) {
                    val assignment = state.assignments.firstOrNull { it.slot == digit.toString() }
                    Text(
                        if (assignment != null) "$digit\n${assignment.displayNameSnapshot}" else digit.toString(),
                        color = ClaroColors.Charcoal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassicDisplay(
    state: CommunicationUiState,
    onOpenContacts: () -> Unit,
    onSearchContacts: (String) -> Unit,
    onLoadMoreContacts: () -> Unit,
    onOpenContact: (String) -> Unit,
    onChooseNumber: (ContactNumber) -> Unit,
    onToggleMute: () -> Unit,
    onAudioRoute: (AudioRoute) -> Unit,
    onToggleHold: () -> Unit,
    onSwap: () -> Unit,
    onRepairPhone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state.route) {
            CommunicationRoute.DIALER -> {
                Text(state.digits.ifBlank { "Enter a number" }, color = Color.White)
                state.history.take(2).forEach { entry ->
                    OutlinedButton(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${entry.identity.accessibleName} · ${entry.outcome.name.lowercase()}")
                    }
                }
                OutlinedButton(onClick = onOpenContacts, modifier = Modifier.fillMaxWidth()) {
                    Text("Contacts")
                }
            }

            CommunicationRoute.CONTACTS -> {
                OutlinedTextField(
                    value = state.contactQuery,
                    onValueChange = onSearchContacts,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search") },
                    singleLine = true,
                )
                LazyColumn(Modifier.height(160.dp)) {
                    items(state.contacts, key = { it.lookupKey }) { contact ->
                        OutlinedButton(
                            onClick = { onOpenContact(contact.lookupKey) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(contact.displayName)
                        }
                    }
                    if (state.contactsNextOffset != null && !state.contactsLoading) {
                        item {
                            OutlinedButton(onClick = onLoadMoreContacts) { Text("More") }
                        }
                    }
                }
            }

            CommunicationRoute.CONTACT_DETAIL -> {
                Text(state.selectedContact?.displayName ?: "Contact unavailable", color = Color.White)
                state.selectedContact?.numbers.orEmpty().forEach { number ->
                    OutlinedButton(
                        onClick = { onChooseNumber(number) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${number.label}: ${number.value}")
                    }
                }
                Text("Edit and delete are in protected Options.", color = Color.White)
            }

            CommunicationRoute.CALL_CONFIRMATION -> {
                Text(state.pendingIdentity?.accessibleName.orEmpty(), color = Color.White)
                Text(state.pendingIdentity?.number.orEmpty(), color = Color.White)
                Text("Press Call to confirm", color = Color.White)
            }

            CommunicationRoute.INCOMING_CALL -> {
                Text(state.call?.identity?.accessibleName ?: "Unknown caller", color = Color.White)
                Text("Call answers · End declines", color = Color.White)
            }

            CommunicationRoute.ACTIVE_CALL -> {
                val call = state.call
                Text(
                    call?.identity?.accessibleName
                        ?: state.pendingIdentity?.accessibleName.orEmpty(),
                    color = Color.White,
                )
                Text(
                    call?.phase?.name?.lowercase()
                        ?: state.message
                        ?: "Waiting for phone service",
                    color = Color.White,
                )
                val duration = call?.connectedDurationMillis(SystemClock.elapsedRealtime())
                if (call?.phase in setOf(CallPhase.CONNECTED, CallPhase.ON_HOLD) && duration != null) {
                    Text(duration.formatCallDuration(), color = Color.White)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = onToggleMute,
                        enabled = call?.capabilities?.canMute == true,
                    ) { Text(if (call?.isMuted == true) "Unmute" else "Mute") }
                    OutlinedButton(
                        onClick = {
                            onAudioRoute(
                                if (call?.audioRoute == AudioRoute.SPEAKER) {
                                    AudioRoute.EARPIECE
                                } else {
                                    AudioRoute.SPEAKER
                                },
                            )
                        },
                        enabled = call?.capabilities?.canChangeAudioRoute == true,
                    ) { Text("Audio") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = onToggleHold,
                        enabled = call?.capabilities?.canHold == true,
                    ) { Text(if (call?.phase == CallPhase.ON_HOLD) "Resume" else "Hold") }
                    OutlinedButton(
                        onClick = onSwap,
                        enabled = call?.capabilities?.canSwap == true,
                    ) { Text("Swap") }
                }
            }

            CommunicationRoute.CALL_ENDED,
            CommunicationRoute.MISSED_CALL,
            CommunicationRoute.CALL_UNANSWERED,
            -> {
                Text(state.pendingIdentity?.accessibleName.orEmpty(), color = Color.White)
                Text(classicTitle(state.route), color = Color.White)
                val duration = state.call?.connectedDurationMillis(SystemClock.elapsedRealtime())
                if (state.route == CommunicationRoute.CALL_ENDED && duration != null) {
                    Text(duration.formatCallDuration(), color = Color.White)
                }
            }

            CommunicationRoute.CALL_FAILED -> {
                Text(state.message ?: "Call did not connect", color = Color.White)
                if (state.qualification.needsRepair) {
                    OutlinedButton(onClick = onRepairPhone) { Text("Repair phone") }
                }
            }

            CommunicationRoute.EXTERNAL_HANDOFF -> {
                Text(state.message.orEmpty(), color = Color.White)
            }
        }
    }
}

@Composable
private fun ClassicControlDeck(
    state: CommunicationUiState,
    onBack: () -> Unit,
    onOptions: () -> Unit,
    onGreen: () -> Unit,
    onRed: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth(0.4f)) { Text("Back") }
        Text("↑\n← OK →\n↓", color = Color.White)
        Button(onClick = onOptions, modifier = Modifier.fillMaxWidth(0.67f)) { Text("Options") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(
            onClick = onGreen,
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(ClaroDimensions.PrimaryTarget),
            colors = ButtonDefaults.buttonColors(containerColor = ClaroColors.Green),
        ) {
            Text(
                when (state.route) {
                    CommunicationRoute.INCOMING_CALL -> "Answer"
                    else -> "Call"
                },
            )
        }
        Button(
            onClick = onRed,
            modifier = Modifier
                .fillMaxWidth()
                .height(ClaroDimensions.PrimaryTarget),
            colors = ButtonDefaults.buttonColors(containerColor = ClaroColors.Red),
        ) {
            Text(
                when (state.route) {
                    CommunicationRoute.INCOMING_CALL -> "Decline"
                    CommunicationRoute.ACTIVE_CALL -> "End"
                    else -> "Home"
                },
            )
        }
    }
}

private fun classicTitle(route: CommunicationRoute): String = when (route) {
    CommunicationRoute.DIALER -> "Phone"
    CommunicationRoute.CONTACTS -> "Contacts"
    CommunicationRoute.CONTACT_DETAIL -> "Contact"
    CommunicationRoute.CALL_CONFIRMATION -> "Confirm call"
    CommunicationRoute.INCOMING_CALL -> "Incoming call"
    CommunicationRoute.ACTIVE_CALL -> "Call"
    CommunicationRoute.CALL_ENDED -> "Call ended"
    CommunicationRoute.MISSED_CALL -> "Missed call"
    CommunicationRoute.CALL_UNANSWERED -> "No answer"
    CommunicationRoute.CALL_FAILED -> "Call failed"
    CommunicationRoute.EXTERNAL_HANDOFF -> "System dialer"
}
