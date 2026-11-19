package com.clarohome.presentation.classic

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarohome.core.design.ClaroAvatar
import com.clarohome.core.design.ClaroContactPhotoPicker
import com.clarohome.core.design.ClaroBannerTone
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClassicMultiTapState
import com.clarohome.core.design.ClassicKeypadMode
import com.clarohome.core.design.applyClassicKeypadInput
import com.clarohome.core.design.ClaroIcon
import com.clarohome.core.design.ClaroIconKind
import com.clarohome.core.design.ClaroInfoCard
import com.clarohome.core.design.LocalClaroDeleteText
import com.clarohome.core.design.LocalClaroKeypadInput
import com.clarohome.core.design.LocalClaroKeypadMode
import com.clarohome.core.model.BatteryStatus
import com.clarohome.core.model.CellularStatus
import com.clarohome.feature.communication.AudioRoute
import com.clarohome.feature.communication.CallDirection
import com.clarohome.feature.communication.CallFailure
import com.clarohome.feature.communication.CallOutcome
import com.clarohome.feature.communication.CallPhase
import com.clarohome.feature.communication.CallSession
import com.clarohome.feature.communication.CallerIdentity
import com.clarohome.feature.communication.ClaroContact
import com.clarohome.feature.communication.CommunicationRoute
import com.clarohome.feature.communication.CommunicationUiState
import com.clarohome.feature.communication.ContactNumber
import com.clarohome.feature.communication.PhoneContactsCoordinator
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
    onCallIdentity: ((CallerIdentity) -> Unit)? = null,
    canManageContacts: Boolean = false,
    onSaveContact: (String?, String, String, String, String?) -> Unit = { _, _, _, _, _ -> },
    onDeleteContact: (String) -> Unit = {},
    onRequestUnlockForContacts: () -> Unit = {},
    onSelectDialerContact: (CallerIdentity) -> Unit = {},
    sharedDialerControls: Boolean = false,
    dialerOptionsOpen: Boolean = false,
    onDialerOptionsChange: ((Boolean) -> Unit)? = null,
    dialerScrollState: ScrollState? = null,
    onContactsBack: (() -> Unit)? = null,
) {
    val detailKey = state.selectedContact?.lookupKey.takeIf { state.route == CommunicationRoute.CONTACT_DETAIL }
    var localOptionsOpen by rememberSaveable(state.route, detailKey) { mutableStateOf(false) }
    val sharedDialer = sharedDialerControls && state.route == CommunicationRoute.DIALER
    val optionsOpen = if (sharedDialer) dialerOptionsOpen else localOptionsOpen
    val setOptionsOpen: (Boolean) -> Unit = {
        if (sharedDialer && onDialerOptionsChange != null) onDialerOptionsChange(it) else localOptionsOpen = it
    }
    BackHandler(enabled = optionsOpen && (sharedDialer || onContactsBack != null)) { setOptionsOpen(false) }
    val localScrollState = key(state.route, detailKey, optionsOpen) { rememberScrollState() }
    val displayScrollState = if (sharedDialer) dialerScrollState ?: localScrollState else localScrollState
    var dtmfFeedback by rememberSaveable(state.call?.id, state.route) { mutableStateOf("") }
    var showCallKeypad by rememberSaveable(state.call?.id, state.route) { mutableStateOf(false) }
    var contactsMode by rememberSaveable(state.route) { mutableStateOf(ClassicKeypadMode.ENGLISH) }
    var contactsMultiTap by remember(state.route) { mutableStateOf<ClassicMultiTapState?>(null) }
    var editorMode by rememberSaveable(state.route, state.selectedContact?.lookupKey) {
        mutableStateOf<String?>(null)
    }
    var confirmDelete by rememberSaveable(state.route, state.selectedContact?.lookupKey) {
        mutableStateOf(false)
    }
    LaunchedEffect(canManageContacts) {
        if (!canManageContacts) {
            editorMode = null
            confirmDelete = false
        }
    }
    val contact = state.selectedContact
    val openEditor: (String) -> Unit = { mode ->
        if (canManageContacts) {
            setOptionsOpen(false)
            editorMode = mode
        } else {
            onRequestUnlockForContacts()
        }
    }
    val addContact = { openEditor("add") }
    val editContact = { openEditor("edit") }
    val requestDelete: () -> Unit = {
        if (canManageContacts) {
            setOptionsOpen(false)
            confirmDelete = true
        } else {
            onRequestUnlockForContacts()
        }
    }
    if (editorMode != null && canManageContacts) {
        ClassicContactEditor(
            contact = contact.takeIf { editorMode == "edit" },
            battery = battery,
            cellular = cellular,
            onSave = { name, primary, secondary, photoUri ->
                if (canManageContacts && (editorMode == "add" || contact?.canEdit == true)) {
                    onSaveContact(contact?.lookupKey.takeIf { editorMode == "edit" }, name, primary, secondary, photoUri)
                    editorMode = null
                }
            },
            onCancel = { editorMode = null },
        )
        return
    }
    val deleting = confirmDelete && canManageContacts && contact != null
    val isSosContact = state.assignments.any {
        it.contactLookupKey == contact?.lookupKey && it.slot.equals("sos", ignoreCase = true)
    }
    val confirmDeleteAction: (() -> Unit)? = if (deleting && !isSosContact && contact?.canDelete == true) {
        {
            if (canManageContacts) {
                onDeleteContact(contact.lookupKey)
                confirmDelete = false
            }
        }
    } else {
        null
    }
    BackHandler(enabled = deleting) { confirmDelete = false }

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
        onOpenContacts = onOpenContacts,
        onToggleMute = onToggleMute,
        onAudioRoute = onAudioRoute,
        onToggleHold = onToggleHold,
        onSwap = onSwap,
        onRepairPhone = onRepairPhone,
    ) + when (state.route) {
        CommunicationRoute.CONTACTS -> listOf(
            ClassicOptionAction(label = stringResource(R.string.classic_add_contact), onSelect = addContact),
        )
        CommunicationRoute.CONTACT_DETAIL -> listOf(
            ClassicOptionAction(
                label = stringResource(R.string.classic_edit_contact),
                enabled = contact?.canEdit == true,
                onSelect = editContact,
            ),
            ClassicOptionAction(
                label = stringResource(R.string.classic_delete_contact),
                enabled = contact?.canDelete == true,
                onSelect = requestDelete,
            ),
        )
        else -> emptyList()
    }

    val backAction: (() -> Unit)? = when {
        deleting -> { { confirmDelete = false } }
        optionsOpen -> {
            { setOptionsOpen(false) }
        }
        state.route == CommunicationRoute.INCOMING_CALL -> null
        state.route == CommunicationRoute.DIALER && state.digits.isNotEmpty() -> onBackspace
        state.route == CommunicationRoute.CONTACTS && state.contactQuery.isNotEmpty() -> {
            {
                contactsMultiTap = null
                onSearchContacts(state.contactQuery.dropLast(1))
            }
        }
        state.route == CommunicationRoute.CONTACTS && onContactsBack != null -> onContactsBack
        state.route == CommunicationRoute.CALL_CONFIRMATION -> onCancelCall
        state.route == CommunicationRoute.CONTACT_DETAIL -> onOpenContacts
        else -> onHome
    }

    val greenAction: (() -> Unit)? = if (deleting) {
        confirmDeleteAction
    } else if (optionsOpen) {
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

    val redAction: () -> Unit = if (deleting) {
        { confirmDelete = false }
    } else when (state.route) {
        CommunicationRoute.INCOMING_CALL -> onDecline
        CommunicationRoute.ACTIVE_CALL -> onEnd
        CommunicationRoute.CALL_CONFIRMATION -> onCancelCall
        else -> {
            {
                setOptionsOpen(false)
                onHome()
            }
        }
    }

    val keypadAction: ((Char) -> Unit)? = when {
        deleting -> null
        optionsOpen -> null
        state.route == CommunicationRoute.DIALER -> onDigit
        state.route == CommunicationRoute.ACTIVE_CALL &&
            state.call?.capabilities?.canSendDtmf == true -> { digit ->
            dtmfFeedback = (dtmfFeedback + digit).takeLast(12)
            onDigit(digit)
        }
        state.route == CommunicationRoute.CONTACTS -> { digit ->
            if (digit == '#') {
                contactsMode = contactsMode.next()
                contactsMultiTap = null
            } else {
                val (updated, nextTap) = applyClassicKeypadInput(
                    currentValue = state.contactQuery,
                    digit = digit,
                    fieldKey = "contact-search",
                    numericMode = contactsMode == ClassicKeypadMode.NUMBERS,
                    previousTap = contactsMultiTap,
                    mode = contactsMode,
                )
                contactsMultiTap = nextTap
                onSearchContacts(updated)
            }
        }
        else -> null
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
            if (!optionsOpen && state.route == CommunicationRoute.ACTIVE_CALL) {
                ClassicActiveCallIdentity(state)
            } else {
                ClassicHeader(
                    title = if (optionsOpen) {
                        stringResource(R.string.classic_options)
                    } else if (deleting) {
                        stringResource(R.string.classic_delete_contact)
                    } else {
                        classicTitle(state.route)
                    },
                    centered = !optionsOpen && isCenteredHeaderRoute(state.route),
                    titleColor = if (!optionsOpen && state.route == CommunicationRoute.CALL_ENDED) {
                        ClaroColors.RedOnDark
                    } else {
                        Color.White
                    },
                    action = if (!optionsOpen && state.route == CommunicationRoute.DIALER) {
                        {
                            ClassicHeaderAction(
                                label = stringResource(R.string.classic_contacts),
                                icon = ClaroIconKind.CONTACT,
                                onClick = onOpenContacts,
                            )
                        }
                    } else if (!optionsOpen && state.route == CommunicationRoute.CONTACTS) {
                        {
                            ClassicHeaderAction(
                                label = stringResource(R.string.classic_add_contact),
                                icon = ClaroIconKind.CONTACT_ADD,
                                onClick = addContact,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("classic-phone-content")
                    .verticalScroll(displayScrollState),
            ) {
                if (deleting) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ClaroInfoCard(
                            text = stringResource(
                                if (isSosContact) R.string.classic_delete_contact_sos_blocked
                                else R.string.classic_delete_contact_confirm,
                                contact?.displayName.orEmpty(),
                            ),
                            tone = if (isSosContact) ClaroBannerTone.ERROR else ClaroBannerTone.WARNING,
                        )
                        if (confirmDeleteAction != null) {
                            ClassicLightButton(
                                label = stringResource(R.string.classic_delete_contact),
                                modifier = Modifier.testTag("classic-confirm-delete-contact"),
                                onClick = confirmDeleteAction,
                                color = ClaroColors.Red,
                                textColor = Color.White,
                            )
                        }
                        ClassicLightButton(
                            label = stringResource(R.string.classic_action_cancel),
                            modifier = Modifier.testTag("classic-cancel-delete-contact"),
                            onClick = { confirmDelete = false },
                        )
                    }
                } else if (optionsOpen) {
                    ClassicOptionsOverlay(
                        options = options,
                        onClose = { setOptionsOpen(false) },
                    )
                } else if (state.route != CommunicationRoute.ACTIVE_CALL) {
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
                        onRepairPhone = onRepairPhone,
                        onSelectDialerContact = onSelectDialerContact,
                    )
                }
            }
            if (!optionsOpen && state.route == CommunicationRoute.ACTIVE_CALL) {
                ClassicCallControls(
                    call = state.call,
                    onToggleMute = onToggleMute,
                    onAudioRoute = onAudioRoute,
                    onToggleHold = onToggleHold,
                    onSwap = onSwap,
                    showKeypad = showCallKeypad,
                    onToggleKeypad = { showCallKeypad = !showCallKeypad },
                )
            }
        }
        if (!sharedDialer) {
        ClassicIntegratedPad(
            menuLabel = stringResource(R.string.classic_options),
            greenLabel = if (deleting) {
                stringResource(R.string.classic_delete_contact)
            } else when (state.route) {
                CommunicationRoute.INCOMING_CALL -> "☎ ${stringResource(R.string.classic_answer)}"
                else -> "☎ ${stringResource(R.string.classic_call)}"
            },
            redLabel = if (deleting) {
                stringResource(R.string.classic_action_cancel)
            } else when (state.route) {
                CommunicationRoute.INCOMING_CALL -> stringResource(R.string.classic_decline)
                CommunicationRoute.ACTIVE_CALL -> stringResource(R.string.classic_end)
                else -> "⌂ ${stringResource(R.string.classic_home)}"
            },
            onBack = backAction,
            onMenu = if (!deleting && (options.isNotEmpty() || optionsOpen)) {
                { setOptionsOpen(!optionsOpen) }
            } else {
                null
            },
            onGreen = greenAction,
            onRed = redAction,
            scrollState = displayScrollState,
        )
        if (state.route != CommunicationRoute.ACTIVE_CALL || showCallKeypad) {
        val keypadMode = contactsMode.takeIf { state.route == CommunicationRoute.CONTACTS && !optionsOpen }
        ClassicModeStatusStrip(
            modeLabel = keypadMode?.displayLabel(),
            feedback = dtmfFeedback.takeIf {
                state.route == CommunicationRoute.ACTIVE_CALL && it.isNotEmpty()
            },
        )
        ClassicNumericKeypad(
            onDigitSelected = keypadAction,
            mode = keypadMode,
            onSwitchLanguage = if (keypadMode != null) {
                {
                    contactsMode = contactsMode.switchLanguage()
                    contactsMultiTap = null
                }
            } else {
                null
            },
            keyHeight = if (state.route == CommunicationRoute.ACTIVE_CALL) 48.dp else 64.dp,
            allowInternationalPrefix = state.route == CommunicationRoute.DIALER &&
                state.digits.isEmpty() && !state.hasActiveCall && !state.awaitingOutgoingCall,
        )
        }
        }
    }
}

@Composable
private fun ClassicContactEditor(
    contact: ClaroContact?,
    battery: BatteryStatus?,
    cellular: CellularStatus?,
    onSave: (String, String, String, String?) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable(contact?.lookupKey) { mutableStateOf(contact?.displayName.orEmpty()) }
    var primary by rememberSaveable(contact?.lookupKey) {
        mutableStateOf(contact?.numbers?.getOrNull(0)?.value.orEmpty())
    }
    var secondary by rememberSaveable(contact?.lookupKey) {
        mutableStateOf(contact?.numbers?.getOrNull(1)?.value.orEmpty())
    }
    var showError by rememberSaveable { mutableStateOf(false) }
    var pickedPhotoUri by rememberSaveable(contact?.lookupKey) { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val input = LocalClaroKeypadInput.current
    val deleteText = LocalClaroDeleteText.current
    val keypadMode = LocalClaroKeypadMode.current
    val save: () -> Unit = {
        val primaryValid = PhoneContactsCoordinator.normalizeDialString(primary).length >= 3
        val secondaryValid = secondary.isBlank() ||
            PhoneContactsCoordinator.normalizeDialString(secondary).length >= 3
        if (name.isBlank() || !primaryValid || !secondaryValid) {
            showError = true
        } else {
            onSave(name.trim(), primary.trim(), secondary.trim(), pickedPhotoUri)
        }
    }
    BackHandler(onBack = onCancel)
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(
        modifier = Modifier.fillMaxSize().background(ClassicFrame).padding(6.dp)
            .testTag("classic-contact-editor"),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)).background(ClassicDisplay),
        ) {
            ClassicSystemStatusBar(battery = battery, cellular = cellular)
            ClassicHeader(
                title = stringResource(
                    if (contact == null) R.string.classic_add_contact else R.string.classic_edit_contact,
                ),
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showError) {
                    ClaroInfoCard(
                        text = stringResource(R.string.classic_contact_invalid),
                        tone = ClaroBannerTone.ERROR,
                    )
                }
                ClassicContactField(
                    label = stringResource(R.string.classic_contact_name),
                    value = name,
                    onChange = { name = it; showError = false },
                    modifier = Modifier.focusRequester(focusRequester).testTag("classic-contact-name"),
                )
                ClassicContactField(
                    label = stringResource(R.string.classic_contact_primary),
                    value = primary,
                    onChange = { primary = it; showError = false },
                    phone = true,
                    modifier = Modifier.testTag("classic-contact-primary"),
                )
                ClassicContactField(
                    label = stringResource(R.string.classic_contact_secondary),
                    value = secondary,
                    onChange = { secondary = it; showError = false },
                    phone = true,
                    modifier = Modifier.testTag("classic-contact-secondary"),
                )
                ClaroContactPhotoPicker(name, pickedPhotoUri ?: contact?.photoUri) { pickedPhotoUri = it }
            }
        }
        ClassicIntegratedPad(
            greenLabel = stringResource(R.string.classic_action_save),
            greenIcon = ClaroIconKind.CHECK,
            redLabel = stringResource(R.string.classic_action_cancel),
            onBack = { if (deleteText?.invoke() != true) onCancel() },
            onGreen = save,
            onRed = onCancel,
            scrollState = scroll,
        )
        ClassicKeypadModeStatus(mode = keypadMode)
        ClassicNumericKeypad(onDigitSelected = input)
    }
}

@Composable
private fun ClassicContactField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    phone: Boolean = false,
) {
    CompositionLocalProvider(
        LocalLayoutDirection provides if (phone) LayoutDirection.Ltr else LocalLayoutDirection.current,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            modifier = modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (phone) KeyboardType.Phone else KeyboardType.Text,
            ),
            textStyle = TextStyle(fontSize = 23.sp, color = ClassicInk),
            shape = RectangleShape,
        )
    }
}

@Composable
private fun buildClassicCommunicationOptions(
    state: CommunicationUiState,
    callbackAction: (() -> Unit)?,
    onOpenContacts: () -> Unit,
    onToggleMute: () -> Unit,
    onAudioRoute: (AudioRoute) -> Unit,
    onToggleHold: () -> Unit,
    onSwap: () -> Unit,
    onRepairPhone: () -> Unit,
): List<ClassicOptionAction> {
    val contactsLabel = stringResource(R.string.classic_contacts)
    val repairPhoneLabel = stringResource(R.string.classic_repair_phone_access)
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
                add(ClassicOptionAction(label = contactsLabel, onSelect = onOpenContacts))
                if (state.qualification.needsRepair && state.qualification.telephonySupported) {
                    add(ClassicOptionAction(label = repairPhoneLabel, onSelect = onRepairPhone))
                }
            }

            CommunicationRoute.CONTACTS -> {
                if (state.qualification.needsRepair && state.qualification.telephonySupported) {
                    add(ClassicOptionAction(label = repairPhoneLabel, onSelect = onRepairPhone))
                }
            }

            CommunicationRoute.CONTACT_DETAIL -> {
                add(ClassicOptionAction(label = contactsLabel, onSelect = onOpenContacts))
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
            }

            CommunicationRoute.CALL_CONFIRMATION -> {
                add(ClassicOptionAction(label = contactsLabel, onSelect = onOpenContacts))
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
    onRepairPhone: () -> Unit,
    onSelectDialerContact: (CallerIdentity) -> Unit,
) {
    when (state.route) {
        CommunicationRoute.DIALER -> {
            val selectedPerson = state.pendingIdentity
            val recentCalls = if (state.digits.isBlank()) {
                state.history.sortedByDescending {
                    it.outcome == CallOutcome.MISSED_INCOMING && !it.isOpened
                }.take(5)
            } else {
                emptyList()
            }
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
                selectedPerson?.let { identity ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(6.dp)
                            .testTag("classic-dialer-selected-contact"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ClaroAvatar(
                            name = identity.displayName,
                            photoUri = identity.photoUri,
                            size = 48.dp,
                            isUnassigned = identity.isPrivate,
                            contactNumber = identity.number.takeUnless { identity.isPrivate },
                        )
                        Text(
                            text = identity.classicAccessibleName(),
                            color = ClassicInk,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
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
                if (state.digits.isNotEmpty() && state.digits.all(Char::isDigit) && selectedPerson == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.classic_matching_contacts),
                            color = ClassicInk,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                            if (state.dialerMatchesLoading) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                    state.dialerMatches.forEach { identity ->
                        key(identity) {
                            ClassicRowItem(
                                title = identity.classicAccessibleName(),
                                subtitle = identity.number,
                                onClick = { onSelectDialerContact(identity) },
                                leading = {
                                    ClaroAvatar(
                                        name = identity.displayName,
                                        photoUri = identity.photoUri,
                                        contactNumber = identity.number,
                                        size = 44.dp,
                                    )
                                },
                            )
                        }
                    }
                }
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
                recentCalls.forEachIndexed { index, entry ->
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
                                photoUri = entry.identity.photoUri,
                                contactNumber = entry.identity.number.takeUnless { entry.identity.isPrivate },
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
                                photoUri = contact.photoUri,
                                contactLookupKey = contact.lookupKey,
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
                    ClaroAvatar(
                        name = contact.displayName,
                        size = 72.dp,
                        photoUri = contact.photoUri,
                        contactLookupKey = contact.lookupKey,
                        modifier = Modifier.testTag("classic-contact-avatar"),
                    )
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
                    photoUri = identity?.photoUri,
                    contactNumber = identity?.number.takeUnless { identity?.isPrivate == true },
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
                    photoUri = identity?.photoUri,
                    contactNumber = identity?.number.takeUnless { identity?.isPrivate == true },
                    isUnassigned = identity == null ||
                        identity.isPrivate ||
                        identity.displayName.isNullOrBlank(),
                )
                DisplayName(identity.classicAccessibleName())
                DisplayNumber(identity)
                DisplayHint(stringResource(R.string.classic_answer_with_green_key))
            }
        }

        CommunicationRoute.ACTIVE_CALL -> Unit

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
                    photoUri = resolvedIdentity?.photoUri,
                    contactNumber = resolvedIdentity?.number.takeUnless { resolvedIdentity?.isPrivate == true },
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
                            DisplayHint(
                                value = stringResource(R.string.classic_call_ended),
                                color = ClaroColors.Red,
                            )
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
private fun ClassicCallControls(
    call: CallSession?,
    onToggleMute: () -> Unit,
    onAudioRoute: (AudioRoute) -> Unit,
    onToggleHold: () -> Unit,
    onSwap: () -> Unit,
    showKeypad: Boolean,
    onToggleKeypad: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val speakerActive = call?.audioRoute == AudioRoute.SPEAKER
            ClassicLightButton(
                label = stringResource(
                    if (speakerActive) R.string.classic_earpiece else R.string.classic_speaker,
                ),
                onClick = {
                    onAudioRoute(if (speakerActive) AudioRoute.EARPIECE else AudioRoute.SPEAKER)
                },
                modifier = Modifier.weight(1f),
                enabled = call?.capabilities?.canChangeAudioRoute == true,
                minHeight = 48,
                leadingIcon = if (speakerActive) ClaroIconKind.PHONE else ClaroIconKind.SPEAKER,
            )
            ClassicLightButton(
                label = stringResource(
                    if (call?.isMuted == true) R.string.classic_unmute else R.string.classic_mute,
                ),
                onClick = onToggleMute,
                modifier = Modifier.weight(1f),
                enabled = call?.capabilities?.canMute == true,
                minHeight = 48,
                leadingIcon = if (call?.isMuted == true) ClaroIconKind.MICROPHONE_OFF else ClaroIconKind.MICROPHONE,
            )
        }
        if (!showKeypad) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ClassicLightButton(
                    label = stringResource(
                        if (call?.phase == CallPhase.ON_HOLD) {
                            R.string.classic_resume_call
                        } else {
                            R.string.classic_hold_call
                        },
                    ),
                    onClick = onToggleHold,
                    modifier = Modifier.weight(1f),
                    enabled = call?.capabilities?.canHold == true,
                    minHeight = 48,
                    leadingIcon = if (call?.phase == CallPhase.ON_HOLD) ClaroIconKind.PLAY else ClaroIconKind.PAUSE,
                )
                ClassicLightButton(
                    label = stringResource(R.string.classic_swap_call),
                    onClick = onSwap,
                    modifier = Modifier.weight(1f),
                    enabled = call?.capabilities?.canSwap == true,
                    minHeight = 48,
                    leadingIcon = ClaroIconKind.SWAP,
                )
            }
        }
        if (call?.capabilities?.canSendDtmf == true) {
            ClassicLightButton(
                label = stringResource(
                    if (showKeypad) R.string.classic_hide_keypad else R.string.classic_show_keypad,
                ),
                onClick = onToggleKeypad,
                minHeight = 48,
                leadingIcon = ClaroIconKind.KEYPAD,
            )
        }
    }
}

@Composable
private fun ClassicActiveCallIdentity(state: CommunicationUiState) {
    val call = state.call
    val identity = call?.identity ?: state.pendingIdentity
    var now by remember(call?.id) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(call?.id, call?.phase) {
        while (call?.phase in setOf(CallPhase.CONNECTED, CallPhase.ON_HOLD)) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("classic-active-caller"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ClaroAvatar(
                name = identity?.displayName,
                photoUri = identity?.photoUri,
                size = 48.dp,
                modifier = Modifier.testTag("classic-active-avatar"),
                isUnassigned = identity?.isPrivate == true,
                contactNumber = identity?.number.takeUnless { identity?.isPrivate == true },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = identity.classicAccessibleName(),
                    color = ClassicInk,
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                identity?.number?.takeIf { !identity.isPrivate && !identity.displayName.isNullOrBlank() }
                    ?.let { number ->
                        ClassicLtrText(
                            text = number,
                            color = ClassicInk,
                            fontSize = 20.sp,
                            lineHeight = 24.sp,
                        )
                    }
            }
        }
        val phaseLabel = when (call?.phase) {
            CallPhase.CONNECTED -> stringResource(R.string.classic_call_connected)
            CallPhase.ON_HOLD -> stringResource(R.string.classic_call_on_hold)
            CallPhase.DIALING -> stringResource(R.string.classic_call_dialing)
            CallPhase.RINGING -> stringResource(R.string.classic_call_ringing)
            else -> stringResource(R.string.classic_waiting_phone_service)
        }
        val duration = call?.connectedDurationMillis(now)
        Text(
            text = if (duration != null) {
                stringResource(R.string.classic_call_status_duration, phaseLabel, duration.formatCallDuration())
            } else {
                phaseLabel
            },
            color = ClaroColors.Muted,
            fontSize = 17.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun DisplayHint(value: String, color: Color = Color(0xFF55647B)) {
    Text(
        text = value,
        color = color,
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
        "Contacts permission is needed" -> stringResource(R.string.classic_contacts_permission_needed)
        "Contacts could not be loaded" -> stringResource(R.string.classic_contacts_load_failed)
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
