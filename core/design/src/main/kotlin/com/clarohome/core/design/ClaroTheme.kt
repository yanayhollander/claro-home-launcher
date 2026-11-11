package com.clarohome.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarohome.core.model.TextSize

object ClaroColors {
    val Charcoal = Color(0xFF202734)
    val Ink = Color(0xFF111827)
    val Muted = Color(0xFF4B5565)
    val Surface = Color(0xFFEDF0F5)
    val RowSurface = Color(0xFFEEF0F4)
    val Border = Color(0xFFB8C2D0)
    val FieldBorder = Color(0xFF6F7E94)
    val Green = Color(0xFF078534)
    val Blue = Color(0xFF075BD8)
    val Orange = Color(0xFFC45100)
    val Red = Color(0xFFCC1731)
    val Violet = Color(0xFF7041C3)
    val AppsInk = Color(0xFF1F344E)
    val AppsPale = Color(0xFFEDF1F7)
    val PaleBlue = Color(0xFFEEF4FF)
    val PaleGreen = Color(0xFFEFF8F2)
    val PaleOrange = Color(0xFFFFF5EA)
    val PaleRed = Color(0xFFFFF0F1)
    val PaleViolet = Color(0xFFF4EFFF)
    val InfoBlueBg = Color(0xFFEDF3FD)
    val InfoBlueInk = Color(0xFF173C70)
    val WarningBg = Color(0xFFFFF3D4)
    val WarningInk = Color(0xFF704000)
    val WarningBorder = Color(0xFFAE6200)
    val ErrorBg = Color(0xFFFFF0F2)
    val ErrorInk = Color(0xFF931227)
    val SuccessBg = Color(0xFFE9F8EE)
    val SuccessInk = Color(0xFF075A2B)
    val EmergencyBg = Color(0xFFFFF1F3)
    val EmergencyInk = Color(0xFFA10F27)
    val EmergencyBorder = Color(0xFFD6ABB3)
}

object ClaroDimensions {
    val PrimaryTarget = 64.dp
    val DockButtonHeight = 68.dp
    val SecondaryTarget = 48.dp
    val TileGap = 3.dp
    val ScreenPadding = 3.dp
}

@Composable
fun ClaroTheme(
    textSize: TextSize = TextSize.STANDARD,
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scale = when (textSize) {
        TextSize.STANDARD -> 1f
        TextSize.LARGE -> 1.2f
        TextSize.HUGE -> 1.45f
    }
    val typography = Typography(
        bodyLarge = TextStyle(fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp),
        titleLarge = TextStyle(
            fontSize = (32 * scale).sp,
            lineHeight = (38 * scale).sp,
            fontWeight = FontWeight.Bold,
        ),
        labelLarge = TextStyle(
            fontSize = (24 * scale).sp,
            lineHeight = (30 * scale).sp,
            fontWeight = FontWeight.Bold,
        ),
    )
    val colorScheme = lightColorScheme(
        primary = if (highContrast) Color(0xFF005FCC) else ClaroColors.Blue,
        secondary = ClaroColors.Green,
        background = Color.White,
        surface = if (highContrast) Color.White else ClaroColors.Surface,
        onSurface = ClaroColors.Ink,
        onBackground = ClaroColors.Ink,
        error = if (highContrast) Color(0xFFFF334D) else ClaroColors.Red,
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = MaterialTheme.shapes.copy(
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(12.dp),
        ),
        content = content,
    )
}
