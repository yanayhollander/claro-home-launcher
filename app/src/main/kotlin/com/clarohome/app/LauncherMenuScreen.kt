package com.clarohome.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroIcon
import com.clarohome.core.design.ClaroIconKind
import com.clarohome.core.design.ClaroRowButton
import com.clarohome.core.design.ClaroScreenScaffold
import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind

@Composable
fun LauncherMenuScreen(
    tiles: List<HomeTile>,
    onBack: () -> Unit,
    onTileSelected: (HomeTile) -> Unit,
    onContacts: () -> Unit,
    onSettings: () -> Unit,
    onUseTouchStyle: () -> Unit = {},
) {
    val orderedTiles = tiles.filter { it.isCore } + tiles.filterNot { it.isCore }

    BackHandler(onBack = onBack)

    ClaroScreenScaffold(
        title = "Menu",
        scrollable = false,
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            item(key = "menu-contacts") {
                ClaroRowButton(
                    title = "Contacts",
                    leading = {
                        ClaroIcon(
                            kind = ClaroIconKind.CONTACT,
                            tint = ClaroColors.Blue,
                            size = 32.dp,
                        )
                    },
                    onClick = onContacts,
                )
            }
            items(orderedTiles, key = { it.id }) { tile ->
                val (subtitle, unread) = tile.menuBadgePresentation()
                ClaroRowButton(
                    title = tile.label,
                    subtitle = subtitle,
                    unreadDot = unread,
                    leading = {
                        ClaroIcon(
                            kind = tile.kind.toClaroIconKind(),
                            tint = tile.kind.toClaroIconTint(),
                            size = 32.dp,
                        )
                    },
                    onClick = {
                        if (tile.label == "Settings") {
                            onUseTouchStyle()
                        } else {
                            onTileSelected(tile)
                        }
                    },
                )
            }
            item(key = "menu-settings") {
                ClaroRowButton(
                    title = "Settings",
                    leading = {
                        ClaroIcon(
                            kind = ClaroIconKind.SETTINGS,
                            tint = ClaroColors.Blue,
                            size = 32.dp,
                        )
                    },
                    onClick = onSettings,
                )
            }
        }
    }
}

private fun HomeTileKind.toClaroIconKind(): ClaroIconKind = when (this) {
    HomeTileKind.PHONE -> ClaroIconKind.PHONE
    HomeTileKind.MESSAGES -> ClaroIconKind.MESSAGES
    HomeTileKind.CAMERA -> ClaroIconKind.CAMERA
    HomeTileKind.GALLERY -> ClaroIconKind.GALLERY
    HomeTileKind.SOS -> ClaroIconKind.SOS
    HomeTileKind.APPS -> ClaroIconKind.APPS
    HomeTileKind.INSTALLED_APP -> ClaroIconKind.APPS
}

private fun HomeTileKind.toClaroIconTint(): Color = when (this) {
    HomeTileKind.PHONE -> ClaroColors.Green
    HomeTileKind.MESSAGES -> ClaroColors.Blue
    HomeTileKind.CAMERA -> ClaroColors.Orange
    HomeTileKind.GALLERY -> ClaroColors.Violet
    HomeTileKind.SOS -> ClaroColors.Red
    HomeTileKind.APPS -> ClaroColors.Blue
    HomeTileKind.INSTALLED_APP -> ClaroColors.Blue
}

private fun HomeTile.menuBadgePresentation(): Pair<String?, Boolean> = when (val current = badge) {
    is BadgeValue.Known -> if (current.count > 0) {
        "${current.count} new" to true
    } else {
        null to false
    }
    BadgeValue.Unknown -> "Access needed · count unknown" to false
    null -> null to false
}
