package com.clarohome.presentation.touch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarohome.core.design.ClaroAvatar
import com.clarohome.core.design.ClaroAppIcon
import com.clarohome.core.design.ClaroBannerTone
import com.clarohome.core.design.ClaroBatteryMeter
import com.clarohome.core.design.ClaroCellularBars
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.core.design.ClaroIcon
import com.clarohome.core.design.ClaroIconKind
import com.clarohome.core.design.ClaroInfoCard
import com.clarohome.core.design.ClaroPrimaryButton
import com.clarohome.core.design.LocalClaroHaptics
import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.CellularAvailability
import com.clarohome.core.model.CellularStatus
import com.clarohome.core.model.FavoritePerson
import com.clarohome.core.model.FavoriteState
import com.clarohome.core.model.HomeRoleState
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind
import com.clarohome.feature.launcher.home.HomeCustomizationPolicy
import com.clarohome.presentation.touch.R

data class TouchHomeContentModel(
    val visibleTileIds: List<String>,
    val showsRoleRepair: Boolean,
    val badgeDescriptions: List<String>,
)

fun touchHomeContentModel(home: HomeSnapshot): TouchHomeContentModel =
    TouchHomeContentModel(
        visibleTileIds = home.tiles.map { it.id },
        showsRoleRepair = home.homeRole != HomeRoleState.GRANTED,
        badgeDescriptions = home.tiles
            .filter { it.badge != null }
            .map { HomeCustomizationPolicy.badgeLabel(it.label, it.badge) },
    )

@Composable
fun TouchHomeScreen(
    home: HomeSnapshot,
    onUseClassicMode: () -> Unit,
    onTileSelected: (HomeTile) -> Unit,
    onFavoriteSelected: (FavoritePerson) -> Unit,
    onRepairHomeRole: () -> Unit,
    onReturnToCall: () -> Unit,
) {
    val coreTiles = home.tiles.take(6)
    val extraTiles = home.tiles.drop(6)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("touch-home")
            .background(ClaroColors.Charcoal),
        verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
    ) {
        item {
            TouchStatusHeader(home, onUseClassicMode)
        }
        if (home.homeRole != HomeRoleState.GRANTED) {
            item {
                RoleRepairCard(home.homeRole, onRepairHomeRole)
            }
        }
        home.activeCall?.let { activeCall ->
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ClaroDimensions.TileGap),
                ) {
                    ClaroPrimaryButton(
                        label = stringResource(R.string.touch_return_to_call, activeCall.displayName),
                        onClick = onReturnToCall,
                        containerColor = ClaroColors.Green,
                        icon = ClaroIconKind.PHONE,
                    )
                }
            }
        }
        item {
            FavoriteStrip(home.favorites, onFavoriteSelected)
        }
        items(coreTiles.chunked(2), key = { row -> "core-${row.joinToString { it.id }}" }) { row ->
            TileGridRow(row = row, onTileSelected = onTileSelected)
        }
        if (extraTiles.isNotEmpty()) {
            item(key = "scroll-cue") {
                val moreAppsDescription = stringResource(R.string.touch_more_apps_below)
                Text(
                    text = "⌄",
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = moreAppsDescription },
                    color = Color.White,
                    fontSize = 20.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
            }
            items(extraTiles.chunked(2), key = { row -> "extra-${row.joinToString { it.id }}" }) { row ->
                TileGridRow(row = row, onTileSelected = onTileSelected)
            }
        }
    }
}

@Composable
private fun TouchStatusHeader(
    home: HomeSnapshot,
    onUseClassicMode: () -> Unit,
) {
    val brandName = stringResource(R.string.touch_brand_name)
    val settingsLabel = stringResource(R.string.touch_settings)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .background(ClaroColors.Charcoal)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClaroBatteryMeter(battery = home.battery)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ClaroCellularBars(cellular = home.cellular)
                home.cellular.unavailableLabel()?.let { networkLabel ->
                    Text(
                        text = networkLabel,
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = home.dayAndDate.ifBlank { brandName },
                    modifier = Modifier.semantics { heading() },
                    color = Color.White,
                    fontSize = 26.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.touch_battery_percentage, home.battery.percentage),
                    color = Color(0xFFE0E6F0),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                )
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = home.time,
                    color = Color.White,
                    fontSize = 36.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            val haptics = LocalClaroHaptics.current
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF384354))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = settingsLabel,
                        onClick = {
                            haptics.softkey()
                            onUseClassicMode()
                        },
                    )
                    .semantics {
                        contentDescription = settingsLabel
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                ClaroIcon(
                    kind = ClaroIconKind.SETTINGS,
                    tint = Color.White,
                    size = 28.dp,
                )
            }
        }
    }
}

@Composable
private fun RoleRepairCard(
    roleState: HomeRoleState,
    onRepairHomeRole: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ClaroDimensions.TileGap),
        verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
    ) {
        ClaroInfoCard(
            text = when (roleState) {
                HomeRoleState.AVAILABLE -> stringResource(R.string.touch_home_role_available)
                HomeRoleState.UNAVAILABLE -> stringResource(R.string.touch_home_role_unavailable)
                HomeRoleState.UNKNOWN -> stringResource(R.string.touch_home_role_unknown)
                HomeRoleState.GRANTED -> ""
            },
            tone = ClaroBannerTone.WARNING,
        )
        ClaroPrimaryButton(
            label = if (roleState == HomeRoleState.AVAILABLE) {
                stringResource(R.string.touch_home_role_choose)
            } else {
                stringResource(R.string.touch_home_role_open_settings)
            },
            onClick = onRepairHomeRole,
            containerColor = ClaroColors.Blue,
        )
    }
}

@Composable
private fun FavoriteStrip(
    favorites: List<FavoritePerson>,
    onFavoriteSelected: (FavoritePerson) -> Unit,
) {
    val haptics = LocalClaroHaptics.current
    val addPersonLabel = stringResource(R.string.touch_add_person)
    val visibleFavorites = List(4) { index ->
        favorites.getOrNull(index) ?: FavoritePerson(
            id = "favorite-$index",
            displayName = addPersonLabel,
            state = FavoriteState.UNASSIGNED,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ClaroDimensions.TileGap),
        horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
    ) {
        visibleFavorites.forEachIndexed { index, person ->
            val unassigned = person.state == FavoriteState.UNASSIGNED
            val shortName = if (unassigned) {
                "+"
            } else {
                person.displayName.trim().split(Regex("\\s+")).firstOrNull()?.ifBlank { "+" } ?: "+"
            }
            val actionLabel = person.favoriteActionLabel()
            val favoriteDesc = person.favoriteDescription()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 84.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFFF0F3F8))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = actionLabel,
                    ) {
                        haptics.softkey()
                        onFavoriteSelected(person)
                    }
                    .semantics {
                        contentDescription = favoriteDesc
                        role = Role.Button
                    }
                    .padding(horizontal = 2.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                ClaroAvatar(
                    name = if (unassigned) addPersonLabel else person.displayName,
                    size = 51.dp,
                    paletteSeed = index,
                    isUnassigned = unassigned,
                )
                Text(
                    text = shortName,
                    color = ClaroColors.Ink,
                    fontSize = 19.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TileGridRow(
    row: List<HomeTile>,
    onTileSelected: (HomeTile) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ClaroDimensions.TileGap),
        horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
    ) {
        row.forEach { tile ->
            HomeTileCard(
                tile = tile,
                onClick = { onTileSelected(tile) },
                modifier = Modifier.weight(1f),
            )
        }
        if (row.size == 1) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeTileCard(
    tile: HomeTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalClaroHaptics.current
    val displayLabel = tile.kind.localizedLabel(tile.label)
    val accessibleLabel = if (tile.kind == HomeTileKind.SOS) {
        stringResource(R.string.touch_tile_sos_a11y)
    } else {
        displayLabel
    }
    val description = when (val badge = tile.badge) {
        is BadgeValue.Known -> if (badge.count == 0) {
            stringResource(R.string.touch_tile_badge_none, accessibleLabel)
        } else {
            stringResource(R.string.touch_tile_badge_count, accessibleLabel, badge.count)
        }
        BadgeValue.Unknown -> stringResource(R.string.touch_tile_badge_unknown, accessibleLabel)
        null -> accessibleLabel
    }
    Box(
        modifier = modifier
            .heightIn(min = 160.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(tile.kind.tileBackgroundColor())
            .clickable(
                role = Role.Button,
                onClick = {
                    if (tile.kind == HomeTileKind.SOS) {
                        haptics.confirmHeavy()
                    } else {
                        haptics.softkey()
                    }
                    onClick()
                },
            )
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        if (tile.kind == HomeTileKind.SOS) {
            Text(
                text = displayLabel,
                color = ClaroColors.Red,
                fontSize = 54.sp,
                lineHeight = 58.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-2).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp),
            )
        } else {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                if (tile.kind == HomeTileKind.INSTALLED_APP) {
                    ClaroAppIcon(
                        packageName = tile.packageName,
                        size = 60.dp,
                        fallbackTint = tile.kind.tileIconTint(),
                    )
                } else {
                    ClaroIcon(
                        kind = tile.kind.iconKind(),
                        tint = tile.kind.tileIconTint(),
                        size = 68.dp,
                    )
                }
                Text(
                    text = displayLabel,
                    color = ClaroColors.Ink,
                    fontSize = 22.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        tile.badge?.let { badge ->
            BadgeBubble(
                badge = badge,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )
        }
    }
}

@Composable
private fun BadgeBubble(badge: BadgeValue, modifier: Modifier = Modifier) {
    val text = when (badge) {
        is BadgeValue.Known -> badge.count.takeIf { it > 0 }?.toString() ?: return
        BadgeValue.Unknown -> "?"
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 32.dp, minHeight = 32.dp)
            .background(ClaroColors.Red, CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 18.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FavoritePerson.favoriteActionLabel(): String = when (state) {
    FavoriteState.READY -> stringResource(R.string.touch_favorite_action_call, displayName)
    FavoriteState.UNASSIGNED -> stringResource(R.string.touch_add_person)
    FavoriteState.CONTACT_MISSING,
    FavoriteState.PHOTO_UNAVAILABLE,
    -> stringResource(R.string.touch_favorite_action_repair, displayName)
}

@Composable
private fun FavoritePerson.favoriteDescription(): String = when (state) {
    FavoriteState.READY -> stringResource(R.string.touch_favorite_desc_ready, displayName)
    FavoriteState.UNASSIGNED -> stringResource(R.string.touch_favorite_desc_unassigned)
    FavoriteState.CONTACT_MISSING ->
        stringResource(R.string.touch_favorite_desc_contact_missing, displayName)
    FavoriteState.PHOTO_UNAVAILABLE ->
        stringResource(R.string.touch_favorite_desc_photo_unavailable, displayName)
}

@Composable
private fun CellularStatus.unavailableLabel(): String? = when (availability) {
    CellularAvailability.AVAILABLE -> null
    CellularAvailability.NO_SIM -> stringResource(R.string.touch_signal_no_sim)
    CellularAvailability.NO_SERVICE -> stringResource(R.string.touch_signal_no_signal)
    CellularAvailability.AIRPLANE_MODE -> stringResource(R.string.touch_signal_airplane_mode)
    CellularAvailability.UNKNOWN -> stringResource(R.string.touch_signal_unknown)
}

private fun HomeTileKind.tileBackgroundColor(): Color = when (this) {
    HomeTileKind.PHONE -> ClaroColors.PaleGreen
    HomeTileKind.MESSAGES -> ClaroColors.PaleBlue
    HomeTileKind.CAMERA -> ClaroColors.PaleOrange
    HomeTileKind.GALLERY -> ClaroColors.PaleViolet
    HomeTileKind.SOS -> ClaroColors.PaleRed
    HomeTileKind.APPS -> ClaroColors.AppsPale
    HomeTileKind.INSTALLED_APP -> Color(0xFFF2F4F8)
}

private fun HomeTileKind.tileIconTint(): Color = when (this) {
    HomeTileKind.PHONE -> ClaroColors.Green
    HomeTileKind.MESSAGES -> ClaroColors.Blue
    HomeTileKind.CAMERA -> ClaroColors.Orange
    HomeTileKind.GALLERY -> ClaroColors.Violet
    HomeTileKind.SOS -> ClaroColors.Red
    HomeTileKind.APPS -> ClaroColors.AppsInk
    HomeTileKind.INSTALLED_APP -> ClaroColors.Blue
}

private fun HomeTileKind.iconKind(): ClaroIconKind = when (this) {
    HomeTileKind.PHONE -> ClaroIconKind.PHONE
    HomeTileKind.MESSAGES -> ClaroIconKind.MESSAGES
    HomeTileKind.CAMERA -> ClaroIconKind.CAMERA
    HomeTileKind.GALLERY -> ClaroIconKind.GALLERY
    HomeTileKind.SOS -> ClaroIconKind.SOS
    HomeTileKind.APPS -> ClaroIconKind.APPS
    HomeTileKind.INSTALLED_APP -> ClaroIconKind.APPS
}

@Composable
private fun HomeTileKind.localizedLabel(fallback: String): String = when (this) {
    HomeTileKind.PHONE -> stringResource(R.string.touch_tile_phone)
    HomeTileKind.MESSAGES -> stringResource(R.string.touch_tile_messages)
    HomeTileKind.CAMERA -> stringResource(R.string.touch_tile_camera)
    HomeTileKind.GALLERY -> stringResource(R.string.touch_tile_gallery)
    HomeTileKind.SOS -> stringResource(R.string.touch_tile_sos)
    HomeTileKind.APPS -> stringResource(R.string.touch_tile_apps)
    HomeTileKind.INSTALLED_APP -> fallback
}
