package com.clarohome.core.design

import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarohome.core.model.BatteryStatus
import com.clarohome.core.model.CellularAvailability
import com.clarohome.core.model.CellularStatus
import kotlin.math.abs
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ClaroIconKind(
    val pathData: String,
    val filled: Boolean,
) {
    PHONE(
        "M8 3H4a1 1 0 0 0-1 1c0 9.4 7.6 17 17 17a1 1 0 0 0 1-1v-4l-5-2-2 2a14 14 0 0 1-6-6l2-2-2-5Z",
        filled = true,
    ),
    MESSAGES(
        "M2 5h20v14H2V5Zm2 2 8 6 8-6h-3l-5 4-5-4H4Z",
        filled = true,
    ),
    CAMERA(
        "M3 7h5l2-3h4l2 3h5v13H3V7Zm9 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z",
        filled = true,
    ),
    GALLERY(
        "M3 3h18v18H3V3Zm2 15h14l-4-5-3 3-4-6-3 8ZM16 6a2 2 0 1 0 0 4 2 2 0 0 0 0-4Z",
        filled = true,
    ),
    SOS(
        "M12 3 2 21h20L12 3Zm0 6v5m0 3h.01",
        filled = false,
    ),
    APPS(
        "M5 3a3 3 0 1 0 0 6 3 3 0 0 0 0-6Zm14 0a3 3 0 1 0 0 6 3 3 0 0 0 0-6ZM5 10a3 3 0 1 0 0 6 3 3 0 0 0 0-6Zm14 0a3 3 0 1 0 0 6 3 3 0 0 0 0-6ZM5 17a3 3 0 1 0 0 6 3 3 0 0 0 0-6Zm14 0a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z",
        filled = true,
    ),
    MICROPHONE(
        "M9 5a3 3 0 0 1 6 0v7a3 3 0 0 1-6 0V5Zm-3 6v1a6 6 0 0 0 12 0v-1m-6 7v4m-4 0h8",
        filled = false,
    ),
    SPEAKER(
        "M3 9h4l5-5v16l-5-5H3V9Zm13-2a7 7 0 0 1 0 10m3-13a11 11 0 0 1 0 16",
        filled = false,
    ),
    END_CALL(
        "M3 14c5-5 13-5 18 0l-2 5-5-2v-3h-4v3l-5 2-2-5Z",
        filled = true,
    ),
    BACKSPACE(
        "M9 5h12v14H9l-7-7 7-7Zm3 4 6 6m0-6-6 6",
        filled = false,
    ),
    CLOCK(
        "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Zm0 4v5l3 2",
        filled = false,
    ),
    SETTINGS(
        "m9 3-1 3-3 1-2 5 2 5 3 1 1 3h6l1-3 3-1 2-5-2-5-3-1-1-3H9Zm3 5a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z",
        filled = false,
    ),
    CONTACT(
        "M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8ZM4 21v-3a8 8 0 0 1 16 0v3",
        filled = false,
    ),
    CHECK(
        "m4 12 5 5L20 6",
        filled = false,
    ),
    ARROW_UP(
        "M12 20V4m-7 7 7-7 7 7",
        filled = false,
    ),
    ARROW_DOWN(
        "M12 4v16m-7-7 7 7 7-7",
        filled = false,
    ),
    ARROW_LEFT(
        "M20 12H4m7-7-7 7 7 7",
        filled = false,
    ),
    ARROW_RIGHT(
        "M4 12h16m-7-7 7 7-7 7",
        filled = false,
    ),
    LOCK(
        "M6 10h12v11H6V10Zm3 0V6a3 3 0 0 1 6 0v4",
        filled = false,
    ),
    SUN(
        "M12 2v3m0 14v3M2 12h3m14 0h3M5 5l2 2m10 10 2 2M5 19l2-2M17 7l2-2M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10Z",
        filled = false,
    ),
    CALL_INCOMING(
        "M17 7 7 17m10 0H7V7",
        filled = false,
    ),
    CALL_OUTGOING(
        "M7 17 17 7M7 7h10v10",
        filled = false,
    ),
    CALL_MISSED(
        "M17 17 7 7m0 10V7h10",
        filled = false,
    ),
    HOME(
        "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1v-9.5Z",
        filled = false,
    ),
    MICROPHONE_OFF(
        "M9 5a3 3 0 0 1 6 0v7a3 3 0 0 1-6 0V5Zm-3 6v1a6 6 0 0 0 12 0v-1m-6 7v4m-4 0h8M3 3l18 18",
        filled = false,
    ),
    KEYPAD(
        "M3 2h3v3H3Z M10 2h3v3h-3Z M17 2h3v3h-3Z M3 8h3v3H3Z M10 8h3v3h-3Z M17 8h3v3h-3Z M3 14h3v3H3Z M10 14h3v3h-3Z M17 14h3v3h-3Z M3 20h3v3H3Z M10 20h3v3h-3Z M17 20h3v3h-3Z",
        filled = true,
    ),
    PAUSE(
        "M6 4h4v16H6Z M14 4h4v16h-4Z",
        filled = true,
    ),
    PLAY(
        "M7 4l13 8L7 20Z",
        filled = true,
    ),
    SWAP(
        "M3 7h16m-4-4 4 4-4 4M21 17H5m4-4-4 4 4 4",
        filled = false,
    ),
    COMPOSE_MESSAGE(
        "M13 4H3v16h16V10M8 16l1-4L19 2l3 3-10 10-4 1Zm9-12 3 3",
        filled = false,
    ),
    CONTACT_ADD(
        "M9 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8ZM2 21v-3a7 7 0 0 1 14 0v3M20 5v8m-4-4h8",
        filled = false,
    ),
    EDIT(
        "M4 20l1-5L17 3l4 4L9 19l-5 1ZM14 6l4 4",
        filled = false,
    ),
    DELETE(
        "M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7",
        filled = false,
    ),
}

fun ClaroIconKind.imageVector(): ImageVector {
    val nodes = PathParser().parsePathString(pathData).toNodes()
    return ImageVector.Builder(
        name = name.lowercase(),
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = nodes,
        pathFillType = PathFillType.EvenOdd,
        fill = if (filled) SolidColor(Color.White) else null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = if (filled) 0.4f else 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
}

@Composable
fun ClaroIcon(
    kind: ClaroIconKind,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    contentDescription: String? = null,
) {
    val vector = remember(kind) { kind.imageVector() }
    Icon(
        imageVector = vector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}

private data class PortraitPalette(
    val background: Color,
    val skin: Color,
    val hair: Color,
    val shirt: Color,
)

private val portraitPalettes = listOf(
    PortraitPalette(Color(0xFFDCEBE5), Color(0xFFBB7955), Color(0xFF3C4D46), Color(0xFF48836E)),
    PortraitPalette(Color(0xFFE0E9F4), Color(0xFFE0AB86), Color(0xFF54473E), Color(0xFF557BB1)),
    PortraitPalette(Color(0xFFF3E7D5), Color(0xFFC58D68), Color(0xFF675042), Color(0xFFBD813C)),
    PortraitPalette(Color(0xFFEBE3F1), Color(0xFFE6B492), Color(0xFF48414C), Color(0xFF8A6DA8)),
)

@Composable
fun ClaroAvatar(
    name: String?,
    modifier: Modifier = Modifier,
    size: Dp = 51.dp,
    paletteSeed: Int? = null,
    isUnassigned: Boolean = false,
    photoUri: String? = null,
    contactNumber: String? = null,
    contactLookupKey: String? = null,
) {
    val context = LocalContext.current
    val pixels = with(LocalDensity.current) { size.roundToPx().coerceIn(1, 512) }
    val number = contactNumber?.takeIf(String::isNotBlank)
    val lookupKey = contactLookupKey?.takeIf(String::isNotBlank)
    val hasContact = number != null || lookupKey != null
    val revision = if (hasContact || photoUri?.let { Uri.parse(it).authority } == ContactsContract.AUTHORITY) {
        LocalClaroContactPhotoRevision.current
    } else {
        0L
    }
    val photo by produceState<ImageBitmap?>(null, photoUri, number, lookupKey, pixels, context, isUnassigned, revision) {
        value = null
        if (!hasContact && (photoUri.isNullOrBlank() || isUnassigned)) return@produceState
        value = withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val currentUri = resolver.currentContactPhoto(number, lookupKey, photoUri)
            if (currentUri.isNullOrBlank()) return@withContext null
            try {
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(resolver, Uri.parse(currentUri)),
                ) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val scale = minOf(
                        1f,
                        pixels.toFloat() / info.size.width,
                        pixels.toFloat() / info.size.height,
                    )
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }.asImageBitmap()
            } catch (_: IOException) {
                Log.w("ClaroAvatar", "The contact photo could not be decoded.")
                null
            } catch (_: SecurityException) {
                Log.w("ClaroAvatar", "Access to the contact photo was denied.")
                null
            } catch (_: IllegalArgumentException) {
                Log.w("ClaroAvatar", "The contact photo URI is unsupported.")
                null
            }
        }
    }
    photo?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(RoundedCornerShape(4.dp)),
        )
        return
    }
    val unassignedLabel = stringResource(R.string.design_unassigned_person)
    val hasPerson = !isUnassigned &&
        !name.isNullOrBlank() &&
        name != "?" &&
        name != "Add person" &&
        name != unassignedLabel
    if (!hasPerson) {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFDBEAFE)),
            contentAlignment = Alignment.Center,
        ) {
            ClaroIcon(
                kind = ClaroIconKind.CONTACT,
                tint = Color(0xFF183C6A),
                size = size * 0.62f,
            )
        }
        return
    }
    val paletteIndex = abs(paletteSeed ?: name.hashCode()) % portraitPalettes.size
    val palette = portraitPalettes[paletteIndex]
    val hairPath = remember {
        PathParser().parsePathString("M15 25V17C15 2 42 2 41 21l-5-9c-5 7-12 3-17 8v5z").toPath()
    }
    val shirtPath = remember {
        PathParser().parsePathString("M7 56c0-20 42-20 42 0").toPath()
    }
    val smilePath = remember {
        PathParser().parsePathString("M24 32q4 4 8 0").toPath()
    }
    Canvas(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        val sx = this.size.width / 56f
        val sy = this.size.height / 56f
        drawRoundRect(
            color = palette.background,
            cornerRadius = CornerRadius(6f * sx, 6f * sy),
        )
        val scaleMatrix = androidx.compose.ui.graphics.Matrix().apply { scale(sx, sy) }
        val scaledShirt = androidx.compose.ui.graphics.Path().apply {
            addPath(shirtPath)
            transform(scaleMatrix)
        }
        drawPath(scaledShirt, color = palette.shirt)
        drawRect(
            color = palette.skin,
            topLeft = Offset(23f * sx, 35f * sy),
            size = Size(10f * sx, 10f * sy),
        )
        drawOval(
            color = palette.skin,
            topLeft = Offset(15f * sx, 8f * sy),
            size = Size(26f * sx, 32f * sy),
        )
        val scaledHair = androidx.compose.ui.graphics.Path().apply {
            addPath(hairPath)
            transform(scaleMatrix)
        }
        drawPath(scaledHair, color = palette.hair)
        drawCircle(
            color = palette.hair,
            radius = 1.4f * sx,
            center = Offset(22.5f * sx, 25f * sy),
        )
        drawCircle(
            color = palette.hair,
            radius = 1.4f * sx,
            center = Offset(33.5f * sx, 25f * sy),
        )
        val scaledSmile = androidx.compose.ui.graphics.Path().apply {
            addPath(smilePath)
            transform(scaleMatrix)
        }
        drawPath(
            scaledSmile,
            color = palette.hair,
            style = Stroke(width = 1.8f * sx, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun ClaroBatteryMeter(
    battery: BatteryStatus,
    modifier: Modifier = Modifier,
    borderColor: Color = Color.White,
    backgroundColor: Color = ClaroColors.Charcoal,
    compact: Boolean = false,
) {
    val label = if (battery.isCharging) {
        stringResource(R.string.design_battery_percent_charging, battery.percentage)
    } else {
        stringResource(R.string.design_battery_percent, battery.percentage)
    }
    val width = if (compact) 24.dp else 96.dp
    val inset = if (compact) 2.dp else 3.dp
    Row(
        modifier = modifier.semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(if (compact) 12.dp else 16.dp)
                .background(borderColor)
                .padding(if (compact) 1.dp else 2.dp)
                .background(backgroundColor)
                .padding(1.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width((width - inset * 2) * (battery.percentage.coerceIn(0, 100) / 100f))
                    .background(Color(0xFF3BE564)),
            )
        }
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(6.dp)
                .background(borderColor),
        )
    }
}

@Composable
fun ClaroCellularBars(
    cellular: CellularStatus,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color(0xFF758095),
) {
    val available = cellular.availability == CellularAvailability.AVAILABLE
    val activeBars = if (available) (cellular.signalLevel ?: 0).coerceIn(0, 4) else 0
    val description = when (cellular.availability) {
        CellularAvailability.AVAILABLE -> cellular.signalLevel?.let {
            stringResource(R.string.design_signal_available, it)
        } ?: stringResource(R.string.design_signal_unknown)
        CellularAvailability.NO_SIM -> stringResource(R.string.design_signal_no_sim)
        CellularAvailability.NO_SERVICE -> stringResource(R.string.design_signal_no_service)
        CellularAvailability.AIRPLANE_MODE -> stringResource(R.string.design_signal_airplane)
        CellularAvailability.UNKNOWN -> stringResource(R.string.design_signal_unknown)
    }
    Row(
        modifier = modifier.semantics { contentDescription = description },
        verticalAlignment = Alignment.Bottom,
    ) {
        val heights = listOf(5.dp, 8.dp, 11.dp, 15.dp, 19.dp)
        heights.forEachIndexed { index, barHeight ->
            val filled = available && index < activeBars + 1 && activeBars > 0
            Box(
                modifier = Modifier
                    .padding(end = if (index < 4) 4.dp else 0.dp)
                    .width(12.dp)
                    .height(barHeight)
                    .background(if (filled) activeColor else inactiveColor),
            )
        }
        if (!available || cellular.signalLevel == null) {
            Text(
                text = if (
                    cellular.availability == CellularAvailability.UNKNOWN ||
                    (available && cellular.signalLevel == null)
                ) {
                    " ?"
                } else {
                    " ×"
                },
                color = activeColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
