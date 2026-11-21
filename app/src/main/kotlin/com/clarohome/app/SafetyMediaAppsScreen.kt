package com.clarohome.app

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarohome.core.design.ClaroAvatar
import com.clarohome.core.design.ClaroBannerTone
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.core.design.ClaroIcon
import com.clarohome.core.design.ClaroIconKind
import com.clarohome.core.design.ClaroInfoCard
import com.clarohome.core.design.ClaroPrimaryButton
import com.clarohome.core.design.ClaroRowButton
import com.clarohome.core.design.ClaroScreenScaffold
import com.clarohome.core.design.ClaroHeaderBackButton
import com.clarohome.core.model.OnboardingStep
import com.clarohome.core.design.ClaroSecondaryButton
import com.clarohome.core.design.ClaroSoftkey
import com.clarohome.feature.communication.ClaroContact
import com.clarohome.feature.safety.media.apps.ClaroPhoto
import com.clarohome.feature.safety.media.apps.MediaLibrary
import com.clarohome.feature.safety.media.apps.MediaUriGrant
import com.clarohome.feature.safety.media.apps.PhotoAvailability
import com.clarohome.feature.safety.media.apps.PhotoOwnership
import com.clarohome.feature.safety.media.apps.PhotoRemovalRequest
import com.clarohome.feature.safety.media.apps.SosCallOutcome
import com.clarohome.feature.safety.media.apps.SosState
import com.clarohome.feature.safety.media.apps.SosReadiness
import com.clarohome.feature.safety.media.apps.TrustedContact
import kotlinx.coroutines.delay
import com.clarohome.core.domain.TelemetryScreen

@Keep
enum class SafetyMediaAppsDestination {
    SOS,
    SOS_SETTINGS,
    CAMERA,
    GALLERY,
    APPS,
}

@Composable
fun SafetyMediaAppsScreen(
    initialDestination: SafetyMediaAppsDestination,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    contacts: List<ClaroContact> = emptyList(),
    onLoadContacts: () -> Unit = {},
    onOpenProtectedSosSettings: () -> Unit = {},
    onRequestMediaAccess: () -> Unit = {},
    hasActiveCall: Boolean = false,
    onReturnToCall: () -> Unit = {},
    onSearchContacts: (String) -> Unit = {},
    onLoadMoreContacts: () -> Unit = {},
    hasMoreContacts: Boolean = false,
    onScreenViewed: (TelemetryScreen) -> Unit = {},
) {
    var destination by rememberSaveable(initialDestination) { mutableStateOf(initialDestination) }
    val sosState by viewModel.sosState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val screen = when (destination) {
        SafetyMediaAppsDestination.SOS -> when (sosState) {
            is SosState.CountingDown -> TelemetryScreen.SOS_COUNTDOWN
            is SosState.Failed -> TelemetryScreen.SOS_FAILED
            else -> TelemetryScreen.SOS
        }
        SafetyMediaAppsDestination.SOS_SETTINGS -> TelemetryScreen.SOS_SETTINGS
        SafetyMediaAppsDestination.CAMERA -> TelemetryScreen.CAMERA
        SafetyMediaAppsDestination.GALLERY -> TelemetryScreen.GALLERY
        SafetyMediaAppsDestination.APPS -> TelemetryScreen.APPS
    }
    LaunchedEffect(screen, uiState.billingObservability.consent) {
        onScreenViewed(screen)
    }

    DisposableEffect(viewModel) {
        onDispose {
            if (viewModel.sosState.value is SosState.CountingDown) {
                viewModel.interruptSos()
            }
        }
    }
    BackHandler {
        if (viewModel.sosState.value is SosState.CountingDown) {
            viewModel.cancelSos()
        }
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        when (destination) {
            SafetyMediaAppsDestination.SOS -> SosScreen(
                state = sosState,
                onStart = viewModel::startSos,
                onCancel = viewModel::cancelSos,
                remainingSeconds = viewModel::sosRemainingSeconds,
                onOpenProtectedSosSettings = onOpenProtectedSosSettings,
                hasActiveCall = hasActiveCall,
                onReturnToCall = onReturnToCall,
            )
            SafetyMediaAppsDestination.SOS_SETTINGS -> SosSettingsScreen(
                state = sosState,
                onboarding = uiState.preferences.onboarding.currentStep != OnboardingStep.COMPLETE,
                contacts = contacts,
                viewModel = viewModel,
                onLoadContacts = onLoadContacts,
                onTestStarted = { destination = SafetyMediaAppsDestination.SOS },
                onBack = onBack,
                onSearchContacts = onSearchContacts,
                onLoadMoreContacts = onLoadMoreContacts,
                hasMoreContacts = hasMoreContacts,
            )
            SafetyMediaAppsDestination.CAMERA -> CameraLaunchScreen(onClosed = onBack)
            SafetyMediaAppsDestination.GALLERY -> DeviceGalleryScreen(
                onRequestAccess = onRequestMediaAccess,
            )
            SafetyMediaAppsDestination.APPS -> AppsMenuScreen(
                apps = uiState.home?.installedApps.orEmpty(),
                message = uiState.message,
                allowCustomization = false,
                onBack = onBack,
                onLaunch = { viewModel.launchAllowedApp(it.packageName) },
                onSetAllowed = { app, allowed -> viewModel.setAppAllowed(app.packageName, allowed) },
                onSetOnHome = { app, onHome -> viewModel.setAppOnHome(app.packageName, onHome) },
            )
        }
    }
}

@Composable
internal fun SosScreen(
    state: SosState,
    onStart: (Boolean, Long) -> Unit,
    onCancel: () -> Unit,
    remainingSeconds: (Long) -> Int,
    onOpenProtectedSosSettings: () -> Unit,
    hasActiveCall: Boolean,
    onReturnToCall: () -> Unit,
) {
    val resources = LocalResources.current
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(state) {
        while (state is SosState.CountingDown) {
            now = SystemClock.elapsedRealtime()
            delay(200)
        }
    }

    val contact = state.trustedContactOrNull()
    val canStart = contact?.recipientConsented == true &&
        (state is SosState.Armed || state is SosState.Canceled || state is SosState.Interrupted ||
            state is SosState.Failed || state is SosState.Calling && state.outcome == SosCallOutcome.ENDED)
    val title = when {
        state is SosState.CountingDown -> stringResource(R.string.app_sos_calling_in)
        contact == null || !contact.recipientConsented -> stringResource(R.string.app_sos_setup_title)
        else -> stringResource(R.string.app_call_for_help)
    }
    val nonDispatchWarning = stringResource(R.string.app_sos_non_dispatch_warning)

    ClaroScreenScaffold(
        title = title,
        modifier = Modifier.testTag("sos-screen"),
        dockInSoftkeys = true,
        dock = {
            when {
                state is SosState.CountingDown -> {
                    ClaroPrimaryButton(
                        label = stringResource(R.string.app_sos_stop),
                        onClick = onCancel,
                        containerColor = ClaroColors.Red,
                    )
                }
                hasActiveCall -> {
                    ClaroPrimaryButton(
                        label = stringResource(R.string.app_return_to_call),
                        onClick = onReturnToCall,
                        icon = ClaroIconKind.PHONE,
                    )
                }
                contact == null || !contact.recipientConsented -> {
                    ClaroPrimaryButton(
                        label = stringResource(R.string.app_sos_setup_title),
                        onClick = onOpenProtectedSosSettings,
                        icon = ClaroIconKind.CONTACT,
                    )
                }
                canStart -> {
                    ClaroPrimaryButton(
                        label = stringResource(R.string.app_call_in_5_seconds),
                        softkey = ClaroSoftkey.GREEN,
                        softkeyLabel = stringResource(R.string.app_sos_call_contact),
                        icon = ClaroIconKind.PHONE,
                        onClick = { onStart(false, SystemClock.elapsedRealtime()) },
                        containerColor = ClaroColors.Red,
                    )
                }
            }
            if (state !is SosState.CountingDown) {
                if (contact != null && contact.recipientConsented && !hasActiveCall) {
                    ClaroSecondaryButton(
                        label = stringResource(R.string.app_sos_change_contact),
                        onClick = onOpenProtectedSosSettings,
                        softkey = ClaroSoftkey.MENU,
                        icon = ClaroIconKind.CONTACT,
                    )
                }
            }
        },
    ) {
        when (state) {
            is SosState.Armed -> {
                SosRecipientCard(
                    displayName = state.contact.displayName,
                    destination = state.contact.destination,
                    photoUri = state.contact.photoUri,
                )
                ClaroInfoCard(
                    text = nonDispatchWarning,
                    tone = ClaroBannerTone.WARNING,
                )
                if (!state.contact.recipientConsented) {
                    ClaroInfoCard(
                        text = stringResource(R.string.app_sos_choose_agreed_contact),
                        tone = ClaroBannerTone.ERROR,
                    )
                }
            }
            is SosState.Unset -> {
                ClaroInfoCard(
                    text = stringResource(R.string.app_sos_choose_help_contact),
                    tone = ClaroBannerTone.WARNING,
                )
                ClaroInfoCard(
                    text = nonDispatchWarning,
                    tone = ClaroBannerTone.WARNING,
                )
                if (state.message != "SOS is not configured.") {
                    ClaroInfoCard(
                        text = localizeSosUnsetMessage(state.message, resources),
                        tone = ClaroBannerTone.ERROR,
                    )
                }
            }
            is SosState.CountingDown -> {
                Text(
                    text = "${remainingSeconds(now)}",
                    color = ClaroColors.Red,
                    fontSize = 104.sp,
                    lineHeight = 112.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
                Text(
                    text = state.contact.displayName,
                    color = ClaroColors.Ink,
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = state.contact.destination,
                    color = ClaroColors.Muted,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontSize = 20.sp,
                        lineHeight = 25.sp,
                        textDirection = TextDirection.Ltr,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.testMode) {
                    ClaroInfoCard(
                        text = stringResource(R.string.app_sos_test_call_mode),
                        tone = ClaroBannerTone.INFO,
                    )
                }
                Text(
                    text = stringResource(R.string.app_sos_back_or_home_cancels),
                    color = ClaroColors.Muted,
                    fontSize = 19.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
            is SosState.Canceled -> {
                ClaroInfoCard(
                    text = stringResource(R.string.app_sos_canceled_no_call),
                    tone = ClaroBannerTone.SUCCESS,
                )
                SosRecipientCard(
                    displayName = state.contact.displayName,
                    destination = state.contact.destination,
                    photoUri = state.contact.photoUri,
                )
            }
            is SosState.Interrupted -> {
                ClaroInfoCard(
                    text = stringResource(R.string.app_sos_canceled_no_call),
                    tone = ClaroBannerTone.INFO,
                )
                SosRecipientCard(
                    displayName = state.contact.displayName,
                    destination = state.contact.destination,
                    photoUri = state.contact.photoUri,
                )
            }
            is SosState.Calling -> {
                SosRecipientCard(
                    displayName = state.contact.displayName,
                    destination = state.contact.destination,
                    photoUri = state.contact.photoUri,
                )
                ClaroInfoCard(
                    text = stringResource(
                        R.string.app_sos_calling_unconfirmed,
                        state.outcome.friendlyName(),
                    ),
                    tone = ClaroBannerTone.INFO,
                )
                ClaroInfoCard(
                    text = nonDispatchWarning,
                    tone = ClaroBannerTone.WARNING,
                )
            }
            is SosState.Failed -> {
                SosRecipientCard(
                    displayName = state.contact.displayName,
                    destination = state.contact.destination,
                    photoUri = state.contact.photoUri,
                )
                ClaroInfoCard(
                    text = stringResource(
                        R.string.app_sos_failed_no_one_reached,
                        state.outcome.friendlyName(),
                    ),
                    tone = ClaroBannerTone.ERROR,
                )
                ClaroInfoCard(
                    text = nonDispatchWarning,
                    tone = ClaroBannerTone.WARNING,
                )
            }
        }
    }
}

@Composable
private fun SosRecipientCard(
    displayName: String,
    destination: String,
    photoUri: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(ClaroColors.InfoBlueBg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(ClaroColors.Blue),
        )
        ClaroAvatar(
            name = displayName,
            photoUri = photoUri,
            contactNumber = destination,
            size = 64.dp,
            modifier = Modifier.padding(10.dp),
        )
        Column(
            modifier = Modifier.weight(1f).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = displayName,
                color = ClaroColors.InfoBlueInk,
                fontSize = 23.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = destination,
                color = ClaroColors.InfoBlueInk,
                style = TextStyle(
                    fontSize = 21.sp,
                    lineHeight = 26.sp,
                    textDirection = TextDirection.Ltr,
                ),
            )
        }
    }
}

@Composable
private fun SosSettingsScreen(
    state: SosState,
    contacts: List<ClaroContact>,
    viewModel: MainViewModel,
    onLoadContacts: () -> Unit,
    onTestStarted: () -> Unit,
    onBack: () -> Unit,
    onSearchContacts: (String) -> Unit,
    onLoadMoreContacts: () -> Unit,
    hasMoreContacts: Boolean,
    onboarding: Boolean = false,
) {
    val resources = LocalResources.current
    val existing = state.trustedContactOrNull()
    var name by rememberSaveable { mutableStateOf(existing?.displayName.orEmpty()) }
    var number by rememberSaveable { mutableStateOf(existing?.destination.orEmpty()) }
    var consented by rememberSaveable { mutableStateOf(existing?.recipientConsented ?: false) }
    var confirmingTest by rememberSaveable { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Pair<String, ClaroBannerTone>?>(null) }
    var pickingContact by rememberSaveable { mutableStateOf(false) }
    var contactLookupKey by rememberSaveable {
        mutableStateOf(existing?.contactId?.takeUnless { it == "manual-number" || it == "trusted-contact" })
    }
    var photoUri by rememberSaveable { mutableStateOf(existing?.photoUri) }

    if (pickingContact) {
        SosContactPicker(
            contacts = contacts,
            onSearch = onSearchContacts,
            onLoadContacts = onLoadContacts,
            onLoadMore = onLoadMoreContacts,
            hasMore = hasMoreContacts,
            onChoose = { contact, selectedNumber ->
                name = contact.displayName
                number = selectedNumber
                contactLookupKey = contact.lookupKey
                photoUri = contact.photoUri
                consented = false
                feedback = null
                pickingContact = false
            },
            onCancel = { pickingContact = false },
            onboarding = onboarding,
        )
        return
    }

    ClaroScreenScaffold(
        title = stringResource(R.string.app_sos_contact_title),
        headerNavigation = if (!onboarding) null else {
            {
                ClaroHeaderBackButton(
                    label = stringResource(R.string.app_back),
                    onClick = onBack,
                )
            }
        },
        dockInSoftkeys = true,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.app_save),
                enabled = number.isNotBlank() && consented,
                onClick = {
                    val result = viewModel.configureSos(name, number, consented, contactLookupKey, photoUri)
                    if (result == SosReadiness.Ready) {
                        onBack()
                    } else {
                        feedback = result.configurationError(resources) to ClaroBannerTone.ERROR
                    }
                },
                containerColor = ClaroColors.Blue,
            )
            ClaroSecondaryButton(
                label = stringResource(R.string.app_cancel),
                softkey = ClaroSoftkey.BACK,
                onClick = onBack,
            )
        },
    ) {
        feedback?.let { (text, tone) ->
            ClaroInfoCard(text = text, tone = tone)
        }

        ClaroInfoCard(text = stringResource(R.string.app_sos_setup_choice_info))
        ClaroSecondaryButton(
            label = stringResource(R.string.app_choose_from_contacts),
            onClick = {
                pickingContact = true
                onLoadContacts()
            },
            icon = ClaroIconKind.CONTACT,
        )
        if (number.isNotBlank()) {
            SosRecipientCard(
                displayName = name.ifBlank { stringResource(R.string.app_sos_custom_number) },
                destination = number,
                photoUri = photoUri,
            )
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.app_sos_name_optional)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = number,
            onValueChange = {
                if (number != it) {
                    consented = false
                    contactLookupKey = null
                    photoUri = null
                }
                number = it
            },
            label = { Text(stringResource(R.string.app_phone_number)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            textStyle = TextStyle(
                textDirection = TextDirection.Ltr,
                fontSize = 24.sp,
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ClaroDimensions.PrimaryTarget)
                .background(ClaroColors.Surface)
                .clickable(role = Role.Checkbox) { consented = !consented }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = consented,
                onCheckedChange = null,
            )
            Text(
                text = stringResource(R.string.app_sos_recipient_agreed),
                color = ClaroColors.Ink,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            )
        }
        ClaroInfoCard(
            text = stringResource(R.string.app_sos_settings_warning),
            tone = ClaroBannerTone.WARNING,
        )
        if (existing?.recipientConsented == true && !confirmingTest) {
            ClaroSecondaryButton(
                label = stringResource(R.string.app_sos_test_call),
                onClick = { confirmingTest = true },
            )
        } else if (confirmingTest) {
            ClaroInfoCard(
                text = stringResource(R.string.app_sos_test_mode_notice),
                tone = ClaroBannerTone.INFO,
            )
            ClaroPrimaryButton(
                label = stringResource(R.string.app_sos_start_test),
                onClick = {
                    val result = viewModel.configureSos(name, number, consented, contactLookupKey, photoUri)
                    if (result != SosReadiness.Ready) {
                        feedback = result.configurationError(resources) to ClaroBannerTone.ERROR
                        confirmingTest = false
                    } else {
                        confirmingTest = false
                        viewModel.startSos(true, SystemClock.elapsedRealtime())
                        if (viewModel.sosState.value is SosState.CountingDown) {
                            onTestStarted()
                        }
                    }
                },
                containerColor = ClaroColors.Red,
            )
            ClaroSecondaryButton(
                label = stringResource(R.string.app_cancel),
                onClick = { confirmingTest = false },
            )
        }
    }
}

@Composable
private fun SosContactPicker(
    contacts: List<ClaroContact>,
    onSearch: (String) -> Unit,
    onLoadContacts: () -> Unit,
    onLoadMore: () -> Unit,
    hasMore: Boolean,
    onChoose: (ClaroContact, String) -> Unit,
    onCancel: () -> Unit,
    onboarding: Boolean = false,
) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { onSearch("") }
    BackHandler(onBack = onCancel)
    ClaroScreenScaffold(
        title = stringResource(R.string.app_choose_from_contacts),
        scrollable = false,
        headerNavigation = if (!onboarding) null else {
            {
                ClaroHeaderBackButton(
                    label = stringResource(R.string.app_back),
                    onClick = onCancel,
                )
            }
        },
        dock = if (onboarding) null else {
            { ClaroSecondaryButton(label = stringResource(R.string.app_cancel), onClick = onCancel) }
        },
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; onSearch(it) },
            label = { Text(stringResource(R.string.app_search)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (contacts.isEmpty()) {
                item {
                    ClaroInfoCard(text = stringResource(R.string.app_no_contacts_available))
                    ClaroSecondaryButton(
                        label = stringResource(R.string.app_load_contacts),
                        onClick = onLoadContacts,
                    )
                }
            }
            items(contacts, key = { it.lookupKey }) { contact ->
                contact.numbers.forEach { number ->
                    ClaroRowButton(
                        title = contact.displayName,
                        subtitle = "\u2066${number.label} · ${number.value}\u2069",
                        leading = {
                            ClaroAvatar(
                                contact.displayName,
                                photoUri = contact.photoUri,
                                size = 44.dp,
                                contactLookupKey = contact.lookupKey,
                            )
                        },
                        onClick = { onChoose(contact, number.value) },
                    )
                }
            }
            if (hasMore && contacts.isNotEmpty()) {
                item {
                    ClaroSecondaryButton(
                        label = stringResource(R.string.app_sos_load_more_contacts),
                        onClick = onLoadMore,
                    )
                }
            }
        }
    }
}

internal fun SosReadiness.configurationError(resources: Resources): String = when (this) {
    SosReadiness.RecipientConsentMissing -> resources.getString(R.string.app_sos_setup_consent_required)
    is SosReadiness.Blocked -> localizeSosUnsetMessage(reason, resources)
    SosReadiness.NotConfigured -> resources.getString(R.string.app_sos_invalid_number)
    SosReadiness.Ready -> error("A saved SOS configuration has no error")
}

internal fun SosState.trustedContactOrNull(): TrustedContact? = when (this) {
    is SosState.Unset -> null
    is SosState.Armed -> contact
    is SosState.CountingDown -> contact
    is SosState.Interrupted -> contact
    is SosState.Canceled -> contact
    is SosState.Calling -> contact
    is SosState.Failed -> contact
}

private fun localizeSosUnsetMessage(message: String, resources: Resources): String = when (message) {
    "SOS contact could not be saved." -> resources.getString(R.string.app_sos_contact_save_failed)
    "Enter a valid SOS phone number." -> resources.getString(R.string.app_sos_invalid_number)
    "Emergency numbers cannot be saved as trusted contacts." ->
        resources.getString(R.string.app_sos_emergency_number_not_allowed)
    else -> message
}

@Composable
private fun SosCallOutcome.friendlyName(): String = when (this) {
    SosCallOutcome.DIALING -> stringResource(R.string.app_sos_outcome_dialing)
    SosCallOutcome.RINGING -> stringResource(R.string.app_sos_outcome_ringing)
    SosCallOutcome.CONNECTED -> stringResource(R.string.app_sos_outcome_connected)
    SosCallOutcome.ENDED -> stringResource(R.string.app_sos_outcome_ended)
    SosCallOutcome.BUSY -> stringResource(R.string.app_sos_outcome_busy)
    SosCallOutcome.NO_ANSWER -> stringResource(R.string.app_sos_outcome_no_answer)
    SosCallOutcome.DECLINED -> stringResource(R.string.app_sos_outcome_declined)
    SosCallOutcome.NO_SERVICE -> stringResource(R.string.app_sos_outcome_no_service)
    SosCallOutcome.ROLE_OR_PERMISSION_LOST -> stringResource(R.string.app_sos_outcome_permission_lost)
    SosCallOutcome.UNKNOWN_FAILURE -> stringResource(R.string.app_sos_outcome_unknown_failure)
}
