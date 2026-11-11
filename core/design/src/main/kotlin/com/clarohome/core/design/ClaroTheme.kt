package com.clarohome.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarohome.core.model.TextSize

object ClaroColors {
    val Charcoal = Color(0xFF202734)
    val Surface = Color(0xFFF8FAFC)
    val Green = Color(0xFF16834A)
    val Blue = Color(0xFF1268B3)
    val Orange = Color(0xFFD86616)
    val Red = Color(0xFFC52D35)
    val Violet = Color(0xFF6844B5)
}

object ClaroDimensions {
    val PrimaryTarget = 64.dp
    val SecondaryTarget = 48.dp
    val TileGap = 4.dp
    val ScreenPadding = 12.dp
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
    val colorScheme = darkColorScheme(
        primary = if (highContrast) Color(0xFF00A85A) else ClaroColors.Green,
        background = if (highContrast) Color.Black else ClaroColors.Charcoal,
        surface = if (highContrast) Color.White else ClaroColors.Surface,
        onSurface = Color.Black,
        onBackground = Color.White,
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
