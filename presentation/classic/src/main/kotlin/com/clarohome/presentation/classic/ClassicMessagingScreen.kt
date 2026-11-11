package com.clarohome.presentation.classic

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clarohome.core.design.ClaroColors
import com.clarohome.feature.communication.ClaroContact
import com.clarohome.feature.communication.ContactNumber
import com.clarohome.feature.communication.MessageDirection
import com.clarohome.feature.communication.MessagingRoute
import com.clarohome.feature.communication.MessagingUiState

@Composable
fun ClassicMessagingScreen(
    state: MessagingUiState,
    onSearchInbox: (String) -> Unit,
    onOpenConversation: (Long) -> Unit,
    onNewMessage: () -> Unit,
    onSearchRecipients: (String) -> Unit,
    onChooseContactNumber: (ClaroContact, ContactNumber) -> Unit,
    onManualRecipient: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onReview: () -> Unit,
    onEdit: () -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onConfirmDuplicateRisk: () -> Unit,
    onCancelFailure: () -> Unit,
    onRepair: () -> Unit,
    onHome: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(classicMessagingTitle(state.route), color = Color.White)
        ClassicMessageDisplay(
            state = state,
            onSearchInbox = onSearchInbox,
            onOpenConversation = onOpenConversation,
            onSearchRecipients = onSearchRecipients,
            onChooseContactNumber = onChooseContactNumber,
            onBodyChange = onBodyChange,
            onRepair = onRepair,
        )
        ClassicMessageControls(
            state = state,
            onOptions = {
                when (state.route) {
                    MessagingRoute.INBOX -> onNewMessage()
                    MessagingRoute.RECIPIENTS -> onManualRecipient(state.recipientQuery)
                    MessagingRoute.CONFIRMATION -> onEdit()
                    MessagingRoute.FAILED -> onCancelFailure()
                    else -> Unit
                }
            },
            onGreen = {
                when (state.route) {
                    MessagingRoute.INBOX -> onNewMessage()
                    MessagingRoute.RECIPIENTS -> onManualRecipient(state.recipientQuery)
                    MessagingRoute.COMPOSE,
                    MessagingRoute.CONVERSATION,
                    -> onReview()

                    MessagingRoute.CONFIRMATION -> onSend()
                    MessagingRoute.FAILED -> if (state.duplicateRiskConfirmationRequired) {
                        onConfirmDuplicateRisk()
                    } else {
                        onRetry()
                    }

                    MessagingRoute.SENDING -> Unit
                }
            },
            onRed = onHome,
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
                    onClick = {
                        if (state.route == MessagingRoute.RECIPIENTS) {
                            onSearchRecipients(state.recipientQuery + digit)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ClaroColors.Surface),
                ) {
                    Text(digit.toString(), color = ClaroColors.Charcoal)
                }
            }
        }
    }
}

@Composable
private fun ClassicMessageDisplay(
    state: MessagingUiState,
    onSearchInbox: (String) -> Unit,
    onOpenConversation: (Long) -> Unit,
    onSearchRecipients: (String) -> Unit,
    onChooseContactNumber: (ClaroContact, ContactNumber) -> Unit,
    onBodyChange: (String) -> Unit,
    onRepair: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (state.route) {
            MessagingRoute.INBOX -> {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchInbox,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search messages") },
                    singleLine = true,
                )
                if (!state.qualification.canRead) {
                    OutlinedButton(onClick = onRepair) { Text("Repair messages access") }
                }
                LazyColumn {
                    items(state.conversations, key = { it.id }) { conversation ->
                        OutlinedButton(
                            onClick = { onOpenConversation(conversation.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "${conversation.recipient.accessibleName}: " +
                                    "${conversation.snippet} (${conversation.unreadCount})",
                            )
                        }
                    }
                }
            }

            MessagingRoute.CONVERSATION -> {
                Text(state.selectedConversation?.recipient?.accessibleName.orEmpty(), color = Color.White)
                LazyColumn(Modifier.height(120.dp)) {
                    items(state.messages, key = { "${it.kind}:${it.providerId}" }) { message ->
                        Text(
                            "${if (message.direction == MessageDirection.INCOMING) "From" else "To"}: " +
                                message.body,
                            color = Color.White,
                        )
                    }
                }
                OutlinedTextField(
                    value = state.draft.body,
                    onValueChange = onBodyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reply") },
                )
            }

            MessagingRoute.RECIPIENTS -> {
                OutlinedTextField(
                    value = state.recipientQuery,
                    onValueChange = onSearchRecipients,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Contact or number") },
                    singleLine = true,
                )
                LazyColumn {
                    state.contacts.forEach { contact ->
                        item(key = contact.lookupKey) {
                            Text(contact.displayName, color = Color.White)
                        }
                        items(contact.numbers, key = { "${contact.lookupKey}:${it.value}" }) { number ->
                            OutlinedButton(
                                onClick = { onChooseContactNumber(contact, number) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("${number.label}: ${number.value}")
                            }
                        }
                    }
                }
            }

            MessagingRoute.COMPOSE -> {
                Text("To: ${state.draft.recipient?.accessibleName.orEmpty()}", color = Color.White)
                Text(state.draft.recipient?.number.orEmpty(), color = Color.White)
                OutlinedTextField(
                    value = state.draft.body,
                    onValueChange = onBodyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Message") },
                )
                Text(
                    "${state.encoding.segments} SMS · ${state.encoding.encoding.name.replace('_', ' ')}",
                    color = Color.White,
                )
            }

            MessagingRoute.CONFIRMATION -> {
                Text("To: ${state.draft.recipient?.accessibleName.orEmpty()}", color = Color.White)
                Text(state.draft.recipient?.number.orEmpty(), color = Color.White)
                Text(state.draft.body, color = Color.White)
                Text("Press Send to confirm", color = Color.White)
            }

            MessagingRoute.SENDING -> {
                Text("Sending", color = Color.White)
                Text("Wait for the carrier result.", color = Color.White)
            }

            MessagingRoute.FAILED -> {
                Text(state.draft.recipient?.accessibleName.orEmpty(), color = Color.White)
                Text(state.draft.recipient?.number.orEmpty(), color = Color.White)
                Text(state.draft.body, color = Color.White)
                Text(state.statusMessage.orEmpty(), color = Color.White)
                if (state.duplicateRiskConfirmationRequired) {
                    Text("Press Send again to accept duplicate risk.", color = Color.White)
                }
            }
        }
        state.statusMessage?.takeIf { state.route != MessagingRoute.FAILED }?.let {
            Text(it, color = Color.White)
        }
    }
}

@Composable
private fun ClassicMessageControls(
    state: MessagingUiState,
    onOptions: () -> Unit,
    onGreen: () -> Unit,
    onRed: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = onOptions, modifier = Modifier.fillMaxWidth(0.4f)) {
            Text("Back")
        }
        Text("↑\n← OK →\n↓", color = Color.White)
        Button(onClick = onOptions, modifier = Modifier.fillMaxWidth(0.67f)) {
            Text("Options")
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(
            onClick = onGreen,
            enabled = state.route != MessagingRoute.SENDING,
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ClaroColors.Green),
        ) {
            Text(
                when (state.route) {
                    MessagingRoute.INBOX -> "New"
                    MessagingRoute.RECIPIENTS -> "Choose"
                    MessagingRoute.COMPOSE,
                    MessagingRoute.CONVERSATION,
                    -> "Review"

                    MessagingRoute.CONFIRMATION -> "Send"
                    MessagingRoute.FAILED -> if (state.duplicateRiskConfirmationRequired) {
                        "Send again"
                    } else {
                        "Try again"
                    }

                    MessagingRoute.SENDING -> "Sending"
                },
            )
        }
        Button(
            onClick = onRed,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ClaroColors.Red),
        ) {
            Text("Home")
        }
    }
}

private fun classicMessagingTitle(route: MessagingRoute): String = when (route) {
    MessagingRoute.INBOX -> "Messages"
    MessagingRoute.CONVERSATION -> "Conversation"
    MessagingRoute.RECIPIENTS -> "Recipient"
    MessagingRoute.COMPOSE -> "Compose"
    MessagingRoute.CONFIRMATION -> "Confirm message"
    MessagingRoute.SENDING -> "Sending"
    MessagingRoute.FAILED -> "Message failed"
}
