package com.clarohome.presentation.classic

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarohome.core.design.ClaroAvatar
import com.clarohome.core.design.ClaroBannerTone
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroEmergencyButton
import com.clarohome.core.design.ClaroIcon
import com.clarohome.core.design.ClaroIconKind
import com.clarohome.core.design.ClaroInfoCard
import com.clarohome.core.model.BatteryStatus
import com.clarohome.core.model.CellularStatus
import com.clarohome.feature.communication.AudioRoute
import com.clarohome.feature.communication.CallOutcome
import com.clarohome.feature.communication.CallPhase
import com.clarohome.feature.communication.CallerIdentity
import com.clarohome.feature.communication.CommunicationRoute
import com.clarohome.feature.communication.CommunicationUiState
import com.clarohome.feature.communication.ContactNumber
import com.clarohome.feature.communication.formatCallDuration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay

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
    onOpenHistoryEntry: (String) -> Unit = {},
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
    battery: BatteryStatus? = null,
    cellular: CellularStatus? = null,
    onEmergencyDialer: () -> Unit = {},
    onCallIdentity: ((CallerIdentity) -> Unit)? = null,
) {
    val displayScrollState = rememberScrollState()
    var optionsOpen by rememberSaveable(state.route) { mutableStateOf(false) }
    var dtmfFeedback by rememberSaveable(state.call?.id, state.route) { mutableStateOf("") }
    var contactsNumericMode by rememberSaveable(state.route) { mutableStateOf(false) }
    var contactsMultiTap by remember(state.route) { mutableStateOf<ClassicMultiTapState?>(null) }

    val resolvedIdentity = state.call?.identity ?: state.pendingIdentity
    val callbackAction: (() -> Unit)? = resolvedIdentity
        ?.takeIf { it.canReturnCall }
        ?.let { target ->
            {
                when {
                    onCallIdentity != null -> onCallIdentity(target)
                    state.pendingIdentity?.canReturnCall == true -> onConfirmCall()
                    else -> {
                        repeat(state.digits.length) { onBackspace() }
                        target.number.orEmpty()
                            .filter { it in "0123456789*#" }
                            .forEach(onDigit)
                        onCallEnteredNumber()
                    }
                }
            }
        }

    val options = buildClassicCommunicationOptions(
        state = state,
        callbackAction = callbackAction,
        onBackspace = onBackspace,
        onOpenContacts = onOpenContacts,
        onSearchContacts = {
            contactsMultiTap = null
            onSearchContacts(it)
        },
        onToggleMute = onToggleMute,
        onAudioRoute = onAudioRoute,
        onToggleHold = onToggleHold,
        onSwap = onSwap,
        onRepairPhone = onRepairPhone,
        onEmergencyDialer = onEmergencyDialer,
    )

    val backAction: (() -> Unit)? = when {
        optionsOpen -> {
            { optionsOpen = false }
        }
        state.route == CommunicationRoute.INCOMING_CALL -> null
        state.route == CommunicationRoute.DIALER && state.digits.isNotEmpty() -> onBackspace
        state.route == CommunicationRoute.CALL_CONFIRMATION -> onCancelCall
        state.route == CommunicationRoute.CONTACT_DETAIL -> onOpenContacts
        else -> onHome
    }

    val greenAction: (() -> Unit)? = if (optionsOpen) {
        null
    } else {
        when (state.route) {
            CommunicationRoute.INCOMING_CALL -> onAnswer
            CommunicationRoute.CALL_CONFIRMATION -> onConfirmCall
            CommunicationRoute.DIALER -> onCallEnteredNumber
            CommunicationRoute.CONTACT_DETAIL ->
                state.selectedContact?.numbers?.firstOrNull()?.let { firstNumber ->
                    { onChooseNumber(firstNumber) }
                }
            CommunicationRoute.CALL_ENDED,
            CommunicationRoute.MISSED_CALL,
            CommunicationRoute.CALL_UNANSWERED,
            CommunicationRoute.CALL_FAILED,
            -> callbackAction
            CommunicationRoute.CONTACTS,
            CommunicationRoute.ACTIVE_CALL,
            CommunicationRoute.EXTERNAL_HANDOFF,
            -> null
        }
    }

    val redAction: () -> Unit = when (state.route) {
        CommunicationRoute.INCOMING_CALL -> onDecline
        CommunicationRoute.ACTIVE_CALL -> onEnd
        CommunicationRoute.CALL_CONFIRMATION -> onCancelCall
        else -> {
            {
                optionsOpen = false
                onHome()
            }
        }
    }

    val keypadAction: ((Char) -> Unit)? = when {
        optionsOpen -> null
        state.route == CommunicationRoute.DIALER -> onDigit
        state.route == CommunicationRoute.ACTIVE_CALL &&
            state.call?.capabilities?.canSendDtmf == true -> { digit ->
            dtmfFeedback = (dtmfFeedback + digit).takeLast(12)
            onDigit(digit)
        }
        state.route == CommunicationRoute.CONTACTS -> { digit ->
            if (digit == '#') {
                contactsNumericMode = !contactsNumericMode
                contactsMultiTap = null
            } else {
                val (updated, nextTap) = applyClassicKeypadInput(
                    currentValue = state.contactQuery,
                    digit = digit,
                    fieldKey = "contact-search",
                    numericMode = contactsNumericMode,
                    previousTap = contactsMultiTap,
                )
                contactsMultiTap = nextTap
                onSearchContacts(updated)
            }
        }
        else -> null
    }

    val speedDialAssignments = if (state.route == CommunicationRoute.DIALER && !optionsOpen) {
        state.assignments
            .filter { it.slot == "1" || it.slot == "2" }
            .associate { it.slot.first() to it.displayNameSnapshot }
    } else {
        emptyMap()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("classic-communication")
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
                title = if (optionsOpen) "Options" else classicTitle(state.route),
                centered = !optionsOpen && isCenteredHeaderRoute(state.route),
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
                    ClassicCommunicationDisplay(
                        state = state,
                        resolvedIdentity = resolvedIdentity,
                        onOpenContacts = onOpenContacts,
                        onSearchContacts = {
                            contactsMultiTap = null
                            onSearchContacts(it)
                        },
                        onLoadMoreContacts = onLoadMoreContacts,
                        onOpenContact = onOpenContact,
                        onOpenHistoryEntry = onOpenHistoryEntry,
                        onChooseNumber = onChooseNumber,
                        onToggleMute = onToggleMute,
                        onAudioRoute = onAudioRoute,
                        onToggleHold = onToggleHold,
                        onSwap = onSwap,
                        onRepairPhone = onRepairPhone,
                        onEmergencyDialer = onEmergencyDialer,
                    )
                }
            }
        }
        ClassicIntegratedPad(
            menuLabel = "Options",
            greenLabel = when (state.route) {
                CommunicationRoute.INCOMING_CALL -> "☎ Answer"
                else -> "☎ Call"
            },
            redLabel = when (state.route) {
                CommunicationRoute.INCOMING_CALL -> "☎ Decline"
                CommunicationRoute.ACTIVE_CALL -> "End"
                else -> "⌂ Home"
            },
            onBack = backAction,
            onMenu = if (options.isNotEmpty() || optionsOpen) {
                { optionsOpen = !optionsOpen }
            } else {
                null
            },
            onGreen = greenAction,
            onRed = redAction,
            scrollState = displayScrollState,
        )
        ClassicModeStatusStrip(
            modeLabel = if (state.route == CommunicationRoute.CONTACTS && !optionsOpen) {
                if (contactsNumericMode) "Numbers" else "Letters"
            } else {
                null
            },
            feedback = dtmfFeedback.takeIf {
                state.route == CommunicationRoute.ACTIVE_CALL && it.isNotEmpty()
            },
        )
        ClassicNumericKeypad(
            onDigitSelected = keypadAction,
            assignments = speedDialAssignments,
            onLongDigitSelected = if (state.route == CommunicationRoute.DIALER && !optionsOpen) {
                { digit ->
                    if (digit == '1' || digit == '2') {
                        onDirectCall(digit.toString())
                    } else {
                        onDigit(digit)
                    }
                }
            } else {
                null
            },
        )
    }
}

private fun buildClassicCommunicationOptions(
    state: CommunicationUiState,
    callbackAction: (() -> Unit)?,
    onBackspace: () -> Unit,
    onOpenContacts: () -> Unit,
    onSearchContacts: (String) -> Unit,
    onToggleMute: () -> Unit,
    onAudioRoute: (AudioRoute) -> Unit,
    onToggleHold: () -> Unit,
    onSwap: () -> Unit,
    onRepairPhone: () -> Unit,
    onEmergencyDialer: () -> Unit,
): List<ClassicOptionAction> = buildList {
    when (state.route) {
        CommunicationRoute.DIALER -> {
            add(
                ClassicOptionAction(
                    label = "Delete character",
                    enabled = state.digits.isNotEmpty(),
                    onSelect = onBackspace,
                ),
            )
            add(
                ClassicOptionAction(
                    label = "Clear text",
                    enabled = state.digits.isNotEmpty(),
                    onSelect = { repeat(state.digits.length) { onBackspace() } },
                ),
            )
            add(ClassicOptionAction(label = "Contacts", onSelect = onOpenContacts))
            if (state.qualification.needsRepair && state.qualification.telephonySupported) {
                add(ClassicOptionAction(label = "Repair phone access", onSelect = onRepairPhone))
            }
            add(ClassicOptionAction(label = "Emergency call", onSelect = onEmergencyDialer))
        }

        CommunicationRoute.CONTACTS -> {
            add(
                ClassicOptionAction(
                    label = "Delete character",
                    enabled = state.contactQuery.isNotEmpty(),
                    onSelect = { onSearchContacts(state.contactQuery.dropLast(1)) },
                ),
            )
            add(
                ClassicOptionAction(
                    label = "Clear text",
                    enabled = state.contactQuery.isNotEmpty(),
                    onSelect = { onSearchContacts("") },
                ),
            )
            if (state.qualification.needsRepair && state.qualification.telephonySupported) {
                add(ClassicOptionAction(label = "Repair phone access", onSelect = onRepairPhone))
            }
            add(ClassicOptionAction(label = "Emergency call", onSelect = onEmergencyDialer))
        }

        CommunicationRoute.CONTACT_DETAIL -> {
            add(ClassicOptionAction(label = "Contacts", onSelect = onOpenContacts))
            add(ClassicOptionAction(label = "Emergency call", onSelect = onEmergencyDialer))
        }

        CommunicationRoute.ACTIVE_CALL -> {
            val call = state.call
            val speakerActive = call?.audioRoute == AudioRoute.SPEAKER
            add(
                ClassicOptionAction(
                    label = if (speakerActive) "Earpiece" else "Speaker",
                    enabled = call?.capabilities?.canChangeAudioRoute == true,
                    onSelect = {
                        onAudioRoute(
                            if (speakerActive) AudioRoute.EARPIECE else AudioRoute.SPEAKER,
                        )
                    },
                ),
            )
            add(
                ClassicOptionAction(
                    label = if (call?.isMuted == true) "Unmute" else "Mute",
                    enabled = call?.capabilities?.canMute == true,
                    onSelect = onToggleMute,
                ),
            )
            add(
                ClassicOptionAction(
                    label = if (call?.phase == CallPhase.ON_HOLD) "Resume" else "Hold",
                    enabled = call?.capabilities?.canHold == true,
                    onSelect = onToggleHold,
                ),
            )
            add(
                ClassicOptionAction(
                    label = "Swap",
                    enabled = call?.capabilities?.canSwap == true,
                    onSelect = onSwap,
                ),
            )
        }

        CommunicationRoute.MISSED_CALL,
        CommunicationRoute.CALL_UNANSWERED,
        CommunicationRoute.CALL_ENDED,
        CommunicationRoute.CALL_FAILED,
        -> {
            if (callbackAction != null) {
                add(ClassicOptionAction(label = "Call back", onSelect = callbackAction))
            }
            add(ClassicOptionAction(label = "Contacts", onSelect = onOpenContacts))
            if (state.qualification.needsRepair && state.qualification.telephonySupported) {
                add(ClassicOptionAction(label = "Repair phone access", onSelect = onRepairPhone))
            }
            add(ClassicOptionAction(label = "Emergency call", onSelect = onEmergencyDialer))
        }

        CommunicationRoute.CALL_CONFIRMATION -> {
            add(ClassicOptionAction(label = "Contacts", onSelect = onOpenContacts))
            add(ClassicOptionAction(label = "Emergency call", onSelect = onEmergencyDialer))
        }

        CommunicationRoute.INCOMING_CALL,
        CommunicationRoute.EXTERNAL_HANDOFF,
        -> Unit
    }
}

@Composable
private fun ClassicCommunicationDisplay(
    state: CommunicationUiState,
    resolvedIdentity: CallerIdentity?,
    onOpenContacts: () -> Unit,
    onSearchContacts: (String) -> Unit,
    onLoadMoreContacts: () -> Unit,
    onOpenContact: (String) -> Unit,
    onOpenHistoryEntry: (String) -> Unit,
    onChooseNumber: (ContactNumber) -> Unit,
    onToggleMute: () -> Unit,
    onAudioRoute: (AudioRoute) -> Unit,
    onToggleHold: () -> Unit,
    onSwap: () -> Unit,
    onRepairPhone: () -> Unit,
    onEmergencyDialer: () -> Unit,
) {
    when (state.route) {
        CommunicationRoute.DIALER -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ClassicLtrText(
                    text = state.digits.ifBlank { "Enter a number" },
                    color = ClassicInk,
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 10.dp)
                        .semantics {
                            contentDescription = "Phone number ${state.digits.ifBlank { "empty" }}"
                        },
                )
                if (!state.qualification.canOwnCalls) {
                    ClaroInfoCard(
                        text = if (!state.qualification.telephonySupported) {
                            "Calling is not available on this device."
                        } else {
                            "Phone access needs attention."
                        },
                        tone = ClaroBannerTone.WARNING,
                    )
                    if (state.qualification.telephonySupported) {
                        ClassicLightButton(
                            label = "Repair phone access",
                            onClick = onRepairPhone,
                            color = ClaroColors.Blue,
                            textColor = Color.White,
                            borderColor = ClaroColors.Blue,
                        )
                    }
                }
                state.history.take(5).forEachIndexed { index, entry ->
                    val isMissed = entry.outcome == CallOutcome.MISSED_INCOMING
                    val timeLabel = formatCallHistoryTimestamp(entry.timestampMillis)
                    ClassicRowItem(
                        title = entry.identity.accessibleName,
                        subtitle = if (isMissed) {
                            "Missed · $timeLabel"
                        } else {
                            "Recent · $timeLabel"
                        },
                        unreadDot = isMissed && !entry.isOpened,
                        onClick = { onOpenHistoryEntry(entry.id) },
                        leading = {
                            ClaroAvatar(
                                name = entry.identity.displayName?.takeIf { !entry.identity.isPrivate },
                                size = 44.dp,
                                paletteSeed = index,
                                isUnassigned = entry.identity.isPrivate ||
                                    entry.identity.displayName.isNullOrBlank(),
                            )
                        },
                    )
                }
                ClassicRowItem(
                    title = "Contacts",
                    onClick = onOpenContacts,
                    leading = {
                        ClaroIcon(
                            kind = ClaroIconKind.CONTACT,
                            tint = ClaroColors.Blue,
                            size = 32.dp,
                        )
                    },
                )
                ClaroEmergencyButton(
                    label = "Emergency call",
                    onClick = onEmergencyDialer,
                )
                state.message?.takeIf { it.isNotBlank() }?.let { msg ->
                    ClaroInfoCard(text = msg, tone = ClaroBannerTone.INFO)
                }
            }
        }

        CommunicationRoute.CONTACTS -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Search name or number",
                    color = ClassicInk,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
                OutlinedTextField(
                    value = state.contactQuery,
                    onValueChange = onSearchContacts,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 23.sp),
                    shape = RectangleShape,
                )
                if (!state.qualification.roleHeld && state.qualification.telephonySupported) {
                    ClassicLightButton(
                        label = "Repair phone access",
                        onClick = onRepairPhone,
                        color = ClaroColors.WarningBg,
                        textColor = ClaroColors.WarningInk,
                        borderColor = ClaroColors.WarningBorder,
                    )
                }
                if (state.contactsLoading) {
                    ClaroInfoCard(text = "Loading contacts…", tone = ClaroBannerTone.INFO)
                } else if (state.contacts.isEmpty()) {
                    ClaroInfoCard(
                        text = if (state.contactQuery.isBlank()) {
                            "No contacts yet."
                        } else {
                            "No matches."
                        },
                        tone = ClaroBannerTone.INFO,
                    )
                }
                state.contacts.forEachIndexed { index, contact ->
                    ClassicRowItem(
                        title = contact.displayName,
                        subtitle = contact.numbers.firstOrNull()?.value,
                        onClick = { onOpenContact(contact.lookupKey) },
                        leading = {
                            ClaroAvatar(
                                name = contact.displayName,
                                size = 44.dp,
                                paletteSeed = index,
                            )
                        },
                    )
                }
                if (state.contactsNextOffset != null && !state.contactsLoading) {
                    ClassicLightButton(
                        label = "More contacts",
                        onClick = onLoadMoreContacts,
                    )
                }
            }
        }

        CommunicationRoute.CONTACT_DETAIL -> {
            val contact = state.selectedContact
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (contact == null) {
                    ClaroInfoCard(
                        text = state.message ?: "Contact is no longer available",
                        tone = ClaroBannerTone.WARNING,
                    )
                    ClassicLightButton(label = "Contacts", onClick = onOpenContacts)
                } else {
                    ClaroAvatar(name = contact.displayName, size = 72.dp)
                    DisplayName(contact.displayName)
                    contact.numbers.forEach { number ->
                        ClassicRowItem(
                            title = number.value,
                            subtitle = number.label,
                            onClick = { onChooseNumber(number) },
                            leading = {
                                ClaroIcon(
                                    kind = ClaroIconKind.PHONE,
                                    tint = ClaroColors.Green,
                                    size = 28.dp,
                                )
                            },
                        )
                    }
                }
            }
        }

        CommunicationRoute.CALL_CONFIRMATION -> {
            val identity = state.pendingIdentity
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ClaroAvatar(
                    name = identity?.displayName?.takeIf { !identity.isPrivate },
                    size = 72.dp,
                    isUnassigned = identity == null ||
                        identity.isPrivate ||
                        identity.displayName.isNullOrBlank(),
                )
                DisplayName(identity?.accessibleName ?: "Unknown caller")
                DisplayNumber(formatDisplayNumber(identity))
                DisplayHint("Press Call to confirm")
            }
        }

        CommunicationRoute.INCOMING_CALL -> {
            val identity = state.call?.identity ?: state.pendingIdentity
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ClaroAvatar(
                    name = identity?.displayName?.takeIf { !identity.isPrivate },
                    size = 72.dp,
                    isUnassigned = identity == null ||
                        identity.isPrivate ||
                        identity.displayName.isNullOrBlank(),
                )
                DisplayName(identity?.accessibleName ?: "Unknown caller")
                DisplayNumber(formatDisplayNumber(identity))
                DisplayHint("Answer with the green key")
            }
        }

        CommunicationRoute.ACTIVE_CALL -> {
            val call = state.call
            var nowElapsed by remember(call?.connectedAtElapsedMillis) {
                mutableLongStateOf(SystemClock.elapsedRealtime())
            }
            LaunchedEffect(call?.connectedAtElapsedMillis, call?.phase) {
                while (
                    call?.connectedAtElapsedMillis != null &&
                    call.phase in setOf(CallPhase.CONNECTED, CallPhase.ON_HOLD)
                ) {
                    nowElapsed = SystemClock.elapsedRealtime()
                    delay(1_000L)
                }
            }
            val duration = call?.connectedDurationMillis(nowElapsed)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (
                    call?.phase in setOf(CallPhase.CONNECTED, CallPhase.ON_HOLD) &&
                    duration != null
                ) {
                    val stateWord = if (call?.phase == CallPhase.ON_HOLD) "On hold" else "Connected"
                    val formattedDuration = duration.formatCallDuration()
                    Text(
                        text = buildAnnotatedString {
                            append("$stateWord · ")
                            withStyle(
                                SpanStyle(
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569),
                                ),
                            ) {
                                append(formattedDuration)
                            }
                        },
                        color = Color(0xFF55647B),
                        fontSize = 21.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    DisplayHint(
                        when (call?.phase) {
                            CallPhase.DIALING -> "Dialing…"
                            CallPhase.RINGING -> "Ringing…"
                            else -> state.message ?: "Waiting for phone service"
                        },
                    )
                }
                ClaroAvatar(
                    name = resolvedIdentity?.displayName?.takeIf { !resolvedIdentity.isPrivate },
                    size = 72.dp,
                    isUnassigned = resolvedIdentity == null ||
                        resolvedIdentity.isPrivate ||
                        resolvedIdentity.displayName.isNullOrBlank(),
                )
                DisplayName(resolvedIdentity?.accessibleName ?: "Unknown caller")
                DisplayNumber(formatDisplayNumber(resolvedIdentity))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val speakerActive = call?.audioRoute == AudioRoute.SPEAKER
                    ClassicLightButton(
                        label = if (speakerActive) "Earpiece" else "Speaker",
                        onClick = {
                            onAudioRoute(
                                if (speakerActive) AudioRoute.EARPIECE else AudioRoute.SPEAKER,
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled = call?.capabilities?.canChangeAudioRoute == true,
                        minHeight = 48,
                    )
                    ClassicLightButton(
                        label = if (call?.isMuted == true) "Unmute" else "Mute",
                        onClick = onToggleMute,
                        modifier = Modifier.weight(1f),
                        enabled = call?.capabilities?.canMute == true,
                        minHeight = 48,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ClassicLightButton(
                        label = if (call?.phase == CallPhase.ON_HOLD) "Resume" else "Hold",
                        onClick = onToggleHold,
                        modifier = Modifier.weight(1f),
                        enabled = call?.capabilities?.canHold == true,
                        minHeight = 48,
                    )
                    ClassicLightButton(
                        label = "Swap",
                        onClick = onSwap,
                        modifier = Modifier.weight(1f),
                        enabled = call?.capabilities?.canSwap == true,
                        minHeight = 48,
                    )
                }
            }
        }

        CommunicationRoute.CALL_ENDED,
        CommunicationRoute.MISSED_CALL,
        CommunicationRoute.CALL_UNANSWERED,
        -> {
            val matchingHistory = state.history.firstOrNull { it.id == state.call?.id }
                ?: state.history.firstOrNull { it.identity == resolvedIdentity }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ClaroAvatar(
                    name = resolvedIdentity?.displayName?.takeIf { !resolvedIdentity.isPrivate },
                    size = 72.dp,
                    isUnassigned = resolvedIdentity == null ||
                        resolvedIdentity.isPrivate ||
                        resolvedIdentity.displayName.isNullOrBlank(),
                )
                DisplayName(resolvedIdentity?.accessibleName ?: "Unknown caller")
                DisplayNumber(formatDisplayNumber(resolvedIdentity))
                when (state.route) {
                    CommunicationRoute.CALL_ENDED -> {
                        val duration = state.call?.connectedDurationMillis(SystemClock.elapsedRealtime())
                            ?: matchingHistory?.connectedDurationMillis
                        if (duration != null) {
                            ClassicLtrText(
                                text = "Duration · ${duration.formatCallDuration()}",
                                color = Color(0xFF475569),
                                fontSize = 22.sp,
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            DisplayHint("Call ended")
                        }
                    }

                    CommunicationRoute.MISSED_CALL -> {
                        DisplayHint(
                            matchingHistory?.let { formatCallHistoryTimestamp(it.timestampMillis) }
                                ?: "Missed call",
                        )
                    }

                    CommunicationRoute.CALL_UNANSWERED -> {
                        DisplayHint("No answer")
                    }

                    else -> Unit
                }
            }
        }

        CommunicationRoute.CALL_FAILED -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ClaroInfoCard(
                    text = state.message ?: "The call did not connect",
                    tone = ClaroBannerTone.ERROR,
                )
                if (resolvedIdentity != null) {
                    DisplayName(resolvedIdentity.accessibleName)
                    DisplayNumber(formatDisplayNumber(resolvedIdentity))
                }
                if (state.qualification.needsRepair && state.qualification.telephonySupported) {
                    ClassicLightButton(
                        label = "Repair phone access",
                        onClick = onRepairPhone,
                        color = ClaroColors.Blue,
                        textColor = Color.White,
                        borderColor = ClaroColors.Blue,
                    )
                }
                ClaroEmergencyButton(
                    label = "Emergency call",
                    onClick = onEmergencyDialer,
                )
            }
        }

        CommunicationRoute.EXTERNAL_HANDOFF -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DisplayName("System dialer")
                DisplayHint(state.message ?: "Continue in the system emergency dialer")
            }
        }
    }
}

@Composable
private fun DisplayName(value: String) {
    Text(
        text = value,
        color = ClassicInk,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun DisplayNumber(value: String) {
    ClassicLtrText(
        text = value,
        color = ClassicInk,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun DisplayHint(value: String) {
    Text(
        text = value,
        color = Color(0xFF55647B),
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
}

private fun formatDisplayNumber(identity: CallerIdentity?): String = when {
    identity?.isPrivate == true -> "Number withheld"
    !identity?.number.isNullOrBlank() -> identity.number.orEmpty()
    else -> "Number unavailable"
}

private fun formatCallHistoryTimestamp(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "Recent"
    val zone = ZoneId.systemDefault()
    val zonedTime = Instant.ofEpochMilli(timestampMillis).atZone(zone)
    val timeText = zonedTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    return if (zonedTime.toLocalDate() == LocalDate.now(zone)) {
        "Today · $timeText"
    } else {
        val dateText = zonedTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
        "$dateText · $timeText"
    }
}

private fun isCenteredHeaderRoute(route: CommunicationRoute): Boolean = when (route) {
    CommunicationRoute.ACTIVE_CALL,
    CommunicationRoute.INCOMING_CALL,
    CommunicationRoute.MISSED_CALL,
    CommunicationRoute.CALL_UNANSWERED,
    CommunicationRoute.CALL_ENDED,
    CommunicationRoute.CALL_CONFIRMATION,
    CommunicationRoute.CALL_FAILED,
    -> true

    CommunicationRoute.DIALER,
    CommunicationRoute.CONTACTS,
    CommunicationRoute.CONTACT_DETAIL,
    CommunicationRoute.EXTERNAL_HANDOFF,
    -> false
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
