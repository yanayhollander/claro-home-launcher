package com.clarohome.presentation.classic

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarohome.core.design.ClaroAppIcon
import com.clarohome.core.design.ClaroAvatar
import com.clarohome.core.design.ClaroBatteryMeter
import com.clarohome.core.design.ClaroCellularBars
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroIcon
import com.clarohome.core.design.ClaroIconKind
import com.clarohome.core.design.LocalClaroHaptics
import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.BatteryStatus
import com.clarohome.core.model.CellularStatus
import com.clarohome.core.model.HomeRoleState
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind
import com.clarohome.presentation.classic.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val ClassicFrame = Color(0xFF202734)
internal val ClassicDeck = Color(0xFF3B485C)
internal val ClassicDeckBorder = Color(0xFF728096)
internal val ClassicDpadBorder = Color(0xFF8997AA)
internal val ClassicOkFill = Color(0xFF61738B)
internal val ClassicOkBorder = Color(0xFFB6C4D6)
internal val ClassicKey = Color(0xFF303B4C)
internal val ClassicKeyBorder = Color(0xFF6B788C)
internal val ClassicKeySublabel = Color(0xFFE1E7F0)
internal val ClassicDisplay = Color(0xFFFFFFFF)
internal val ClassicDisplayMuted = Color(0xFFEDF0F5)
internal val ClassicRowSurface = Color(0xFFEEF0F4)
internal val ClassicInk = Color(0xFF172033)
internal val ClassicFocusOutline = Color(0xFF0879FF)
private val ClassicStatusBarBg = Color(0xFFEEF2F7)
private val ClassicSignalInactive = Color(0xFF94A3B8)

data class ClassicHomeContentModel(
    val homeTileIds: List<String>,
    val menuTileIds: List<String>,
    val showsRoleRepair: Boolean,
)

fun classicHomeContentModel(home: HomeSnapshot): ClassicHomeContentModel =
    ClassicHomeContentModel(
        homeTileIds = home.homeTiles.map { it.id },
        menuTileIds = home.tiles.map { it.id },
        showsRoleRepair = home.homeRole != HomeRoleState.GRANTED,
    )

@Composable
fun ClassicPhoneScreen(
    home: HomeSnapshot,
    onOpenMenu: () -> Unit,
    onTileSelected: (HomeTile) -> Unit,
    onRepairHomeRole: () -> Unit,
    onReturnToCall: () -> Unit,
    onDigitSelected: (Char) -> Unit = {},
    onBack: () -> Unit = {},
    onHome: () -> Unit = {},
    assignments: Map<Char, String> = emptyMap(),
    onLongDigitSelected: ((Char) -> Unit)? = null,
) {
    val phoneTile = home.tiles.firstOrNull { it.kind == HomeTileKind.PHONE }
    val displayScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("classic-home")
            .background(ClassicFrame)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ClassicDisplay),
        ) {
            val minDisplayHeight = if (constraints.hasBoundedHeight) maxHeight else 0.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(displayScrollState),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minDisplayHeight),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ClassicHomeStatusBar(home)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1.38f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CompositionLocalProvider(
                                    LocalLayoutDirection provides LayoutDirection.Ltr,
                                ) {
                                    Text(
                                        text = home.time.ifBlank {
                                            stringResource(R.string.classic_brand_claro)
                                        },
                                        modifier = Modifier.semantics { heading() },
                                        color = ClassicInk,
                                        fontSize = 52.sp,
                                        lineHeight = 54.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                }
                                Text(
                                    text = home.dayAndDate,
                                    color = ClassicInk,
                                    fontSize = 20.sp,
                                    lineHeight = 24.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        ClassicHomeTileGrid(
                            tiles = home.homeTiles,
                            onTileSelected = onTileSelected,
                        )
                        if (home.homeRole != HomeRoleState.GRANTED) {
                            ClassicLightButton(
                                label = when (home.homeRole) {
                                    HomeRoleState.AVAILABLE ->
                                        stringResource(R.string.classic_choose_claro_as_home)
                                    HomeRoleState.UNAVAILABLE ->
                                        stringResource(R.string.classic_open_home_settings)
                                    HomeRoleState.UNKNOWN ->
                                        stringResource(R.string.classic_repair_home_access)
                                    HomeRoleState.GRANTED -> ""
                                },
                                onClick = onRepairHomeRole,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                                color = ClaroColors.WarningBg,
                                textColor = ClaroColors.WarningInk,
                                borderColor = ClaroColors.WarningBorder,
                            )
                        }
                        home.activeCall?.let { activeCall ->
                            val callerDisplayName = when (activeCall.displayName) {
                                "Private number" -> stringResource(R.string.classic_private_number)
                                "Unknown caller" -> stringResource(R.string.classic_unknown_caller)
                                else -> activeCall.displayName
                            }
                            ClassicLightButton(
                                label = stringResource(
                                    R.string.classic_return_to_call,
                                    callerDisplayName,
                                ),
                                onClick = onReturnToCall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                color = ClaroColors.Green,
                                textColor = Color.White,
                                borderColor = ClaroColors.Green,
                                leadingIcon = ClaroIconKind.PHONE,
                            )
                        }
                    }
                }
            }
        }
        ClassicIntegratedPad(
            menuLabel = stringResource(R.string.classic_menu),
            greenLabel = "☎ ${stringResource(R.string.classic_call)}",
            redLabel = "⌂ ${stringResource(R.string.classic_home)}",
            onBack = onBack,
            onMenu = onOpenMenu,
            onGreen = phoneTile?.let { tile -> { onTileSelected(tile) } },
            onRed = onHome,
            scrollState = displayScrollState,
        )
        ClassicNumericKeypad(
            onDigitSelected = onDigitSelected,
            assignments = assignments,
            onLongDigitSelected = onLongDigitSelected,
        )
    }
}

@Composable
private fun ClassicHomeStatusBar(home: HomeSnapshot) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(ClassicStatusBarBg)
                .padding(horizontal = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.classic_brand_claro),
                color = ClassicInk,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            ClaroCellularBars(
                cellular = home.cellular,
                activeColor = ClassicInk,
                inactiveColor = ClassicSignalInactive,
            )
            Text(
                text = stringResource(
                    R.string.classic_battery_dot_percent,
                    home.battery.percentage,
                ),
                color = ClassicInk,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            ClassicCompactBatteryBar(battery = home.battery)
        }
    }
}

@Composable
private fun ClassicCompactBatteryBar(battery: BatteryStatus) {
    val label = if (battery.isCharging) {
        stringResource(R.string.classic_battery_percent_charging, battery.percentage)
    } else {
        stringResource(R.string.classic_battery_percent, battery.percentage)
    }
    val fillFraction = battery.percentage.coerceIn(0, 100) / 100f
    val fillColor = if (battery.percentage <= 15 && !battery.isCharging) {
        ClaroColors.Red
    } else {
        Color(0xFF22C55E)
    }
    Row(
        modifier = Modifier.semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(12.dp)
                .border(1.5.dp, ClassicInk, RoundedCornerShape(2.dp))
                .padding(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(20.dp * fillFraction)
                    .background(fillColor),
            )
        }
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(5.dp)
                .background(ClassicInk),
        )
    }
}

private const val CLASSIC_HOME_COLUMNS = 3

@Composable
private fun ClassicHomeTileGrid(
    tiles: List<HomeTile>,
    onTileSelected: (HomeTile) -> Unit,
) {
    if (tiles.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tiles.chunked(CLASSIC_HOME_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { tile ->
                    ClassicHomeTile(
                        tile = tile,
                        modifier = Modifier.weight(1f),
                        onClick = { onTileSelected(tile) },
                    )
                }
                repeat(CLASSIC_HOME_COLUMNS - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ClassicHomeTile(
    tile: HomeTile,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val haptics = LocalClaroHaptics.current
    val tileLabel = when (tile.kind) {
        HomeTileKind.PHONE -> stringResource(R.string.classic_phone_title)
        HomeTileKind.MESSAGES -> stringResource(R.string.classic_messages)
        HomeTileKind.SOS -> stringResource(R.string.classic_sos)
        HomeTileKind.CAMERA -> stringResource(R.string.classic_tile_camera)
        HomeTileKind.GALLERY -> stringResource(R.string.classic_tile_gallery)
        HomeTileKind.APPS -> stringResource(R.string.classic_tile_apps)
        HomeTileKind.ALARM -> stringResource(R.string.classic_tile_alarm)
        HomeTileKind.CONTACT, HomeTileKind.INSTALLED_APP -> tile.label
    }
    val badgeDescription = when (val badge = tile.badge) {
        is BadgeValue.Known -> if (badge.count == 0) {
            stringResource(R.string.classic_badge_no_new, tileLabel)
        } else {
            stringResource(R.string.classic_badge_new_count, tileLabel, badge.count)
        }
        BadgeValue.Unknown -> stringResource(R.string.classic_badge_unknown, tileLabel)
        null -> tileLabel
    }
    val isSos = tile.kind == HomeTileKind.SOS
    Box(
        modifier = modifier
            .heightIn(min = 86.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (isSos) ClaroColors.Red else Color(0xFFEEF3FB))
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) ClassicFocusOutline else Color(0xFFBAC8DC),
                shape = RoundedCornerShape(3.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                role = Role.Button,
                onClick = {
                    haptics.softkey()
                    onClick()
                },
            )
            .semantics {
                contentDescription = badgeDescription
                role = Role.Button
            }
            .padding(horizontal = 3.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            when (tile.kind) {
                HomeTileKind.INSTALLED_APP -> ClaroAppIcon(packageName = tile.packageName, size = 38.dp)
                HomeTileKind.CONTACT -> ClaroAvatar(name = tile.label, size = 38.dp)
                else -> ClaroIcon(
                    kind = tile.kind.classicIconKind(),
                    tint = if (isSos) Color.White else tile.kind.classicIconTint(),
                    size = 36.dp,
                )
            }
            Text(
                text = tileLabel,
                color = if (isSos) Color.White else ClassicInk,
                fontSize = 15.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        tile.badge?.let { badge ->
            ClassicBadge(badge = badge, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

private fun HomeTileKind.classicIconKind(): ClaroIconKind = when (this) {
    HomeTileKind.PHONE -> ClaroIconKind.PHONE
    HomeTileKind.MESSAGES -> ClaroIconKind.MESSAGES
    HomeTileKind.CAMERA -> ClaroIconKind.CAMERA
    HomeTileKind.GALLERY -> ClaroIconKind.GALLERY
    HomeTileKind.SOS -> ClaroIconKind.SOS
    HomeTileKind.APPS, HomeTileKind.INSTALLED_APP -> ClaroIconKind.APPS
    HomeTileKind.CONTACT -> ClaroIconKind.CONTACT
    HomeTileKind.ALARM -> ClaroIconKind.CLOCK
}

private fun HomeTileKind.classicIconTint(): Color = when (this) {
    HomeTileKind.PHONE -> ClaroColors.Green
    HomeTileKind.CAMERA, HomeTileKind.ALARM -> ClaroColors.Orange
    HomeTileKind.GALLERY -> ClaroColors.Violet
    HomeTileKind.SOS -> ClaroColors.Red
    else -> ClaroColors.Blue
}

@Composable
private fun ClassicBadge(badge: BadgeValue, modifier: Modifier = Modifier) {
    val text = when (badge) {
        is BadgeValue.Known -> badge.count.takeIf { it > 0 }?.toString() ?: return
        BadgeValue.Unknown -> "?"
    }
    Box(
        modifier = modifier
            .size(30.dp)
            .background(ClaroColors.Red, CircleShape)
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 18.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
internal fun ClassicHeader(
    title: String,
    centered: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .background(ClassicFrame)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
fun ClassicSystemStatusBar(
    battery: BatteryStatus? = null,
    cellular: CellularStatus? = null,
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    var time by remember(locale) {
        mutableStateOf(DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date()))
    }
    LaunchedEffect(locale) {
        while (true) {
            time = DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date())
            delay(15_000L)
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(ClassicStatusBarBg)
                .padding(horizontal = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = time,
                color = ClassicInk,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (cellular != null) {
                ClaroCellularBars(
                    cellular = cellular,
                    activeColor = ClassicInk,
                    inactiveColor = ClassicSignalInactive,
                )
            }
            if (battery != null) {
                Text(
                    text = stringResource(
                        R.string.classic_battery_percent_short,
                        battery.percentage,
                    ),
                    color = ClassicInk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                ClaroBatteryMeter(
                    battery = battery,
                    borderColor = ClassicInk,
                    backgroundColor = Color(0xFFD8E0EC),
                )
            }
        }
    }
}

@Composable
internal fun ClassicLightButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = ClassicDisplayMuted,
    textColor: Color = ClassicInk,
    borderColor: Color = Color(0xFFB8C2D0),
    enabled: Boolean = true,
    minHeight: Int = 64,
    leadingIcon: ClaroIconKind? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val haptics = LocalClaroHaptics.current
    Button(
        onClick = {
            if (color == ClaroColors.Red) {
                haptics.alertDouble()
            } else {
                haptics.softkey()
            }
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp)
            .onFocusChanged { isFocused = it.isFocused },
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) ClassicFocusOutline else borderColor,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = textColor,
            disabledContainerColor = Color(0xFFD8DDE5),
            disabledContentColor = Color(0xFF788497),
        ),
        shape = RoundedCornerShape(3.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        if (leadingIcon != null) {
            ClaroIcon(
                kind = leadingIcon,
                tint = if (enabled) textColor else Color(0xFF788497),
                size = 24.dp,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        Text(
            text = label,
            fontSize = 19.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun ClassicRowItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    unreadDot: Boolean = false,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val haptics = LocalClaroHaptics.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isFocused) {
                    Modifier.border(2.5.dp, ClassicFocusOutline)
                } else {
                    Modifier
                },
            ),
    ) {
        Button(
            onClick = {
                haptics.softkey()
                onClick()
            },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 66.dp)
                .onFocusChanged { isFocused = it.isFocused },
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = ClassicRowSurface,
                contentColor = ClassicInk,
                disabledContainerColor = ClassicRowSurface.copy(alpha = 0.6f),
                disabledContentColor = Color(0xFF788497),
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
                        color = if (enabled) ClassicInk else Color(0xFF788497),
                        fontSize = 23.sp,
                        lineHeight = 27.sp,
                        fontWeight = if (unreadDot) FontWeight.Bold else FontWeight.SemiBold,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = if (unreadDot) "● $subtitle" else subtitle,
                            color = Color(0xFF4B5565),
                            fontSize = 17.sp,
                            lineHeight = 21.sp,
                        )
                    }
                }
                if (trailing != null) {
                    trailing()
                } else {
                    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                    Text(
                        text = if (isRtl) "←" else "→",
                        color = Color(0xFF5A687C),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        HorizontalDivider(thickness = 2.dp, color = ClassicFrame)
    }
}

internal data class ClassicOptionAction(
    val label: String,
    val enabled: Boolean = true,
    val onSelect: () -> Unit,
)

@Composable
internal fun ClassicOptionsOverlay(
    options: List<ClassicOptionAction>,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            ClassicRowItem(
                title = option.label,
                enabled = option.enabled,
                onClick = {
                    onClose()
                    option.onSelect()
                },
            )
        }
    }
}

@Composable
internal fun ClassicModeStatusStrip(
    modeLabel: String? = null,
    feedback: String? = null,
) {
    if (modeLabel == null && feedback.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!feedback.isNullOrBlank()) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = feedback,
                    color = ClassicKeySublabel,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (modeLabel != null) {
            Box(
                modifier = Modifier
                    .background(ClassicDeck, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = modeLabel,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun ClassicLtrText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Center,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            textAlign = textAlign,
            modifier = modifier,
        )
    }
}

@Composable
fun ClassicIntegratedPad(
    menuLabel: String = "",
    greenLabel: String = "",
    redLabel: String = "",
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    onGreen: (() -> Unit)? = null,
    onRed: (() -> Unit)? = null,
    scrollState: ScrollState? = null,
    onOk: (() -> Unit)? = null,
    backLabel: String = "",
    backIcon: ClaroIconKind? = null,
    greenIcon: ClaroIconKind? = null,
    menuIcon: ClaroIconKind? = null,
    greenEnabled: Boolean = true,
    menuEnabled: Boolean = true,
    greenColor: Color? = null,
) {
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val resolvedMenuLabel = if (menuLabel.isBlank() || menuLabel == "Menu") {
        stringResource(R.string.classic_menu)
    } else {
        menuLabel
    }
    val resolvedGreenLabel = if (greenLabel.isBlank() || greenLabel == "☎ Call") {
        "☎ ${stringResource(R.string.classic_call)}"
    } else {
        greenLabel
    }
    val resolvedRedLabel = if (redLabel.isBlank() || redLabel == "⌂ Home") {
        "⌂ ${stringResource(R.string.classic_home)}"
    } else {
        redLabel
    }

    val sendKey: (Int) -> Unit = { keyCode ->
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
    val navigate: (FocusDirection, Int, Int) -> Unit = { direction, keyCode, scrollDelta ->
        val moved = runCatching { focusManager.moveFocus(direction) }.getOrDefault(false)
        if (!moved) {
            sendKey(keyCode)
            if (scrollState != null && scrollDelta != 0) {
                val target = (scrollState.value + scrollDelta).coerceIn(0, scrollState.maxValue)
                if (target != scrollState.value) {
                    scope.launch { scrollState.animateScrollTo(target) }
                }
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(146.dp)
                .focusProperties { canFocus = false },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ClassicDeckButton(
                    label = backLabel.ifBlank { stringResource(R.string.classic_back) },
                    color = ClassicDeck,
                    borderColor = ClassicDeckBorder,
                    modifier = Modifier.weight(1f),
                    onClick = onBack,
                    explicitIconKind = backIcon,
                )
                ClassicDeckButton(
                    label = resolvedGreenLabel,
                    color = greenColor ?: ClaroColors.Green,
                    borderColor = if (greenColor != null) Color(0xFF5B9BF0) else Color(0xFF29A564),
                    modifier = Modifier.weight(1f),
                    onClick = if (greenEnabled) onGreen else null,
                    explicitIconKind = greenIcon,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1.32f)
                    .height(146.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(ClassicDeck)
                    .border(1.dp, ClassicDpadBorder, RoundedCornerShape(20.dp))
                    .padding(3.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ClassicDirectionButton(
                    label = "↑",
                    modifier = Modifier.fillMaxWidth(),
                    accessibilityLabel = stringResource(R.string.classic_navigate_up),
                    onClick = { navigate(FocusDirection.Up, KeyEvent.KEYCODE_DPAD_UP, -220) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ClassicDirectionButton(
                        label = "←",
                        modifier = Modifier.weight(1f),
                        accessibilityLabel = stringResource(R.string.classic_navigate_left),
                        onClick = { navigate(FocusDirection.Left, KeyEvent.KEYCODE_DPAD_LEFT, 0) },
                    )
                    ClassicDirectionButton(
                        label = stringResource(R.string.classic_ok),
                        modifier = Modifier.weight(1.1f),
                        center = true,
                        onClick = {
                            if (onOk != null) {
                                onOk()
                            } else {
                                sendKey(KeyEvent.KEYCODE_ENTER)
                            }
                        },
                    )
                    ClassicDirectionButton(
                        label = "→",
                        modifier = Modifier.weight(1f),
                        accessibilityLabel = stringResource(R.string.classic_navigate_right),
                        onClick = { navigate(FocusDirection.Right, KeyEvent.KEYCODE_DPAD_RIGHT, 0) },
                    )
                }
                ClassicDirectionButton(
                    label = "↓",
                    modifier = Modifier.fillMaxWidth(),
                    accessibilityLabel = stringResource(R.string.classic_navigate_down),
                    onClick = { navigate(FocusDirection.Down, KeyEvent.KEYCODE_DPAD_DOWN, 220) },
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ClassicDeckButton(
                    label = resolvedMenuLabel,
                    color = ClassicDeck,
                    borderColor = ClassicDeckBorder,
                    modifier = Modifier.weight(1f),
                    onClick = if (menuEnabled) onMenu else null,
                    explicitIconKind = menuIcon,
                )
                ClassicDeckButton(
                    label = resolvedRedLabel,
                    color = ClaroColors.Red,
                    borderColor = Color(0xFFE76674),
                    modifier = Modifier.weight(1f),
                    onClick = onRed,
                )
            }
        }
    }
}

@Composable
private fun ClassicDeckButton(
    label: String,
    color: Color,
    borderColor: Color,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    explicitIconKind: ClaroIconKind? = null,
) {
    val cleanLabel = label.replace(Regex("^[☎⌂✉↻＋✓…\\s]+"), "").trim().ifBlank { label }
    val declineLabel = stringResource(R.string.classic_decline)
    val endLabel = stringResource(R.string.classic_end)
    val haptics = LocalClaroHaptics.current
    val isHeavyAction = color == ClaroColors.Green || color == ClaroColors.Red
    val iconKind = explicitIconKind ?: when {
        cleanLabel.equals("Decline", ignoreCase = true) ||
            cleanLabel.equals("End", ignoreCase = true) ||
            cleanLabel.equals(declineLabel, ignoreCase = true) ||
            cleanLabel.equals(endLabel, ignoreCase = true) -> ClaroIconKind.END_CALL
        label.startsWith("☎") -> ClaroIconKind.PHONE
        label.startsWith("⌂") -> ClaroIconKind.HOME
        label.startsWith("✉") -> ClaroIconKind.MESSAGES
        label.startsWith("✓") -> ClaroIconKind.CHECK
        else -> null
    }
    val tint = if (onClick != null) Color.White else Color.White.copy(alpha = 0.55f)
    Button(
        onClick = {
            if (isHeavyAction) {
                haptics.confirmHeavy()
            } else {
                haptics.softkey()
            }
            onClick?.invoke()
        },
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, if (onClick != null) borderColor else borderColor.copy(alpha = 0.45f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = color.copy(alpha = 0.55f),
            disabledContentColor = Color.White.copy(alpha = 0.55f),
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (iconKind != null) {
                ClaroIcon(
                    kind = iconKind,
                    tint = tint,
                    size = 20.dp,
                )
            }
            Text(
                text = cleanLabel,
                color = tint,
                fontSize = 16.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ClassicDirectionButton(
    label: String,
    modifier: Modifier = Modifier,
    center: Boolean = false,
    accessibilityLabel: String? = null,
    onClick: () -> Unit,
) {
    val haptics = LocalClaroHaptics.current
    Box(
        modifier = modifier
            .height(if (center) 48.dp else 44.dp)
            .then(
                if (center) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ClassicOkFill)
                        .border(1.dp, ClassicOkBorder, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .clickable(
                role = Role.Button,
                onClick = {
                    if (center) haptics.keyClick() else haptics.dpadStep()
                    onClick()
                },
            )
            .then(
                if (accessibilityLabel != null) {
                    Modifier.semantics {
                        contentDescription = accessibilityLabel
                        role = Role.Button
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (center) Color.White else Color(0xFFE2E8F0),
            fontSize = if (center) 20.sp else 28.sp,
            lineHeight = if (center) 22.sp else 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ClassicNumericKeypad(
    onDigitSelected: ((Char) -> Unit)?,
    assignments: Map<Char, String> = emptyMap(),
    onLongDigitSelected: ((Char) -> Unit)? = null,
) {
    val haptics = LocalClaroHaptics.current
    val legends = mapOf(
        '1' to stringResource(R.string.classic_keypad_legend_1),
        '2' to stringResource(R.string.classic_keypad_legend_2),
        '3' to stringResource(R.string.classic_keypad_legend_3),
        '4' to stringResource(R.string.classic_keypad_legend_4),
        '5' to stringResource(R.string.classic_keypad_legend_5),
        '6' to stringResource(R.string.classic_keypad_legend_6),
        '7' to stringResource(R.string.classic_keypad_legend_7),
        '8' to stringResource(R.string.classic_keypad_legend_8),
        '9' to stringResource(R.string.classic_keypad_legend_9),
        '*' to stringResource(R.string.classic_keypad_legend_star),
        '0' to stringResource(R.string.classic_keypad_space),
        '#' to stringResource(R.string.classic_keypad_legend_pound),
    )
    val keys = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#'),
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier.focusProperties { canFocus = false },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            keys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    row.forEach { key ->
                        val assignedName = assignments[key]?.takeIf { it.isNotBlank() }?.take(10)
                        val legend = assignedName ?: legends.getValue(key)
                        val digitAction = onDigitSelected
                        val enabled = digitAction != null
                        val keyDescription = if (
                            (key == '1' || key == '2') && onLongDigitSelected != null
                        ) {
                            stringResource(
                                R.string.classic_keypad_key_speed_dial,
                                key.toString(),
                                legend,
                            )
                        } else {
                            stringResource(
                                R.string.classic_keypad_key_description,
                                key.toString(),
                                legend,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClassicKey)
                                .border(
                                    width = 1.dp,
                                    color = if (enabled) {
                                        ClassicKeyBorder
                                    } else {
                                        ClassicKeyBorder.copy(alpha = 0.45f)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .then(
                                    if (enabled) {
                                        Modifier.combinedClickable(
                                            role = Role.Button,
                                            hapticFeedbackEnabled = false,
                                            onClick = {
                                                haptics.keyClick()
                                                digitAction(key)
                                            },
                                            onLongClick = {
                                                if ((key == '1' || key == '2') && onLongDigitSelected != null) {
                                                    haptics.confirmHeavy()
                                                    onLongDigitSelected(key)
                                                } else {
                                                    haptics.keyClick()
                                                    digitAction(key)
                                                }
                                            },
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .semantics {
                                    contentDescription = keyDescription
                                    role = Role.Button
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = key.toString(),
                                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.48f),
                                    fontSize = 32.sp,
                                    lineHeight = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = legend,
                                    color = if (enabled) {
                                        ClassicKeySublabel
                                    } else {
                                        ClassicKeySublabel.copy(alpha = 0.48f)
                                    },
                                    fontSize = 12.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal data class ClassicMultiTapState(
    val fieldKey: String,
    val digit: Char,
    val cycleIndex: Int,
    val replaceIndex: Int,
    val committedValue: String,
    val timestampElapsedMillis: Long,
)

private val classicMultiTapAlphabets = mapOf(
    '1' to ".,?!1",
    '2' to "abc2",
    '3' to "def3",
    '4' to "ghi4",
    '5' to "jkl5",
    '6' to "mno6",
    '7' to "pqrs7",
    '8' to "tuv8",
    '9' to "wxyz9",
    '0' to " 0",
    '*' to ".,?!*",
)

internal fun applyClassicKeypadInput(
    currentValue: String,
    digit: Char,
    fieldKey: String,
    numericMode: Boolean,
    phoneNumberField: Boolean = false,
    previousTap: ClassicMultiTapState?,
): Pair<String, ClassicMultiTapState?> {
    if (phoneNumberField) {
        val appended = when {
            digit == '*' && currentValue.isEmpty() -> "+"
            digit in "0123456789*#" -> digit.toString()
            else -> ""
        }
        return (currentValue + appended) to null
    }
    if (numericMode) {
        return (currentValue + digit) to null
    }
    val alphabet = classicMultiTapAlphabets[digit] ?: return currentValue to null
    val now = SystemClock.elapsedRealtime()
    val continuing = previousTap != null &&
        previousTap.fieldKey == fieldKey &&
        previousTap.digit == digit &&
        (now - previousTap.timestampElapsedMillis) < 1_000L &&
        previousTap.committedValue == currentValue &&
        previousTap.replaceIndex in currentValue.indices

    val replaceIndex = if (continuing && previousTap != null) {
        previousTap.replaceIndex
    } else {
        currentValue.length
    }
    val nextCycleIndex = if (continuing && previousTap != null) {
        (previousTap.cycleIndex + 1) % alphabet.length
    } else {
        0
    }
    val prefix = currentValue.take(replaceIndex)
    val shouldCapitalize = prefix.isEmpty() ||
        prefix.endsWith(". ") ||
        prefix.endsWith("? ") ||
        prefix.endsWith("! ")
    val rawChar = alphabet[nextCycleIndex]
    val nextChar = if (shouldCapitalize && rawChar.isLetter()) {
        rawChar.uppercaseChar()
    } else {
        rawChar
    }
    val updatedValue = prefix + nextChar
    val nextState = ClassicMultiTapState(
        fieldKey = fieldKey,
        digit = digit,
        cycleIndex = nextCycleIndex,
        replaceIndex = replaceIndex,
        committedValue = updatedValue,
        timestampElapsedMillis = now,
    )
    return updatedValue to nextState
}
