package com.clarohome.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ClaroBannerTone {
    INFO,
    WARNING,
    ERROR,
    SUCCESS,
}

@Composable
fun ClaroScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    bannerText: String? = null,
    bannerTone: ClaroBannerTone = ClaroBannerTone.INFO,
    scrollable: Boolean = true,
    dock: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        ClaroHeader(title = title)
        val bodyModifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(ClaroDimensions.ScreenPadding)
        Column(
            modifier = bodyModifier,
            verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
        ) {
            if (!bannerText.isNullOrBlank()) {
                ClaroInfoCard(text = bannerText, tone = bannerTone)
            }
            content()
        }
        if (dock != null) {
            ClaroActionDock(content = dock)
        }
    }
}

@Composable
fun ClaroHeader(
    title: String,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ClaroColors.Charcoal)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 29.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
fun ClaroActionDock(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ClaroColors.Charcoal)
            .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        content = content,
    )
}

@Composable
fun ClaroInfoCard(
    text: String,
    modifier: Modifier = Modifier,
    tone: ClaroBannerTone = ClaroBannerTone.INFO,
) {
    val (bg, ink, bar) = when (tone) {
        ClaroBannerTone.INFO -> Triple(ClaroColors.InfoBlueBg, ClaroColors.InfoBlueInk, ClaroColors.Blue)
        ClaroBannerTone.WARNING -> Triple(ClaroColors.WarningBg, ClaroColors.WarningInk, ClaroColors.WarningBorder)
        ClaroBannerTone.ERROR -> Triple(ClaroColors.ErrorBg, ClaroColors.ErrorInk, ClaroColors.Red)
        ClaroBannerTone.SUCCESS -> Triple(ClaroColors.SuccessBg, ClaroColors.SuccessInk, ClaroColors.Green)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(bg),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(bar),
        )
        Text(
            text = text,
            color = ink,
            fontSize = 21.sp,
            lineHeight = 28.sp,
            modifier = Modifier.padding(10.dp),
        )
    }
}

@Composable
fun ClaroPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = ClaroColors.Blue,
    icon: ClaroIconKind? = null,
) {
    val haptics = LocalClaroHaptics.current
    Button(
        onClick = {
            if (containerColor == ClaroColors.Red) {
                haptics.alertDouble()
            } else {
                haptics.confirmHeavy()
            }
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ClaroDimensions.DockButtonHeight),
        shape = RoundedCornerShape(3.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
            disabledContainerColor = containerColor.copy(alpha = 0.45f),
            disabledContentColor = Color.White.copy(alpha = 0.85f),
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            ClaroIcon(
                kind = icon,
                tint = Color.White,
                size = 28.dp,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text(
            text = label,
            fontSize = 24.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ClaroSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ClaroIconKind? = null,
) {
    val haptics = LocalClaroHaptics.current
    OutlinedButton(
        onClick = {
            haptics.softkey()
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ClaroDimensions.PrimaryTarget),
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(1.dp, ClaroColors.Border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ClaroColors.Surface,
            contentColor = ClaroColors.Ink,
            disabledContainerColor = ClaroColors.Surface.copy(alpha = 0.55f),
            disabledContentColor = ClaroColors.Muted,
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            ClaroIcon(
                kind = icon,
                tint = ClaroColors.Ink,
                size = 28.dp,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text(
            text = label,
            fontSize = 24.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ClaroEmergencyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalClaroHaptics.current
    OutlinedButton(
        onClick = {
            haptics.alertDouble()
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ClaroDimensions.PrimaryTarget),
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(1.dp, ClaroColors.EmergencyBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ClaroColors.EmergencyBg,
            contentColor = ClaroColors.EmergencyInk,
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            fontSize = 22.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ClaroRowButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    unreadDot: Boolean = false,
) {
    val haptics = LocalClaroHaptics.current
    Column(modifier = modifier.fillMaxWidth()) {
        Button(
            onClick = {
                haptics.softkey()
                onClick()
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp),
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = ClaroColors.RowSurface,
                contentColor = ClaroColors.Ink,
            ),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (leading != null) {
                    leading()
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = ClaroColors.Ink,
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                        fontWeight = if (unreadDot) FontWeight.Bold else FontWeight.Medium,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = if (unreadDot) "● $subtitle" else subtitle,
                            color = ClaroColors.Muted,
                            fontSize = 17.sp,
                            lineHeight = 21.sp,
                        )
                    }
                }
                val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                Text(
                    text = if (isRtl) "←" else "→",
                    color = Color(0xFF5A687C),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        HorizontalDivider(thickness = 2.dp, color = ClaroColors.Charcoal)
    }
}
