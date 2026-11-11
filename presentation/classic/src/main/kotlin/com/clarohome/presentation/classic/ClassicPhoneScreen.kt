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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
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
import com.clarohome.core.design.ClaroAvatar
import com.clarohome.core.design.ClaroBatteryMeter
import com.clarohome.core.design.ClaroCellularBars
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroIcon
import com.clarohome.core.design.ClaroIconKind
import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.BatteryStatus
import com.clarohome.core.model.CellularStatus
import com.clarohome.core.model.FavoritePerson
import com.clarohome.core.model.FavoriteState
import com.clarohome.core.model.HomeRoleState
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind
import com.clarohome.feature.launcher.home.HomeCustomizationPolicy
import java.text.DateFormat
import java.util.Date
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
    val shortcutTileIds: List<String>,
    val menuTileIds: List<String>,
    val showsRoleRepair: Boolean,
)

fun classicHomeContentModel(home: HomeSnapshot): ClassicHomeContentModel =
    ClassicHomeContentModel(
        shortcutTileIds = home.tiles
            .filter { it.kind == HomeTileKind.PHONE || it.kind == HomeTileKind.MESSAGES }
            .map { it.id },
        menuTileIds = home.tiles.map { it.id },
        showsRoleRepair = home.homeRole != HomeRoleState.GRANTED,
    )

@Composable
fun ClassicPhoneScreen(
    home: HomeSnapshot,
    onOpenMenu: () -> Unit,
    onTileSelected: (HomeTile) -> Unit,
    onFavoriteSelected: (FavoritePerson) -> Unit,
    onRepairHomeRole: () -> Unit,
    onReturnToCall: () -> Unit,
    onDigitSelected: (Char) -> Unit = {},
    onBack: () -> Unit = {},
    onHome: () -> Unit = {},
    assignments: Map<Char, String> = emptyMap(),
    onLongDigitSelected: ((Char) -> Unit)? = null,
) {
    val phoneTile = home.tiles.firstOrNull { it.kind == HomeTileKind.PHONE }
    val messagesTile = home.tiles.firstOrNull { it.kind == HomeTileKind.MESSAGES }
    val sosTile = home.tiles.firstOrNull { it.kind == HomeTileKind.SOS }
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
                                        text = home.time.ifBlank { "Claro" },
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
                            phoneTile?.let { tile ->
                                ClassicShortcut(
                                    tile = tile,
                                    modifier = Modifier.weight(0.75f),
                                    onClick = { onTileSelected(tile) },
                                )
                            }
                            messagesTile?.let { tile ->
                                ClassicShortcut(
                                    tile = tile,
                                    modifier = Modifier.weight(0.75f),
                                    onClick = { onTileSelected(tile) },
                                )
                            }
                        }
                        FavoriteRow(
                            favorites = home.favorites,
                            onFavoriteSelected = onFavoriteSelected,
                        )
                        if (home.homeRole != HomeRoleState.GRANTED) {
                            ClassicLightButton(
                                label = when (home.homeRole) {
                                    HomeRoleState.AVAILABLE -> "Choose Claro as Home"
                                    HomeRoleState.UNAVAILABLE -> "Open Home settings"
                                    HomeRoleState.UNKNOWN -> "Repair Home access"
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
                            ClassicLightButton(
                                label = "Return to call · ${activeCall.displayName}",
                                onClick = onReturnToCall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                color = ClaroColors.Green,
                                textColor = Color.White,
                                borderColor = ClaroColors.Green,
                                leadingIcon = ClaroIconKind.PHONE,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        messagesTile?.let { tile ->
                            ClassicLightButton(
                                label = "Messages",
                                onClick = { onTileSelected(tile) },
                                modifier = Modifier.weight(1f),
                                color = ClassicDisplayMuted,
                                textColor = ClassicInk,
                                leadingIcon = ClaroIconKind.MESSAGES,
                            )
                        }
                        sosTile?.let { tile ->
                            ClassicLightButton(
                                label = "SOS",
                                onClick = { onTileSelected(tile) },
                                modifier = Modifier.weight(1f),
                                color = ClaroColors.Red,
                                textColor = Color.White,
                                borderColor = ClaroColors.Red,
                                leadingIcon = ClaroIconKind.SOS,
                            )
                        }
                    }
                }
            }
        }
        ClassicIntegratedPad(
            menuLabel = "Menu",
            greenLabel = "☎ Call",
            redLabel = "⌂ Home",
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
                text = "Claro",
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
                text = "· ${home.battery.percentage}%",
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
    val label = buildString {
        append("Battery ${battery.percentage} percent")
        if (battery.isCharging) append(", charging")
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

@Composable
private fun ClassicShortcut(
    tile: HomeTile,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val hasVisibleBadge = when (val badge = tile.badge) {
        is BadgeValue.Known -> badge.count > 0
        BadgeValue.Unknown -> true
        null -> false
    }
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFFEEF3FB))
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) ClassicFocusOutline else Color(0xFFBAC8DC),
                shape = RoundedCornerShape(3.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = HomeCustomizationPolicy.badgeLabel(tile.label, tile.badge)
                role = Role.Button
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        ClaroIcon(
            kind = if (tile.kind == HomeTileKind.PHONE) {
                ClaroIconKind.PHONE
            } else {
                ClaroIconKind.MESSAGES
            },
            tint = if (tile.kind == HomeTileKind.PHONE) ClaroColors.Green else ClaroColors.Blue,
            size = 28.dp,
            modifier = Modifier.align(
                if (hasVisibleBadge) Alignment.BottomStart else Alignment.Center,
            ),
        )
        tile.badge?.let { badge ->
            ClassicBadge(badge = badge, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
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
private fun FavoriteRow(
    favorites: List<FavoritePerson>,
    onFavoriteSelected: (FavoritePerson) -> Unit,
) {
    val slots = remember(favorites) {
        List(4) { index ->
            favorites.getOrNull(index) ?: FavoritePerson(
                id = "unassigned-$index",
                displayName = "+",
                phoneNumber = null,
                state = FavoriteState.UNASSIGNED,
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        slots.forEachIndexed { index, person ->
            var isFocused by remember { mutableStateOf(false) }
            val isUnassigned = person.state == FavoriteState.UNASSIGNED ||
                person.displayName.isBlank() ||
                person.displayName == "+"
            val shortName = if (isUnassigned) {
                "+"
            } else {
                person.displayName.trim().substringBefore(' ')
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 82.dp)
                    .background(Color(0xFFF0F3F8), RoundedCornerShape(2.dp))
                    .then(
                        if (isFocused) {
                            Modifier.border(2.5.dp, ClassicFocusOutline, RoundedCornerShape(2.dp))
                        } else {
                            Modifier
                        },
                    )
                    .onFocusChanged { isFocused = it.isFocused }
                    .clickable(role = Role.Button) { onFavoriteSelected(person) }
                    .semantics {
                        contentDescription = if (isUnassigned) {
                            "Add person, favorite slot ${index + 1}"
                        } else {
                            "${person.displayName}, favorite person"
                        }
                        role = Role.Button
                    }
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                ClaroAvatar(
                    name = person.displayName,
                    size = 44.dp,
                    paletteSeed = index,
                    isUnassigned = isUnassigned,
                )
                Text(
                    text = shortName,
                    color = ClassicInk,
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
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
    var time by remember {
        mutableStateOf(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
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
                    text = "${battery.percentage}%",
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
    Button(
        onClick = onClick,
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
) {
    var isFocused by remember { mutableStateOf(false) }
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
            onClick = onClick,
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
                Text(
                    text = "→",
                    color = Color(0xFF5A687C),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
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
    menuLabel: String = "Menu",
    greenLabel: String = "☎ Call",
    redLabel: String = "⌂ Home",
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    onGreen: (() -> Unit)? = null,
    onRed: (() -> Unit)? = null,
    scrollState: ScrollState? = null,
    onOk: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

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
                    label = "Back",
                    color = ClassicDeck,
                    borderColor = ClassicDeckBorder,
                    modifier = Modifier.weight(1f),
                    onClick = onBack,
                )
                ClassicDeckButton(
                    label = greenLabel,
                    color = ClaroColors.Green,
                    borderColor = Color(0xFF29A564),
                    modifier = Modifier.weight(1f),
                    onClick = onGreen,
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
                    accessibilityLabel = "Navigate up",
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
                        accessibilityLabel = "Navigate left",
                        onClick = { navigate(FocusDirection.Left, KeyEvent.KEYCODE_DPAD_LEFT, 0) },
                    )
                    ClassicDirectionButton(
                        label = "OK",
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
                        accessibilityLabel = "Navigate right",
                        onClick = { navigate(FocusDirection.Right, KeyEvent.KEYCODE_DPAD_RIGHT, 0) },
                    )
                }
                ClassicDirectionButton(
                    label = "↓",
                    modifier = Modifier.fillMaxWidth(),
                    accessibilityLabel = "Navigate down",
                    onClick = { navigate(FocusDirection.Down, KeyEvent.KEYCODE_DPAD_DOWN, 220) },
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ClassicDeckButton(
                    label = menuLabel,
                    color = ClassicDeck,
                    borderColor = ClassicDeckBorder,
                    modifier = Modifier.weight(1f),
                    onClick = onMenu,
                )
                ClassicDeckButton(
                    label = redLabel,
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
) {
    val cleanLabel = label.replace(Regex("^[☎⌂✉↻＋✓…\\s]+"), "").trim().ifBlank { label }
    val iconKind = when {
        cleanLabel.equals("Decline", ignoreCase = true) ||
            cleanLabel.equals("End", ignoreCase = true) -> ClaroIconKind.END_CALL
        label.startsWith("☎") -> ClaroIconKind.PHONE
        label.startsWith("⌂") -> ClaroIconKind.HOME
        label.startsWith("✉") -> ClaroIconKind.MESSAGES
        label.startsWith("✓") -> ClaroIconKind.CHECK
        else -> null
    }
    val tint = if (onClick != null) Color.White else Color.White.copy(alpha = 0.55f)
    Button(
        onClick = { onClick?.invoke() },
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
            .clickable(role = Role.Button, onClick = onClick)
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
    val legends = mapOf(
        '1' to ".,?!",
        '2' to "ABC",
        '3' to "DEF",
        '4' to "GHI",
        '5' to "JKL",
        '6' to "MNO",
        '7' to "PQRS",
        '8' to "TUV",
        '9' to "WXYZ",
        '*' to "+",
        '0' to "space",
        '#' to "abc / 123",
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
                                            onClick = { digitAction(key) },
                                            onLongClick = {
                                                if ((key == '1' || key == '2') && onLongDigitSelected != null) {
                                                    onLongDigitSelected(key)
                                                } else {
                                                    digitAction(key)
                                                }
                                            },
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .semantics {
                                    contentDescription = buildString {
                                        append(key)
                                        if (legend.isNotBlank()) {
                                            append(", ")
                                            append(legend)
                                        }
                                        if ((key == '1' || key == '2') && onLongDigitSelected != null) {
                                            append(". Hold for speed dial")
                                        }
                                    }
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
