package com.clarohome.app

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.annotation.Keep
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.core.domain.ExternalAppLaunchResult
import com.clarohome.core.model.VisualMode
import com.clarohome.feature.safety.media.apps.AllowedApp
import com.clarohome.feature.safety.media.apps.AllowedAppOpenResult
import com.clarohome.feature.safety.media.apps.AllowedAppsMenu
import com.clarohome.feature.safety.media.apps.AppListFilter
import com.clarohome.feature.safety.media.apps.ClaroPhoto
import com.clarohome.feature.safety.media.apps.MediaLibrary
import com.clarohome.feature.safety.media.apps.MediaUriGrant
import com.clarohome.feature.safety.media.apps.PhotoAvailability
import com.clarohome.feature.safety.media.apps.PhotoOwnership
import com.clarohome.feature.safety.media.apps.PhotoRemovalRequest
import com.clarohome.feature.safety.media.apps.SosCallOutcome
import com.clarohome.feature.safety.media.apps.SosState
import kotlinx.coroutines.delay

@Keep
enum class SafetyMediaAppsDestination {
    SOS,
    CAMERA,
    GALLERY,
    APPS,
}

@Composable
fun SafetyMediaAppsScreen(
    initialDestination: SafetyMediaAppsDestination,
    visualMode: VisualMode,
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(initialDestination) }
    val sosState by viewModel.sosState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            if (sosState is SosState.CountingDown) viewModel.interruptSos()
        }
    }
    BackHandler {
        if (sosState is SosState.CountingDown) viewModel.cancelSos()
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClaroColors.Charcoal),
    ) {
        when (destination) {
            SafetyMediaAppsDestination.SOS -> SosScreen(
                state = sosState,
                viewModel = viewModel,
                onBack = onBack,
            )
            SafetyMediaAppsDestination.CAMERA -> CameraGalleryScreen(
                showCameraFirst = true,
                onShowGallery = { destination = SafetyMediaAppsDestination.GALLERY },
                onBack = onBack,
            )
            SafetyMediaAppsDestination.GALLERY -> CameraGalleryScreen(
                showCameraFirst = false,
                onShowGallery = { destination = SafetyMediaAppsDestination.GALLERY },
                onBack = onBack,
            )
            SafetyMediaAppsDestination.APPS -> AppsScreen(
                viewModel = viewModel,
                onBack = onBack,
            )
        }
        if (visualMode == VisualMode.CLASSIC) {
            ClassicFeatureDeck(
                onBack = onBack,
                onHome = onBack,
            )
        }
    }
}

@Composable
private fun SosScreen(
    state: SosState,
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var consented by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }

    LaunchedEffect(state) {
        while (state is SosState.CountingDown) {
            now = android.os.SystemClock.elapsedRealtime()
            delay(200)
        }
    }

    FeatureBody("Call for help") {
        Text(
            "SOS",
            color = ClaroColors.Red,
            fontSize = 52.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        when (state) {
            is SosState.Unset -> {
                StatusCard(state.message)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Trusted person") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Phone number") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        textDirection = TextDirection.Ltr,
                        fontSize = 24.sp,
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consented, onCheckedChange = { consented = it })
                    Text("The recipient agreed to receive a help call.")
                }
                PrimaryButton("Save trusted person") {
                    viewModel.configureSos(name, number, consented)
                }
            }
            is SosState.Armed -> {
                TrustedPersonCard(state.contact.displayName, state.contact.destination)
                NonDispatchNotice()
                PrimaryButton("Call in 5 seconds", danger = true) {
                    viewModel.startSos(false, android.os.SystemClock.elapsedRealtime())
                }
                PrimaryButton("Test with caregiver", danger = false) {
                    viewModel.startSos(true, android.os.SystemClock.elapsedRealtime())
                }
            }
            is SosState.CountingDown -> {
                TrustedPersonCard(state.contact.displayName, state.contact.destination)
                Text(
                    "Calling in ${viewModel.sosRemainingSeconds(now)}",
                    fontSize = 42.sp,
                    color = Color.White,
                )
                PrimaryButton("STOP", danger = true) { viewModel.cancelSos() }
                Text("Canceling starts no call.", color = Color.White)
            }
            is SosState.Interrupted -> {
                StatusCard("Canceled after interruption. No delayed call was placed.")
                PrimaryButton("Start again", danger = true) {
                    viewModel.startSos(state.testMode, android.os.SystemClock.elapsedRealtime())
                }
            }
            is SosState.Canceled -> {
                StatusCard("Canceled. No call started.")
                PrimaryButton("Try again", danger = true) {
                    viewModel.startSos(state.testMode, android.os.SystemClock.elapsedRealtime())
                }
            }
            is SosState.Calling -> {
                TrustedPersonCard(state.contact.displayName, state.contact.destination)
                StatusCard("${state.outcome.friendlyName()}. Help is not confirmed.")
                NonDispatchNotice()
            }
            is SosState.Failed -> {
                StatusCard("${state.outcome.friendlyName()}. No alert or location was sent.")
                PrimaryButton("Try again", danger = true) {
                    viewModel.startSos(state.testMode, android.os.SystemClock.elapsedRealtime())
                }
            }
        }
        PrimaryButton("Emergency call", danger = true) {
            viewModel.openEmergencyDialer()
        }
        SecondaryButton("Back", onBack)
    }
}

@Composable
private fun CameraGalleryScreen(
    showCameraFirst: Boolean,
    onShowGallery: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val library = remember {
        MediaLibrary(
            listOf(
                ClaroPhoto(
                    id = "sample",
                    title = "Selected family photo",
                    contentUri = "content://claro/reference/sample",
                    ownership = PhotoOwnership.REFERENCED,
                ),
            ),
        )
    }
    val photos = remember { mutableStateListOf<ClaroPhoto>().apply { addAll(library.all()) } }
    var status by rememberSaveable {
        mutableStateOf(
            if (showCameraFirst) {
                "The system camera opens outside Claro. Its controls are not enlarged by Claro."
            } else {
                "Claro shows photos it captured or that you selected."
            },
        )
    }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var pendingOwnedDeleteId by remember { mutableStateOf<String?>(null) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val photoId = pendingOwnedDeleteId
        val authorized = result.resultCode == Activity.RESULT_OK
        val removed = photoId != null && library.confirmOwnedDeletion(photoId, authorized)
        photos.clear()
        photos.addAll(library.all())
        status = if (removed) {
            "Photo deleted."
        } else {
            "Delete canceled or denied. Photo kept."
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
                title = "Claro camera photo",
                contentUri = uri.toString(),
                ownership = PhotoOwnership.CLARO_OWNED,
            )
            library.addOwned(photo)
            photos.clear()
            photos.addAll(library.all())
            status = "Picture saved. You returned from the system camera."
        } else {
            uri?.let { context.contentResolver.delete(it, null, null) }
            status = "Camera canceled. No picture saved."
        }
        pendingCaptureUri = null
    }
    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) {
            status = "Photo selection canceled."
        } else {
            val persistable = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess
            val photo = ClaroPhoto(
                id = uri.toString(),
                title = "Selected photo",
                contentUri = uri.toString(),
                ownership = PhotoOwnership.REFERENCED,
                availability = PhotoAvailability.AVAILABLE,
            )
            library.addReferenced(
                photo,
                MediaUriGrant(read = true, write = false, persistable = persistable),
            )
            photos.clear()
            photos.addAll(library.all())
            status = if (persistable) {
                "Photo added to Claro."
            } else {
                "Photo added. Android may ask you to select it again later."
            }
        }
    }

    FeatureBody(if (showCameraFirst) "Camera" else "Gallery") {
        StatusCard(status)
        if (showCameraFirst) {
            PrimaryButton("Open system camera") {
                val uri = createCaptureUri(context)
                if (uri == null) {
                    status = "Camera or storage is unavailable."
                } else {
                    pendingCaptureUri = uri
                    runCatching { captureLauncher.launch(uri) }
                        .onFailure {
                            context.contentResolver.delete(uri, null, null)
                            pendingCaptureUri = null
                            status = "No system camera is available."
                        }
                }
            }
            SecondaryButton("Gallery", onShowGallery)
        } else {
            if (photos.isEmpty()) {
                StatusCard("No pictures yet.")
            }
            photos.forEach { photo ->
                var confirming by rememberSaveable(photo.id) { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ClaroColors.Surface)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(photo.title, color = ClaroColors.Charcoal)
                    Text(
                        when (photo.availability) {
                            PhotoAvailability.PERMISSION_LOST ->
                                "Access was removed. Select this photo again."
                            PhotoAvailability.CLOUD_UNAVAILABLE ->
                                "This cloud photo is unavailable offline."
                            PhotoAvailability.MISSING ->
                                "This photo is missing."
                            PhotoAvailability.AVAILABLE -> {
                                if (photo.ownership == PhotoOwnership.CLARO_OWNED) {
                                    "Claro-owned photo"
                                } else {
                                    "Selected reference; the original stays on your device"
                                }
                            }
                        },
                        color = ClaroColors.Charcoal,
                        fontSize = 18.sp,
                    )
                    SecondaryButton(
                        if (photo.ownership == PhotoOwnership.CLARO_OWNED) {
                            "Delete photo"
                        } else {
                            "Remove from Claro"
                        },
                    ) { confirming = true }
                    if (confirming) {
                        StatusCard(
                            if (photo.ownership == PhotoOwnership.CLARO_OWNED) {
                                "Android may require authorization before deleting this photo."
                            } else {
                                "Remove from Claro? The original is retained."
                            },
                        )
                        PrimaryButton("Confirm", danger = true) {
                            val request = library.requestRemoval(photo.id)
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
                                            IntentSenderRequest.Builder(deleteRequest.intentSender)
                                                .build(),
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
                                photos.clear()
                                photos.addAll(library.all())
                                status = if (removed) {
                                    "Photo removed."
                                } else {
                                    "Could not delete. Photo kept."
                                }
                            } else {
                                status = "Waiting for Android delete confirmation."
                            }
                        }
                        SecondaryButton("Cancel") { confirming = false }
                    }
                }
            }
            PrimaryButton("Add photos") {
                pickerLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            }
            SecondaryButton("Camera") {
                status = "Return Home and open Camera to take a new photo."
            }
        }
        SecondaryButton("Back", onBack)
    }
}

@Composable
private fun AppsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val discovered = remember { discoverLaunchableApps(context) }
    var allowedPackages by rememberSaveable {
        mutableStateOf<List<String>>(discovered.take(4).map { it.packageName })
    }
    var favoritePackages by rememberSaveable {
        mutableStateOf<List<String>>(discovered.take(2).map { it.packageName })
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    val allowedApps = discovered
        .filter { it.packageName in allowedPackages }
        .map { it.copy(favorite = it.packageName in favoritePackages) }
    val menu = remember(allowedApps) { AllowedAppsMenu(allowedApps) }
    var query by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(true) }
    var status by rememberSaveable {
        mutableStateOf("Apps open outside Claro and keep their own controls.")
    }
    val apps = menu.search(
        query,
        if (favoritesOnly) AppListFilter.FAVORITES else AppListFilter.ALL_ALLOWED,
    )

    FeatureBody("Apps") {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search allowed apps") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Favorites") { favoritesOnly = true }
            SecondaryButton("All allowed") { favoritesOnly = false }
        }
        SecondaryButton(if (editing) "Done editing" else "Edit allowed apps") {
            editing = !editing
        }
        if (editing) {
            StatusCard("Caregiver selection controls only this Claro menu. It does not lock the device.")
            discovered.forEach { app ->
                val isAllowed = app.packageName in allowedPackages
                val isFavorite = app.packageName in favoritePackages
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isAllowed,
                        onCheckedChange = { checked ->
                            allowedPackages = if (checked) {
                                (allowedPackages + app.packageName).distinct()
                            } else {
                                allowedPackages - app.packageName
                            }
                            if (!checked) {
                                favoritePackages = favoritePackages - app.packageName
                            }
                        },
                    )
                    Text(
                        app.label,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    Checkbox(
                        checked = isFavorite,
                        enabled = isAllowed,
                        onCheckedChange = { checked ->
                            favoritePackages = if (checked) {
                                (favoritePackages + app.packageName).distinct()
                            } else {
                                favoritePackages - app.packageName
                            }
                        },
                    )
                    Text("Favorite", color = Color.White, fontSize = 16.sp)
                }
            }
        }
        StatusCard(status)
        if (apps.isEmpty()) StatusCard("No allowed apps found.")
        apps.forEach { app ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(ClaroColors.Surface)
                    .clickable {
                        status = when (val result = menu.open(app.packageName)) {
                            is AllowedAppOpenResult.Ready -> when (
                                viewModel.launchAllowedApp(result.app.packageName)
                            ) {
                                ExternalAppLaunchResult.Launched ->
                                    "Opened ${result.app.label}. Use Home to return to Claro."
                                ExternalAppLaunchResult.MissingOrDisabled ->
                                    "${result.app.label} is missing or disabled. Ask a caregiver to repair the allowlist."
                                ExternalAppLaunchResult.BlockedBySystem ->
                                    "Android blocked ${result.app.label} from opening."
                                else -> "Android could not open ${result.app.label}."
                            }
                            is AllowedAppOpenResult.Missing ->
                                "${result.app.label} is missing or disabled."
                            AllowedAppOpenResult.NotAllowed ->
                                "This app is not allowed."
                        }
                    }
                    .padding(12.dp)
                    .semantics { contentDescription = "Open allowed app ${app.label}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(app.label, color = ClaroColors.Charcoal)
            }
        }
        SecondaryButton("Back", onBack)
    }
}

@Composable
private fun FeatureBody(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(ClaroDimensions.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = 32.sp,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun StatusCard(text: String) {
    Text(
        text,
        color = ClaroColors.Charcoal,
        modifier = Modifier
            .fillMaxWidth()
            .background(ClaroColors.Surface)
            .padding(12.dp),
    )
}

@Composable
private fun TrustedPersonCard(name: String, destination: String) {
    StatusCard("$name\n$destination")
}

@Composable
private fun NonDispatchNotice() {
    StatusCard("Not an emergency service. This places one ordinary call only. No message, location, monitoring, or dispatch.")
}

@Composable
private fun PrimaryButton(
    text: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(ClaroDimensions.PrimaryTarget),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) ClaroColors.Red else ClaroColors.Green,
        ),
    ) {
        Text(text)
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(ClaroDimensions.PrimaryTarget),
        colors = ButtonDefaults.buttonColors(
            containerColor = ClaroColors.Surface,
            contentColor = ClaroColors.Charcoal,
        ),
    ) {
        Text(text)
    }
}

@Composable
private fun ClassicFeatureDeck(
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("1", "2", "3", "4", "5", "6").forEach { key ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(ClaroColors.Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(key, color = ClaroColors.Charcoal)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
            ) { Text("Back") }
            Button(
                onClick = onHome,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ClaroColors.Red),
            ) { Text("Home") }
        }
    }
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

private fun discoverLaunchableApps(context: Context): List<AllowedApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .asSequence()
        .filter { it.activityInfo.packageName != context.packageName }
        .distinctBy { it.activityInfo.packageName }
        .mapIndexed { index, info ->
            AllowedApp(
                packageName = info.activityInfo.packageName,
                label = info.loadLabel(context.packageManager).toString(),
                favorite = index < 4,
                enabled = info.activityInfo.enabled,
                installed = true,
            )
        }
        .sortedBy { it.label.lowercase() }
        .toList()
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
