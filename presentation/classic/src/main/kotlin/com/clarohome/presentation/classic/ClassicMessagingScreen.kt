package com.clarohome.presentation.classic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarohome.core.design.ClaroAvatar
import com.clarohome.core.design.ClaroBannerTone
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroIconKind
import com.clarohome.core.design.ClaroInfoCard
import com.clarohome.core.model.BatteryStatus
import com.clarohome.core.model.CellularStatus
import com.clarohome.feature.communication.AttachmentState
import com.clarohome.feature.communication.ClaroContact
import com.clarohome.feature.communication.ContactNumber
import com.clarohome.feature.communication.MessageDeliveryState
import com.clarohome.feature.communication.MessageDirection
import com.clarohome.feature.communication.MessageFailure
import com.clarohome.feature.communication.MessageKind
import com.clarohome.feature.communication.MessageRecipient
import com.clarohome.feature.communication.MessagingRoute
import com.clarohome.feature.communication.MessagingUiState
import com.clarohome.feature.communication.SmsEncoding
import com.clarohome.presentation.classic.R

private enum class ClassicComposeTarget {
    RECIPIENT,
    BODY,
}

private val IncomingBubbleColor = Color(0xFFEDF0F5)
private val OutgoingBubbleColor = Color(0xFFE0ECFF)

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
    battery: BatteryStatus? = null,
    cellular: CellularStatus? = null,
    onOpenRecipients: () -> Unit = {},
    onCancelRecipients: () -> Unit = onEdit,
    onRecipientInputChange: (String) -> Unit = onManualRecipient,
) {
    val displayScrollState = rememberScrollState()
    var optionsOpen by rememberSaveable(state.route) { mutableStateOf(false) }
    var numericMode by rememberSaveable(state.route) { mutableStateOf(false) }
    var multiTapState by remember(state.route) { mutableStateOf<ClassicMultiTapState?>(null) }
    var composeTarget by rememberSaveable(state.route, state.draft.id) {
        mutableStateOf(ClassicComposeTarget.BODY)
    }

    val recipientFieldValue = state.recipientQuery.ifEmpty {
        state.draft.recipient?.number.orEmpty()
    }

    val isEditingRoute = !optionsOpen && state.route in setOf(
        MessagingRoute.INBOX,
        MessagingRoute.RECIPIENTS,
        MessagingRoute.COMPOSE,
        MessagingRoute.CONVERSATION,
    )

    val activeModeLabel: String? = when {
        !isEditingRoute -> null
        state.route == MessagingRoute.COMPOSE &&
            composeTarget == ClassicComposeTarget.RECIPIENT ->
            stringResource(R.string.classic_mode_numbers)
        numericMode -> stringResource(R.string.classic_mode_numbers)
        else -> stringResource(R.string.classic_mode_letters)
    }

    val options = buildClassicMessagingOptions(
        state = state,
        composeTarget = composeTarget,
        recipientFieldValue = recipientFieldValue,
        onNewMessage = onNewMessage,
        onOpenRecipients = onOpenRecipients,
        onSearchInbox = {
            multiTapState = null
            onSearchInbox(it)
        },
        onSearchRecipients = {
            multiTapState = null
            onSearchRecipients(it)
        },
        onRecipientInputChange = {
            multiTapState = null
            onRecipientInputChange(it)
        },
        onBodyChange = {
            multiTapState = null
            onBodyChange(it)
        },
        onEdit = onEdit,
        onRepair = onRepair,
        onHome = onHome,
    )

    val backAction: () -> Unit = {
        val editingRecipientPhone = state.route == MessagingRoute.COMPOSE &&
            composeTarget == ClassicComposeTarget.RECIPIENT
        when {
            optionsOpen -> optionsOpen = false
            state.route == MessagingRoute.INBOX && state.searchQuery.isNotEmpty() -> {
                multiTapState = null
                onSearchInbox(state.searchQuery.dropLast(1))
            }
            state.route == MessagingRoute.RECIPIENTS && state.recipientQuery.isNotEmpty() -> {
                multiTapState = null
                onSearchRecipients(state.recipientQuery.dropLast(1))
            }
            editingRecipientPhone && recipientFieldValue.isNotEmpty() -> {
                multiTapState = null
                onRecipientInputChange(recipientFieldValue.dropLast(1))
            }
            (state.route == MessagingRoute.COMPOSE || state.route == MessagingRoute.CONVERSATION) &&
                !editingRecipientPhone && state.draft.body.isNotEmpty() -> {
                multiTapState = null
                onBodyChange(state.draft.body.dropLast(1))
            }
            state.route == MessagingRoute.CONFIRMATION -> onEdit()
            state.route == MessagingRoute.RECIPIENTS -> onCancelRecipients()
            state.route == MessagingRoute.COMPOSE -> onCancelFailure()
            state.route == MessagingRoute.FAILED -> onCancelFailure()
            state.route == MessagingRoute.SENDING -> onCancelFailure()
            else -> onHome()
        }
    }

    val greenAction: (() -> Unit)? = if (optionsOpen) {
        null
    } else {
        when (state.route) {
            MessagingRoute.INBOX -> onNewMessage
            MessagingRoute.RECIPIENTS -> state.recipientQuery
                .takeIf { it.isNotBlank() }
                ?.let { query -> { onManualRecipient(query) } }
            MessagingRoute.COMPOSE,
            MessagingRoute.CONVERSATION,
            -> onReview
            MessagingRoute.CONFIRMATION -> onSend
            MessagingRoute.FAILED -> if (state.duplicateRiskConfirmationRequired) {
                onConfirmDuplicateRisk
            } else {
                onRetry
            }
            MessagingRoute.SENDING -> null
        }
    }

    val keypadAction: ((Char) -> Unit)? = if (!isEditingRoute) {
        null
    } else {
        { digit ->
            val editingRecipientPhone = state.route == MessagingRoute.COMPOSE &&
                composeTarget == ClassicComposeTarget.RECIPIENT
            if (digit == '#' && !editingRecipientPhone) {
                numericMode = !numericMode
                multiTapState = null
            } else {
                when (state.route) {
                    MessagingRoute.INBOX -> {
                        val (updated, nextTap) = applyClassicKeypadInput(
                            currentValue = state.searchQuery,
                            digit = digit,
                            fieldKey = "inbox-search",
                            numericMode = numericMode,
                            previousTap = multiTapState,
                        )
                        multiTapState = nextTap
                        onSearchInbox(updated)
                    }

                    MessagingRoute.RECIPIENTS -> {
                        val (updated, nextTap) = applyClassicKeypadInput(
                            currentValue = state.recipientQuery,
                            digit = digit,
                            fieldKey = "recipient-search",
                            numericMode = numericMode,
                            previousTap = multiTapState,
                        )
                        multiTapState = nextTap
                        onSearchRecipients(updated)
                    }

                    MessagingRoute.COMPOSE -> {
                        if (editingRecipientPhone) {
                            val (updated, _) = applyClassicKeypadInput(
                                currentValue = recipientFieldValue,
                                digit = digit,
                                fieldKey = "compose-recipient",
                                numericMode = true,
                                phoneNumberField = true,
                                previousTap = null,
                            )
                            multiTapState = null
                            onRecipientInputChange(updated)
                        } else {
                            val (updated, nextTap) = applyClassicKeypadInput(
                                currentValue = state.draft.body,
                                digit = digit,
                                fieldKey = "compose-body",
                                numericMode = numericMode,
                                previousTap = multiTapState,
                            )
                            multiTapState = nextTap
                            onBodyChange(updated)
                        }
                    }

                    MessagingRoute.CONVERSATION -> {
                        val (updated, nextTap) = applyClassicKeypadInput(
                            currentValue = state.draft.body,
                            digit = digit,
                            fieldKey = "conversation-reply",
                            numericMode = numericMode,
                            previousTap = multiTapState,
                        )
                        multiTapState = nextTap
                        onBodyChange(updated)
                    }

                    else -> Unit
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("classic-messaging")
            .background(ClassicFrame)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ClassicDisplay),
        ) {
            ClassicSystemStatusBar(battery = battery, cellular = cellular)
            ClassicHeader(
                title = if (optionsOpen) {
                    stringResource(R.string.classic_options)
                } else {
                    classicMessagingTitle(state)
                },
                centered = !optionsOpen && state.route in setOf(
                    MessagingRoute.CONFIRMATION,
                    MessagingRoute.SENDING,
                    MessagingRoute.FAILED,
                ),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(displayScrollState),
            ) {
                if (optionsOpen) {
                    ClassicOptionsOverlay(
                        options = options,
                        onClose = { optionsOpen = false },
                    )
                } else {
                    ClassicMessageDisplay(
                        state = state,
                        recipientFieldValue = recipientFieldValue,
                        onComposeTargetChange = { target ->
                            if (composeTarget != target) {
                                composeTarget = target
                                multiTapState = null
                            }
                        },
                        onSearchInbox = {
                            multiTapState = null
                            onSearchInbox(it)
                        },
                        onOpenConversation = onOpenConversation,
                        onNewMessage = onNewMessage,
                        onOpenRecipients = onOpenRecipients,
                        onSearchRecipients = {
                            multiTapState = null
                            onSearchRecipients(it)
                        },
                        onManualRecipient = onManualRecipient,
                        onRecipientInputChange = {
                            multiTapState = null
                            onRecipientInputChange(it)
                        },
                        onChooseContactNumber = onChooseContactNumber,
                        onBodyChange = {
                            multiTapState = null
                            onBodyChange(it)
                        },
                        onReview = onReview,
                        onEdit = onEdit,
                        onSend = onSend,
                        onRetry = onRetry,
                        onConfirmDuplicateRisk = onConfirmDuplicateRisk,
                        onCancelFailure = onCancelFailure,
                        onRepair = onRepair,
                    )
                }
            }
        }
        ClassicIntegratedPad(
            menuLabel = stringResource(R.string.classic_options),
            greenLabel = when (state.route) {
                MessagingRoute.INBOX -> "＋ ${stringResource(R.string.classic_deck_new)}"
                MessagingRoute.RECIPIENTS -> "✓ ${stringResource(R.string.classic_deck_choose)}"
                MessagingRoute.COMPOSE,
                MessagingRoute.CONVERSATION,
                -> "✓ ${stringResource(R.string.classic_review)}"
                MessagingRoute.CONFIRMATION -> "✉ ${stringResource(R.string.classic_send)}"
                MessagingRoute.FAILED -> if (state.duplicateRiskConfirmationRequired) {
                    "✉ ${stringResource(R.string.classic_send)}"
                } else {
                    "↻ ${stringResource(R.string.classic_deck_retry)}"
                }
                MessagingRoute.SENDING -> stringResource(R.string.classic_sending_ellipsis)
            },
            redLabel = "⌂ ${stringResource(R.string.classic_home)}",
            onBack = backAction,
            onMenu = if (options.isNotEmpty() || optionsOpen) {
                { optionsOpen = !optionsOpen }
            } else {
                null
            },
            onGreen = greenAction,
            onRed = {
                optionsOpen = false
                onHome()
            },
            scrollState = displayScrollState,
        )
        ClassicModeStatusStrip(modeLabel = activeModeLabel)
        ClassicNumericKeypad(onDigitSelected = keypadAction)
    }
}

@Composable
private fun buildClassicMessagingOptions(
    state: MessagingUiState,
    composeTarget: ClassicComposeTarget,
    recipientFieldValue: String,
    onNewMessage: () -> Unit,
    onOpenRecipients: () -> Unit,
    onSearchInbox: (String) -> Unit,
    onSearchRecipients: (String) -> Unit,
    onRecipientInputChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onEdit: () -> Unit,
    onRepair: () -> Unit,
    onHome: () -> Unit,
): List<ClassicOptionAction> {
    val newMessageLabel = stringResource(R.string.classic_new_message)
    val contactsLabel = stringResource(R.string.classic_contacts)
    val clearTextLabel = stringResource(R.string.classic_clear_text)
    val repairMessagesLabel = stringResource(R.string.classic_repair_messages_access)
    val editLabel = stringResource(R.string.classic_edit)

    return buildList {
        when (state.route) {
            MessagingRoute.INBOX -> {
                add(ClassicOptionAction(label = newMessageLabel, onSelect = onNewMessage))
                add(ClassicOptionAction(label = contactsLabel, onSelect = onOpenRecipients))
                add(
                    ClassicOptionAction(
                        label = clearTextLabel,
                        enabled = state.searchQuery.isNotEmpty(),
                        onSelect = { onSearchInbox("") },
                    ),
                )
                if (!state.qualification.canRead && state.qualification.telephonySupported) {
                    add(ClassicOptionAction(label = repairMessagesLabel, onSelect = onRepair))
                }
            }

            MessagingRoute.CONVERSATION -> {
                add(
                    ClassicOptionAction(
                        label = clearTextLabel,
                        enabled = state.draft.body.isNotEmpty(),
                        onSelect = { onBodyChange("") },
                    ),
                )
                add(ClassicOptionAction(label = newMessageLabel, onSelect = onNewMessage))
                add(ClassicOptionAction(label = contactsLabel, onSelect = onOpenRecipients))
            }

            MessagingRoute.RECIPIENTS -> {
                add(
                    ClassicOptionAction(
                        label = clearTextLabel,
                        enabled = state.recipientQuery.isNotEmpty(),
                        onSelect = { onSearchRecipients("") },
                    ),
                )
                add(ClassicOptionAction(label = newMessageLabel, onSelect = onNewMessage))
            }

            MessagingRoute.COMPOSE -> {
                val editingRecipient = composeTarget == ClassicComposeTarget.RECIPIENT
                val activeText = if (editingRecipient) recipientFieldValue else state.draft.body
                add(ClassicOptionAction(label = contactsLabel, onSelect = onOpenRecipients))
                add(
                    ClassicOptionAction(
                        label = clearTextLabel,
                        enabled = activeText.isNotEmpty(),
                        onSelect = {
                            if (editingRecipient) {
                                onRecipientInputChange("")
                            } else {
                                onBodyChange("")
                            }
                        },
                    ),
                )
                add(ClassicOptionAction(label = newMessageLabel, onSelect = onNewMessage))
            }

            MessagingRoute.CONFIRMATION -> {
                add(ClassicOptionAction(label = editLabel, onSelect = onEdit))
                add(ClassicOptionAction(label = contactsLabel, onSelect = onOpenRecipients))
                add(ClassicOptionAction(label = newMessageLabel, onSelect = onNewMessage))
            }

            MessagingRoute.FAILED -> {
                add(ClassicOptionAction(label = editLabel, onSelect = onEdit))
                add(ClassicOptionAction(label = newMessageLabel, onSelect = onNewMessage))
                add(ClassicOptionAction(label = contactsLabel, onSelect = onOpenRecipients))
            }

            MessagingRoute.SENDING -> {
            }
        }
    }
}

@Composable
private fun ClassicMessageDisplay(
    state: MessagingUiState,
    recipientFieldValue: String,
    onComposeTargetChange: (ClassicComposeTarget) -> Unit,
    onSearchInbox: (String) -> Unit,
    onOpenConversation: (Long) -> Unit,
    onNewMessage: () -> Unit,
    onOpenRecipients: () -> Unit,
    onSearchRecipients: (String) -> Unit,
    onManualRecipient: (String) -> Unit,
    onRecipientInputChange: (String) -> Unit,
    onChooseContactNumber: (ClaroContact, ContactNumber) -> Unit,
    onBodyChange: (String) -> Unit,
    onReview: () -> Unit,
    onEdit: () -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onConfirmDuplicateRisk: () -> Unit,
    onCancelFailure: () -> Unit,
    onRepair: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        when (state.route) {
            MessagingRoute.INBOX -> {
                val searchLabel = stringResource(R.string.classic_search_name_or_number)
                Text(
                    text = searchLabel,
                    color = ClassicInk,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchInbox,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = searchLabel },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 23.sp),
                    shape = RectangleShape,
                )
                if (!state.qualification.canRead) {
                    ClaroInfoCard(
                        text = if (!state.qualification.telephonySupported) {
                            stringResource(R.string.classic_messages_unavailable)
                        } else {
                            stringResource(R.string.classic_messages_access_repair)
                        },
                        tone = ClaroBannerTone.WARNING,
                    )
                    if (state.qualification.telephonySupported) {
                        ClassicLightButton(
                            label = stringResource(R.string.classic_repair_messages_access),
                            onClick = onRepair,
                            color = ClaroColors.Blue,
                            textColor = Color.White,
                            borderColor = ClaroColors.Blue,
                        )
                    }
                }
                if (state.conversations.isEmpty()) {
                    ClaroInfoCard(
                        text = if (state.searchQuery.isBlank()) {
                            stringResource(R.string.classic_no_messages_yet)
                        } else {
                            stringResource(R.string.classic_no_matching_messages)
                        },
                        tone = ClaroBannerTone.INFO,
                    )
                }
                state.conversations.forEachIndexed { index, conversation ->
                    val hasUnread = conversation.unreadCount > 0
                    val title = if (conversation.unreadCount > 1) {
                        stringResource(
                            R.string.classic_conversation_unread_title,
                            conversation.recipient.accessibleName,
                            conversation.unreadCount,
                        )
                    } else {
                        conversation.recipient.accessibleName
                    }
                    val subtitle = if (conversation.kind == MessageKind.MMS) {
                        stringResource(R.string.classic_mms_snippet, conversation.snippet)
                    } else {
                        conversation.snippet
                    }
                    ClassicRowItem(
                        title = title,
                        subtitle = subtitle,
                        unreadDot = hasUnread,
                        onClick = { onOpenConversation(conversation.id) },
                        leading = {
                            ClaroAvatar(
                                name = conversation.recipient.displayName,
                                size = 44.dp,
                                paletteSeed = index,
                                isUnassigned = conversation.recipient.displayName.isNullOrBlank(),
                            )
                        },
                    )
                }
                ClassicLightButton(
                    label = stringResource(R.string.classic_write_message),
                    onClick = onNewMessage,
                    color = ClaroColors.Blue,
                    textColor = Color.White,
                    borderColor = ClaroColors.Blue,
                    leadingIcon = ClaroIconKind.MESSAGES,
                )
            }

            MessagingRoute.CONVERSATION -> {
                MessageRecipientHeader(recipient = state.selectedConversation?.recipient)
                state.messages.forEach { message ->
                    val incoming = message.direction == MessageDirection.INCOMING
                    val deliveryNote = when (message.deliveryState) {
                        MessageDeliveryState.DELIVERY_CONFIRMED ->
                            stringResource(R.string.classic_delivery_confirmed)
                        MessageDeliveryState.DELIVERY_UNKNOWN ->
                            stringResource(R.string.classic_delivery_unknown)
                        MessageDeliveryState.KNOWN_FAILURE ->
                            stringResource(R.string.classic_delivery_not_sent)
                        MessageDeliveryState.PENDING ->
                            stringResource(R.string.classic_sending_ellipsis)
                        MessageDeliveryState.SENT ->
                            stringResource(R.string.classic_delivery_sent_unconfirmed)
                        MessageDeliveryState.RECEIVED,
                        MessageDeliveryState.DRAFT,
                        -> null
                    }
                    Column(
                        modifier = Modifier
                            .align(if (incoming) Alignment.Start else Alignment.End)
                            .fillMaxWidth(0.94f)
                            .background(
                                color = if (incoming) IncomingBubbleColor else OutgoingBubbleColor,
                                shape = if (incoming) {
                                    RoundedCornerShape(
                                        topStart = 3.dp,
                                        topEnd = 10.dp,
                                        bottomEnd = 10.dp,
                                        bottomStart = 3.dp,
                                    )
                                } else {
                                    RoundedCornerShape(
                                        topStart = 10.dp,
                                        topEnd = 3.dp,
                                        bottomEnd = 3.dp,
                                        bottomStart = 10.dp,
                                    )
                                },
                            )
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = message.body,
                            color = ClassicInk,
                            fontSize = 22.sp,
                            lineHeight = 29.sp,
                        )
                        deliveryNote?.let { note ->
                            Text(
                                text = note,
                                color = Color(0xFF5D6A80),
                                fontSize = 15.sp,
                                lineHeight = 19.sp,
                            )
                        }
                        message.attachments.forEach { attachment ->
                            val attachmentText = attachment.state.classicAttachmentLabel()
                            if (attachmentText.isNotBlank()) {
                                Text(
                                    text = attachmentText,
                                    color = Color(0xFF4B5565),
                                    fontSize = 16.sp,
                                    lineHeight = 20.sp,
                                )
                            }
                        }
                    }
                }
                val replyLabel = stringResource(R.string.classic_reply)
                Text(
                    text = replyLabel,
                    color = ClassicInk,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
                OutlinedTextField(
                    value = state.draft.body,
                    onValueChange = onBodyChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 84.dp)
                        .semantics { contentDescription = replyLabel },
                    placeholder = {
                        Text(
                            stringResource(R.string.classic_write_reply_placeholder),
                            fontSize = 21.sp,
                        )
                    },
                    textStyle = TextStyle(fontSize = 22.sp, lineHeight = 28.sp),
                    shape = RectangleShape,
                )
                SmsCounterLine(state)
                if (!state.qualification.canSend && state.qualification.telephonySupported) {
                    ClassicLightButton(
                        label = stringResource(R.string.classic_repair_messages_access),
                        onClick = onRepair,
                        color = ClaroColors.WarningBg,
                        textColor = ClaroColors.WarningInk,
                        borderColor = ClaroColors.WarningBorder,
                    )
                }
            }

            MessagingRoute.RECIPIENTS -> {
                val toLabel = stringResource(R.string.classic_to_label)
                Text(
                    text = toLabel,
                    color = ClassicInk,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
                OutlinedTextField(
                    value = state.recipientQuery,
                    onValueChange = onSearchRecipients,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = toLabel },
                    placeholder = {
                        Text(
                            stringResource(R.string.classic_name_or_number_placeholder),
                            fontSize = 21.sp,
                        )
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 23.sp),
                    shape = RectangleShape,
                )
                if (state.recipientQuery.isNotBlank()) {
                    ClassicLightButton(
                        label = stringResource(R.string.classic_use_typed_number),
                        onClick = { onManualRecipient(state.recipientQuery) },
                        color = ClaroColors.Blue,
                        textColor = Color.White,
                        borderColor = ClaroColors.Blue,
                    )
                }
                if (state.contactsLoading) {
                    ClaroInfoCard(
                        text = stringResource(R.string.classic_loading_contacts),
                        tone = ClaroBannerTone.INFO,
                    )
                } else if (state.contacts.isEmpty()) {
                    ClaroInfoCard(
                        text = stringResource(R.string.classic_no_contacts_found),
                        tone = ClaroBannerTone.INFO,
                    )
                }
                state.contacts.forEachIndexed { index, contact ->
                    contact.numbers.forEach { number ->
                        ClassicRowItem(
                            title = contact.displayName,
                            subtitle = if (number.label.isBlank()) {
                                number.value
                            } else {
                                stringResource(
                                    R.string.classic_number_with_label,
                                    number.value,
                                    number.label,
                                )
                            },
                            onClick = { onChooseContactNumber(contact, number) },
                            leading = {
                                ClaroAvatar(
                                    name = contact.displayName,
                                    size = 44.dp,
                                    paletteSeed = index,
                                )
                            },
                        )
                    }
                }
            }

            MessagingRoute.COMPOSE -> {
                if (state.draft.recipient?.displayName != null) {
                    MessageRecipientHeader(recipient = state.draft.recipient)
                }
                val toLabel = stringResource(R.string.classic_to_label)
                val phonePlaceholder = stringResource(R.string.classic_phone_number_placeholder)
                Text(
                    text = toLabel,
                    color = ClassicInk,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = recipientFieldValue,
                        onValueChange = onRecipientInputChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = toLabel }
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    onComposeTargetChange(ClassicComposeTarget.RECIPIENT)
                                }
                            },
                        placeholder = { Text(phonePlaceholder, fontSize = 21.sp) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 23.sp),
                        shape = RectangleShape,
                    )
                }
                ClassicLightButton(
                    label = stringResource(R.string.classic_contacts),
                    onClick = onOpenRecipients,
                    leadingIcon = ClaroIconKind.CONTACT,
                )
                val messageLabel = stringResource(R.string.classic_message_label)
                Text(
                    text = messageLabel,
                    color = ClassicInk,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
                OutlinedTextField(
                    value = state.draft.body,
                    onValueChange = onBodyChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 112.dp)
                        .semantics { contentDescription = messageLabel }
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                onComposeTargetChange(ClassicComposeTarget.BODY)
                            }
                        },
                    placeholder = {
                        Text(
                            stringResource(R.string.classic_write_here_placeholder),
                            fontSize = 21.sp,
                        )
                    },
                    textStyle = TextStyle(fontSize = 22.sp, lineHeight = 28.sp),
                    shape = RectangleShape,
                )
                SmsCounterLine(state)
                if (!state.qualification.canSend && state.qualification.telephonySupported) {
                    ClassicLightButton(
                        label = stringResource(R.string.classic_repair_messages_access),
                        onClick = onRepair,
                        color = ClaroColors.WarningBg,
                        textColor = ClaroColors.WarningInk,
                        borderColor = ClaroColors.WarningBorder,
                    )
                }
                ClassicLightButton(
                    label = stringResource(R.string.classic_review),
                    onClick = onReview,
                    color = ClaroColors.Blue,
                    textColor = Color.White,
                    borderColor = ClaroColors.Blue,
                )
            }

            MessagingRoute.CONFIRMATION -> {
                MessageRecipientHeader(recipient = state.draft.recipient)
                Text(
                    text = state.draft.body,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OutgoingBubbleColor, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    color = ClassicInk,
                    fontSize = 22.sp,
                    lineHeight = 29.sp,
                )
                MessageHint(stringResource(R.string.classic_press_send_to_confirm))
                ClassicLightButton(
                    label = stringResource(R.string.classic_send),
                    onClick = onSend,
                    color = ClaroColors.Green,
                    textColor = Color.White,
                    borderColor = ClaroColors.Green,
                )
                ClassicLightButton(
                    label = stringResource(R.string.classic_edit),
                    onClick = onEdit,
                )
            }

            MessagingRoute.SENDING -> {
                MessageRecipientHeader(recipient = state.draft.recipient)
                MessageTitle(stringResource(R.string.classic_sending_ellipsis))
                MessageHint(stringResource(R.string.classic_wait_carrier_result))
            }

            MessagingRoute.FAILED -> {
                MessageRecipientHeader(recipient = state.draft.recipient)
                val currentStatus = state.statusMessage
                val explanation = when {
                    state.duplicateRiskConfirmationRequired ->
                        stringResource(R.string.classic_msg_duplicate_risk)
                    state.unknownOutcome ->
                        if (
                            currentStatus == null ||
                            currentStatus ==
                            "The carrier did not confirm the result. It may already have been sent." ||
                            currentStatus ==
                            "This message may already have been sent. Send it again?"
                        ) {
                            stringResource(R.string.classic_msg_unknown_outcome)
                        } else {
                            currentStatus
                        }
                    state.sendFailure != null ->
                        state.sendFailure.classicUserMessage()
                    currentStatus != null ->
                        localizeMessagingStatus(currentStatus, null)
                    else ->
                        stringResource(R.string.classic_msg_not_sent_draft_saved)
                }
                ClaroInfoCard(
                    text = explanation,
                    tone = if (state.unknownOutcome || state.duplicateRiskConfirmationRequired) {
                        ClaroBannerTone.WARNING
                    } else {
                        ClaroBannerTone.ERROR
                    },
                )
                if (state.draft.body.isNotBlank()) {
                    Text(
                        text = state.draft.body,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(IncomingBubbleColor, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        color = ClassicInk,
                        fontSize = 21.sp,
                        lineHeight = 28.sp,
                    )
                }
                if (state.duplicateRiskConfirmationRequired) {
                    ClassicLightButton(
                        label = stringResource(R.string.classic_send_again),
                        onClick = onConfirmDuplicateRisk,
                        color = ClaroColors.Green,
                        textColor = Color.White,
                        borderColor = ClaroColors.Green,
                    )
                    ClassicLightButton(
                        label = stringResource(R.string.classic_do_not_resend),
                        onClick = onCancelFailure,
                    )
                } else {
                    ClassicLightButton(
                        label = stringResource(R.string.classic_try_again),
                        onClick = onRetry,
                        color = ClaroColors.Green,
                        textColor = Color.White,
                        borderColor = ClaroColors.Green,
                    )
                    ClassicLightButton(
                        label = stringResource(R.string.classic_edit),
                        onClick = onEdit,
                    )
                    ClassicLightButton(
                        label = stringResource(R.string.classic_cancel_keep_draft),
                        onClick = onCancelFailure,
                    )
                }
            }
        }
        state.statusMessage
            ?.takeIf { state.route != MessagingRoute.FAILED && it.isNotBlank() }
            ?.let { status ->
                ClaroInfoCard(
                    text = localizeMessagingStatus(status, state.sendFailure),
                    tone = ClaroBannerTone.INFO,
                )
            }
    }
}

@Composable
private fun SmsCounterLine(state: MessagingUiState) {
    val encoding = state.encoding
    val text = if (encoding.encoding == SmsEncoding.UNICODE) {
        stringResource(
            R.string.classic_sms_counter_unicode,
            state.draft.body.length,
            encoding.segments,
        )
    } else {
        stringResource(
            R.string.classic_sms_counter_gsm,
            state.draft.body.length,
            encoding.segments,
        )
    }
    Text(
        text = text,
        color = Color(0xFF4B5565),
        fontSize = 16.sp,
        lineHeight = 21.sp,
        modifier = Modifier.padding(horizontal = 3.dp),
    )
}

@Composable
private fun MessageRecipientHeader(recipient: MessageRecipient?) {
    val name = recipient?.accessibleName.orEmpty()
        .ifBlank { stringResource(R.string.classic_recipient) }
    val number = recipient?.number.orEmpty()
    val nameIsRawNumber = recipient?.displayName.isNullOrBlank() && number.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(IncomingBubbleColor, RoundedCornerShape(4.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClaroAvatar(
            name = recipient?.displayName,
            size = 48.dp,
            isUnassigned = recipient?.displayName.isNullOrBlank(),
        )
        Column(modifier = Modifier.weight(1f)) {
            if (nameIsRawNumber) {
                ClassicLtrText(
                    text = name,
                    color = ClassicInk,
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                )
            } else {
                Text(
                    text = name,
                    color = ClassicInk,
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (number.isNotBlank() && number != name) {
                ClassicLtrText(
                    text = number,
                    color = Color(0xFF4B5565),
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

@Composable
private fun MessageTitle(value: String) {
    Text(
        text = value,
        modifier = Modifier.fillMaxWidth(),
        color = ClassicInk,
        fontSize = 28.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun MessageHint(value: String) {
    Text(
        text = value,
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF55647B),
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun AttachmentState.classicAttachmentLabel(): String = when (this) {
    AttachmentState.NONE -> ""
    AttachmentState.AVAILABLE -> stringResource(R.string.classic_attachment_available)
    AttachmentState.DOWNLOAD_REQUIRED ->
        stringResource(R.string.classic_attachment_download_required)
    AttachmentState.DOWNLOAD_FAILED -> stringResource(R.string.classic_attachment_download_failed)
    AttachmentState.UNSUPPORTED -> stringResource(R.string.classic_attachment_unsupported)
}

@Composable
private fun MessageFailure?.classicUserMessage(): String = when (this) {
    MessageFailure.INVALID_RECIPIENT ->
        stringResource(R.string.classic_msg_failure_invalid_recipient)
    MessageFailure.EMPTY_CONTENT -> stringResource(R.string.classic_msg_failure_empty_content)
    MessageFailure.ROLE_LOST -> stringResource(R.string.classic_msg_failure_role_lost)
    MessageFailure.PERMISSION_DENIED ->
        stringResource(R.string.classic_msg_failure_permission_denied)
    MessageFailure.NO_SIM -> stringResource(R.string.classic_msg_failure_no_sim)
    MessageFailure.NO_SERVICE -> stringResource(R.string.classic_msg_failure_no_service)
    MessageFailure.ACCOUNT_UNAVAILABLE ->
        stringResource(R.string.classic_msg_failure_account_unavailable)
    MessageFailure.CARRIER_REJECTED ->
        stringResource(R.string.classic_msg_failure_carrier_rejected)
    MessageFailure.UNKNOWN, null -> stringResource(R.string.classic_msg_failure_unknown)
}

@Composable
private fun localizeMessagingStatus(
    rawStatus: String,
    failure: MessageFailure?,
): String = when (rawStatus) {
    "Enter a valid phone number" -> stringResource(R.string.classic_call_failure_invalid_number)
    "Choose a default SMS account in Android. Your draft is saved." ->
        stringResource(R.string.classic_msg_choose_default_sms_account)
    "Sent. Delivery is not confirmed." ->
        stringResource(R.string.classic_msg_sent_delivery_unconfirmed)
    "Delivery confirmed by the carrier" ->
        stringResource(R.string.classic_msg_delivery_confirmed_carrier)
    "Sending message" -> stringResource(R.string.classic_msg_sending_status)
    else -> failure?.classicUserMessage() ?: rawStatus
}

@Composable
private fun classicMessagingTitle(state: MessagingUiState): String = when (state.route) {
    MessagingRoute.INBOX -> stringResource(R.string.classic_messages)
    MessagingRoute.CONVERSATION -> state.selectedConversation?.recipient?.accessibleName
        ?.ifBlank { null }
        ?: stringResource(R.string.classic_conversation)
    MessagingRoute.RECIPIENTS -> stringResource(R.string.classic_choose_recipient)
    MessagingRoute.COMPOSE -> stringResource(R.string.classic_write_message)
    MessagingRoute.CONFIRMATION -> stringResource(R.string.classic_send_message_title)
    MessagingRoute.SENDING -> stringResource(R.string.classic_sending_title)
    MessagingRoute.FAILED -> if (
        state.unknownOutcome || state.duplicateRiskConfirmationRequired
    ) {
        stringResource(R.string.classic_delivery_uncertain)
    } else {
        stringResource(R.string.classic_message_not_sent)
    }
}
