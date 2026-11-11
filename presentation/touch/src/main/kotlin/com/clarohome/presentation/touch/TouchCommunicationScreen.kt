package com.clarohome.presentation.touch

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarohome.core.design.ClaroActionDock
import com.clarohome.core.design.ClaroAvatar
import com.clarohome.core.design.ClaroBannerTone
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.core.design.ClaroEmergencyButton
import com.clarohome.core.design.ClaroHeader
import com.clarohome.core.design.ClaroIcon
import com.clarohome.core.design.ClaroIconKind
import com.clarohome.core.design.ClaroInfoCard
import com.clarohome.core.design.ClaroPrimaryButton
import com.clarohome.core.design.ClaroRowButton
import com.clarohome.core.design.ClaroSecondaryButton
import com.clarohome.feature.communication.AssignmentStatus
import com.clarohome.feature.communication.AudioRoute
import com.clarohome.feature.communication.CallDirection
import com.clarohome.feature.communication.CallHistoryEntry
import com.clarohome.feature.communication.CallOutcome
import com.clarohome.feature.communication.CallPhase
import com.clarohome.feature.communication.CallerIdentity
import com.clarohome.feature.communication.ClaroContact
import com.clarohome.feature.communication.CommunicationRoute
import com.clarohome.feature.communication.CommunicationUiState
import com.clarohome.feature.communication.ContactNumber
import com.clarohome.feature.communication.PhoneContactsCoordinator
import com.clarohome.feature.communication.formatCallDuration
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

private enum class DialerTab(val label: String) {
    KEYPAD("Keypad"),
    RECENT("Recent"),
    MISSED("Missed"),
}

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
    onOpenHistoryEntry: (String) -> Unit = {},
    onMessageNumber: (String, String?) -> Unit = { _, _ -> },
    onEmergencyDialer: () -> Unit = {},
    canManageContacts: Boolean = false,
    onSaveContact: (String?, String, String, String) -> Unit = { _, _, _, _ -> },
    onDeleteContact: (String) -> Unit = {},
    onRequestUnlockForContacts: () -> Unit = {},
) {
    var editorMode by rememberSaveable(state.route, state.selectedContact?.lookupKey) {
        mutableStateOf<String?>(null)
    }
    var draftName by rememberSaveable { mutableStateOf("") }
    var draftPrimaryNumber by rememberSaveable { mutableStateOf("") }
    var draftSecondaryNumber by rememberSaveable { mutableStateOf("") }
    var editorError by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("touch-communication")
            .background(Color.White),
    ) {
        when {
            editorMode != null && canManageContacts -> {
                val editingContact = if (editorMode == "edit") state.selectedContact else null
                ContactEditorView(
                    isEdit = editingContact != null,
                    name = draftName,
                    primaryNumber = draftPrimaryNumber,
                    secondaryNumber = draftSecondaryNumber,
                    errorMessage = editorError,
                    onNameChange = {
                        draftName = it
                        editorError = null
                    },
                    onPrimaryChange = {
                        draftPrimaryNumber = it
                        editorError = null
                    },
                    onSecondaryChange = {
                        draftSecondaryNumber = it
                        editorError = null
                    },
                    onSave = {
                        val trimmedName = draftName.trim()
                        val normalizedPrimary =
                            PhoneContactsCoordinator.normalizeDialString(draftPrimaryNumber)
                        val normalizedSecondary =
                            PhoneContactsCoordinator.normalizeDialString(draftSecondaryNumber)
                        when {
                            trimmedName.isBlank() || normalizedPrimary.length < 3 -> {
                                editorError = "Enter a name and a valid mobile number."
                            }
                            draftSecondaryNumber.isNotBlank() && normalizedSecondary.length < 3 -> {
                                editorError = "Enter a valid second number or leave it empty."
                            }
                            else -> {
                                editorMode = null
                                editorError = null
                                onSaveContact(
                                    editingContact?.lookupKey,
                                    trimmedName,
                                    draftPrimaryNumber.trim(),
                                    draftSecondaryNumber.trim(),
                                )
                            }
                        }
                    },
                    onCancel = {
                        editorMode = null
                        editorError = null
                    },
                )
            }

            state.route == CommunicationRoute.DIALER -> TouchDialerRoute(
                state = state,
                onDigit = onDigit,
                onBackspace = onBackspace,
                onCallEnteredNumber = onCallEnteredNumber,
                onDirectCall = onDirectCall,
                onOpenContacts = onOpenContacts,
                onOpenHistoryEntry = onOpenHistoryEntry,
                onEmergencyDialer = onEmergencyDialer,
                onRepairPhone = onRepairPhone,
            )

            state.route == CommunicationRoute.CONTACTS -> ContactListRoute(
                state = state,
                onSearch = onSearchContacts,
                onLoadMore = onLoadMoreContacts,
                onOpenContact = onOpenContact,
                onRepairPhone = onRepairPhone,
                onAddPerson = {
                    if (canManageContacts) {
                        draftName = ""
                        draftPrimaryNumber = ""
                        draftSecondaryNumber = ""
                        editorError = null
                        editorMode = "add"
                    } else {
                        onRequestUnlockForContacts()
                    }
                },
            )

            state.route == CommunicationRoute.CONTACT_DETAIL -> ContactDetailRoute(
                state = state,
                canManageContacts = canManageContacts,
                onChooseNumber = onChooseNumber,
                onMessageNumber = onMessageNumber,
                onOpenContacts = onOpenContacts,
                onEditContact = { contact ->
                    if (canManageContacts) {
                        draftName = contact.displayName
                        draftPrimaryNumber = contact.numbers.getOrNull(0)?.value.orEmpty()
                        draftSecondaryNumber = contact.numbers.getOrNull(1)?.value.orEmpty()
                        editorError = null
                        editorMode = "edit"
                    } else {
                        onRequestUnlockForContacts()
                    }
                },
                onDeleteContact = onDeleteContact,
                onRequestUnlockForContacts = onRequestUnlockForContacts,
            )

            state.route == CommunicationRoute.CALL_CONFIRMATION -> CallConfirmationRoute(
                state = state,
                onConfirm = onConfirmCall,
                onCancel = onCancelCall,
            )

            state.route == CommunicationRoute.INCOMING_CALL -> IncomingCallRoute(
                identity = state.call?.identity ?: state.pendingIdentity,
                onAnswer = onAnswer,
                onDecline = onDecline,
            )

            state.route == CommunicationRoute.ACTIVE_CALL -> ActiveCallRoute(
                state = state,
                onDigit = onDigit,
                onEnd = onEnd,
                onToggleMute = onToggleMute,
                onAudioRoute = onAudioRoute,
                onToggleHold = onToggleHold,
                onSwap = onSwap,
            )

            state.route == CommunicationRoute.CALL_ENDED ||
                state.route == CommunicationRoute.MISSED_CALL ||
                state.route == CommunicationRoute.CALL_UNANSWERED -> CallOutcomeRoute(
                state = state,
                onRetryCall = {
                    retryTargetCall(
                        state = state,
                        onDigit = onDigit,
                        onBackspace = onBackspace,
                        onCallEnteredNumber = onCallEnteredNumber,
                        onConfirmCall = onConfirmCall,
                    )
                },
                onMessageNumber = onMessageNumber,
                onRepairPhone = onRepairPhone,
                onHome = onHome,
            )

            state.route == CommunicationRoute.CALL_FAILED -> CallFailedRoute(
                state = state,
                onRetryCall = {
                    retryTargetCall(
                        state = state,
                        onDigit = onDigit,
                        onBackspace = onBackspace,
                        onCallEnteredNumber = onCallEnteredNumber,
                        onConfirmCall = onConfirmCall,
                    )
                },
                onRepairPhone = onRepairPhone,
                onEmergencyDialer = onEmergencyDialer,
                onHome = onHome,
            )

            state.route == CommunicationRoute.EXTERNAL_HANDOFF -> {
                ClaroHeader(title = "Emergency dialer")
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(ClaroDimensions.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
                ) {
                    ClaroInfoCard(
                        text = state.message ?: "Continue in the system emergency dialer.",
                    )
                }
                ClaroActionDock {
                    ClaroPrimaryButton(
                        label = "Home",
                        onClick = onHome,
                        containerColor = ClaroColors.Blue,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.TouchDialerRoute(
    state: CommunicationUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onCallEnteredNumber: () -> Unit,
    onDirectCall: (String) -> Unit,
    onOpenContacts: () -> Unit,
    onOpenHistoryEntry: (String) -> Unit,
    onEmergencyDialer: () -> Unit,
    onRepairPhone: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(DialerTab.KEYPAD) }

    ClaroHeader(title = "Phone")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClaroColors.Charcoal)
            .padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        DialerTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .background(if (isSelected) ClaroColors.Blue else Color(0xFFE6EAF0))
                    .clickable(role = Role.Tab) { selectedTab = tab }
                    .semantics {
                        role = Role.Tab
                        selected = isSelected
                    }
                    .padding(horizontal = 4.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    color = if (isSelected) Color.White else Color(0xFF26364E),
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (selectedTab == DialerTab.KEYPAD) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(ClaroDimensions.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
        ) {
            if (state.qualification.needsRepair) {
                ClaroInfoCard(
                    text = if (!state.qualification.telephonySupported) {
                        "Calling is not available on this device."
                    } else {
                        "Phone access needs attention."
                    },
                    tone = ClaroBannerTone.WARNING,
                )
                if (state.qualification.telephonySupported) {
                    ClaroSecondaryButton(
                        label = "Repair phone access",
                        onClick = onRepairPhone,
                    )
                }
            }
            state.message?.let { message ->
                ClaroInfoCard(text = message)
            }
            DialNumberDisplayRow(
                digits = state.digits,
                onBackspace = onBackspace,
            )
            SpeedDialShortcutBar(
                state = state,
                onDigit = onDigit,
                onDirectCall = onDirectCall,
            )
            DialKeypadGrid(
                state = state,
                onDigit = onDigit,
                onDirectCall = onDirectCall,
            )
            ClaroEmergencyButton(
                label = "Emergency call",
                onClick = onEmergencyDialer,
            )
        }
        ClaroActionDock {
            ClaroPrimaryButton(
                label = "Call",
                onClick = onCallEnteredNumber,
                containerColor = ClaroColors.Green,
                icon = ClaroIconKind.PHONE,
            )
            ClaroSecondaryButton(
                label = "Contacts",
                onClick = onOpenContacts,
                icon = ClaroIconKind.CONTACT,
            )
        }
    } else {
        val logBlocked =
            !state.qualification.roleHeld || !state.qualification.callLogPermissionGranted
        val filteredHistory = remember(state.history, selectedTab) {
            if (selectedTab == DialerTab.MISSED) {
                state.history.filter { it.outcome == CallOutcome.MISSED_INCOMING }
            } else {
                state.history
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(ClaroDimensions.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
        ) {
            if (logBlocked) {
                item(key = "history-repair") {
                    Column(verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap)) {
                        ClaroInfoCard(
                            text = if (!state.qualification.telephonySupported) {
                                "Calling is not available on this device."
                            } else {
                                "Phone and call log access are needed to view recent calls."
                            },
                            tone = ClaroBannerTone.WARNING,
                        )
                        if (state.qualification.telephonySupported) {
                            ClaroPrimaryButton(
                                label = "Repair phone access",
                                onClick = onRepairPhone,
                                containerColor = ClaroColors.Blue,
                            )
                        }
                    }
                }
            } else if (filteredHistory.isEmpty()) {
                item(key = "history-empty") {
                    ClaroInfoCard(text = "No calls yet.")
                }
            } else {
                itemsIndexed(filteredHistory, key = { _, entry -> entry.id }) { index, entry ->
                    val unread =
                        entry.outcome == CallOutcome.MISSED_INCOMING && !entry.isOpened
                    ClaroRowButton(
                        title = entry.identity.accessibleName,
                        subtitle = entry.historySubtitle(),
                        unreadDot = unread,
                        leading = {
                            ClaroAvatar(
                                name = entry.identity.displayName,
                                size = 48.dp,
                                paletteSeed = index,
                                isUnassigned = entry.identity.isPrivate ||
                                    entry.identity.displayName.isNullOrBlank(),
                            )
                        },
                        onClick = { onOpenHistoryEntry(entry.id) },
                    )
                }
            }
            item(key = "history-emergency") {
                ClaroEmergencyButton(
                    label = "Emergency call",
                    onClick = onEmergencyDialer,
                )
            }
        }
        ClaroActionDock {
            ClaroSecondaryButton(
                label = "Contacts",
                onClick = onOpenContacts,
                icon = ClaroIconKind.CONTACT,
            )
        }
    }
}

@Composable
private fun DialNumberDisplayRow(
    digits: String,
    onBackspace: () -> Unit,
) {
    val isEmpty = digits.isBlank()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .semantics {
                    contentDescription = "Phone number ${digits.ifBlank { "empty" }}"
                },
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = digits.ifBlank { "Enter a number" },
                    color = if (isEmpty) ClaroColors.Muted else ClaroColors.Ink,
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = if (isEmpty) FontWeight.Normal else FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        OutlinedButton(
            onClick = onBackspace,
            enabled = !isEmpty,
            modifier = Modifier
                .size(64.dp)
                .semantics { contentDescription = "Backspace" },
            shape = RoundedCornerShape(3.dp),
            border = BorderStroke(1.dp, ClaroColors.Border),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = ClaroColors.Surface,
                contentColor = ClaroColors.Ink,
                disabledContainerColor = ClaroColors.Surface.copy(alpha = 0.5f),
                disabledContentColor = ClaroColors.Muted,
            ),
            contentPadding = PaddingValues(0.dp),
        ) {
            ClaroIcon(
                kind = ClaroIconKind.BACKSPACE,
                tint = if (isEmpty) ClaroColors.Muted else ClaroColors.Ink,
                size = 28.dp,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialShortcutBar(
    state: CommunicationUiState,
    onDigit: (Char) -> Unit,
    onDirectCall: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
    ) {
        listOf("1", "2").forEach { slot ->
            val assignment = state.assignments.firstOrNull { it.slot == slot }
            val nameLabel = assignment?.displayNameSnapshot?.ifBlank { "—" } ?: "—"
            val statusHint = when {
                assignment == null -> "Speed dial $slot not set"
                assignment.status != AssignmentStatus.RESOLVED ->
                    "Speed dial $slot, ${assignment.displayNameSnapshot}, needs repair"
                else -> "Hold $slot to call ${assignment.displayNameSnapshot}"
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ClaroColors.Surface)
                    .border(1.dp, ClaroColors.Border, RoundedCornerShape(3.dp))
                    .combinedClickable(
                        role = Role.Button,
                        onClick = { onDigit(slot.single()) },
                        onLongClick = { onDirectCall(slot) },
                    )
                    .semantics {
                        contentDescription = statusHint
                        role = Role.Button
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$slot · $nameLabel",
                    color = ClaroColors.Ink,
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKeypadGrid(
    state: CommunicationUiState,
    onDigit: (Char) -> Unit,
    onDirectCall: (String) -> Unit,
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#'),
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ClaroColors.Charcoal)
                .padding(ClaroDimensions.TileGap),
            verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
        ) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
                ) {
                    row.forEach { digit ->
                        val speedDialSlot = if (digit == '1' || digit == '2') digit.toString() else null
                        val assignedName = speedDialSlot?.let { slot ->
                            state.assignments.firstOrNull { it.slot == slot }?.displayNameSnapshot
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 74.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(ClaroColors.Surface)
                                .combinedClickable(
                                    role = Role.Button,
                                    onClick = { onDigit(digit) },
                                    onLongClick = speedDialSlot?.let { slot ->
                                        { onDirectCall(slot) }
                                    },
                                )
                                .semantics {
                                    contentDescription = buildString {
                                        append(digit)
                                        if (assignedName != null) {
                                            append(". Hold to call $assignedName")
                                        } else if (speedDialSlot != null) {
                                            append(". Hold for speed dial")
                                        }
                                    }
                                    role = Role.Button
                                }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = digit.toString(),
                                    color = ClaroColors.Ink,
                                    fontSize = 40.sp,
                                    lineHeight = 42.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = keypadLetters(digit).orEmpty(),
                                    color = ClaroColors.Muted,
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ContactListRoute(
    state: CommunicationUiState,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenContact: (String) -> Unit,
    onRepairPhone: () -> Unit,
    onAddPerson: () -> Unit,
) {
    ClaroHeader(title = "Contacts")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ClaroDimensions.ScreenPadding,
                end = ClaroDimensions.ScreenPadding,
                top = ClaroDimensions.ScreenPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
    ) {
        PhoneFieldLabel("Search name or number")
        OutlinedTextField(
            value = state.contactQuery,
            onValueChange = onSearch,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
            singleLine = true,
            textStyle = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, color = ClaroColors.Ink),
            shape = RoundedCornerShape(2.dp),
            colors = claroTextFieldColors(),
        )
        if (!state.qualification.roleHeld) {
            ClaroInfoCard(
                text = "Contacts are available, but phone access needs repair.",
                tone = ClaroBannerTone.WARNING,
            )
            if (state.qualification.telephonySupported) {
                ClaroSecondaryButton(
                    label = "Repair phone access",
                    onClick = onRepairPhone,
                )
            }
        }
        state.message?.let { message ->
            ClaroInfoCard(text = message)
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(ClaroDimensions.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
    ) {
        if (!state.contactsLoading && state.contacts.isEmpty()) {
            item(key = "contacts-empty") {
                ClaroInfoCard(
                    text = if (state.contactQuery.isBlank()) "No contacts yet." else "No matches.",
                )
            }
        }
        itemsIndexed(state.contacts, key = { _, contact -> contact.lookupKey }) { index, contact ->
            ClaroRowButton(
                title = contact.displayName,
                subtitle = contact.numbers.firstOrNull()?.value,
                leading = {
                    ClaroAvatar(
                        name = contact.displayName,
                        size = 48.dp,
                        paletteSeed = index,
                    )
                },
                onClick = { onOpenContact(contact.lookupKey) },
            )
        }
        if (state.contactsNextOffset != null && !state.contactsLoading) {
            item(key = "contacts-load-more") {
                ClaroSecondaryButton(
                    label = "Load more contacts",
                    onClick = onLoadMore,
                )
            }
        }
        if (state.contactsLoading) {
            item(key = "contacts-loading") {
                ClaroInfoCard(text = "Loading contacts")
            }
        }
    }
    ClaroActionDock {
        ClaroSecondaryButton(
            label = "Add person",
            onClick = onAddPerson,
            icon = ClaroIconKind.CONTACT,
        )
    }
}

@Composable
private fun ColumnScope.ContactDetailRoute(
    state: CommunicationUiState,
    canManageContacts: Boolean,
    onChooseNumber: (ContactNumber) -> Unit,
    onMessageNumber: (String, String?) -> Unit,
    onOpenContacts: () -> Unit,
    onEditContact: (ClaroContact) -> Unit,
    onDeleteContact: (String) -> Unit,
    onRequestUnlockForContacts: () -> Unit,
) {
    val contact = state.selectedContact
    var confirmDelete by rememberSaveable(contact?.lookupKey) { mutableStateOf(false) }

    if (contact == null) {
        ClaroHeader(title = "Contact")
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(ClaroDimensions.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
        ) {
            ClaroInfoCard(
                text = state.message ?: "Contact is no longer available",
                tone = ClaroBannerTone.WARNING,
            )
        }
        ClaroActionDock {
            ClaroPrimaryButton(
                label = "Contacts",
                onClick = onOpenContacts,
                containerColor = ClaroColors.Blue,
                icon = ClaroIconKind.CONTACT,
            )
        }
        return
    }

    val linkedAssignments = state.assignments.filter { it.contactLookupKey == contact.lookupKey }
    val isSosLinked = linkedAssignments.any { it.slot.equals("sos", ignoreCase = true) }
    val isSpeedDialLinked = linkedAssignments.any { !it.slot.equals("sos", ignoreCase = true) }

    ClaroHeader(title = contact.displayName.ifBlank { "Contact" })
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(ClaroDimensions.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClaroAvatar(
                name = contact.displayName,
                size = 104.dp,
            )
            Text(
                text = contact.displayName,
                color = ClaroColors.Ink,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }

        state.message?.let { message ->
            ClaroInfoCard(text = message)
        }

        if (confirmDelete && canManageContacts) {
            ClaroInfoCard(
                text = when {
                    isSosLinked ->
                        "${contact.displayName} is your SOS contact. Replace the SOS contact in Settings before deleting."
                    isSpeedDialLinked ->
                        "Delete ${contact.displayName}? Speed-dial and favorite shortcuts using this contact will be cleared."
                    else ->
                        "Delete ${contact.displayName}? Favorites using this contact will be cleared."
                },
                tone = if (isSosLinked) ClaroBannerTone.ERROR else ClaroBannerTone.WARNING,
            )
        } else {
            val multipleNumbers = contact.numbers.size > 1
            contact.numbers.forEach { number ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ClaroColors.Surface, RoundedCornerShape(3.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = number.label,
                            color = ClaroColors.Muted,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = number.value,
                                color = ClaroColors.Ink,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    ClaroPrimaryButton(
                        label = if (multipleNumbers) {
                            "Call ${number.label} · ${number.value}"
                        } else {
                            "Call ${contact.displayName}"
                        },
                        onClick = { onChooseNumber(number) },
                        containerColor = ClaroColors.Green,
                        icon = ClaroIconKind.PHONE,
                    )
                    ClaroSecondaryButton(
                        label = if (multipleNumbers) {
                            "Write a message · ${number.label}"
                        } else {
                            "Write a message"
                        },
                        onClick = { onMessageNumber(number.value, contact.displayName) },
                        icon = ClaroIconKind.MESSAGES,
                    )
                }
            }
        }
    }

    ClaroActionDock {
        if (confirmDelete && canManageContacts) {
            ClaroSecondaryButton(
                label = "Cancel",
                onClick = { confirmDelete = false },
            )
            if (!isSosLinked) {
                ClaroPrimaryButton(
                    label = "Delete",
                    onClick = {
                        confirmDelete = false
                        onDeleteContact(contact.lookupKey)
                    },
                    containerColor = ClaroColors.Red,
                )
            }
        } else {
            ClaroSecondaryButton(
                label = "Edit",
                enabled = contact.canEdit,
                onClick = { onEditContact(contact) },
            )
            ClaroSecondaryButton(
                label = "Delete",
                enabled = contact.canDelete,
                onClick = {
                    if (canManageContacts) {
                        confirmDelete = true
                    } else {
                        onRequestUnlockForContacts()
                    }
                },
            )
        }
    }
}

@Composable
private fun ColumnScope.ContactEditorView(
    isEdit: Boolean,
    name: String,
    primaryNumber: String,
    secondaryNumber: String,
    errorMessage: String?,
    onNameChange: (String) -> Unit,
    onPrimaryChange: (String) -> Unit,
    onSecondaryChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    ClaroHeader(title = if (isEdit) "Edit contact" else "Add contact")
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(ClaroDimensions.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
    ) {
        errorMessage?.let { error ->
            ClaroInfoCard(text = error, tone = ClaroBannerTone.ERROR)
        }
        PhoneFieldLabel("Name")
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
            singleLine = true,
            textStyle = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, color = ClaroColors.Ink),
            shape = RoundedCornerShape(2.dp),
            colors = claroTextFieldColors(),
        )
        PhoneFieldLabel("Mobile number")
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = primaryNumber,
                onValueChange = onPrimaryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                textStyle = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, color = ClaroColors.Ink),
                shape = RoundedCornerShape(2.dp),
                colors = claroTextFieldColors(),
            )
        }
        PhoneFieldLabel("Other number (optional)")
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = secondaryNumber,
                onValueChange = onSecondaryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                textStyle = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, color = ClaroColors.Ink),
                shape = RoundedCornerShape(2.dp),
                colors = claroTextFieldColors(),
            )
        }
    }
    ClaroActionDock {
        ClaroPrimaryButton(
            label = "Save",
            onClick = onSave,
            containerColor = ClaroColors.Blue,
        )
        ClaroSecondaryButton(
            label = "Cancel",
            onClick = onCancel,
        )
    }
}

@Composable
private fun ColumnScope.CallConfirmationRoute(
    state: CommunicationUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val identity = state.pendingIdentity
    ClaroHeader(title = "Call?", centered = true)
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(ClaroDimensions.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CenteredCallerBlock(
            identity = identity,
            avatarSizeDp = 112,
            fallbackTitle = "Unknown number",
        )
        if (!state.qualification.canOwnCalls) {
            ClaroInfoCard(
                text = if (!state.qualification.telephonySupported) {
                    "No SIM. Calling unavailable."
                } else {
                    "Phone access needs attention."
                },
                tone = ClaroBannerTone.WARNING,
            )
        }
    }
    ClaroActionDock {
        ClaroPrimaryButton(
            label = "Call",
            onClick = onConfirm,
            modifier = Modifier.heightIn(min = 80.dp),
            containerColor = ClaroColors.Green,
            icon = ClaroIconKind.PHONE,
        )
        ClaroSecondaryButton(
            label = "Cancel",
            onClick = onCancel,
        )
    }
}

@Composable
private fun ColumnScope.IncomingCallRoute(
    identity: CallerIdentity?,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
) {
    val displayName = identity?.displayName?.takeIf { it.isNotBlank() }
    val number = identity?.number?.takeIf { it.isNotBlank() }
    val callerName = when {
        identity == null -> "Unknown caller"
        identity.isPrivate -> "Private number"
        displayName != null -> displayName
        else -> "Unknown caller"
    }
    val callerNumber = when {
        identity == null -> null
        identity.isPrivate -> "Number withheld"
        number != null -> number
        else -> null
    }

    ClaroHeader(title = "Incoming call", centered = true)
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(ClaroDimensions.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        ClaroAvatar(
            name = identity?.displayName,
            size = 112.dp,
            isUnassigned = identity?.isPrivate == true || identity?.displayName.isNullOrBlank(),
        )
        Text(
            text = callerName,
            color = ClaroColors.Ink,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        callerNumber?.let { number ->
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = number,
                    color = ClaroColors.Muted,
                    fontSize = 23.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    ClaroActionDock {
        ClaroPrimaryButton(
            label = "Answer",
            onClick = onAnswer,
            modifier = Modifier.heightIn(min = 80.dp),
            containerColor = ClaroColors.Green,
            icon = ClaroIconKind.PHONE,
        )
        ClaroPrimaryButton(
            label = "Decline",
            onClick = onDecline,
            containerColor = ClaroColors.Red,
            icon = ClaroIconKind.END_CALL,
        )
    }
}

@Composable
private fun ColumnScope.ActiveCallRoute(
    state: CommunicationUiState,
    onDigit: (Char) -> Unit,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onAudioRoute: (AudioRoute) -> Unit,
    onToggleHold: () -> Unit,
    onSwap: () -> Unit,
) {
    val call = state.call
    val identity = call?.identity ?: state.pendingIdentity
    var showDtmfPad by rememberSaveable { mutableStateOf(false) }
    var dtmfTones by rememberSaveable { mutableStateOf("") }

    ClaroHeader(title = "Call", centered = true)
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(ClaroDimensions.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CenteredCallerBlock(
            identity = identity,
            avatarSizeDp = 96,
            fallbackTitle = "Call",
        )
        val phaseLabel = when (call?.phase) {
            CallPhase.DIALING -> "Dialing"
            CallPhase.RINGING -> "Ringing"
            CallPhase.CONNECTED -> "Connected"
            CallPhase.ON_HOLD -> "On hold"
            CallPhase.INCOMING -> "Incoming"
            CallPhase.DISCONNECTED -> "Disconnected"
            CallPhase.IDLE, null -> state.message ?: "Waiting for phone service"
        }
        val durationMillis = rememberConnectedDuration(call?.connectedAtElapsedMillis)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = phaseLabel,
                color = Color(0xFF55647B),
                fontSize = 23.sp,
                lineHeight = 28.sp,
                textAlign = TextAlign.Center,
            )
            if (
                durationMillis != null &&
                call?.phase in setOf(CallPhase.CONNECTED, CallPhase.ON_HOLD)
            ) {
                Text(
                    text = "·",
                    color = Color(0xFF55647B),
                    fontSize = 23.sp,
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = durationMillis.formatCallDuration(),
                        color = ClaroColors.Ink,
                        style = TextStyle(
                            fontSize = 32.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                        ),
                    )
                }
            }
        }

        if (call?.audioRoute == AudioRoute.BLUETOOTH) {
            ClaroInfoCard(text = "Bluetooth")
        }

        if (call != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
                ) {
                    CallControlTile(
                        label = "Speaker",
                        icon = ClaroIconKind.SPEAKER,
                        active = call.audioRoute == AudioRoute.SPEAKER,
                        enabled = call.capabilities.canChangeAudioRoute,
                        onClick = {
                            onAudioRoute(
                                if (call.audioRoute == AudioRoute.SPEAKER) {
                                    AudioRoute.EARPIECE
                                } else {
                                    AudioRoute.SPEAKER
                                },
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    CallControlTile(
                        label = "Mute",
                        icon = ClaroIconKind.MICROPHONE,
                        active = call.isMuted,
                        enabled = call.capabilities.canMute,
                        onClick = onToggleMute,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
                ) {
                    CallControlTile(
                        label = if (call.phase == CallPhase.ON_HOLD) "Resume" else "Hold",
                        icon = null,
                        active = call.phase == CallPhase.ON_HOLD,
                        enabled = call.capabilities.canHold,
                        onClick = onToggleHold,
                        modifier = Modifier.weight(1f),
                    )
                    CallControlTile(
                        label = "Keypad",
                        icon = ClaroIconKind.APPS,
                        active = showDtmfPad,
                        enabled = call.capabilities.canSendDtmf,
                        onClick = { showDtmfPad = !showDtmfPad },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (call.capabilities.canSwap) {
                    ClaroSecondaryButton(
                        label = "Swap calls",
                        onClick = onSwap,
                    )
                }
            }

            if (showDtmfPad && call.capabilities.canSendDtmf) {
                InCallDtmfPad(
                    tones = dtmfTones,
                    onDigit = { digit ->
                        dtmfTones = (dtmfTones + digit).takeLast(20)
                        onDigit(digit)
                    },
                )
            }
        }
    }
    ClaroActionDock {
        ClaroPrimaryButton(
            label = "End",
            onClick = onEnd,
            modifier = Modifier.heightIn(min = 76.dp),
            containerColor = ClaroColors.Red,
            icon = ClaroIconKind.END_CALL,
        )
    }
}

@Composable
private fun CallControlTile(
    label: String,
    icon: ClaroIconKind?,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (active) ClaroColors.InfoBlueBg else ClaroColors.Surface
    val border = if (active) ClaroColors.Blue else ClaroColors.Border
    val contentColor = when {
        !enabled -> ClaroColors.Muted
        active -> ClaroColors.Blue
        else -> ClaroColors.Ink
    }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 88.dp),
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(if (active) 2.dp else 1.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container,
            contentColor = contentColor,
            disabledContainerColor = ClaroColors.Surface.copy(alpha = 0.5f),
            disabledContentColor = ClaroColors.Muted,
        ),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            if (icon != null) {
                ClaroIcon(
                    kind = icon,
                    tint = contentColor,
                    size = 32.dp,
                )
            }
            Text(
                text = label,
                fontSize = 21.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun InCallDtmfPad(
    tones: String,
    onDigit: (Char) -> Unit,
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#'),
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ClaroColors.Charcoal)
                    .padding(ClaroDimensions.TileGap),
                verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
            ) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
                    ) {
                        row.forEach { digit ->
                            OutlinedButton(
                                onClick = { onDigit(digit) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 60.dp),
                                shape = RoundedCornerShape(1.dp),
                                border = BorderStroke(0.dp, Color.Transparent),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = ClaroColors.Surface,
                                    contentColor = ClaroColors.Ink,
                                ),
                                contentPadding = PaddingValues(2.dp),
                            ) {
                                Text(
                                    text = digit.toString(),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
            if (tones.isNotEmpty()) {
                Text(
                    text = tones,
                    color = ClaroColors.Muted,
                    fontSize = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.CallOutcomeRoute(
    state: CommunicationUiState,
    onRetryCall: () -> Unit,
    onMessageNumber: (String, String?) -> Unit,
    onRepairPhone: () -> Unit,
    onHome: () -> Unit,
) {
    val identity = state.call?.identity ?: state.pendingIdentity
    val canReturnCall = identity?.canReturnCall == true
    val title = when (state.route) {
        CommunicationRoute.MISSED_CALL -> "Missed call"
        CommunicationRoute.CALL_UNANSWERED -> "No answer"
        else -> "Call ended"
    }

    ClaroHeader(title = title, centered = true)
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(ClaroDimensions.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.route == CommunicationRoute.MISSED_CALL && !state.qualification.roleHeld) {
            ClaroInfoCard(
                text = "Phone access needs attention.",
                tone = ClaroBannerTone.WARNING,
            )
            if (state.qualification.telephonySupported) {
                ClaroSecondaryButton(
                    label = "Repair phone access",
                    onClick = onRepairPhone,
                )
            }
        }

        val displayName = identity?.displayName?.takeIf { it.isNotBlank() }
        val rawNumber = identity?.number?.takeIf { it.isNotBlank() }
        val callerName = when {
            identity == null -> "Unknown caller"
            identity.isPrivate -> "Private number"
            displayName != null -> displayName
            rawNumber != null -> rawNumber
            else -> "Unknown caller"
        }
        val numberLine = when {
            identity == null -> "Number withheld"
            !canReturnCall -> "Number withheld"
            else -> rawNumber
        }

        Spacer(modifier = Modifier.height(6.dp))
        ClaroAvatar(
            name = identity?.displayName,
            size = 112.dp,
            isUnassigned = identity?.isPrivate == true || identity?.displayName.isNullOrBlank(),
        )
        Text(
            text = callerName,
            color = ClaroColors.Ink,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (!numberLine.isNullOrBlank() && numberLine != callerName) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = numberLine,
                    color = ClaroColors.Muted,
                    fontSize = 23.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        val connectedDuration = if (state.route == CommunicationRoute.CALL_ENDED) {
            state.call?.connectedDurationMillis(SystemClock.elapsedRealtime())
        } else {
            null
        }
        if (state.route == CommunicationRoute.CALL_ENDED && connectedDuration != null) {
            val formatted = connectedDuration.formatCallDuration()
            Row(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Call duration $formatted"
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ClaroIcon(
                    kind = ClaroIconKind.CLOCK,
                    tint = Color(0xFF55647B),
                    size = 32.dp,
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = formatted,
                        color = ClaroColors.Ink,
                        style = TextStyle(
                            fontSize = 38.sp,
                            lineHeight = 42.sp,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                        ),
                    )
                }
            }
        } else {
            Text(
                text = when (state.route) {
                    CommunicationRoute.MISSED_CALL -> "Missed incoming call"
                    CommunicationRoute.CALL_UNANSWERED -> "No answer"
                    else -> when (state.call?.outcome) {
                        CallOutcome.DECLINED_INCOMING -> "Call declined"
                        CallOutcome.CANCELED_OUTGOING -> "Call canceled"
                        else -> "Call ended"
                    }
                },
                color = ClaroColors.Muted,
                fontSize = 21.sp,
                lineHeight = 27.sp,
                textAlign = TextAlign.Center,
            )
        }
    }

    ClaroActionDock {
        if (canReturnCall && identity != null) {
            val primaryLabel = when (state.route) {
                CommunicationRoute.MISSED_CALL -> "Call back"
                CommunicationRoute.CALL_UNANSWERED -> "Try again"
                else -> "Call again"
            }
            ClaroPrimaryButton(
                label = primaryLabel,
                onClick = onRetryCall,
                containerColor = ClaroColors.Green,
                icon = ClaroIconKind.PHONE,
            )
            ClaroSecondaryButton(
                label = "Message",
                onClick = { onMessageNumber(identity.number.orEmpty(), identity.displayName) },
                icon = ClaroIconKind.MESSAGES,
            )
            ClaroSecondaryButton(
                label = "Home",
                onClick = onHome,
            )
        } else {
            ClaroPrimaryButton(
                label = "Home",
                onClick = onHome,
                containerColor = ClaroColors.Blue,
            )
        }
    }
}

@Composable
private fun ColumnScope.CallFailedRoute(
    state: CommunicationUiState,
    onRetryCall: () -> Unit,
    onRepairPhone: () -> Unit,
    onEmergencyDialer: () -> Unit,
    onHome: () -> Unit,
) {
    val identity = state.pendingIdentity ?: state.call?.identity
    ClaroHeader(title = "Call not connected")
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(ClaroDimensions.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClaroInfoCard(
            text = state.message ?: "The call did not connect",
            tone = ClaroBannerTone.ERROR,
        )
        identity?.let { target ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ClaroColors.Surface, RoundedCornerShape(3.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ClaroAvatar(
                    name = target.displayName,
                    size = 48.dp,
                    isUnassigned = target.isPrivate || target.displayName.isNullOrBlank(),
                )
                Column(modifier = Modifier.weight(1f)) {
                    val targetNumber = target.number?.takeIf { it.isNotBlank() }
                    Text(
                        text = target.accessibleName,
                        color = ClaroColors.Ink,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (targetNumber != null && targetNumber != target.accessibleName) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = targetNumber,
                                color = ClaroColors.Muted,
                                fontSize = 19.sp,
                            )
                        }
                    }
                }
            }
        }
        ClaroEmergencyButton(
            label = "Emergency call",
            onClick = onEmergencyDialer,
        )
    }
    ClaroActionDock {
        if (state.qualification.needsRepair && state.qualification.telephonySupported) {
            ClaroPrimaryButton(
                label = "Repair phone access",
                onClick = onRepairPhone,
                containerColor = ClaroColors.Blue,
            )
        } else if (identity?.canReturnCall == true) {
            ClaroPrimaryButton(
                label = "Try again",
                onClick = onRetryCall,
                containerColor = ClaroColors.Blue,
            )
        }
        ClaroSecondaryButton(
            label = "Home",
            onClick = onHome,
        )
    }
}

@Composable
private fun CenteredCallerBlock(
    identity: CallerIdentity?,
    avatarSizeDp: Int,
    fallbackTitle: String,
) {
    val displayName = identity?.displayName?.takeIf { it.isNotBlank() }
    val number = identity?.number?.takeIf { it.isNotBlank() }
    val title = identity?.accessibleName?.ifBlank { fallbackTitle } ?: fallbackTitle
    val showNumberBelow = number != null && number != title
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ClaroAvatar(
            name = displayName,
            size = avatarSizeDp.dp,
            isUnassigned = identity?.isPrivate == true || displayName == null,
        )
        if (displayName == null && number != null) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = title,
                    color = ClaroColors.Ink,
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Text(
                text = title,
                color = ClaroColors.Ink,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        if (showNumberBelow) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = number,
                    color = ClaroColors.Muted,
                    fontSize = 23.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PhoneFieldLabel(label: String) {
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
private fun claroTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ClaroColors.Blue,
    unfocusedBorderColor = ClaroColors.FieldBorder,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedTextColor = ClaroColors.Ink,
    unfocusedTextColor = ClaroColors.Ink,
    cursorColor = ClaroColors.Blue,
)

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

private fun retryTargetCall(
    state: CommunicationUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onCallEnteredNumber: () -> Unit,
    onConfirmCall: () -> Unit,
) {
    if (state.pendingIdentity != null) {
        onConfirmCall()
        return
    }
    val fallbackNumber = state.call?.identity?.takeIf { it.canReturnCall }?.number ?: return
    repeat(state.digits.length) { onBackspace() }
    fallbackNumber.forEach { char ->
        if (char in "0123456789*#") onDigit(char)
    }
    onCallEnteredNumber()
}

private fun CallHistoryEntry.historySubtitle(): String {
    val status = when (outcome) {
        CallOutcome.MISSED_INCOMING -> "Missed"
        CallOutcome.UNANSWERED_OUTGOING -> "No answer"
        CallOutcome.DECLINED_INCOMING -> "Declined"
        CallOutcome.CANCELED_OUTGOING -> "Canceled"
        CallOutcome.FAILED -> "Not connected"
        CallOutcome.ENDED -> if (direction == CallDirection.INCOMING) "Incoming" else "Outgoing"
    }
    val timeText = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestampMillis))
    return "$status · $timeText"
}

private fun keypadLetters(digit: Char): String? = when (digit) {
    '2' -> "ABC"
    '3' -> "DEF"
    '4' -> "GHI"
    '5' -> "JKL"
    '6' -> "MNO"
    '7' -> "PQRS"
    '8' -> "TUV"
    '9' -> "WXYZ"
    '0' -> "+"
    else -> null
}
