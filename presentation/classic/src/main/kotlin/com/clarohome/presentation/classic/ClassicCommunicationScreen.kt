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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.clarohome.feature.communication.CallDirection
import com.clarohome.feature.communication.CallFailure
import com.clarohome.feature.communication.CallOutcome
import com.clarohome.feature.communication.CallPhase
import com.clarohome.feature.communication.CallerIdentity
import com.clarohome.feature.communication.CommunicationRoute
import com.clarohome.feature.communication.CommunicationUiState
import com.clarohome.feature.communication.ContactNumber
import com.clarohome.feature.communication.formatCallDuration
import com.clarohome.presentation.classic.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
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
                title = if (optionsOpen) {
                    stringResource(R.string.classic_options)
                } else {
                    classicTitle(state.route)
                },
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
            menuLabel = stringResource(R.string.classic_options),
            greenLabel = when (state.route) {
                CommunicationRoute.INCOMING_CALL -> "☎ ${stringResource(R.string.classic_answer)}"
                else -> "☎ ${stringResource(R.string.classic_call)}"
            },
            redLabel = when (state.route) {
                CommunicationRoute.INCOMING_CALL -> stringResource(R.string.classic_decline)
                CommunicationRoute.ACTIVE_CALL -> stringResource(R.string.classic_end)
                else -> "⌂ ${stringResource(R.string.classic_home)}"
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
                if (contactsNumericMode) {
                    stringResource(R.string.classic_mode_numbers)
                } else {
                    stringResource(R.string.classic_mode_letters)
                }
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

@Composable
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
): List<ClassicOptionAction> {
    val deleteCharacterLabel = stringResource(R.string.classic_delete_character)
    val clearTextLabel = stringResource(R.string.classic_clear_text)
    val contactsLabel = stringResource(R.string.classic_contacts)
    val repairPhoneLabel = stringResource(R.string.classic_repair_phone_access)
    val emergencyCallLabel = stringResource(R.string.classic_emergency_call)
    val earpieceLabel = stringResource(R.string.classic_earpiece)
    val speakerLabel = stringResource(R.string.classic_speaker)
    val unmuteLabel = stringResource(R.string.classic_unmute)
    val muteLabel = stringResource(R.string.classic_mute)
    val resumeLabel = stringResource(R.string.classic_resume_call)
    val holdLabel = stringResource(R.string.classic_hold_call)
    val swapLabel = stringResource(R.string.classic_swap_call)
    val callBackLabel = stringResource(R.string.classic_call_back)

    return buildList {
        when (state.route) {
            CommunicationRoute.DIALER -> {
                add(
                    ClassicOptionAction(
                        label = deleteCharacterLabel,
                        enabled = state.digits.isNotEmpty(),
                        onSelect = onBackspace,
                    ),
                )
                add(
                    ClassicOptionAction(
                        label = clearTextLabel,
                        enabled = state.digits.isNotEmpty(),
                        onSelect = { repeat(state.digits.length) { onBackspace() } },
                    ),
                )
                add(ClassicOptionAction(label = contactsLabel, onSelect = onOpenContacts))
                if (state.qualification.needsRepair && state.qualification.telephonySupported) {
                    add(ClassicOptionAction(label = repairPhoneLabel, onSelect = onRepairPhone))
                }
                add(ClassicOptionAction(label = emergencyCallLabel, onSelect = onEmergencyDialer))
            }

            CommunicationRoute.CONTACTS -> {
                add(
                    ClassicOptionAction(
                        label = deleteCharacterLabel,
                        enabled = state.contactQuery.isNotEmpty(),
                        onSelect = { onSearchContacts(state.contactQuery.dropLast(1)) },
                    ),
                )
                add(
                    ClassicOptionAction(
                        label = clearTextLabel,
                        enabled = state.contactQuery.isNotEmpty(),
                        onSelect = { onSearchContacts("") },
                    ),
                )
                if (state.qualification.needsRepair && state.qualification.telephonySupported) {
                    add(ClassicOptionAction(label = repairPhoneLabel, onSelect = onRepairPhone))
                }
                add(ClassicOptionAction(label = emergencyCallLabel, onSelect = onEmergencyDialer))
            }

            CommunicationRoute.CONTACT_DETAIL -> {
                add(ClassicOptionAction(label = contactsLabel, onSelect = onOpenContacts))
                add(ClassicOptionAction(label = emergencyCallLabel, onSelect = onEmergencyDialer))
            }

            CommunicationRoute.ACTIVE_CALL -> {
                val call = state.call
                val speakerActive = call?.audioRoute == AudioRoute.SPEAKER
                add(
                    ClassicOptionAction(
                        label = if (speakerActive) earpieceLabel else speakerLabel,
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
                        label = if (call?.isMuted == true) unmuteLabel else muteLabel,
                        enabled = call?.capabilities?.canMute == true,
                        onSelect = onToggleMute,
                    ),
                )
                add(
                    ClassicOptionAction(
                        label = if (call?.phase == CallPhase.ON_HOLD) resumeLabel else holdLabel,
                        enabled = call?.capabilities?.canHold == true,
                        onSelect = onToggleHold,
                    ),
                )
                add(
                    ClassicOptionAction(
                        label = swapLabel,
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
                    add(ClassicOptionAction(label = callBackLabel, onSelect = callbackAction))
                }
                add(ClassicOptionAction(label = contactsLabel, onSelect = onOpenContacts))
                if (state.qualification.needsRepair && state.qualification.telephonySupported) {
                    add(ClassicOptionAction(label = repairPhoneLabel, onSelect = onRepairPhone))
                }
                add(ClassicOptionAction(label = emergencyCallLabel, onSelect = onEmergencyDialer))
            }

            CommunicationRoute.CALL_CONFIRMATION -> {
                add(ClassicOptionAction(label = contactsLabel, onSelect = onOpenContacts))
                add(ClassicOptionAction(label = emergencyCallLabel, onSelect = onEmergencyDialer))
            }

            CommunicationRoute.INCOMING_CALL,
            CommunicationRoute.EXTERNAL_HANDOFF,
            -> Unit
        }
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
            val numberDescription = if (state.digits.isBlank()) {
                stringResource(R.string.classic_phone_number_empty)
            } else {
                stringResource(R.string.classic_phone_number_value, state.digits)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ClassicLtrText(
                    text = state.digits.ifBlank {
                        stringResource(R.string.classic_enter_number)
                    },
                    color = ClassicInk,
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 10.dp)
                        .semantics {
                            contentDescription = numberDescription
                        },
                )
                if (!state.qualification.canOwnCalls) {
                    ClaroInfoCard(
                        text = if (!state.qualification.telephonySupported) {
                            stringResource(R.string.classic_calling_unavailable)
                        } else {
                            stringResource(R.string.classic_phone_access_attention)
                        },
                        tone = ClaroBannerTone.WARNING,
                    )
                    if (state.qualification.telephonySupported) {
                        ClassicLightButton(
                            label = stringResource(R.string.classic_repair_phone_access),
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
                        title = entry.identity.classicAccessibleName(),
                        subtitle = timeLabel,
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
                        trailing = {
                            val (kind, tint, label) = when {
                                isMissed -> Triple(
                                    ClaroIconKind.CALL_MISSED,
                                    ClaroColors.Red,
                                    stringResource(R.string.classic_call_missed),
                                )
                                entry.direction == CallDirection.INCOMING -> Triple(
                                    ClaroIconKind.CALL_INCOMING,
                                    ClaroColors.Green,
                                    stringResource(R.string.classic_call_incoming),
                                )
                                else -> Triple(
                                    ClaroIconKind.CALL_OUTGOING,
                                    ClaroColors.Blue,
                                    stringResource(R.string.classic_call_outgoing),
                                )
                            }
                            ClaroIcon(
                                kind = kind,
                                tint = tint,
                                size = 30.dp,
                                contentDescription = label,
                            )
                        },
                    )
                }
                ClassicRowItem(
                    title = stringResource(R.string.classic_contacts),
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
                    label = stringResource(R.string.classic_emergency_call),
                    onClick = onEmergencyDialer,
                )
                state.message?.takeIf { it.isNotBlank() }?.let { msg ->
                    ClaroInfoCard(
                        text = localizeCommunicationMessage(msg, state.failure),
                        tone = ClaroBannerTone.INFO,
                    )
                }
            }
        }

        CommunicationRoute.CONTACTS -> {
            val searchLabel = stringResource(R.string.classic_search_name_or_number)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = searchLabel,
                    color = ClassicInk,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
                OutlinedTextField(
                    value = state.contactQuery,
                    onValueChange = onSearchContacts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = searchLabel },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 23.sp),
                    shape = RectangleShape,
                )
                if (!state.qualification.roleHeld && state.qualification.telephonySupported) {
                    ClassicLightButton(
                        label = stringResource(R.string.classic_repair_phone_access),
                        onClick = onRepairPhone,
                        color = ClaroColors.WarningBg,
                        textColor = ClaroColors.WarningInk,
                        borderColor = ClaroColors.WarningBorder,
                    )
                }
                if (state.contactsLoading) {
                    ClaroInfoCard(
                        text = stringResource(R.string.classic_loading_contacts),
                        tone = ClaroBannerTone.INFO,
                    )
                } else if (state.contacts.isEmpty()) {
                    ClaroInfoCard(
                        text = if (state.contactQuery.isBlank()) {
                            stringResource(R.string.classic_no_contacts_yet)
                        } else {
                            stringResource(R.string.classic_no_matches)
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
                        label = stringResource(R.string.classic_more_contacts),
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
                        text = state.message?.let { localizeCommunicationMessage(it, state.failure) }
                            ?: stringResource(R.string.classic_contact_unavailable),
                        tone = ClaroBannerTone.WARNING,
                    )
                    ClassicLightButton(
                        label = stringResource(R.string.classic_contacts),
                        onClick = onOpenContacts,
                    )
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
                DisplayName(identity.classicAccessibleName())
                DisplayNumber(identity)
                DisplayHint(stringResource(R.string.classic_press_call_to_confirm))
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
                DisplayName(identity.classicAccessibleName())
                DisplayNumber(identity)
                DisplayHint(stringResource(R.string.classic_answer_with_green_key))
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
                    val stateWord = if (call?.phase == CallPhase.ON_HOLD) {
                        stringResource(R.string.classic_call_on_hold)
                    } else {
                        stringResource(R.string.classic_call_connected)
                    }
                    val formattedDuration = duration.formatCallDuration()
                    val statusLine = stringResource(
                        R.string.classic_call_status_duration,
                        stateWord,
                        formattedDuration,
                    )
                    Text(
                        text = buildAnnotatedString {
                            append(statusLine)
                            val durationStart = statusLine.indexOf(formattedDuration)
                            if (durationStart >= 0) {
                                addStyle(
                                    SpanStyle(
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569),
                                    ),
                                    start = durationStart,
                                    end = durationStart + formattedDuration.length,
                                )
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
                            CallPhase.DIALING -> stringResource(R.string.classic_call_dialing)
                            CallPhase.RINGING -> stringResource(R.string.classic_call_ringing)
                            else -> state.message?.let {
                                localizeCommunicationMessage(it, state.failure)
                            } ?: stringResource(R.string.classic_waiting_phone_service)
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
                DisplayName(resolvedIdentity.classicAccessibleName())
                DisplayNumber(resolvedIdentity)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val speakerActive = call?.audioRoute == AudioRoute.SPEAKER
                    ClassicLightButton(
                        label = if (speakerActive) {
                            stringResource(R.string.classic_earpiece)
                        } else {
                            stringResource(R.string.classic_speaker)
                        },
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
                        label = if (call?.isMuted == true) {
                            stringResource(R.string.classic_unmute)
                        } else {
                            stringResource(R.string.classic_mute)
                        },
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
                        label = if (call?.phase == CallPhase.ON_HOLD) {
                            stringResource(R.string.classic_resume_call)
                        } else {
                            stringResource(R.string.classic_hold_call)
                        },
                        onClick = onToggleHold,
                        modifier = Modifier.weight(1f),
                        enabled = call?.capabilities?.canHold == true,
                        minHeight = 48,
                    )
                    ClassicLightButton(
                        label = stringResource(R.string.classic_swap_call),
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
                DisplayName(resolvedIdentity.classicAccessibleName())
                DisplayNumber(resolvedIdentity)
                when (state.route) {
                    CommunicationRoute.CALL_ENDED -> {
                        val duration = state.call?.connectedDurationMillis(SystemClock.elapsedRealtime())
                            ?: matchingHistory?.connectedDurationMillis
                        if (duration != null) {
                            Text(
                                text = stringResource(
                                    R.string.classic_call_duration,
                                    duration.formatCallDuration(),
                                ),
                                color = Color(0xFF475569),
                                fontSize = 22.sp,
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            DisplayHint(stringResource(R.string.classic_call_ended))
                        }
                    }

                    CommunicationRoute.MISSED_CALL -> {
                        DisplayHint(
                            matchingHistory?.let { formatCallHistoryTimestamp(it.timestampMillis) }
                                ?: stringResource(R.string.classic_missed_call),
                        )
                    }

                    CommunicationRoute.CALL_UNANSWERED -> {
                        DisplayHint(stringResource(R.string.classic_no_answer))
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
                val currentMessage = state.message
                val failureText = when {
                    state.failure != null -> state.failure.classicMessage()
                    currentMessage != null -> localizeCommunicationMessage(currentMessage, null)
                    else -> stringResource(R.string.classic_call_did_not_connect)
                }
                ClaroInfoCard(
                    text = failureText,
                    tone = ClaroBannerTone.ERROR,
                )
                if (resolvedIdentity != null) {
                    DisplayName(resolvedIdentity.classicAccessibleName())
                    DisplayNumber(resolvedIdentity)
                }
                if (state.qualification.needsRepair && state.qualification.telephonySupported) {
                    ClassicLightButton(
                        label = stringResource(R.string.classic_repair_phone_access),
                        onClick = onRepairPhone,
                        color = ClaroColors.Blue,
                        textColor = Color.White,
                        borderColor = ClaroColors.Blue,
                    )
                }
                ClaroEmergencyButton(
                    label = stringResource(R.string.classic_emergency_call),
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
                DisplayName(stringResource(R.string.classic_system_dialer))
                DisplayHint(
                    state.message?.let { localizeCommunicationMessage(it, state.failure) }
                        ?: stringResource(R.string.classic_continue_system_emergency_dialer),
                )
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
private fun DisplayNumber(identity: CallerIdentity?) {
    val hasDirectNumber = identity?.isPrivate != true && !identity?.number.isNullOrBlank()
    if (hasDirectNumber) {
        ClassicLtrText(
            text = identity?.number.orEmpty(),
            color = ClassicInk,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    } else {
        Text(
            text = if (identity?.isPrivate == true) {
                stringResource(R.string.classic_number_withheld)
            } else {
                stringResource(R.string.classic_number_unavailable)
            },
            color = ClassicInk,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
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

@Composable
private fun CallerIdentity?.classicAccessibleName(): String = when {
    this == null -> stringResource(R.string.classic_unknown_caller)
    isPrivate -> stringResource(R.string.classic_private_number)
    !displayName.isNullOrBlank() -> displayName.orEmpty()
    !number.isNullOrBlank() -> "\u2066${number.orEmpty()}\u2069"
    else -> stringResource(R.string.classic_unknown_caller)
}

@Composable
private fun CallFailure?.classicMessage(): String = when (this) {
    CallFailure.INVALID_NUMBER -> stringResource(R.string.classic_call_failure_invalid_number)
    CallFailure.NO_SIM -> stringResource(R.string.classic_call_failure_no_sim)
    CallFailure.NO_SERVICE -> stringResource(R.string.classic_call_failure_no_service)
    CallFailure.ROLE_LOST -> stringResource(R.string.classic_call_failure_role_lost)
    CallFailure.PERMISSION_DENIED -> stringResource(R.string.classic_call_failure_permission_denied)
    CallFailure.ACCOUNT_UNAVAILABLE ->
        stringResource(R.string.classic_call_failure_account_unavailable)
    CallFailure.NETWORK_FAILURE -> stringResource(R.string.classic_call_failure_network)
    CallFailure.UNKNOWN, null -> stringResource(R.string.classic_call_did_not_connect)
}

@Composable
private fun localizeCommunicationMessage(
    rawMessage: String,
    failure: CallFailure?,
): String {
    if (failure != null) return failure.classicMessage()
    val holdNotSetMatch = Regex("^Hold ([12]) is not set$").matchEntire(rawMessage)
    if (holdNotSetMatch != null) {
        return stringResource(R.string.classic_hold_slot_not_set, holdNotSetMatch.groupValues[1])
    }
    val holdRepairMatch = Regex("^Ask your caregiver to repair Hold ([12])$").matchEntire(rawMessage)
    if (holdRepairMatch != null) {
        return stringResource(
            R.string.classic_hold_slot_needs_repair,
            holdRepairMatch.groupValues[1],
        )
    }
    return when (rawMessage) {
        "Call canceled" -> stringResource(R.string.classic_msg_call_canceled)
        "Continue in the system emergency dialer" ->
            stringResource(R.string.classic_continue_system_emergency_dialer)
        "Contact is no longer available" -> stringResource(R.string.classic_contact_unavailable)
        else -> rawMessage
    }
}

@Composable
private fun formatCallHistoryTimestamp(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return stringResource(R.string.classic_timestamp_recent)
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    val zone = ZoneId.systemDefault()
    val zonedTime = Instant.ofEpochMilli(timestampMillis).atZone(zone)
    val timeText = zonedTime.format(
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale),
    )
    return if (zonedTime.toLocalDate() == LocalDate.now(zone)) {
        stringResource(R.string.classic_timestamp_today, timeText)
    } else {
        val dateText = zonedTime.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale),
        )
        stringResource(R.string.classic_timestamp_date_time, dateText, timeText)
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

@Composable
private fun classicTitle(route: CommunicationRoute): String = when (route) {
    CommunicationRoute.DIALER -> stringResource(R.string.classic_phone_title)
    CommunicationRoute.CONTACTS -> stringResource(R.string.classic_contacts)
    CommunicationRoute.CONTACT_DETAIL -> stringResource(R.string.classic_contact)
    CommunicationRoute.CALL_CONFIRMATION -> stringResource(R.string.classic_confirm_call)
    CommunicationRoute.INCOMING_CALL -> stringResource(R.string.classic_incoming_call)
    CommunicationRoute.ACTIVE_CALL -> stringResource(R.string.classic_active_call_title)
    CommunicationRoute.CALL_ENDED -> stringResource(R.string.classic_call_ended)
    CommunicationRoute.MISSED_CALL -> stringResource(R.string.classic_missed_call)
    CommunicationRoute.CALL_UNANSWERED -> stringResource(R.string.classic_no_answer)
    CommunicationRoute.CALL_FAILED -> stringResource(R.string.classic_call_failed)
    CommunicationRoute.EXTERNAL_HANDOFF -> stringResource(R.string.classic_system_dialer)
}
