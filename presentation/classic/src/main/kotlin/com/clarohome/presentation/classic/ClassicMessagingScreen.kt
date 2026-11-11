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
import com.clarohome.feature.communication.MessageKind
import com.clarohome.feature.communication.MessageRecipient
import com.clarohome.feature.communication.MessagingRoute
import com.clarohome.feature.communication.MessagingUiState
import com.clarohome.feature.communication.SmsEncoding

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
            composeTarget == ClassicComposeTarget.RECIPIENT -> "Numbers"
        numericMode -> "Numbers"
        else -> "Letters"
    }

    val options = buildClassicMessagingOptions(
        state = state,
        composeTarget = composeTarget,
        recipientFieldValue = recipientFieldValue,
        onNewMessage = onNewMessage,
        onOpenRecipients = onOpenRecipients,
        onCancelRecipients = onCancelRecipients,
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
        onCancelKeepDraft = onCancelFailure,
        onRepair = onRepair,
        onHome = onHome,
    )

    val backAction: () -> Unit = {
        when {
            optionsOpen -> optionsOpen = false
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
                    "Options"
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
            menuLabel = "Options",
            greenLabel = when (state.route) {
                MessagingRoute.INBOX -> "＋ New"
                MessagingRoute.RECIPIENTS -> "✓ Choose"
                MessagingRoute.COMPOSE,
                MessagingRoute.CONVERSATION,
                -> "✓ Review"
                MessagingRoute.CONFIRMATION -> "✉ Send"
                MessagingRoute.FAILED -> if (state.duplicateRiskConfirmationRequired) {
                    "✉ Send"
                } else {
                    "↻ Retry"
                }
                MessagingRoute.SENDING -> "Sending…"
            },
            redLabel = "⌂ Home",
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

private fun buildClassicMessagingOptions(
    state: MessagingUiState,
    composeTarget: ClassicComposeTarget,
    recipientFieldValue: String,
    onNewMessage: () -> Unit,
    onOpenRecipients: () -> Unit,
    onCancelRecipients: () -> Unit,
    onSearchInbox: (String) -> Unit,
    onSearchRecipients: (String) -> Unit,
    onRecipientInputChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onEdit: () -> Unit,
    onCancelKeepDraft: () -> Unit,
    onRepair: () -> Unit,
    onHome: () -> Unit,
): List<ClassicOptionAction> = buildList {
    when (state.route) {
        MessagingRoute.INBOX -> {
            add(ClassicOptionAction(label = "New message", onSelect = onNewMessage))
            add(ClassicOptionAction(label = "Contacts", onSelect = onOpenRecipients))
            add(
                ClassicOptionAction(
                    label = "Delete character",
                    enabled = state.searchQuery.isNotEmpty(),
                    onSelect = { onSearchInbox(state.searchQuery.dropLast(1)) },
                ),
            )
            add(
                ClassicOptionAction(
                    label = "Clear text",
                    enabled = state.searchQuery.isNotEmpty(),
                    onSelect = { onSearchInbox("") },
                ),
            )
            if (!state.qualification.canRead && state.qualification.telephonySupported) {
                add(ClassicOptionAction(label = "Repair Messages access", onSelect = onRepair))
            }
            add(ClassicOptionAction(label = "Cancel", onSelect = onHome))
        }

        MessagingRoute.CONVERSATION -> {
            add(
                ClassicOptionAction(
                    label = "Delete character",
                    enabled = state.draft.body.isNotEmpty(),
                    onSelect = { onBodyChange(state.draft.body.dropLast(1)) },
                ),
            )
            add(
                ClassicOptionAction(
                    label = "Clear text",
                    enabled = state.draft.body.isNotEmpty(),
                    onSelect = { onBodyChange("") },
                ),
            )
            add(ClassicOptionAction(label = "New message", onSelect = onNewMessage))
            add(ClassicOptionAction(label = "Contacts", onSelect = onOpenRecipients))
            add(ClassicOptionAction(label = "Cancel", onSelect = onHome))
        }

        MessagingRoute.RECIPIENTS -> {
            add(
                ClassicOptionAction(
                    label = "Delete character",
                    enabled = state.recipientQuery.isNotEmpty(),
                    onSelect = { onSearchRecipients(state.recipientQuery.dropLast(1)) },
                ),
            )
            add(
                ClassicOptionAction(
                    label = "Clear text",
                    enabled = state.recipientQuery.isNotEmpty(),
                    onSelect = { onSearchRecipients("") },
                ),
            )
            add(ClassicOptionAction(label = "New message", onSelect = onNewMessage))
            add(ClassicOptionAction(label = "Cancel", onSelect = onCancelRecipients))
        }

        MessagingRoute.COMPOSE -> {
            val editingRecipient = composeTarget == ClassicComposeTarget.RECIPIENT
            val activeText = if (editingRecipient) recipientFieldValue else state.draft.body
            add(ClassicOptionAction(label = "Contacts", onSelect = onOpenRecipients))
            add(
                ClassicOptionAction(
                    label = "Delete character",
                    enabled = activeText.isNotEmpty(),
                    onSelect = {
                        if (editingRecipient) {
                            onRecipientInputChange(recipientFieldValue.dropLast(1))
                        } else {
                            onBodyChange(state.draft.body.dropLast(1))
                        }
                    },
                ),
            )
            add(
                ClassicOptionAction(
                    label = "Clear text",
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
            add(ClassicOptionAction(label = "New message", onSelect = onNewMessage))
            add(ClassicOptionAction(label = "Cancel", onSelect = onCancelKeepDraft))
        }

        MessagingRoute.CONFIRMATION -> {
            add(ClassicOptionAction(label = "Edit", onSelect = onEdit))
            add(ClassicOptionAction(label = "Contacts", onSelect = onOpenRecipients))
            add(ClassicOptionAction(label = "New message", onSelect = onNewMessage))
            add(ClassicOptionAction(label = "Cancel", onSelect = onCancelKeepDraft))
        }

        MessagingRoute.FAILED -> {
            add(ClassicOptionAction(label = "Edit", onSelect = onEdit))
            add(ClassicOptionAction(label = "New message", onSelect = onNewMessage))
            add(ClassicOptionAction(label = "Contacts", onSelect = onOpenRecipients))
            add(ClassicOptionAction(label = "Cancel", onSelect = onCancelKeepDraft))
        }

        MessagingRoute.SENDING -> {
            add(ClassicOptionAction(label = "Cancel", onSelect = onCancelKeepDraft))
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
                Text(
                    text = "Search name or number",
                    color = ClassicInk,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchInbox,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 23.sp),
                    shape = RectangleShape,
                )
                if (!state.qualification.canRead) {
                    ClaroInfoCard(
                        text = if (!state.qualification.telephonySupported) {
                            "Messages are not available on this device."
                        } else {
                            "Messages access needs repair."
                        },
                        tone = ClaroBannerTone.WARNING,
                    )
                    if (state.qualification.telephonySupported) {
                        ClassicLightButton(
                            label = "Repair Messages access",
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
                            "No messages yet."
                        } else {
                            "No matching messages."
                        },
                        tone = ClaroBannerTone.INFO,
                    )
                }
                state.conversations.forEachIndexed { index, conversation ->
                    val hasUnread = conversation.unreadCount > 0
                    val title = if (conversation.unreadCount > 1) {
                        "${conversation.recipient.accessibleName} (${conversation.unreadCount})"
                    } else {
                        conversation.recipient.accessibleName
                    }
                    val subtitle = if (conversation.kind == MessageKind.MMS) {
                        "MMS · ${conversation.snippet}"
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
                    label = "Write a message",
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
                        MessageDeliveryState.DELIVERY_CONFIRMED -> "Delivered"
                        MessageDeliveryState.DELIVERY_UNKNOWN -> "Delivery unknown"
                        MessageDeliveryState.KNOWN_FAILURE -> "Not sent"
                        MessageDeliveryState.PENDING -> "Sending…"
                        MessageDeliveryState.SENT -> "Sent · delivery not confirmed"
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
                            Text(
                                text = attachment.state.classicAttachmentLabel(),
                                color = Color(0xFF4B5565),
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                            )
                        }
                    }
                }
                Text(
                    text = "Reply",
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
                        .heightIn(min = 84.dp),
                    placeholder = { Text("Write a reply…", fontSize = 21.sp) },
                    textStyle = TextStyle(fontSize = 22.sp, lineHeight = 28.sp),
                    shape = RectangleShape,
                )
                SmsCounterLine(state)
                if (!state.qualification.canSend && state.qualification.telephonySupported) {
                    ClassicLightButton(
                        label = "Repair Messages access",
                        onClick = onRepair,
                        color = ClaroColors.WarningBg,
                        textColor = ClaroColors.WarningInk,
                        borderColor = ClaroColors.WarningBorder,
                    )
                }
            }

            MessagingRoute.RECIPIENTS -> {
                Text(
                    text = "To",
                    color = ClassicInk,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
                OutlinedTextField(
                    value = state.recipientQuery,
                    onValueChange = onSearchRecipients,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Name or number", fontSize = 21.sp) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 23.sp),
                    shape = RectangleShape,
                )
                if (state.recipientQuery.isNotBlank()) {
                    ClassicLightButton(
                        label = "Use typed number",
                        onClick = { onManualRecipient(state.recipientQuery) },
                        color = ClaroColors.Blue,
                        textColor = Color.White,
                        borderColor = ClaroColors.Blue,
                    )
                }
                if (state.contactsLoading) {
                    ClaroInfoCard(text = "Loading contacts…", tone = ClaroBannerTone.INFO)
                } else if (state.contacts.isEmpty()) {
                    ClaroInfoCard(text = "No contacts found.", tone = ClaroBannerTone.INFO)
                }
                state.contacts.forEachIndexed { index, contact ->
                    contact.numbers.forEach { number ->
                        ClassicRowItem(
                            title = contact.displayName,
                            subtitle = "${number.value} · ${number.label}",
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
                Text(
                    text = "To",
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
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    onComposeTargetChange(ClassicComposeTarget.RECIPIENT)
                                }
                            },
                        placeholder = { Text("Phone number", fontSize = 21.sp) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 23.sp),
                        shape = RectangleShape,
                    )
                }
                ClassicLightButton(
                    label = "Contacts",
                    onClick = onOpenRecipients,
                    leadingIcon = ClaroIconKind.CONTACT,
                )
                Text(
                    text = "Message",
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
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                onComposeTargetChange(ClassicComposeTarget.BODY)
                            }
                        },
                    placeholder = { Text("Write here…", fontSize = 21.sp) },
                    textStyle = TextStyle(fontSize = 22.sp, lineHeight = 28.sp),
                    shape = RectangleShape,
                )
                SmsCounterLine(state)
                if (!state.qualification.canSend && state.qualification.telephonySupported) {
                    ClassicLightButton(
                        label = "Repair Messages access",
                        onClick = onRepair,
                        color = ClaroColors.WarningBg,
                        textColor = ClaroColors.WarningInk,
                        borderColor = ClaroColors.WarningBorder,
                    )
                }
                ClassicLightButton(
                    label = "Review",
                    onClick = onReview,
                    color = ClaroColors.Blue,
                    textColor = Color.White,
                    borderColor = ClaroColors.Blue,
                )
                ClassicLightButton(
                    label = "Cancel · keep draft",
                    onClick = onCancelFailure,
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
                MessageHint("Press Send to confirm")
                ClassicLightButton(
                    label = "Send",
                    onClick = onSend,
                    color = ClaroColors.Green,
                    textColor = Color.White,
                    borderColor = ClaroColors.Green,
                )
                ClassicLightButton(label = "Edit", onClick = onEdit)
                ClassicLightButton(label = "Cancel · keep draft", onClick = onCancelFailure)
            }

            MessagingRoute.SENDING -> {
                MessageRecipientHeader(recipient = state.draft.recipient)
                MessageTitle("Sending…")
                MessageHint("Wait for the carrier result.")
            }

            MessagingRoute.FAILED -> {
                MessageRecipientHeader(recipient = state.draft.recipient)
                val explanation = when {
                    state.duplicateRiskConfirmationRequired ->
                        "Sending again may deliver this message twice. Confirm only if you want to resend."
                    state.unknownOutcome ->
                        state.statusMessage
                            ?: "The carrier did not confirm the result. This message may already have been sent."
                    else ->
                        state.statusMessage ?: "The message could not be sent. Your draft is saved."
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
                        label = "Send again",
                        onClick = onConfirmDuplicateRisk,
                        color = ClaroColors.Green,
                        textColor = Color.White,
                        borderColor = ClaroColors.Green,
                    )
                    ClassicLightButton(label = "Do not resend", onClick = onCancelFailure)
                } else {
                    ClassicLightButton(
                        label = "Try again",
                        onClick = onRetry,
                        color = ClaroColors.Green,
                        textColor = Color.White,
                        borderColor = ClaroColors.Green,
                    )
                    ClassicLightButton(label = "Edit", onClick = onEdit)
                    ClassicLightButton(label = "Cancel · keep draft", onClick = onCancelFailure)
                }
            }
        }
        state.statusMessage
            ?.takeIf { state.route != MessagingRoute.FAILED && it.isNotBlank() }
            ?.let { status ->
                ClaroInfoCard(text = status, tone = ClaroBannerTone.INFO)
            }
    }
}

@Composable
private fun SmsCounterLine(state: MessagingUiState) {
    val encoding = state.encoding
    Text(
        text = buildString {
            append(state.draft.body.length)
            append(" · ~")
            append(encoding.segments)
            append(" SMS")
            if (encoding.encoding == SmsEncoding.UNICODE) {
                append(" · Unicode")
            }
            append(" · carrier charges may apply")
        },
        color = Color(0xFF4B5565),
        fontSize = 16.sp,
        lineHeight = 21.sp,
        modifier = Modifier.padding(horizontal = 3.dp),
    )
}

@Composable
private fun MessageRecipientHeader(recipient: MessageRecipient?) {
    val name = recipient?.accessibleName.orEmpty().ifBlank { "Recipient" }
    val number = recipient?.number.orEmpty()
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
            Text(
                text = name,
                color = ClassicInk,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
            )
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

private fun AttachmentState.classicAttachmentLabel(): String = when (this) {
    AttachmentState.NONE -> ""
    AttachmentState.AVAILABLE -> "Attachment available"
    AttachmentState.DOWNLOAD_REQUIRED -> "Attachment needs mobile data"
    AttachmentState.DOWNLOAD_FAILED -> "Attachment download failed"
    AttachmentState.UNSUPPORTED -> "Unsupported attachment"
}

private fun classicMessagingTitle(state: MessagingUiState): String = when (state.route) {
    MessagingRoute.INBOX -> "Messages"
    MessagingRoute.CONVERSATION -> state.selectedConversation?.recipient?.accessibleName
        ?.ifBlank { "Conversation" }
        ?: "Conversation"
    MessagingRoute.RECIPIENTS -> "Choose recipient"
    MessagingRoute.COMPOSE -> "Write a message"
    MessagingRoute.CONFIRMATION -> "Send message?"
    MessagingRoute.SENDING -> "Sending"
    MessagingRoute.FAILED -> if (
        state.unknownOutcome || state.duplicateRiskConfirmationRequired
    ) {
        "Delivery uncertain"
    } else {
        "Message not sent"
    }
}
