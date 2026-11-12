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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarohome.core.design.ClaroAvatar
import com.clarohome.core.design.ClaroBannerTone
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.core.design.ClaroEmergencyButton
import com.clarohome.core.design.ClaroIcon
import com.clarohome.core.design.ClaroIconKind
import com.clarohome.core.design.ClaroInfoCard
import com.clarohome.core.design.ClaroPrimaryButton
import com.clarohome.core.design.ClaroRowButton
import com.clarohome.core.design.ClaroScreenScaffold
import com.clarohome.core.design.ClaroSecondaryButton
import com.clarohome.feature.communication.ClaroContact
import com.clarohome.feature.safety.media.apps.ClaroPhoto
import com.clarohome.feature.safety.media.apps.MediaLibrary
import com.clarohome.feature.safety.media.apps.MediaUriGrant
import com.clarohome.feature.safety.media.apps.PhotoAvailability
import com.clarohome.feature.safety.media.apps.PhotoOwnership
import com.clarohome.feature.safety.media.apps.PhotoRemovalRequest
import com.clarohome.feature.safety.media.apps.SosCallOutcome
import com.clarohome.feature.safety.media.apps.SosState
import com.clarohome.feature.safety.media.apps.TrustedContact
import kotlinx.coroutines.delay

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
) {
    var destination by rememberSaveable(initialDestination) { mutableStateOf(initialDestination) }
    val sosState by viewModel.sosState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                viewModel = viewModel,
                onOpenProtectedSosSettings = onOpenProtectedSosSettings,
            )
            SafetyMediaAppsDestination.SOS_SETTINGS -> SosSettingsScreen(
                state = sosState,
                contacts = contacts,
                viewModel = viewModel,
                onLoadContacts = onLoadContacts,
                onTestStarted = { destination = SafetyMediaAppsDestination.SOS },
                onBack = onBack,
            )
            SafetyMediaAppsDestination.CAMERA -> CameraLaunchScreen(
                onCaptured = { destination = SafetyMediaAppsDestination.GALLERY },
                onCanceled = onBack,
            )
            SafetyMediaAppsDestination.GALLERY -> DeviceGalleryScreen(
                onRequestAccess = onRequestMediaAccess,
            )
            SafetyMediaAppsDestination.APPS -> AppsMenuScreen(
                apps = uiState.home?.installedApps.orEmpty(),
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
private fun SosScreen(
    state: SosState,
    viewModel: MainViewModel,
    onOpenProtectedSosSettings: () -> Unit,
) {
    val resources = LocalResources.current
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(state) {
        while (state is SosState.CountingDown) {
            now = SystemClock.elapsedRealtime()
            delay(200)
        }
    }

    val title = if (state is SosState.CountingDown) {
        stringResource(R.string.app_sos_calling_in)
    } else {
        stringResource(R.string.app_call_for_help)
    }
    val nonDispatchWarning = stringResource(R.string.app_sos_non_dispatch_warning)

    ClaroScreenScaffold(
        title = title,
        modifier = Modifier.testTag("sos-screen"),
        dock = {
            when (state) {
                is SosState.Armed -> {
                    ClaroSecondaryButton(
                        label = stringResource(R.string.app_settings),
                        onClick = onOpenProtectedSosSettings,
                    )
                    ClaroPrimaryButton(
                        label = stringResource(R.string.app_call_in_5_seconds),
                        onClick = { viewModel.startSos(false, SystemClock.elapsedRealtime()) },
                        containerColor = ClaroColors.Red,
                        enabled = state.contact.recipientConsented,
                    )
                    ClaroEmergencyButton(
                        label = stringResource(R.string.app_emergency_call),
                        onClick = { viewModel.openEmergencyDialer() },
                    )
                }
                is SosState.Unset -> {
                    ClaroSecondaryButton(
                        label = stringResource(R.string.app_settings),
                        onClick = onOpenProtectedSosSettings,
                    )
                    ClaroPrimaryButton(
                        label = stringResource(R.string.app_call_in_5_seconds),
                        onClick = {},
                        containerColor = ClaroColors.Red,
                        enabled = false,
                    )
                    ClaroEmergencyButton(
                        label = stringResource(R.string.app_emergency_call),
                        onClick = { viewModel.openEmergencyDialer() },
                    )
                }
                is SosState.CountingDown -> {
                    ClaroPrimaryButton(
                        label = stringResource(R.string.app_sos_stop),
                        onClick = { viewModel.cancelSos() },
                        containerColor = ClaroColors.Red,
                    )
                }
                is SosState.Canceled -> {
                    ClaroPrimaryButton(
                        label = stringResource(R.string.app_try_again),
                        onClick = {
                            viewModel.startSos(state.testMode, SystemClock.elapsedRealtime())
                        },
                        containerColor = ClaroColors.Red,
                        enabled = state.contact.recipientConsented,
                    )
                    ClaroEmergencyButton(
                        label = stringResource(R.string.app_emergency_call),
                        onClick = { viewModel.openEmergencyDialer() },
                    )
                }
                is SosState.Interrupted -> {
                    ClaroPrimaryButton(
                        label = stringResource(R.string.app_try_again),
                        onClick = {
                            viewModel.startSos(state.testMode, SystemClock.elapsedRealtime())
                        },
                        containerColor = ClaroColors.Red,
                        enabled = state.contact.recipientConsented,
                    )
                    ClaroEmergencyButton(
                        label = stringResource(R.string.app_emergency_call),
                        onClick = { viewModel.openEmergencyDialer() },
                    )
                }
                is SosState.Calling -> {
                    ClaroPrimaryButton(
                        label = stringResource(R.string.app_try_again),
                        onClick = {
                            viewModel.configureSos(
                                state.contact.displayName,
                                state.contact.destination,
                                state.contact.recipientConsented,
                            )
                            viewModel.startSos(state.testMode, SystemClock.elapsedRealtime())
                        },
                        containerColor = ClaroColors.Red,
                        enabled = state.contact.recipientConsented,
                    )
                    ClaroEmergencyButton(
                        label = stringResource(R.string.app_emergency_call),
                        onClick = { viewModel.openEmergencyDialer() },
                    )
                }
                is SosState.Failed -> {
                    ClaroPrimaryButton(
                        label = stringResource(R.string.app_try_again),
                        onClick = {
                            viewModel.startSos(state.testMode, SystemClock.elapsedRealtime())
                        },
                        containerColor = ClaroColors.Red,
                        enabled = state.contact.recipientConsented,
                    )
                    ClaroEmergencyButton(
                        label = stringResource(R.string.app_emergency_call),
                        onClick = { viewModel.openEmergencyDialer() },
                    )
                }
            }
        },
    ) {
        when (state) {
            is SosState.Armed -> {
                SosWordmark()
                SosRecipientCard(
                    displayName = state.contact.displayName,
                    destination = state.contact.destination,
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
                SosWordmark()
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
                    text = "${viewModel.sosRemainingSeconds(now)}",
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
                SosWordmark()
                ClaroInfoCard(
                    text = stringResource(R.string.app_sos_canceled_no_call),
                    tone = ClaroBannerTone.SUCCESS,
                )
                SosRecipientCard(
                    displayName = state.contact.displayName,
                    destination = state.contact.destination,
                )
            }
            is SosState.Interrupted -> {
                SosWordmark()
                ClaroInfoCard(
                    text = stringResource(R.string.app_sos_canceled_no_call),
                    tone = ClaroBannerTone.INFO,
                )
                SosRecipientCard(
                    displayName = state.contact.displayName,
                    destination = state.contact.destination,
                )
            }
            is SosState.Calling -> {
                SosRecipientCard(
                    displayName = state.contact.displayName,
                    destination = state.contact.destination,
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
private fun SosWordmark() {
    Text(
        text = stringResource(R.string.app_sos),
        color = ClaroColors.Red,
        fontSize = 60.sp,
        lineHeight = 64.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-2).sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    )
}

@Composable
private fun SosRecipientCard(
    displayName: String,
    destination: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(ClaroColors.InfoBlueBg),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(ClaroColors.Blue),
        )
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ClaroAvatar(
                name = displayName,
                size = 52.dp,
            )
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
) {
    val resources = LocalResources.current
    val existing = state.trustedContactOrNull()
    var name by rememberSaveable { mutableStateOf(existing?.displayName.orEmpty()) }
    var number by rememberSaveable { mutableStateOf(existing?.destination.orEmpty()) }
    var consented by rememberSaveable { mutableStateOf(existing?.recipientConsented ?: false) }
    var confirmingTest by rememberSaveable { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Pair<String, ClaroBannerTone>?>(null) }

    ClaroScreenScaffold(
        title = stringResource(R.string.app_sos_contact_title),
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.app_save),
                onClick = {
                    viewModel.configureSos(name, number, consented)
                    val updated = viewModel.sosState.value
                    if (name.isNotBlank() && number.isBlank().not() && updated is SosState.Unset) {
                        feedback = localizeSosUnsetMessage(updated.message, resources) to ClaroBannerTone.ERROR
                    } else {
                        onBack()
                    }
                },
                containerColor = ClaroColors.Blue,
            )
            ClaroSecondaryButton(
                label = stringResource(R.string.app_cancel),
                onClick = onBack,
            )
        },
    ) {
        feedback?.let { (text, tone) ->
            ClaroInfoCard(text = text, tone = tone)
        }

        if (contacts.isEmpty()) {
            ClaroSecondaryButton(
                label = stringResource(R.string.app_load_contacts),
                onClick = onLoadContacts,
                icon = ClaroIconKind.CONTACT,
            )
        } else {
            Text(
                text = stringResource(R.string.app_choose_from_contacts),
                color = ClaroColors.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
            contacts.forEach { contact ->
                contact.numbers.forEach { contactNumber ->
                    ClaroRowButton(
                        title = contact.displayName,
                        subtitle = "\u2066${contactNumber.label} · ${contactNumber.value}\u2069",
                        leading = { ClaroAvatar(name = contact.displayName, size = 44.dp) },
                        onClick = {
                            name = contact.displayName
                            number = contactNumber.value
                            feedback = resources.getString(
                                R.string.app_selected_contact,
                                contact.displayName,
                            ) to ClaroBannerTone.INFO
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.app_trusted_person)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = number,
            onValueChange = { number = it },
            label = { Text(stringResource(R.string.app_phone_number)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
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
        if (!confirmingTest) {
            ClaroSecondaryButton(
                label = stringResource(R.string.app_sos_test_call),
                onClick = { confirmingTest = true },
            )
        } else {
            ClaroInfoCard(
                text = stringResource(R.string.app_sos_test_mode_notice),
                tone = ClaroBannerTone.INFO,
            )
            ClaroPrimaryButton(
                label = stringResource(R.string.app_sos_start_test),
                onClick = {
                    viewModel.configureSos(name, number, consented)
                    if (name.isBlank() || number.isBlank() || !consented || viewModel.sosState.value is SosState.Unset) {
                        val reason = (viewModel.sosState.value as? SosState.Unset)
                            ?.takeIf { it.message != "SOS is not configured." }
                            ?.let { localizeSosUnsetMessage(it.message, resources) }
                            ?: resources.getString(R.string.app_sos_test_requires_agreed_contact)
                        feedback = reason to ClaroBannerTone.ERROR
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

private fun SosState.trustedContactOrNull(): TrustedContact? = when (this) {
    is SosState.Unset -> null
    is SosState.Armed -> contact
    is SosState.CountingDown -> contact
    is SosState.Interrupted -> contact
    is SosState.Canceled -> contact
    is SosState.Calling -> contact
    is SosState.Failed -> contact
}

private fun localizeSosUnsetMessage(message: String, resources: Resources): String = when (message) {
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
