package com.clarohome.core.design

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shows the app's own launcher icon, falling back to the generic apps glyph if it cannot be loaded. */
@Composable
fun ClaroAppIcon(
    packageName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    fallbackTint: Color = ClaroColors.Blue,
) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = if (packageName == null) {
            null
        } else {
            withContext(Dispatchers.IO) { loadAppIcon(context.packageManager, packageName) }
        }
    }
    val loaded = icon
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.then(Modifier.size(size)),
        )
    } else {
        ClaroIcon(
            kind = ClaroIconKind.APPS,
            tint = fallbackTint,
            size = size,
            modifier = modifier,
        )
    }
}

private fun loadAppIcon(packageManager: PackageManager, packageName: String): ImageBitmap? =
    runCatching {
        val drawable = packageManager.getApplicationIcon(packageName)
        val edge = 192
        val bitmap = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, edge, edge)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    }.getOrNull()
