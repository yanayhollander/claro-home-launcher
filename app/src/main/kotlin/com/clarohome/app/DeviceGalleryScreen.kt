package com.clarohome.app

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.clarohome.core.design.ClaroBannerTone
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.core.design.ClaroIcon
import com.clarohome.core.design.ClaroIconKind
import com.clarohome.core.design.ClaroInfoCard
import com.clarohome.core.design.ClaroPrimaryButton
import com.clarohome.core.design.ClaroScreenScaffold
import com.clarohome.core.design.ClaroSecondaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class DeviceMediaAccess { FULL, PARTIAL, DENIED }

internal fun deviceMediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

internal fun deviceMediaAccess(context: Context): DeviceMediaAccess {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> when {
            granted(Manifest.permission.READ_MEDIA_IMAGES) -> DeviceMediaAccess.FULL
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> DeviceMediaAccess.PARTIAL
            else -> DeviceMediaAccess.DENIED
        }
        granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> DeviceMediaAccess.FULL
        else -> DeviceMediaAccess.DENIED
    }
}

private data class DevicePhoto(val id: Long, val uri: Uri, val name: String)

private const val MAX_DEVICE_PHOTOS = 300

private fun queryDevicePhotos(context: Context): List<DevicePhoto> {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val query = Bundle().apply {
        putInt(ContentResolver.QUERY_ARG_LIMIT, MAX_DEVICE_PHOTOS)
        putStringArray(
            ContentResolver.QUERY_ARG_SORT_COLUMNS,
            arrayOf(MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media._ID),
        )
        putInt(
            ContentResolver.QUERY_ARG_SORT_DIRECTION,
            ContentResolver.QUERY_SORT_DIRECTION_DESCENDING,
        )
    }
    return runCatching {
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
            query,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    add(
                        DevicePhoto(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            name = cursor.getString(nameColumn).orEmpty(),
                        ),
                    )
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())
}

private class LoadedImage(val bitmap: Bitmap?, val finished: Boolean)

@Composable
private fun DevicePhotoImage(
    photo: DevicePhoto,
    sizePx: Int,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val loaded by produceState(LoadedImage(null, false), photo.uri, sizePx) {
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.loadThumbnail(photo.uri, Size(sizePx, sizePx), null)
            }.getOrNull()
        }
        value = LoadedImage(bitmap, true)
    }
    val bitmap = loaded.bitmap
    Box(
        modifier = modifier.background(ClaroColors.Border),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = photo.name,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (loaded.finished) {
            ClaroIcon(kind = ClaroIconKind.GALLERY, tint = ClaroColors.Charcoal, size = 40.dp)
        }
    }
}

@Composable
internal fun CameraLaunchScreen(
    onCaptured: () -> Unit,
    onCanceled: () -> Unit,
) {
    val context = LocalContext.current
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var launched by rememberSaveable { mutableStateOf(false) }
    var errorRes by rememberSaveable { mutableStateOf<Int?>(null) }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val uri = pendingUri
        pendingUri = null
        if (saved) {
            onCaptured()
        } else {
            uri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            onCanceled()
        }
    }

    val openCamera = {
        errorRes = null
        val uri = createCaptureUri(context)
        if (uri == null) {
            errorRes = R.string.app_camera_storage_unavailable
        } else {
            pendingUri = uri
            runCatching { captureLauncher.launch(uri) }.onFailure {
                runCatching { context.contentResolver.delete(uri, null, null) }
                pendingUri = null
                errorRes = R.string.app_no_system_camera
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            openCamera()
        }
    }

    val error = errorRes
    ClaroScreenScaffold(
        title = stringResource(R.string.app_camera),
        dock = if (error != null) {
            {
                ClaroPrimaryButton(
                    label = stringResource(R.string.app_open_camera),
                    onClick = { openCamera() },
                    containerColor = ClaroColors.Blue,
                    icon = ClaroIconKind.CAMERA,
                )
            }
        } else {
            null
        },
    ) {
        if (error != null) {
            ClaroInfoCard(text = stringResource(error), tone = ClaroBannerTone.ERROR)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(ClaroColors.PaleOrange),
            contentAlignment = Alignment.Center,
        ) {
            ClaroIcon(kind = ClaroIconKind.CAMERA, tint = ClaroColors.Orange, size = 72.dp)
        }
    }
}

@Composable
internal fun DeviceGalleryScreen(onRequestAccess: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var refreshTick by remember { mutableIntStateOf(0) }
    var photos by remember { mutableStateOf<List<DevicePhoto>?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf<Pair<String, ClaroBannerTone>?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshTick++ }

    val access = remember(refreshTick) { deviceMediaAccess(context) }
    LaunchedEffect(refreshTick) {
        photos = if (access == DeviceMediaAccess.DENIED) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) { queryDevicePhotos(context) }
        }
    }

    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingDeleteId != null) {
            selectedId = null
            status = resources.getString(R.string.app_photo_deleted) to ClaroBannerTone.SUCCESS
            refreshTick++
        } else {
            status = resources.getString(R.string.app_photo_delete_canceled) to ClaroBannerTone.WARNING
        }
        pendingDeleteId = null
    }

    val list = photos.orEmpty()
    val selectedIndex = list.indexOfFirst { it.id == selectedId }
    val selected = list.getOrNull(selectedIndex)

    BackHandler(enabled = selected != null) {
        selectedId = null
        confirmingDelete = false
    }

    if (selected != null) {
        PhotoDetail(
            photo = selected,
            position = selectedIndex + 1,
            total = list.size,
            confirmingDelete = confirmingDelete,
            status = status,
            onPrevious = {
                selectedId = list[(selectedIndex - 1 + list.size) % list.size].id
                confirmingDelete = false
            },
            onNext = {
                selectedId = list[(selectedIndex + 1) % list.size].id
                confirmingDelete = false
            },
            onRequestDelete = { confirmingDelete = true },
            onCancelDelete = { confirmingDelete = false },
            onConfirmDelete = {
                confirmingDelete = false
                val started = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val request = MediaStore.createDeleteRequest(
                            context.contentResolver,
                            listOf(selected.uri),
                        )
                        pendingDeleteId = selected.id
                        deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                        true
                    } else {
                        context.contentResolver.delete(selected.uri, null, null) > 0
                    }
                }.getOrDefault(false)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    if (started) {
                        selectedId = null
                        refreshTick++
                    }
                    status = if (started) {
                        resources.getString(R.string.app_photo_deleted) to ClaroBannerTone.SUCCESS
                    } else {
                        resources.getString(R.string.app_picture_delete_failed) to ClaroBannerTone.ERROR
                    }
                } else if (!started) {
                    status = resources.getString(R.string.app_picture_delete_failed) to ClaroBannerTone.ERROR
                }
            },
            onBackToGrid = {
                selectedId = null
                confirmingDelete = false
            },
        )
        return
    }

    ClaroScreenScaffold(
        title = stringResource(R.string.app_gallery),
        scrollable = false,
        dock = if (access != DeviceMediaAccess.FULL) {
            {
                ClaroPrimaryButton(
                    label = stringResource(R.string.app_gallery_allow_pictures),
                    onClick = onRequestAccess,
                    containerColor = ClaroColors.Blue,
                )
            }
        } else {
            null
        },
    ) {
        status?.let { (message, tone) -> ClaroInfoCard(text = message, tone = tone) }
        when {
            access == DeviceMediaAccess.DENIED -> ClaroInfoCard(
                text = stringResource(R.string.app_gallery_access_needed),
                tone = ClaroBannerTone.WARNING,
            )
            photos == null -> Unit
            else -> {
                if (access == DeviceMediaAccess.PARTIAL) {
                    ClaroInfoCard(
                        text = stringResource(R.string.app_gallery_partial_access),
                        tone = ClaroBannerTone.WARNING,
                    )
                }
                if (list.isEmpty()) {
                    ClaroInfoCard(
                        text = stringResource(R.string.app_no_pictures_yet),
                        tone = ClaroBannerTone.INFO,
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
                        verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
                    ) {
                        items(list, key = { it.id }) { photo ->
                            DevicePhotoImage(
                                photo = photo,
                                sizePx = 360,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clickable(role = Role.Button) {
                                        selectedId = photo.id
                                        confirmingDelete = false
                                        status = null
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoDetail(
    photo: DevicePhoto,
    position: Int,
    total: Int,
    confirmingDelete: Boolean,
    status: Pair<String, ClaroBannerTone>?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRequestDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onBackToGrid: () -> Unit,
) {
    ClaroScreenScaffold(
        title = stringResource(R.string.app_picture_position, position, total),
        dock = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ClaroSecondaryButton(
                    label = stringResource(R.string.app_photo_previous),
                    onClick = onPrevious,
                    enabled = total > 1,
                    modifier = Modifier.weight(1f),
                )
                ClaroSecondaryButton(
                    label = stringResource(R.string.app_photo_next),
                    onClick = onNext,
                    enabled = total > 1,
                    modifier = Modifier.weight(1f),
                )
            }
            ClaroSecondaryButton(
                label = stringResource(R.string.app_all_pictures),
                onClick = onBackToGrid,
            )
        },
    ) {
        status?.let { (message, tone) -> ClaroInfoCard(text = message, tone = tone) }
        DevicePhotoImage(
            photo = photo,
            sizePx = 1280,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .semantics {
                    contentDescription = photo.name
                    role = Role.Image
                },
        )
        if (!confirmingDelete) {
            ClaroSecondaryButton(
                label = stringResource(R.string.app_delete_photo),
                onClick = onRequestDelete,
            )
        } else {
            ClaroInfoCard(
                text = stringResource(R.string.app_confirm_delete_device_photo),
                tone = ClaroBannerTone.WARNING,
            )
            ClaroPrimaryButton(
                label = stringResource(R.string.app_delete_photo),
                onClick = onConfirmDelete,
                containerColor = ClaroColors.Red,
            )
            ClaroSecondaryButton(
                label = stringResource(R.string.app_cancel),
                onClick = onCancelDelete,
            )
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
        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }.getOrNull()
}
