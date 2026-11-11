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

private val ClaroTypography = Typography(
    bodyLarge = TextStyle(fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    labelLarge = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
)

private val ClaroColorScheme = darkColorScheme(
    primary = ClaroColors.Green,
    background = ClaroColors.Charcoal,
    surface = ClaroColors.Surface,
    onSurface = ClaroColors.Charcoal,
    onBackground = Color.White,
    error = ClaroColors.Red,
)

@Composable
fun ClaroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ClaroColorScheme,
        typography = ClaroTypography,
        shapes = MaterialTheme.shapes.copy(
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(12.dp),
        ),
        content = content,
    )
}
