package com.clarohome.app

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.platform.testTag
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
) {
    var destination by rememberSaveable(initialDestination) { mutableStateOf(initialDestination) }
    val sosState by viewModel.sosState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val library = remember { MediaLibrary() }
    val photos = remember { mutableStateListOf<ClaroPhoto>().apply { addAll(library.all()) } }

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
            SafetyMediaAppsDestination.CAMERA -> CameraGalleryScreen(
                showCameraFirst = true,
                library = library,
                photos = photos,
                onShowCamera = { destination = SafetyMediaAppsDestination.CAMERA },
                onShowGallery = { destination = SafetyMediaAppsDestination.GALLERY },
            )
            SafetyMediaAppsDestination.GALLERY -> CameraGalleryScreen(
                showCameraFirst = false,
                library = library,
                photos = photos,
                onShowCamera = { destination = SafetyMediaAppsDestination.CAMERA },
                onShowGallery = { destination = SafetyMediaAppsDestination.GALLERY },
            )
            SafetyMediaAppsDestination.APPS -> AppsMenuScreen(
                apps = uiState.home?.installedApps.orEmpty(),
                allowCustomization = viewModel.isSettingsUnlocked(),
                onBack = onBack,
                onLaunch = { viewModel.launchAllowedApp(it.packageName) },
                onSetAllowed = { app, allowed -> viewModel.setAppAllowed(app.packageName, allowed) },
                onSetOnHome = { app, onHome -> viewModel.setAppOnHome(app.packageName, onHome) },
                onRequestCustomize = onOpenProtectedSosSettings,
            )
        }
    }
}

private const val NON_DISPATCH_SOS_WARNING =
    "Not an emergency service. Calls your contact only. No message or location sent."

@Composable
private fun SosScreen(
    state: SosState,
    viewModel: MainViewModel,
    onOpenProtectedSosSettings: () -> Unit,
) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(state) {
        while (state is SosState.CountingDown) {
            now = SystemClock.elapsedRealtime()
            delay(200)
        }
    }

    val title = if (state is SosState.CountingDown) "Calling in…" else "Call for help"

    ClaroScreenScaffold(
        title = title,
        modifier = Modifier.testTag("sos-screen"),
        dock = {
            when (state) {
                is SosState.Armed -> {
                    ClaroSecondaryButton(
                        label = "Settings",
                        onClick = onOpenProtectedSosSettings,
                    )
                    ClaroPrimaryButton(
                        label = "Call in 5 seconds",
                        onClick = { viewModel.startSos(false, SystemClock.elapsedRealtime()) },
                        containerColor = ClaroColors.Red,
                        enabled = state.contact.recipientConsented,
                    )
                    ClaroEmergencyButton(
                        label = "Emergency call",
                        onClick = { viewModel.openEmergencyDialer() },
                    )
                }
                is SosState.Unset -> {
                    ClaroSecondaryButton(
                        label = "Settings",
                        onClick = onOpenProtectedSosSettings,
                    )
                    ClaroPrimaryButton(
                        label = "Call in 5 seconds",
                        onClick = {},
                        containerColor = ClaroColors.Red,
                        enabled = false,
                    )
                    ClaroEmergencyButton(
                        label = "Emergency call",
                        onClick = { viewModel.openEmergencyDialer() },
                    )
                }
                is SosState.CountingDown -> {
                    ClaroPrimaryButton(
                        label = "STOP",
                        onClick = { viewModel.cancelSos() },
                        containerColor = ClaroColors.Red,
                    )
                }
                is SosState.Canceled -> {
                    ClaroPrimaryButton(
                        label = "Try again",
                        onClick = {
                            viewModel.startSos(state.testMode, SystemClock.elapsedRealtime())
                        },
                        containerColor = ClaroColors.Red,
                        enabled = state.contact.recipientConsented,
                    )
                    ClaroEmergencyButton(
                        label = "Emergency call",
                        onClick = { viewModel.openEmergencyDialer() },
                    )
                }
                is SosState.Interrupted -> {
                    ClaroPrimaryButton(
                        label = "Try again",
                        onClick = {
                            viewModel.startSos(state.testMode, SystemClock.elapsedRealtime())
                        },
                        containerColor = ClaroColors.Red,
                        enabled = state.contact.recipientConsented,
                    )
                    ClaroEmergencyButton(
                        label = "Emergency call",
                        onClick = { viewModel.openEmergencyDialer() },
                    )
                }
                is SosState.Calling -> {
                    ClaroPrimaryButton(
                        label = "Try again",
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
                        label = "Emergency call",
                        onClick = { viewModel.openEmergencyDialer() },
                    )
                }
                is SosState.Failed -> {
                    ClaroPrimaryButton(
                        label = "Try again",
                        onClick = {
                            viewModel.startSos(state.testMode, SystemClock.elapsedRealtime())
                        },
                        containerColor = ClaroColors.Red,
                        enabled = state.contact.recipientConsented,
                    )
                    ClaroEmergencyButton(
                        label = "Emergency call",
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
                    text = NON_DISPATCH_SOS_WARNING,
                    tone = ClaroBannerTone.WARNING,
                )
                if (!state.contact.recipientConsented) {
                    ClaroInfoCard(
                        text = "Choose a contact who has agreed in Settings.",
                        tone = ClaroBannerTone.ERROR,
                    )
                }
            }
            is SosState.Unset -> {
                SosWordmark()
                ClaroInfoCard(
                    text = "Choose a help contact in Settings.",
                    tone = ClaroBannerTone.WARNING,
                )
                ClaroInfoCard(
                    text = NON_DISPATCH_SOS_WARNING,
                    tone = ClaroBannerTone.WARNING,
                )
                if (state.message != "SOS is not configured.") {
                    ClaroInfoCard(
                        text = state.message,
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
                        text = "Test call mode.",
                        tone = ClaroBannerTone.INFO,
                    )
                }
                Text(
                    text = "Back or Home cancels the call.",
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
                    text = "Canceled. No call started.",
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
                    text = "Canceled. No call started.",
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
                    text = "${state.outcome.friendlyName()}. Help is not confirmed.",
                    tone = ClaroBannerTone.INFO,
                )
                ClaroInfoCard(
                    text = NON_DISPATCH_SOS_WARNING,
                    tone = ClaroBannerTone.WARNING,
                )
            }
            is SosState.Failed -> {
                SosRecipientCard(
                    displayName = state.contact.displayName,
                    destination = state.contact.destination,
                )
                ClaroInfoCard(
                    text = "${state.outcome.friendlyName()}. No one reached. No alert or location delivered.",
                    tone = ClaroBannerTone.ERROR,
                )
                ClaroInfoCard(
                    text = NON_DISPATCH_SOS_WARNING,
                    tone = ClaroBannerTone.WARNING,
                )
            }
        }
    }
}

@Composable
private fun SosWordmark() {
    Text(
        text = "SOS",
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
    val existing = state.trustedContactOrNull()
    var name by rememberSaveable { mutableStateOf(existing?.displayName.orEmpty()) }
    var number by rememberSaveable { mutableStateOf(existing?.destination.orEmpty()) }
    var consented by rememberSaveable { mutableStateOf(existing?.recipientConsented ?: false) }
    var confirmingTest by rememberSaveable { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Pair<String, ClaroBannerTone>?>(null) }

    ClaroScreenScaffold(
        title = "SOS contact",
        dock = {
            ClaroPrimaryButton(
                label = "Save",
                onClick = {
                    viewModel.configureSos(name, number, consented)
                    val updated = viewModel.sosState.value
                    if (name.isNotBlank() && number.isNotBlank() && updated is SosState.Unset) {
                        feedback = updated.message to ClaroBannerTone.ERROR
                    } else {
                        onBack()
                    }
                },
                containerColor = ClaroColors.Blue,
            )
            ClaroSecondaryButton(
                label = "Cancel",
                onClick = onBack,
            )
        },
    ) {
        feedback?.let { (text, tone) ->
            ClaroInfoCard(text = text, tone = tone)
        }

        if (contacts.isEmpty()) {
            ClaroSecondaryButton(
                label = "Load contacts",
                onClick = onLoadContacts,
                icon = ClaroIconKind.CONTACT,
            )
        } else {
            Text(
                text = "Choose from contacts",
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
                            feedback = "Selected ${contact.displayName}." to ClaroBannerTone.INFO
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Trusted person") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = number,
            onValueChange = { number = it },
            label = { Text("Phone number") },
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
                text = "They agreed to receive help calls.",
                color = ClaroColors.Ink,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            )
        }
        ClaroInfoCard(
            text = "Not an emergency service. Needs mobile service and an answer. No message or location sent.",
            tone = ClaroBannerTone.WARNING,
        )
        if (!confirmingTest) {
            ClaroSecondaryButton(
                label = "Test call",
                onClick = { confirmingTest = true },
            )
        } else {
            ClaroInfoCard(
                text = "Test mode: tell your contact before placing a real test call.",
                tone = ClaroBannerTone.INFO,
            )
            ClaroPrimaryButton(
                label = "Start test",
                onClick = {
                    viewModel.configureSos(name, number, consented)
                    if (name.isBlank() || number.isBlank() || !consented || viewModel.sosState.value is SosState.Unset) {
                        val reason = (viewModel.sosState.value as? SosState.Unset)
                            ?.takeIf { it.message != "SOS is not configured." }
                            ?.message
                            ?: "Enter a non-emergency contact and confirm they agreed before testing."
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
                label = "Cancel",
                onClick = { confirmingTest = false },
            )
        }
    }
}

@Composable
private fun CameraGalleryScreen(
    showCameraFirst: Boolean,
    library: MediaLibrary,
    photos: SnapshotStateList<ClaroPhoto>,
    onShowCamera: () -> Unit,
    onShowGallery: () -> Unit,
) {
    val context = LocalContext.current
    var status by remember(showCameraFirst) {
        mutableStateOf<Pair<String, ClaroBannerTone>?>(null)
    }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var pendingOwnedDeleteId by remember { mutableStateOf<String?>(null) }
    var selectedPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmingRemoval by rememberSaveable { mutableStateOf(false) }

    val syncPhotos: (String?) -> Unit = { removedId ->
        val updated = library.all()
        photos.clear()
        photos.addAll(updated)
        if (removedId != null && selectedPhotoId == removedId) {
            selectedPhotoId = updated.firstOrNull()?.id
            confirmingRemoval = false
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val photoId = pendingOwnedDeleteId
        val authorized = result.resultCode == Activity.RESULT_OK
        val removed = photoId != null && library.confirmOwnedDeletion(photoId, authorized)
        syncPhotos(if (removed) photoId else null)
        status = if (removed) {
            "Photo deleted." to ClaroBannerTone.SUCCESS
        } else {
            "Delete canceled or denied. Photo kept." to ClaroBannerTone.WARNING
        }
        pendingOwnedDeleteId = null
    }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val uri = pendingCaptureUri
        if (saved && uri != null) {
            val photo = ClaroPhoto(
                id = uri.toString(),
                title = "Camera photo ${photos.size + 1}",
                contentUri = uri.toString(),
                ownership = PhotoOwnership.CLARO_OWNED,
            )
            library.addOwned(photo)
            syncPhotos(null)
            status = "Picture saved. Tap Gallery to view it." to ClaroBannerTone.SUCCESS
        } else {
            uri?.let { context.contentResolver.delete(it, null, null) }
            status = "Camera canceled. No picture saved." to ClaroBannerTone.WARNING
        }
        pendingCaptureUri = null
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) {
            status = "Photo selection canceled." to ClaroBannerTone.INFO
        } else {
            val persistable = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess
            val photo = ClaroPhoto(
                id = uri.toString(),
                title = "Selected photo ${photos.size + 1}",
                contentUri = uri.toString(),
                ownership = PhotoOwnership.REFERENCED,
                availability = PhotoAvailability.AVAILABLE,
            )
            library.addReferenced(
                photo,
                MediaUriGrant(read = true, write = false, persistable = persistable),
            )
            syncPhotos(null)
            status = if (persistable) {
                "Picture added to Claro." to ClaroBannerTone.SUCCESS
            } else {
                "Picture added. Android may ask you to select it again later." to ClaroBannerTone.INFO
            }
        }
    }

    if (showCameraFirst) {
        ClaroScreenScaffold(
            title = "Camera",
            dock = {
                ClaroPrimaryButton(
                    label = "Open camera",
                    onClick = {
                        val uri = createCaptureUri(context)
                        if (uri == null) {
                            status = "Camera or storage is unavailable." to ClaroBannerTone.ERROR
                        } else {
                            pendingCaptureUri = uri
                            runCatching { captureLauncher.launch(uri) }
                                .onFailure {
                                    context.contentResolver.delete(uri, null, null)
                                    pendingCaptureUri = null
                                    status = "No system camera is available." to ClaroBannerTone.ERROR
                                }
                        }
                    },
                    containerColor = ClaroColors.Blue,
                    icon = ClaroIconKind.CAMERA,
                )
                ClaroSecondaryButton(
                    label = "Gallery",
                    onClick = onShowGallery,
                )
            },
        ) {
            ClaroInfoCard(
                text = "Opens the Android camera outside Claro. Camera controls are not enlarged by Claro.",
                tone = ClaroBannerTone.INFO,
            )
            status?.let { (message, tone) ->
                ClaroInfoCard(text = message, tone = tone)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ClaroColors.PaleOrange)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ClaroIcon(
                    kind = ClaroIconKind.CAMERA,
                    tint = ClaroColors.Orange,
                    size = 72.dp,
                )
                Text(
                    text = "Take a picture in your phone's camera app, then return here.",
                    color = ClaroColors.Ink,
                    fontSize = 21.sp,
                    lineHeight = 27.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    val selectedIndex = photos.indexOfFirst { it.id == selectedPhotoId }
    val selectedPhoto = photos.getOrNull(selectedIndex)

    BackHandler(enabled = selectedPhoto != null) {
        selectedPhotoId = null
        confirmingRemoval = false
    }

    ClaroScreenScaffold(
        title = selectedPhoto?.title ?: "Gallery",
        dock = {
            ClaroPrimaryButton(
                label = "Add picture",
                onClick = {
                    pickerLauncher.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
                containerColor = ClaroColors.Blue,
            )
            ClaroSecondaryButton(
                label = "Camera",
                onClick = {
                    selectedPhotoId = null
                    confirmingRemoval = false
                    onShowCamera()
                },
                icon = ClaroIconKind.CAMERA,
            )
        },
    ) {
        status?.let { (message, tone) ->
            ClaroInfoCard(text = message, tone = tone)
        }

        if (selectedPhoto != null) {
            PhotoDetailContent(
                photo = selectedPhoto,
                hasMultiple = photos.size > 1,
                confirmingRemoval = confirmingRemoval,
                onPrevious = {
                    val prevIndex = (selectedIndex - 1 + photos.size) % photos.size
                    selectedPhotoId = photos[prevIndex].id
                    confirmingRemoval = false
                },
                onNext = {
                    val nextIndex = (selectedIndex + 1) % photos.size
                    selectedPhotoId = photos[nextIndex].id
                    confirmingRemoval = false
                },
                onRequestRemove = { confirmingRemoval = true },
                onCancelRemove = { confirmingRemoval = false },
                onConfirmRemove = {
                    val request = library.requestRemoval(selectedPhoto.id)
                    val removed = when (request) {
                        is PhotoRemovalRequest.RemoveReference ->
                            library.confirmReferenceRemoval(request.photoId)
                        is PhotoRemovalRequest.RequestOwnedDeleteAuthorization -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val deleteRequest = MediaStore.createDeleteRequest(
                                    context.contentResolver,
                                    listOf(request.contentUri.toUri()),
                                )
                                pendingOwnedDeleteId = request.photoId
                                deleteLauncher.launch(
                                    IntentSenderRequest.Builder(deleteRequest.intentSender).build(),
                                )
                                false
                            } else {
                                val deleted = runCatching {
                                    context.contentResolver.delete(
                                        request.contentUri.toUri(),
                                        null,
                                        null,
                                    ) > 0
                                }.getOrDefault(false)
                                library.confirmOwnedDeletion(request.photoId, deleted)
                            }
                        }
                        null -> false
                    }
                    if (pendingOwnedDeleteId == null) {
                        confirmingRemoval = false
                        syncPhotos(if (removed) selectedPhoto.id else null)
                        status = if (removed) {
                            "Picture removed." to ClaroBannerTone.SUCCESS
                        } else {
                            "Could not delete. Picture kept." to ClaroBannerTone.ERROR
                        }
                    } else {
                        confirmingRemoval = false
                        status = "Waiting for Android delete confirmation." to ClaroBannerTone.INFO
                    }
                },
                onBackToGrid = {
                    selectedPhotoId = null
                    confirmingRemoval = false
                },
            )
        } else if (photos.isEmpty()) {
            ClaroInfoCard(
                text = "No pictures yet.",
                tone = ClaroBannerTone.INFO,
            )
        } else {
            photos.chunked(2).forEachIndexed { rowIndex, rowPhotos ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
                ) {
                    rowPhotos.forEachIndexed { colIndex, photo ->
                        val itemIndex = rowIndex * 2 + colIndex
                        PhotoGridTile(
                            photo = photo,
                            evenTile = itemIndex % 2 == 0,
                            onClick = {
                                selectedPhotoId = photo.id
                                confirmingRemoval = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowPhotos.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoGridTile(
    photo: ClaroPhoto,
    evenTile: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(162.dp),
        shape = RoundedCornerShape(2.dp),
        color = if (evenTile) Color(0xFFB6E2FC) else Color(0xFFE6D8FF),
        border = BorderStroke(1.dp, ClaroColors.Border),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            ClaroIcon(
                kind = ClaroIconKind.GALLERY,
                tint = if (evenTile) ClaroColors.Blue else ClaroColors.Violet,
                size = 36.dp,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.94f))
                    .padding(horizontal = 7.dp, vertical = 5.dp),
            ) {
                Text(
                    text = photo.title,
                    color = ClaroColors.Ink,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PhotoDetailContent(
    photo: ClaroPhoto,
    hasMultiple: Boolean,
    confirmingRemoval: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRequestRemove: () -> Unit,
    onCancelRemove: () -> Unit,
    onConfirmRemove: () -> Unit,
    onBackToGrid: () -> Unit,
) {
    val actionLabel = if (photo.ownership == PhotoOwnership.CLARO_OWNED) {
        "Delete photo"
    } else {
        "Remove from Claro"
    }
    val ownershipBadge = if (photo.ownership == PhotoOwnership.CLARO_OWNED) {
        "Saved by Claro"
    } else {
        "Device photo reference"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            .background(
                if (photo.ownership == PhotoOwnership.CLARO_OWNED) {
                    Color(0xFFB6E2FC)
                } else {
                    Color(0xFFE6D8FF)
                },
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = ownershipBadge,
            color = Color(0xFF174B93),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(Color(0xFFE4EDFC), RoundedCornerShape(2.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        ClaroIcon(
            kind = ClaroIconKind.GALLERY,
            tint = ClaroColors.Charcoal,
            size = 64.dp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            text = photo.title,
            color = ClaroColors.Ink,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.94f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }

    when (photo.availability) {
        PhotoAvailability.PERMISSION_LOST -> ClaroInfoCard(
            text = "Access was removed. Select this photo again.",
            tone = ClaroBannerTone.WARNING,
        )
        PhotoAvailability.CLOUD_UNAVAILABLE -> ClaroInfoCard(
            text = "Connect to view cloud photos.",
            tone = ClaroBannerTone.WARNING,
        )
        PhotoAvailability.MISSING -> ClaroInfoCard(
            text = "This photo is missing.",
            tone = ClaroBannerTone.ERROR,
        )
        PhotoAvailability.AVAILABLE -> Unit
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
    ) {
        ClaroSecondaryButton(
            label = "← Previous",
            onClick = onPrevious,
            enabled = hasMultiple,
            modifier = Modifier.weight(1f),
        )
        ClaroSecondaryButton(
            label = "Next →",
            onClick = onNext,
            enabled = hasMultiple,
            modifier = Modifier.weight(1f),
        )
    }

    if (!confirmingRemoval) {
        ClaroSecondaryButton(
            label = actionLabel,
            onClick = onRequestRemove,
        )
    } else {
        ClaroInfoCard(
            text = if (photo.ownership == PhotoOwnership.CLARO_OWNED) {
                "Delete this photo from your device? Android may ask you to confirm."
            } else {
                "Remove from Claro? The original stays on your device."
            },
            tone = ClaroBannerTone.WARNING,
        )
        ClaroPrimaryButton(
            label = actionLabel,
            onClick = onConfirmRemove,
            containerColor = ClaroColors.Red,
        )
        ClaroSecondaryButton(
            label = "Cancel",
            onClick = onCancelRemove,
        )
    }

    ClaroSecondaryButton(
        label = "All pictures",
        onClick = onBackToGrid,
    )
}

private fun createCaptureUri(context: Context): Uri? {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "claro-${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Claro")
    }
    return runCatching {
        context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        )
    }.getOrNull()
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

private fun SosCallOutcome.friendlyName(): String = when (this) {
    SosCallOutcome.DIALING -> "Calling"
    SosCallOutcome.RINGING -> "Ringing"
    SosCallOutcome.CONNECTED -> "Connected"
    SosCallOutcome.ENDED -> "Call ended"
    SosCallOutcome.BUSY -> "Line busy"
    SosCallOutcome.NO_ANSWER -> "No answer"
    SosCallOutcome.DECLINED -> "Call declined"
    SosCallOutcome.NO_SERVICE -> "No mobile service"
    SosCallOutcome.ROLE_OR_PERMISSION_LOST -> "Phone access needs repair"
    SosCallOutcome.UNKNOWN_FAILURE -> "Call did not connect"
}
