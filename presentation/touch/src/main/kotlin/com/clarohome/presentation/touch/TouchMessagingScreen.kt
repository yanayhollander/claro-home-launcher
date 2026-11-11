package com.clarohome.presentation.touch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.feature.communication.AttachmentState
import com.clarohome.feature.communication.ClaroContact
import com.clarohome.feature.communication.ContactNumber
import com.clarohome.feature.communication.MessageDeliveryState
import com.clarohome.feature.communication.MessageDirection
import com.clarohome.feature.communication.MessageKind
import com.clarohome.feature.communication.MessagingRoute
import com.clarohome.feature.communication.MessagingUiState
import com.clarohome.feature.communication.SmsEncoding

@Composable
fun TouchMessagingScreen(
    state: MessagingUiState,
    onSearchInbox: (String) -> Unit,
    onOpenConversation: (Long) -> Unit,
    onNewMessage: () -> Unit,
    onSearchRecipients: (String) -> Unit,
    onLoadMoreRecipients: () -> Unit,
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
            .padding(ClaroDimensions.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(touchMessagingTitle(state.route), color = Color.White)
        when (state.route) {
            MessagingRoute.INBOX -> Inbox(
                state,
                onSearchInbox,
                onOpenConversation,
                onNewMessage,
                onRepair,
            )

            MessagingRoute.CONVERSATION -> Conversation(state, onBodyChange, onReview)
            MessagingRoute.RECIPIENTS -> Recipients(
                state,
                onSearchRecipients,
                onLoadMoreRecipients,
                onChooseContactNumber,
                onManualRecipient,
            )

            MessagingRoute.COMPOSE -> Composer(state, onBodyChange, onReview)
            MessagingRoute.CONFIRMATION -> Confirmation(state, onSend, onEdit, onCancelFailure)
            MessagingRoute.SENDING -> {
                Text("Sending to ${state.draft.recipient?.accessibleName.orEmpty()}", color = Color.White)
                Text("Wait for the carrier result before sending again.", color = Color.White)
            }

            MessagingRoute.FAILED -> Failure(
                state,
                onRetry,
                onConfirmDuplicateRisk,
                onCancelFailure,
            )
        }
        state.statusMessage?.let { Text(it, color = Color.White) }
        if (state.route !in setOf(MessagingRoute.CONFIRMATION, MessagingRoute.FAILED)) {
            SecondaryButton("Home", onHome)
        }
    }
}

@Composable
private fun Inbox(
    state: MessagingUiState,
    onSearch: (String) -> Unit,
    onOpenConversation: (Long) -> Unit,
    onNewMessage: () -> Unit,
    onRepair: () -> Unit,
) {
    OutlinedTextField(
        value = state.searchQuery,
        onValueChange = onSearch,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Search messages or numbers") },
        singleLine = true,
    )
    if (!state.qualification.canRead) {
        Text("Claro cannot read messages until SMS access is repaired.", color = Color.White)
        PrimaryButton("Repair messages access", onRepair)
    }
    LazyColumn(
        modifier = Modifier.height(360.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (state.conversations.isEmpty()) {
            item {
                Text(
                    if (state.searchQuery.isBlank()) "No messages" else "No messages found",
                    color = Color.White,
                )
            }
        }
        items(state.conversations, key = { it.id }) { conversation ->
            OutlinedButton(
                onClick = { onOpenConversation(conversation.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ClaroDimensions.PrimaryTarget),
            ) {
                val type = if (conversation.kind == MessageKind.MMS) "MMS · " else ""
                val unread = if (conversation.unreadCount > 0) {
                    " · ${conversation.unreadCount} unread"
                } else {
                    ""
                }
                Text("$type${conversation.recipient.accessibleName}: ${conversation.snippet}$unread")
            }
        }
    }
    PrimaryButton("New message", onNewMessage, ClaroColors.Blue)
}

@Composable
private fun Conversation(
    state: MessagingUiState,
    onBodyChange: (String) -> Unit,
    onReview: () -> Unit,
) {
    Text(state.selectedConversation?.recipient?.accessibleName.orEmpty(), color = Color.White)
    LazyColumn(
        modifier = Modifier.height(300.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(state.messages, key = { "${it.kind}:${it.providerId}" }) { message ->
            val direction = if (message.direction == MessageDirection.INCOMING) "From" else "To"
            val status = when (message.deliveryState) {
                MessageDeliveryState.DELIVERY_CONFIRMED -> " · Delivered"
                MessageDeliveryState.DELIVERY_UNKNOWN -> " · Delivery unknown"
                MessageDeliveryState.KNOWN_FAILURE -> " · Failed"
                MessageDeliveryState.PENDING -> " · Sending"
                MessageDeliveryState.SENT -> " · Sent, delivery not confirmed"
                else -> ""
            }
            Text("$direction ${message.senderOrRecipient}: ${message.body}$status", color = Color.White)
            message.attachments.forEach { attachment ->
                Text(attachment.state.accessibleAttachmentMessage(), color = Color.White)
            }
        }
    }
    MessageEditor(state, onBodyChange)
    PrimaryButton("Review message", onReview)
}

@Composable
private fun Recipients(
    state: MessagingUiState,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onChooseNumber: (ClaroContact, ContactNumber) -> Unit,
    onManualRecipient: (String) -> Unit,
) {
    OutlinedTextField(
        value = state.recipientQuery,
        onValueChange = onSearch,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Contact or phone number") },
        singleLine = true,
    )
    PrimaryButton(
        "Use typed number",
        { onManualRecipient(state.recipientQuery) },
        ClaroColors.Blue,
    )
    LazyColumn(
        modifier = Modifier.height(300.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        state.contacts.forEach { contact ->
            item(key = "name-${contact.lookupKey}") {
                Text(contact.displayName, color = Color.White)
            }
            items(contact.numbers, key = { "${contact.lookupKey}:${it.value}" }) { number ->
                OutlinedButton(
                    onClick = { onChooseNumber(contact, number) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("${number.label}: ${number.value}")
                }
            }
        }
        if (state.contactsNextOffset != null && !state.contactsLoading) {
            item { SecondaryButton("Load more contacts", onLoadMore) }
        }
    }
}

@Composable
private fun Composer(
    state: MessagingUiState,
    onBodyChange: (String) -> Unit,
    onReview: () -> Unit,
) {
    Text("To: ${state.draft.recipient?.accessibleName.orEmpty()}", color = Color.White)
    Text(state.draft.recipient?.number.orEmpty(), color = Color.White)
    MessageEditor(state, onBodyChange)
    PrimaryButton("Review message", onReview)
}

@Composable
private fun MessageEditor(state: MessagingUiState, onBodyChange: (String) -> Unit) {
    OutlinedTextField(
        value = state.draft.body,
        onValueChange = onBodyChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        label = { Text("Message") },
    )
    val encoding = state.encoding
    Text(
        buildString {
            append(if (encoding.encoding == SmsEncoding.UNICODE) "Unicode" else "Standard text")
            append(" · ${encoding.segments} SMS")
            if (encoding.isMultipart) append(" · carrier may charge for each part")
        },
        color = Color.White,
    )
}

@Composable
private fun Confirmation(
    state: MessagingUiState,
    onSend: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
) {
    Text("To: ${state.draft.recipient?.accessibleName.orEmpty()}", color = Color.White)
    Text(state.draft.recipient?.number.orEmpty(), color = Color.White)
    Text(state.draft.body, color = Color.White)
    PrimaryButton("Send", onSend)
    SecondaryButton("Edit", onEdit)
    SecondaryButton("Cancel", onCancel)
}

@Composable
private fun Failure(
    state: MessagingUiState,
    onRetry: () -> Unit,
    onConfirmDuplicateRisk: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(state.draft.recipient?.accessibleName.orEmpty(), color = Color.White)
    Text(state.draft.recipient?.number.orEmpty(), color = Color.White)
    Text(state.draft.body, color = Color.White)
    if (state.unknownOutcome) {
        Text("This message may already have been sent.", color = Color.White)
    }
    if (state.duplicateRiskConfirmationRequired) {
        PrimaryButton("Send again", onConfirmDuplicateRisk)
        SecondaryButton("Do not resend", onCancel)
    } else {
        PrimaryButton("Try again", onRetry)
        SecondaryButton("Cancel", onCancel)
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit, color: Color = ClaroColors.Green) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(ClaroDimensions.PrimaryTarget),
        colors = ButtonDefaults.buttonColors(containerColor = color),
    ) {
        Text(label)
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(ClaroDimensions.PrimaryTarget),
    ) {
        Text(label)
    }
}

private fun touchMessagingTitle(route: MessagingRoute): String = when (route) {
    MessagingRoute.INBOX -> "Messages"
    MessagingRoute.CONVERSATION -> "Conversation"
    MessagingRoute.RECIPIENTS -> "Choose recipient"
    MessagingRoute.COMPOSE -> "New message"
    MessagingRoute.CONFIRMATION -> "Send this message?"
    MessagingRoute.SENDING -> "Sending"
    MessagingRoute.FAILED -> "Message not sent"
}

private fun AttachmentState.accessibleAttachmentMessage(): String = when (this) {
    AttachmentState.NONE -> "No attachment"
    AttachmentState.AVAILABLE -> "Attachment available"
    AttachmentState.DOWNLOAD_REQUIRED -> "Attachment needs mobile data to download"
    AttachmentState.DOWNLOAD_FAILED -> "Attachment download failed"
    AttachmentState.UNSUPPORTED -> "This attachment type is not supported"
}
