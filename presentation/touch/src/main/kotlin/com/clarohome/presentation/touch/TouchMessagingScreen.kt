package com.clarohome.presentation.touch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarohome.core.design.ClaroAvatar
import com.clarohome.core.design.ClaroBannerTone
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroIconKind
import com.clarohome.core.design.ClaroInfoCard
import com.clarohome.core.design.ClaroPrimaryButton
import com.clarohome.core.design.ClaroRowButton
import com.clarohome.core.design.ClaroScreenScaffold
import com.clarohome.core.design.ClaroSecondaryButton
import com.clarohome.feature.communication.AttachmentState
import com.clarohome.feature.communication.ClaroContact
import com.clarohome.feature.communication.ClaroMessage
import com.clarohome.feature.communication.ContactNumber
import com.clarohome.feature.communication.MessageDeliveryState
import com.clarohome.feature.communication.MessageDirection
import com.clarohome.feature.communication.MessageFailure
import com.clarohome.feature.communication.MessageKind
import com.clarohome.feature.communication.MessageRecipient
import com.clarohome.feature.communication.MessagingRoute
import com.clarohome.feature.communication.MessagingUiState
import com.clarohome.feature.communication.SmsEncoding
import com.clarohome.presentation.touch.R

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
    @Suppress("UNUSED_PARAMETER") onHome: () -> Unit,
    onOpenRecipients: () -> Unit = {},
    onCancelRecipients: () -> Unit = {},
    onRecipientInputChange: (String) -> Unit = onManualRecipient,
) {
    val rootModifier = Modifier.testTag("touch-messaging")

    BackHandler(
        enabled = state.route in setOf(
            MessagingRoute.COMPOSE,
            MessagingRoute.RECIPIENTS,
            MessagingRoute.CONFIRMATION,
            MessagingRoute.FAILED,
        ),
    ) {
        when (state.route) {
            MessagingRoute.RECIPIENTS -> onCancelRecipients()
            MessagingRoute.CONFIRMATION -> onEdit()
            else -> onCancelFailure()
        }
    }

    when (state.route) {
        MessagingRoute.INBOX -> InboxRoute(
            state = state,
            modifier = rootModifier,
            onSearchInbox = onSearchInbox,
            onOpenConversation = onOpenConversation,
            onNewMessage = onNewMessage,
            onRepair = onRepair,
        )

        MessagingRoute.COMPOSE -> ComposeRoute(
            state = state,
            modifier = rootModifier,
            onRecipientInputChange = onRecipientInputChange,
            onOpenRecipients = onOpenRecipients,
            onBodyChange = onBodyChange,
            onReview = onReview,
            onRepair = onRepair,
        )

        MessagingRoute.RECIPIENTS -> RecipientsRoute(
            state = state,
            modifier = rootModifier,
            onSearchRecipients = onSearchRecipients,
            onLoadMoreRecipients = onLoadMoreRecipients,
            onChooseContactNumber = onChooseContactNumber,
            onManualRecipient = onManualRecipient,
        )

        MessagingRoute.CONVERSATION -> ConversationRoute(
            state = state,
            modifier = rootModifier,
            onBodyChange = onBodyChange,
            onReview = onReview,
            onRepair = onRepair,
        )

        MessagingRoute.CONFIRMATION -> ConfirmationRoute(
            state = state,
            modifier = rootModifier,
            onSend = onSend,
            onEdit = onEdit,
        )

        MessagingRoute.SENDING -> SendingRoute(
            state = state,
            modifier = rootModifier,
        )

        MessagingRoute.FAILED -> FailedRoute(
            state = state,
            modifier = rootModifier,
            onRetry = onRetry,
            onConfirmDuplicateRisk = onConfirmDuplicateRisk,
            onCancel = onCancelFailure,
            onRepair = onRepair,
        )
    }
}

@Composable
private fun InboxRoute(
    state: MessagingUiState,
    onSearchInbox: (String) -> Unit,
    onOpenConversation: (Long) -> Unit,
    onNewMessage: () -> Unit,
    onRepair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClaroScreenScaffold(
        title = stringResource(R.string.touch_tile_messages),
        modifier = modifier,
        scrollable = false,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.touch_write_message),
                onClick = onNewMessage,
                containerColor = ClaroColors.Blue,
            )
        },
    ) {
        MessageFieldLabel(stringResource(R.string.touch_search_name_or_number))
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchInbox,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
            singleLine = true,
            textStyle = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, color = ClaroColors.Ink),
            shape = RoundedCornerShape(2.dp),
            colors = messagingFieldColors(),
        )
        if (!state.qualification.canRead) {
            ClaroInfoCard(
                text = if (!state.qualification.telephonySupported) {
                    stringResource(R.string.touch_messages_unavailable_device)
                } else {
                    stringResource(R.string.touch_message_access_repair)
                },
                tone = ClaroBannerTone.WARNING,
            )
            if (state.qualification.telephonySupported) {
                ClaroPrimaryButton(
                    label = stringResource(R.string.touch_repair_message_access),
                    onClick = onRepair,
                    containerColor = ClaroColors.Blue,
                )
            }
        }
        state.statusMessage?.let { status ->
            ClaroInfoCard(text = localizedMessagingStatus(status, state.sendFailure))
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (state.conversations.isEmpty()) {
                item(key = "inbox-empty") {
                    ClaroInfoCard(
                        text = if (state.searchQuery.isBlank()) {
                            stringResource(R.string.touch_no_messages_yet)
                        } else {
                            stringResource(R.string.touch_no_matching_messages)
                        },
                    )
                }
            }
            itemsIndexed(state.conversations, key = { _, item -> item.id }) { index, conversation ->
                val snippetText = when {
                    conversation.kind == MessageKind.MMS && conversation.groupSize > 1 ->
                        stringResource(
                            R.string.touch_mms_group_snippet,
                            conversation.groupSize,
                            conversation.snippet,
                        )
                    conversation.kind == MessageKind.MMS ->
                        stringResource(R.string.touch_mms_snippet, conversation.snippet)
                    else -> conversation.snippet
                }
                val titleText = conversation.recipient.accessibleName
                    .ifBlank { stringResource(R.string.touch_unknown_number) }
                ClaroRowButton(
                    title = titleText,
                    subtitle = snippetText,
                    unreadDot = conversation.unreadCount > 0,
                    leading = {
                        ClaroAvatar(
                            name = conversation.recipient.displayName,
                            size = 48.dp,
                            paletteSeed = index,
                            isUnassigned = conversation.recipient.displayName.isNullOrBlank(),
                        )
                    },
                    onClick = { onOpenConversation(conversation.id) },
                )
            }
        }
    }
}

@Composable
private fun ComposeRoute(
    state: MessagingUiState,
    onRecipientInputChange: (String) -> Unit,
    onOpenRecipients: () -> Unit,
    onBodyChange: (String) -> Unit,
    onReview: () -> Unit,
    onRepair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClaroScreenScaffold(
        title = stringResource(R.string.touch_write_message),
        modifier = modifier,
        scrollable = true,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.touch_action_review),
                onClick = onReview,
                containerColor = ClaroColors.Blue,
            )
        },
    ) {
        state.statusMessage?.takeIf { it.isNotBlank() }?.let { status ->
            ClaroInfoCard(
                text = localizedMessagingStatus(status, state.sendFailure),
                tone = if (state.sendFailure != null) ClaroBannerTone.ERROR else ClaroBannerTone.INFO,
            )
        }
        MessageFieldLabel(stringResource(R.string.touch_field_to))
        state.draft.recipient?.displayName?.takeIf { it.isNotBlank() }?.let { recipientName ->
            Text(
                text = recipientName,
                color = ClaroColors.Muted,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 3.dp),
            )
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = state.draft.recipient?.number ?: state.recipientQuery,
                onValueChange = onRecipientInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                textStyle = TextStyle(fontSize = 25.sp, lineHeight = 31.sp, color = ClaroColors.Ink),
                shape = RoundedCornerShape(2.dp),
                colors = messagingFieldColors(),
            )
        }
        ClaroSecondaryButton(
            label = stringResource(R.string.touch_contacts),
            onClick = onOpenRecipients,
            icon = ClaroIconKind.CONTACT,
        )
        MessageFieldLabel(stringResource(R.string.touch_action_message))
        OutlinedTextField(
            value = state.draft.body,
            onValueChange = onBodyChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 168.dp),
            placeholder = {
                Text(
                    text = stringResource(R.string.touch_message_placeholder),
                    color = ClaroColors.Muted,
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                )
            },
            textStyle = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, color = ClaroColors.Ink),
            shape = RoundedCornerShape(2.dp),
            colors = messagingFieldColors(),
        )
        Text(
            text = smsLengthHelper(state),
            color = ClaroColors.Muted,
            fontSize = 17.sp,
            lineHeight = 23.sp,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        if (!state.qualification.canSend) {
            ClaroInfoCard(
                text = if (!state.qualification.telephonySupported) {
                    stringResource(R.string.touch_sms_sending_unavailable_device)
                } else {
                    stringResource(R.string.touch_sending_messages_repair)
                },
                tone = ClaroBannerTone.WARNING,
            )
            if (state.qualification.telephonySupported) {
                ClaroSecondaryButton(
                    label = stringResource(R.string.touch_repair_message_access),
                    onClick = onRepair,
                )
            }
        }
    }
}

@Composable
private fun RecipientsRoute(
    state: MessagingUiState,
    onSearchRecipients: (String) -> Unit,
    onLoadMoreRecipients: () -> Unit,
    onChooseContactNumber: (ClaroContact, ContactNumber) -> Unit,
    onManualRecipient: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ClaroScreenScaffold(
        title = stringResource(R.string.touch_choose_recipient_title),
        modifier = modifier,
        scrollable = false,
    ) {
        MessageFieldLabel(stringResource(R.string.touch_search_name_or_number))
        OutlinedTextField(
            value = state.recipientQuery,
            onValueChange = onSearchRecipients,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
            singleLine = true,
            textStyle = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, color = ClaroColors.Ink),
            shape = RoundedCornerShape(2.dp),
            colors = messagingFieldColors(),
        )
        ClaroPrimaryButton(
            label = stringResource(R.string.touch_use_typed_number),
            onClick = { onManualRecipient(state.recipientQuery) },
            enabled = state.recipientQuery.isNotBlank(),
            containerColor = ClaroColors.Blue,
        )
        state.statusMessage?.let { status ->
            ClaroInfoCard(
                text = localizedMessagingStatus(status, state.sendFailure),
                tone = ClaroBannerTone.WARNING,
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            state.contacts.forEachIndexed { contactIndex, contact ->
                items(
                    items = contact.numbers,
                    key = { "${contact.lookupKey}:${it.value}" },
                ) { number ->
                    ClaroRowButton(
                        title = contact.displayName,
                        subtitle = stringResource(
                            R.string.touch_contact_number_subtitle,
                            number.localizedLabel(),
                            number.value,
                        ),
                        leading = {
                            ClaroAvatar(
                                name = contact.displayName,
                                size = 48.dp,
                                paletteSeed = contactIndex,
                            )
                        },
                        onClick = { onChooseContactNumber(contact, number) },
                    )
                }
            }
            if (state.contacts.isEmpty() && !state.contactsLoading) {
                item(key = "recipients-empty") {
                    ClaroInfoCard(
                        text = if (state.recipientQuery.isBlank()) {
                            stringResource(R.string.touch_no_contacts_yet)
                        } else {
                            stringResource(R.string.touch_no_matches)
                        },
                    )
                }
            }
            if (state.contactsLoading) {
                item(key = "recipients-loading") {
                    ClaroInfoCard(text = stringResource(R.string.touch_loading_contacts))
                }
            }
            if (state.contactsNextOffset != null && !state.contactsLoading) {
                item(key = "recipients-more") {
                    ClaroSecondaryButton(
                        label = stringResource(R.string.touch_load_more_contacts),
                        onClick = onLoadMoreRecipients,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRoute(
    state: MessagingUiState,
    onBodyChange: (String) -> Unit,
    onReview: () -> Unit,
    onRepair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recipient = state.selectedConversation?.recipient ?: state.draft.recipient
    val conversationFallback = stringResource(R.string.touch_conversation_title)
    ClaroScreenScaffold(
        title = recipient?.accessibleName?.ifBlank { conversationFallback } ?: conversationFallback,
        modifier = modifier,
        scrollable = false,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.touch_action_review),
                onClick = onReview,
                containerColor = ClaroColors.Blue,
            )
        },
    ) {
        RecipientSummaryBar(recipient = recipient)
        state.statusMessage?.let { status ->
            ClaroInfoCard(
                text = localizedMessagingStatus(status, state.sendFailure),
                tone = if (state.sendFailure != null) ClaroBannerTone.ERROR else ClaroBannerTone.INFO,
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.messages.isEmpty()) {
                item(key = "conversation-empty") {
                    ClaroInfoCard(text = stringResource(R.string.touch_conversation_empty))
                }
            }
            items(
                items = state.messages,
                key = { message ->
                    message.clientMessageId ?: "${message.kind}:${message.providerId}"
                },
            ) { message ->
                ConversationBubble(message = message)
            }
        }
        MessageFieldLabel(stringResource(R.string.touch_action_message))
        OutlinedTextField(
            value = state.draft.body,
            onValueChange = onBodyChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 104.dp),
            placeholder = {
                Text(
                    text = stringResource(R.string.touch_message_placeholder),
                    color = ClaroColors.Muted,
                    fontSize = 24.sp,
                )
            },
            textStyle = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, color = ClaroColors.Ink),
            shape = RoundedCornerShape(2.dp),
            colors = messagingFieldColors(),
        )
        Text(
            text = smsLengthHelper(state),
            color = ClaroColors.Muted,
            fontSize = 17.sp,
            lineHeight = 22.sp,
        )
        if (!state.qualification.canSend) {
            ClaroInfoCard(
                text = if (!state.qualification.telephonySupported) {
                    stringResource(R.string.touch_sms_sending_unavailable_device)
                } else {
                    stringResource(R.string.touch_sending_messages_repair)
                },
                tone = ClaroBannerTone.WARNING,
            )
            if (state.qualification.telephonySupported) {
                ClaroSecondaryButton(
                    label = stringResource(R.string.touch_repair_message_access),
                    onClick = onRepair,
                )
            }
        }
    }
}

@Composable
private fun ConversationBubble(message: ClaroMessage) {
    val isOutgoing = message.direction == MessageDirection.OUTGOING
    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 10.dp, topEnd = 3.dp, bottomEnd = 3.dp, bottomStart = 10.dp)
    } else {
        RoundedCornerShape(topStart = 3.dp, topEnd = 10.dp, bottomEnd = 10.dp, bottomStart = 3.dp)
    }
    val statusLabel = when (message.deliveryState) {
        MessageDeliveryState.DELIVERY_CONFIRMED ->
            stringResource(R.string.touch_delivery_confirmed)
        MessageDeliveryState.DELIVERY_UNKNOWN ->
            stringResource(R.string.touch_delivery_unknown)
        MessageDeliveryState.KNOWN_FAILURE ->
            stringResource(R.string.touch_delivery_failed)
        MessageDeliveryState.PENDING ->
            stringResource(R.string.touch_delivery_sending)
        MessageDeliveryState.SENT ->
            stringResource(R.string.touch_delivery_sent_unconfirmed)
        MessageDeliveryState.RECEIVED,
        MessageDeliveryState.DRAFT,
        -> null
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .background(
                    color = if (isOutgoing) Color(0xFFE0ECFF) else ClaroColors.Surface,
                    shape = bubbleShape,
                )
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (message.body.isNotBlank()) {
                Text(
                    text = message.body,
                    color = ClaroColors.Ink,
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                )
            }
            if (message.kind == MessageKind.MMS && message.groupParticipants.size > 1) {
                Text(
                    text = stringResource(
                        R.string.touch_mms_group_label,
                        message.groupParticipants.size,
                    ),
                    color = ClaroColors.Muted,
                    fontSize = 17.sp,
                )
            }
            message.attachments.forEach { attachment ->
                val attachmentName = attachment.displayName?.takeIf { it.isNotBlank() }
                val attachmentStatus = attachment.state.accessibleAttachmentMessage()
                val attachmentText = if (attachmentName != null) {
                    stringResource(R.string.touch_attachment_named, attachmentName, attachmentStatus)
                } else {
                    attachmentStatus
                }
                Text(
                    text = attachmentText,
                    color = ClaroColors.Muted,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                )
            }
        }
        statusLabel?.let { status ->
            Text(
                text = status,
                color = Color(0xFF5D6A80),
                fontSize = 16.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ConfirmationRoute(
    state: MessagingUiState,
    onSend: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClaroScreenScaffold(
        title = stringResource(R.string.touch_send_message_confirm_title),
        modifier = modifier,
        scrollable = true,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.touch_action_send),
                onClick = onSend,
                enabled = state.canConfirm,
                containerColor = ClaroColors.Blue,
            )
            ClaroSecondaryButton(
                label = stringResource(R.string.touch_action_edit),
                onClick = onEdit,
            )
        },
    ) {
        RecipientSummaryBar(recipient = state.draft.recipient)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color(0xFFE0ECFF),
                    shape = RoundedCornerShape(
                        topStart = 10.dp,
                        topEnd = 3.dp,
                        bottomEnd = 3.dp,
                        bottomStart = 10.dp,
                    ),
                )
                .padding(12.dp),
        ) {
            Text(
                text = state.draft.body.ifBlank { stringResource(R.string.touch_draft_empty) },
                color = ClaroColors.Ink,
                fontSize = 24.sp,
                lineHeight = 33.sp,
            )
        }
        Text(
            text = smsLengthHelper(state),
            color = ClaroColors.Muted,
            fontSize = 17.sp,
            lineHeight = 23.sp,
        )
        state.statusMessage?.let { status ->
            ClaroInfoCard(text = localizedMessagingStatus(status, state.sendFailure))
        }
    }
}

@Composable
private fun SendingRoute(
    state: MessagingUiState,
    modifier: Modifier = Modifier,
) {
    ClaroScreenScaffold(
        title = stringResource(R.string.touch_delivery_sending),
        modifier = modifier,
        scrollable = true,
    ) {
        RecipientSummaryBar(recipient = state.draft.recipient)
        ClaroInfoCard(
            text = stringResource(R.string.touch_sending_please_wait),
            tone = ClaroBannerTone.WARNING,
        )
        if (state.draft.body.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFFE0ECFF),
                        shape = RoundedCornerShape(
                            topStart = 10.dp,
                            topEnd = 3.dp,
                            bottomEnd = 3.dp,
                            bottomStart = 10.dp,
                        ),
                    )
                    .padding(12.dp),
            ) {
                Text(
                    text = state.draft.body,
                    color = ClaroColors.Ink,
                    fontSize = 24.sp,
                    lineHeight = 33.sp,
                )
            }
        }
    }
}

@Composable
private fun FailedRoute(
    state: MessagingUiState,
    onRetry: () -> Unit,
    onConfirmDuplicateRisk: () -> Unit,
    onCancel: () -> Unit,
    onRepair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val headerTitle = if (state.unknownOutcome) {
        stringResource(R.string.touch_delivery_uncertain_title)
    } else {
        stringResource(R.string.touch_message_not_sent_title)
    }
    val explanation = when {
        state.unknownOutcome && state.duplicateRiskConfirmationRequired ->
            stringResource(R.string.touch_duplicate_risk_warning)
        state.unknownOutcome ->
            stringResource(R.string.touch_message_may_be_sent)
        else ->
            state.statusMessage?.let { localizedMessagingStatus(it, state.sendFailure) }
                ?: stringResource(R.string.touch_message_send_failed_default)
    }

    ClaroScreenScaffold(
        title = headerTitle,
        modifier = modifier,
        scrollable = true,
        dock = {
            ClaroPrimaryButton(
                label = if (state.duplicateRiskConfirmationRequired) {
                    stringResource(R.string.touch_action_send_again)
                } else {
                    stringResource(R.string.touch_action_try_again)
                },
                onClick = if (state.duplicateRiskConfirmationRequired) {
                    onConfirmDuplicateRisk
                } else {
                    onRetry
                },
                containerColor = ClaroColors.Blue,
            )
            ClaroSecondaryButton(
                label = stringResource(R.string.touch_action_cancel),
                onClick = onCancel,
            )
        },
    ) {
        RecipientSummaryBar(recipient = state.draft.recipient)
        ClaroInfoCard(
            text = explanation,
            tone = if (state.unknownOutcome) ClaroBannerTone.WARNING else ClaroBannerTone.ERROR,
        )
        if (state.draft.body.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = ClaroColors.Surface,
                        shape = RoundedCornerShape(
                            topStart = 3.dp,
                            topEnd = 10.dp,
                            bottomEnd = 10.dp,
                            bottomStart = 3.dp,
                        ),
                    )
                    .padding(12.dp),
            ) {
                Text(
                    text = state.draft.body,
                    color = ClaroColors.Ink,
                    fontSize = 24.sp,
                    lineHeight = 33.sp,
                )
            }
        }
        if (!state.qualification.canSend && state.qualification.telephonySupported) {
            ClaroSecondaryButton(
                label = stringResource(R.string.touch_repair_message_access),
                onClick = onRepair,
            )
        }
    }
}

@Composable
private fun RecipientSummaryBar(recipient: MessageRecipient?) {
    val displayName = recipient?.displayName?.takeIf { it.isNotBlank() }
    val number = recipient?.number?.takeIf { it.isNotBlank() }
    val name = displayName ?: number ?: stringResource(R.string.touch_field_to)
    val recipientDescription = if (number != null && number != name) {
        stringResource(R.string.touch_recipient_name_and_number_a11y, name, number)
    } else {
        stringResource(R.string.touch_recipient_name_only_a11y, name)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClaroColors.Surface, RoundedCornerShape(3.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = recipientDescription
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ClaroAvatar(
            name = displayName,
            size = 56.dp,
            isUnassigned = displayName == null,
        )
        Column(modifier = Modifier.weight(1f)) {
            if (displayName == null && number != null) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = number,
                        color = ClaroColors.Ink,
                        fontSize = 28.sp,
                        lineHeight = 33.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Text(
                    text = name,
                    color = ClaroColors.Ink,
                    fontSize = 28.sp,
                    lineHeight = 33.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (number != null && number != name) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = number,
                            color = ClaroColors.Muted,
                            fontSize = 19.sp,
                            lineHeight = 23.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageFieldLabel(label: String) {
    Text(
        text = label,
        modifier = Modifier.padding(horizontal = 3.dp, vertical = 3.dp),
        color = ClaroColors.Ink,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun messagingFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ClaroColors.Blue,
    unfocusedBorderColor = ClaroColors.FieldBorder,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedTextColor = ClaroColors.Ink,
    unfocusedTextColor = ClaroColors.Ink,
    cursorColor = ClaroColors.Blue,
)

@Composable
private fun smsLengthHelper(state: MessagingUiState): String =
    if (state.encoding.encoding == SmsEncoding.UNICODE) {
        stringResource(
            R.string.touch_sms_length_unicode,
            state.draft.body.length,
            state.encoding.segments,
        )
    } else {
        stringResource(
            R.string.touch_sms_length_standard,
            state.draft.body.length,
            state.encoding.segments,
        )
    }

@Composable
private fun AttachmentState.accessibleAttachmentMessage(): String = when (this) {
    AttachmentState.NONE -> stringResource(R.string.touch_attachment_none)
    AttachmentState.AVAILABLE -> stringResource(R.string.touch_attachment_available)
    AttachmentState.DOWNLOAD_REQUIRED -> stringResource(R.string.touch_attachment_download_required)
    AttachmentState.DOWNLOAD_FAILED -> stringResource(R.string.touch_attachment_download_failed)
    AttachmentState.UNSUPPORTED -> stringResource(R.string.touch_attachment_unsupported)
}

@Composable
private fun localizedMessagingStatus(message: String, failure: MessageFailure?): String {
    when (message) {
        "Messages access needs repair",
        "Message access needs repair.",
        -> return stringResource(R.string.touch_message_access_repair)
        "Enter a valid phone number" ->
            return stringResource(R.string.touch_phone_err_valid_number)
        "Sending message" ->
            return stringResource(R.string.touch_delivery_sending)
        "Choose a default SMS account in Android. Your draft is saved." ->
            return stringResource(R.string.touch_msg_err_account_saved_draft)
        "This message may already have been sent. Send it again?" ->
            return stringResource(R.string.touch_msg_status_duplicate_prompt)
        "Sent. Delivery is not confirmed." ->
            return stringResource(R.string.touch_msg_status_sent_unconfirmed)
        "Delivery confirmed by the carrier" ->
            return stringResource(R.string.touch_msg_status_delivery_confirmed)
        "The carrier did not confirm the result. It may already have been sent." ->
            return stringResource(R.string.touch_msg_status_carrier_unconfirmed)
    }
    return when (failure) {
        MessageFailure.INVALID_RECIPIENT -> stringResource(R.string.touch_msg_err_invalid_recipient)
        MessageFailure.EMPTY_CONTENT -> stringResource(R.string.touch_msg_err_empty_content)
        MessageFailure.ROLE_LOST -> stringResource(R.string.touch_msg_err_role_lost)
        MessageFailure.PERMISSION_DENIED -> stringResource(R.string.touch_msg_err_permission_denied)
        MessageFailure.NO_SIM -> stringResource(R.string.touch_msg_err_no_sim)
        MessageFailure.NO_SERVICE -> stringResource(R.string.touch_msg_err_no_service)
        MessageFailure.ACCOUNT_UNAVAILABLE ->
            stringResource(R.string.touch_msg_err_account_unavailable)
        MessageFailure.CARRIER_REJECTED -> stringResource(R.string.touch_msg_err_carrier_rejected)
        MessageFailure.UNKNOWN -> stringResource(R.string.touch_msg_err_unknown)
        null -> message
    }
}
