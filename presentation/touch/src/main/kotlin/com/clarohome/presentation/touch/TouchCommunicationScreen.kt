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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import com.clarohome.core.design.LocalClaroHaptics
import com.clarohome.feature.communication.AssignmentStatus
import com.clarohome.feature.communication.AudioRoute
import com.clarohome.feature.communication.CallDirection
import com.clarohome.feature.communication.CallFailure
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
import com.clarohome.presentation.touch.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private enum class DialerTab {
    KEYPAD,
    RECENT,
    MISSED,
}

private enum class ContactEditorError {
    INVALID_PRIMARY,
    INVALID_SECONDARY,
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
    var editorError by rememberSaveable { mutableStateOf<ContactEditorError?>(null) }

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
                    errorMessage = editorError?.message(),
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
                                editorError = ContactEditorError.INVALID_PRIMARY
                            }
                            draftSecondaryNumber.isNotBlank() && normalizedSecondary.length < 3 -> {
                                editorError = ContactEditorError.INVALID_SECONDARY
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
                ClaroHeader(title = stringResource(R.string.touch_emergency_dialer_title))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(ClaroDimensions.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
                ) {
                    ClaroInfoCard(
                        text = state.message?.let { localizedPhoneMessage(it, state.failure) }
                            ?: stringResource(R.string.touch_emergency_handoff_message),
                    )
                }
                ClaroActionDock {
                    ClaroPrimaryButton(
                        label = stringResource(R.string.touch_action_home),
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

    ClaroHeader(title = stringResource(R.string.touch_tile_phone))
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
                    text = tab.label(),
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
                        stringResource(R.string.touch_calling_unavailable_device)
                    } else {
                        stringResource(R.string.touch_phone_access_attention)
                    },
                    tone = ClaroBannerTone.WARNING,
                )
                if (state.qualification.telephonySupported) {
                    ClaroSecondaryButton(
                        label = stringResource(R.string.touch_repair_phone_access),
                        onClick = onRepairPhone,
                    )
                }
            }
            state.message?.let { message ->
                ClaroInfoCard(text = localizedPhoneMessage(message, state.failure))
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
                label = stringResource(R.string.touch_emergency_call),
                onClick = onEmergencyDialer,
            )
        }
        ClaroActionDock {
            ClaroPrimaryButton(
                label = stringResource(R.string.touch_action_call),
                onClick = onCallEnteredNumber,
                containerColor = ClaroColors.Green,
                icon = ClaroIconKind.PHONE,
            )
            ClaroSecondaryButton(
                label = stringResource(R.string.touch_contacts),
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
                                stringResource(R.string.touch_calling_unavailable_device)
                            } else {
                                stringResource(R.string.touch_call_log_access_needed)
                            },
                            tone = ClaroBannerTone.WARNING,
                        )
                        if (state.qualification.telephonySupported) {
                            ClaroPrimaryButton(
                                label = stringResource(R.string.touch_repair_phone_access),
                                onClick = onRepairPhone,
                                containerColor = ClaroColors.Blue,
                            )
                        }
                    }
                }
            } else if (filteredHistory.isEmpty()) {
                item(key = "history-empty") {
                    ClaroInfoCard(text = stringResource(R.string.touch_no_calls_yet))
                }
            } else {
                itemsIndexed(filteredHistory, key = { _, entry -> entry.id }) { index, entry ->
                    val unread =
                        entry.outcome == CallOutcome.MISSED_INCOMING && !entry.isOpened
                    ClaroRowButton(
                        title = entry.identity.localizedCallerName(),
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
                        trailing = {
                            val (kind, tint, label) = entry.historyIcon()
                            ClaroIcon(
                                kind = kind,
                                tint = tint,
                                size = 34.dp,
                                contentDescription = label,
                            )
                        },
                        onClick = { onOpenHistoryEntry(entry.id) },
                    )
                }
            }
            item(key = "history-emergency") {
                ClaroEmergencyButton(
                    label = stringResource(R.string.touch_emergency_call),
                    onClick = onEmergencyDialer,
                )
            }
        }
        ClaroActionDock {
            ClaroSecondaryButton(
                label = stringResource(R.string.touch_contacts),
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
    val enterNumberLabel = stringResource(R.string.touch_enter_a_number)
    val numberDescription = if (isEmpty) {
        stringResource(R.string.touch_dial_number_empty_a11y)
    } else {
        stringResource(R.string.touch_dial_number_value_a11y, digits)
    }
    val backspaceDescription = stringResource(R.string.touch_backspace)
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
                    contentDescription = numberDescription
                },
            contentAlignment = Alignment.Center,
        ) {
            if (isEmpty) {
                Text(
                    text = enterNumberLabel,
                    color = ClaroColors.Muted,
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
            } else {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = digits,
                        color = ClaroColors.Ink,
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        val haptics = LocalClaroHaptics.current
        OutlinedButton(
            onClick = {
                haptics.keyClick()
                onBackspace()
            },
            enabled = !isEmpty,
            modifier = Modifier
                .size(64.dp)
                .semantics { contentDescription = backspaceDescription },
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
    val haptics = LocalClaroHaptics.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
    ) {
        listOf("1", "2").forEach { slot ->
            val assignment = state.assignments.firstOrNull { it.slot == slot }
            val nameLabel = assignment?.displayNameSnapshot?.ifBlank { "—" } ?: "—"
            val statusHint = when {
                assignment == null -> stringResource(R.string.touch_speed_dial_not_set, slot)
                assignment.status != AssignmentStatus.RESOLVED ->
                    stringResource(
                        R.string.touch_speed_dial_needs_repair,
                        slot,
                        assignment.displayNameSnapshot,
                    )
                else -> stringResource(
                    R.string.touch_speed_dial_hold_to_call,
                    slot,
                    assignment.displayNameSnapshot,
                )
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
                        hapticFeedbackEnabled = false,
                        onClick = {
                            haptics.keyClick()
                            onDigit(slot.single())
                        },
                        onLongClick = {
                            haptics.confirmHeavy()
                            onDirectCall(slot)
                        },
                    )
                    .semantics {
                        contentDescription = statusHint
                        role = Role.Button
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.touch_speed_dial_chip, slot, nameLabel),
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
    val haptics = LocalClaroHaptics.current
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
                        val keyDescription = when {
                            !assignedName.isNullOrBlank() ->
                                stringResource(
                                    R.string.touch_keypad_digit_hold_call,
                                    digit.toString(),
                                    assignedName,
                                )
                            speedDialSlot != null ->
                                stringResource(
                                    R.string.touch_keypad_digit_hold_speed_dial,
                                    digit.toString(),
                                )
                            else -> digit.toString()
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 74.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(ClaroColors.Surface)
                                .combinedClickable(
                                    role = Role.Button,
                                    hapticFeedbackEnabled = false,
                                    onClick = {
                                        haptics.keyClick()
                                        onDigit(digit)
                                    },
                                    onLongClick = speedDialSlot?.let { slot ->
                                        {
                                            haptics.confirmHeavy()
                                            onDirectCall(slot)
                                        }
                                    },
                                )
                                .semantics {
                                    contentDescription = keyDescription
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
    ClaroHeader(title = stringResource(R.string.touch_contacts))
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
        PhoneFieldLabel(stringResource(R.string.touch_search_name_or_number))
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
                text = stringResource(R.string.touch_contacts_phone_needs_repair),
                tone = ClaroBannerTone.WARNING,
            )
            if (state.qualification.telephonySupported) {
                ClaroSecondaryButton(
                    label = stringResource(R.string.touch_repair_phone_access),
                    onClick = onRepairPhone,
                )
            }
        }
        state.message?.let { message ->
            ClaroInfoCard(text = localizedPhoneMessage(message, state.failure))
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
                    text = if (state.contactQuery.isBlank()) {
                        stringResource(R.string.touch_no_contacts_yet)
                    } else {
                        stringResource(R.string.touch_no_matches)
                    },
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
                    label = stringResource(R.string.touch_load_more_contacts),
                    onClick = onLoadMore,
                )
            }
        }
        if (state.contactsLoading) {
            item(key = "contacts-loading") {
                ClaroInfoCard(text = stringResource(R.string.touch_loading_contacts))
            }
        }
    }
    ClaroActionDock {
        ClaroSecondaryButton(
            label = stringResource(R.string.touch_add_person),
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
    val contactTitleFallback = stringResource(R.string.touch_contact_title)

    if (contact == null) {
        ClaroHeader(title = contactTitleFallback)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(ClaroDimensions.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
        ) {
            ClaroInfoCard(
                text = state.message?.let { localizedPhoneMessage(it, state.failure) }
                    ?: stringResource(R.string.touch_contact_unavailable),
                tone = ClaroBannerTone.WARNING,
            )
        }
        ClaroActionDock {
            ClaroPrimaryButton(
                label = stringResource(R.string.touch_contacts),
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

    ClaroHeader(title = contact.displayName.ifBlank { contactTitleFallback })
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
            ClaroInfoCard(text = localizedPhoneMessage(message, state.failure))
        }

        if (confirmDelete && canManageContacts) {
            ClaroInfoCard(
                text = when {
                    isSosLinked ->
                        stringResource(R.string.touch_contact_delete_sos_blocked, contact.displayName)
                    isSpeedDialLinked ->
                        stringResource(
                            R.string.touch_contact_delete_confirm_speed_dial,
                            contact.displayName,
                        )
                    else ->
                        stringResource(
                            R.string.touch_contact_delete_confirm_favorites,
                            contact.displayName,
                        )
                },
                tone = if (isSosLinked) ClaroBannerTone.ERROR else ClaroBannerTone.WARNING,
            )
        } else {
            val multipleNumbers = contact.numbers.size > 1
            contact.numbers.forEach { number ->
                val numberLabel = number.localizedLabel()
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
                            text = numberLabel,
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
                            stringResource(
                                R.string.touch_call_labeled_number,
                                numberLabel,
                                number.value,
                            )
                        } else {
                            stringResource(R.string.touch_favorite_action_call, contact.displayName)
                        },
                        onClick = { onChooseNumber(number) },
                        containerColor = ClaroColors.Green,
                        icon = ClaroIconKind.PHONE,
                    )
                    ClaroSecondaryButton(
                        label = if (multipleNumbers) {
                            stringResource(R.string.touch_write_message_labeled, numberLabel)
                        } else {
                            stringResource(R.string.touch_write_message)
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
                label = stringResource(R.string.touch_action_cancel),
                onClick = { confirmDelete = false },
            )
            if (!isSosLinked) {
                ClaroPrimaryButton(
                    label = stringResource(R.string.touch_action_delete),
                    onClick = {
                        confirmDelete = false
                        onDeleteContact(contact.lookupKey)
                    },
                    containerColor = ClaroColors.Red,
                )
            }
        } else {
            ClaroSecondaryButton(
                label = stringResource(R.string.touch_action_edit),
                enabled = contact.canEdit,
                onClick = { onEditContact(contact) },
            )
            ClaroSecondaryButton(
                label = stringResource(R.string.touch_action_delete),
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
    ClaroHeader(
        title = if (isEdit) {
            stringResource(R.string.touch_edit_contact_title)
        } else {
            stringResource(R.string.touch_add_contact_title)
        },
    )
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
        PhoneFieldLabel(stringResource(R.string.touch_field_name))
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
        PhoneFieldLabel(stringResource(R.string.touch_field_mobile_number))
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
        PhoneFieldLabel(stringResource(R.string.touch_field_other_number_optional))
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
            label = stringResource(R.string.touch_action_save),
            onClick = onSave,
            containerColor = ClaroColors.Blue,
        )
        ClaroSecondaryButton(
            label = stringResource(R.string.touch_action_cancel),
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
    ClaroHeader(title = stringResource(R.string.touch_call_confirm_title), centered = true)
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
            fallbackTitle = stringResource(R.string.touch_unknown_number),
        )
        if (!state.qualification.canOwnCalls) {
            ClaroInfoCard(
                text = if (!state.qualification.telephonySupported) {
                    stringResource(R.string.touch_no_sim_calling_unavailable)
                } else {
                    stringResource(R.string.touch_phone_access_attention)
                },
                tone = ClaroBannerTone.WARNING,
            )
        }
    }
    ClaroActionDock {
        ClaroPrimaryButton(
            label = stringResource(R.string.touch_action_call),
            onClick = onConfirm,
            modifier = Modifier.heightIn(min = 80.dp),
            containerColor = ClaroColors.Green,
            icon = ClaroIconKind.PHONE,
        )
        ClaroSecondaryButton(
            label = stringResource(R.string.touch_action_cancel),
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
        identity == null -> stringResource(R.string.touch_caller_unknown)
        identity.isPrivate -> stringResource(R.string.touch_caller_private)
        displayName != null -> displayName
        else -> stringResource(R.string.touch_caller_unknown)
    }
    val isPrivate = identity?.isPrivate == true
    val callerNumber = when {
        identity == null -> null
        isPrivate -> stringResource(R.string.touch_number_withheld)
        number != null -> number
        else -> null
    }

    ClaroHeader(title = stringResource(R.string.touch_incoming_call_title), centered = true)
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
            isUnassigned = isPrivate || identity?.displayName.isNullOrBlank(),
        )
        Text(
            text = callerName,
            color = ClaroColors.Ink,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        callerNumber?.let { line ->
            if (isPrivate) {
                Text(
                    text = line,
                    color = ClaroColors.Muted,
                    fontSize = 23.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = line,
                        color = ClaroColors.Muted,
                        fontSize = 23.sp,
                        lineHeight = 28.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
    ClaroActionDock {
        ClaroPrimaryButton(
            label = stringResource(R.string.touch_action_answer),
            onClick = onAnswer,
            modifier = Modifier.heightIn(min = 80.dp),
            containerColor = ClaroColors.Green,
            icon = ClaroIconKind.PHONE,
        )
        ClaroPrimaryButton(
            label = stringResource(R.string.touch_action_decline),
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
    val activeCallTitle = stringResource(R.string.touch_active_call_title)

    ClaroHeader(title = activeCallTitle, centered = true)
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
            fallbackTitle = activeCallTitle,
        )
        val phaseLabel = when (call?.phase) {
            CallPhase.DIALING -> stringResource(R.string.touch_call_phase_dialing)
            CallPhase.RINGING -> stringResource(R.string.touch_call_phase_ringing)
            CallPhase.CONNECTED -> stringResource(R.string.touch_call_phase_connected)
            CallPhase.ON_HOLD -> stringResource(R.string.touch_call_phase_on_hold)
            CallPhase.INCOMING -> stringResource(R.string.touch_call_phase_incoming)
            CallPhase.DISCONNECTED -> stringResource(R.string.touch_call_phase_disconnected)
            CallPhase.IDLE, null -> state.message?.let { localizedPhoneMessage(it, state.failure) }
                ?: stringResource(R.string.touch_call_phase_waiting_service)
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
            ClaroInfoCard(text = stringResource(R.string.touch_audio_bluetooth))
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
                        label = stringResource(R.string.touch_call_control_speaker),
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
                        label = stringResource(R.string.touch_call_control_mute),
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
                        label = if (call.phase == CallPhase.ON_HOLD) {
                            stringResource(R.string.touch_call_control_resume)
                        } else {
                            stringResource(R.string.touch_call_control_hold)
                        },
                        icon = null,
                        active = call.phase == CallPhase.ON_HOLD,
                        enabled = call.capabilities.canHold,
                        onClick = onToggleHold,
                        modifier = Modifier.weight(1f),
                    )
                    CallControlTile(
                        label = stringResource(R.string.touch_tab_keypad),
                        icon = ClaroIconKind.APPS,
                        active = showDtmfPad,
                        enabled = call.capabilities.canSendDtmf,
                        onClick = { showDtmfPad = !showDtmfPad },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (call.capabilities.canSwap) {
                    ClaroSecondaryButton(
                        label = stringResource(R.string.touch_call_control_swap),
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
            label = stringResource(R.string.touch_action_end_call),
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
        CommunicationRoute.MISSED_CALL -> stringResource(R.string.touch_outcome_missed_call_title)
        CommunicationRoute.CALL_UNANSWERED -> stringResource(R.string.touch_history_status_no_answer)
        else -> stringResource(R.string.touch_outcome_call_ended)
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
                text = stringResource(R.string.touch_phone_access_attention),
                tone = ClaroBannerTone.WARNING,
            )
            if (state.qualification.telephonySupported) {
                ClaroSecondaryButton(
                    label = stringResource(R.string.touch_repair_phone_access),
                    onClick = onRepairPhone,
                )
            }
        }

        val displayName = identity?.displayName?.takeIf { it.isNotBlank() }
        val rawNumber = identity?.number?.takeIf { it.isNotBlank() }
        val callerName = when {
            identity == null -> stringResource(R.string.touch_caller_unknown)
            identity.isPrivate -> stringResource(R.string.touch_caller_private)
            displayName != null -> displayName
            rawNumber != null -> rawNumber
            else -> stringResource(R.string.touch_caller_unknown)
        }
        val withheld = identity == null || !canReturnCall
        val numberLine = if (withheld) {
            stringResource(R.string.touch_number_withheld)
        } else {
            rawNumber
        }

        Spacer(modifier = Modifier.height(6.dp))
        ClaroAvatar(
            name = identity?.displayName,
            size = 112.dp,
            isUnassigned = identity?.isPrivate == true || identity?.displayName.isNullOrBlank(),
        )
        if (displayName == null && rawNumber != null && callerName == rawNumber) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = callerName,
                    color = ClaroColors.Ink,
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Text(
                text = callerName,
                color = ClaroColors.Ink,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        if (!numberLine.isNullOrBlank() && numberLine != callerName) {
            if (withheld) {
                Text(
                    text = numberLine,
                    color = ClaroColors.Muted,
                    fontSize = 23.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
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
        }

        val connectedDuration = if (state.route == CommunicationRoute.CALL_ENDED) {
            state.call?.connectedDurationMillis(SystemClock.elapsedRealtime())
        } else {
            null
        }
        if (state.route == CommunicationRoute.CALL_ENDED && connectedDuration != null) {
            val formatted = connectedDuration.formatCallDuration()
            val durationDescription = stringResource(R.string.touch_call_duration_a11y, formatted)
            Row(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = durationDescription
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
                    CommunicationRoute.MISSED_CALL ->
                        stringResource(R.string.touch_outcome_missed_incoming)
                    CommunicationRoute.CALL_UNANSWERED ->
                        stringResource(R.string.touch_history_status_no_answer)
                    else -> when (state.call?.outcome) {
                        CallOutcome.DECLINED_INCOMING ->
                            stringResource(R.string.touch_outcome_call_declined)
                        CallOutcome.CANCELED_OUTGOING ->
                            stringResource(R.string.touch_outcome_call_canceled)
                        else -> stringResource(R.string.touch_outcome_call_ended)
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
        if (canReturnCall) {
            val primaryLabel = when (state.route) {
                CommunicationRoute.MISSED_CALL -> stringResource(R.string.touch_action_call_back)
                CommunicationRoute.CALL_UNANSWERED -> stringResource(R.string.touch_action_try_again)
                else -> stringResource(R.string.touch_action_call_again)
            }
            ClaroPrimaryButton(
                label = primaryLabel,
                onClick = onRetryCall,
                containerColor = ClaroColors.Green,
                icon = ClaroIconKind.PHONE,
            )
            ClaroSecondaryButton(
                label = stringResource(R.string.touch_action_message),
                onClick = { onMessageNumber(identity?.number.orEmpty(), identity?.displayName) },
                icon = ClaroIconKind.MESSAGES,
            )
            ClaroSecondaryButton(
                label = stringResource(R.string.touch_action_home),
                onClick = onHome,
            )
        } else {
            ClaroPrimaryButton(
                label = stringResource(R.string.touch_action_home),
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
    ClaroHeader(title = stringResource(R.string.touch_call_not_connected_title))
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(ClaroDimensions.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClaroInfoCard(
            text = state.message?.let { localizedPhoneMessage(it, state.failure) }
                ?: stringResource(R.string.touch_call_did_not_connect),
            tone = ClaroBannerTone.ERROR,
        )
        identity?.let { target ->
            val targetTitle = target.localizedCallerName()
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
                    if (!target.isPrivate && target.displayName.isNullOrBlank() && targetNumber != null) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = targetTitle,
                                color = ClaroColors.Ink,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        Text(
                            text = targetTitle,
                            color = ClaroColors.Ink,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (!target.isPrivate && targetNumber != null && targetNumber != targetTitle) {
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
            label = stringResource(R.string.touch_emergency_call),
            onClick = onEmergencyDialer,
        )
    }
    ClaroActionDock {
        if (state.qualification.needsRepair && state.qualification.telephonySupported) {
            ClaroPrimaryButton(
                label = stringResource(R.string.touch_repair_phone_access),
                onClick = onRepairPhone,
                containerColor = ClaroColors.Blue,
            )
        } else if (identity?.canReturnCall == true) {
            ClaroPrimaryButton(
                label = stringResource(R.string.touch_action_try_again),
                onClick = onRetryCall,
                containerColor = ClaroColors.Blue,
            )
        }
        ClaroSecondaryButton(
            label = stringResource(R.string.touch_action_home),
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
    val title = identity?.localizedCallerName(fallbackTitle)?.ifBlank { fallbackTitle } ?: fallbackTitle
    val showNumberBelow = identity?.isPrivate != true && number != null && number != title
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
        if (identity?.isPrivate != true && displayName == null && number != null) {
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

@Composable
private fun DialerTab.label(): String = when (this) {
    DialerTab.KEYPAD -> stringResource(R.string.touch_tab_keypad)
    DialerTab.RECENT -> stringResource(R.string.touch_tab_recent)
    DialerTab.MISSED -> stringResource(R.string.touch_tab_missed)
}

@Composable
private fun ContactEditorError.message(): String = when (this) {
    ContactEditorError.INVALID_PRIMARY -> stringResource(R.string.touch_contact_error_primary)
    ContactEditorError.INVALID_SECONDARY -> stringResource(R.string.touch_contact_error_secondary)
}

@Composable
internal fun ContactNumber.localizedLabel(): String = when (label.trim().lowercase(Locale.ROOT)) {
    "mobile" -> stringResource(R.string.touch_number_label_mobile)
    "home" -> stringResource(R.string.touch_number_label_home)
    "work" -> stringResource(R.string.touch_number_label_work)
    "other" -> stringResource(R.string.touch_number_label_other)
    else -> label
}

@Composable
private fun CallerIdentity.localizedCallerName(
    fallback: String = stringResource(R.string.touch_caller_unknown),
): String = when {
    isPrivate -> stringResource(R.string.touch_caller_private)
    !displayName.isNullOrBlank() -> displayName.orEmpty()
    !number.isNullOrBlank() -> "\u2066${number.orEmpty()}\u2069"
    else -> fallback
}

@Composable
private fun localizedPhoneMessage(message: String, failure: CallFailure?): String {
    when {
        message == "Enter a phone number" ->
            return stringResource(R.string.touch_phone_err_enter_number)
        message == "Enter a valid phone number" ->
            return stringResource(R.string.touch_phone_err_valid_number)
        message == "This caller cannot be called back" ->
            return stringResource(R.string.touch_phone_err_cannot_call_back)
        message == "Call canceled" ->
            return stringResource(R.string.touch_outcome_call_canceled)
        message == "Waiting for phone service" ->
            return stringResource(R.string.touch_call_phase_waiting_service)
        message == "Continue in the system emergency dialer" ||
            message == "Continue in the system emergency dialer." ->
            return stringResource(R.string.touch_emergency_handoff_message)
        message == "Contact is no longer available" ->
            return stringResource(R.string.touch_contact_unavailable)
        message.startsWith("Hold ") && message.endsWith(" is not set") -> {
            val slot = message.removePrefix("Hold ").removeSuffix(" is not set")
            return stringResource(R.string.touch_phone_hold_slot_not_set, slot)
        }
        message.startsWith("Ask your caregiver to repair Hold ") -> {
            val slot = message.removePrefix("Ask your caregiver to repair Hold ")
            return stringResource(R.string.touch_phone_hold_slot_repair, slot)
        }
        message.startsWith("Starting call to ") -> {
            val target = message.removePrefix("Starting call to ")
            val localizedTarget = when (target) {
                "Private number" -> stringResource(R.string.touch_caller_private)
                "Unknown caller" -> stringResource(R.string.touch_caller_unknown)
                else -> target
            }
            return stringResource(R.string.touch_phone_starting_call, localizedTarget)
        }
    }
    return when (failure) {
        CallFailure.INVALID_NUMBER -> stringResource(R.string.touch_phone_err_valid_number)
        CallFailure.NO_SIM -> stringResource(R.string.touch_phone_err_no_sim)
        CallFailure.NO_SERVICE -> stringResource(R.string.touch_phone_err_no_service)
        CallFailure.ROLE_LOST -> stringResource(R.string.touch_phone_err_role_lost)
        CallFailure.PERMISSION_DENIED -> stringResource(R.string.touch_phone_err_permission_denied)
        CallFailure.ACCOUNT_UNAVAILABLE -> stringResource(R.string.touch_phone_err_account_unavailable)
        CallFailure.NETWORK_FAILURE -> stringResource(R.string.touch_phone_err_network_failure)
        CallFailure.UNKNOWN -> stringResource(R.string.touch_call_did_not_connect)
        null -> message
    }
}

@Composable
private fun CallHistoryEntry.historyIcon(): Triple<ClaroIconKind, Color, String> = when {
    outcome == CallOutcome.MISSED_INCOMING -> Triple(
        ClaroIconKind.CALL_MISSED,
        ClaroColors.Red,
        stringResource(R.string.touch_history_status_missed),
    )
    direction == CallDirection.INCOMING -> Triple(
        ClaroIconKind.CALL_INCOMING,
        ClaroColors.Green,
        stringResource(R.string.touch_history_status_incoming),
    )
    else -> Triple(
        ClaroIconKind.CALL_OUTGOING,
        ClaroColors.Blue,
        stringResource(R.string.touch_history_status_outgoing),
    )
}

@Composable
private fun CallHistoryEntry.historySubtitle(): String {
    val status = when (outcome) {
        CallOutcome.UNANSWERED_OUTGOING -> stringResource(R.string.touch_history_status_no_answer)
        CallOutcome.DECLINED_INCOMING -> stringResource(R.string.touch_history_status_declined)
        CallOutcome.CANCELED_OUTGOING -> stringResource(R.string.touch_history_status_canceled)
        CallOutcome.FAILED -> stringResource(R.string.touch_history_status_not_connected)
        CallOutcome.MISSED_INCOMING, CallOutcome.ENDED -> null
    }
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.ENGLISH
    val timeText = DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(timestampMillis))
    return if (status == null) timeText else stringResource(R.string.touch_history_subtitle, status, timeText)
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
