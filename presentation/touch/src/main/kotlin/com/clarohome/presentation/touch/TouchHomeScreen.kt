package com.clarohome.presentation.touch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.CellularAvailability
import com.clarohome.core.model.FavoritePerson
import com.clarohome.core.model.FavoriteState
import com.clarohome.core.model.HomeRoleState
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind
import com.clarohome.core.model.TileAccent
import com.clarohome.feature.launcher.home.HomeCustomizationPolicy

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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
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
                Button(
                    onClick = onReturnToCall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ClaroDimensions.PrimaryTarget)
                        .padding(horizontal = ClaroDimensions.TileGap),
                    colors = ButtonDefaults.buttonColors(containerColor = ClaroColors.Green),
                ) {
                    Text(
                        "Return to call · ${activeCall.displayName} · ${activeCall.stateLabel}",
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        item {
            FavoriteStrip(home.favorites, onFavoriteSelected)
        }
        items(home.tiles.chunked(2), key = { row -> row.joinToString { it.id } }) { row ->
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
        item {
            Text(
                "More apps continue below",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun TouchStatusHeader(
    home: HomeSnapshot,
    onUseClassicMode: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                home.dayAndDate,
                modifier = Modifier.semantics { heading() },
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(home.time, color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { home.battery.percentage / 100f },
                    modifier = Modifier
                        .width(92.dp)
                        .semantics {
                            contentDescription = buildString {
                                append("Battery ${home.battery.percentage} percent")
                                if (home.battery.isCharging) append(", charging")
                            }
                        },
                    color = ClaroColors.Green,
                    trackColor = Color.White,
                )
                Text(" ${home.battery.percentage}%", color = Color.White)
            }
            Text(
                home.cellular.accessibleLabel(),
                modifier = Modifier.semantics {
                    contentDescription = home.cellular.accessibleLabel()
                },
                color = Color.White,
            )
            Button(
                onClick = onUseClassicMode,
                modifier = Modifier.heightIn(min = ClaroDimensions.SecondaryTarget),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("Classic style")
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
            .padding(horizontal = 8.dp)
            .background(Color(0xFFFFF3CD), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            when (roleState) {
                HomeRoleState.AVAILABLE -> "Claro is not your Home app yet."
                HomeRoleState.UNAVAILABLE -> "This device cannot make Claro the Home app."
                HomeRoleState.UNKNOWN -> "Home app status is unavailable."
                HomeRoleState.GRANTED -> ""
            },
            color = ClaroColors.Charcoal,
        )
        Button(
            onClick = onRepairHomeRole,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ClaroDimensions.PrimaryTarget),
        ) {
            Text(if (roleState == HomeRoleState.AVAILABLE) "Choose Claro as Home" else "Open Home settings")
        }
    }
}

@Composable
private fun FavoriteStrip(
    favorites: List<FavoritePerson>,
    onFavoriteSelected: (FavoritePerson) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ClaroDimensions.TileGap),
        horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
    ) {
        favorites.take(4).forEach { person ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 88.dp)
                    .background(Color(0xFFF4F7FB))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = person.favoriteActionLabel(),
                    ) { onFavoriteSelected(person) }
                    .semantics {
                        contentDescription = person.favoriteDescription()
                        role = Role.Button
                    }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color(0xFFDCE8F3), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (person.state == FavoriteState.READY) "●" else "+",
                        color = ClaroColors.Charcoal,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Text(
                    person.displayName,
                    color = ClaroColors.Charcoal,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun HomeTileCard(
    tile: HomeTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = HomeCustomizationPolicy.badgeLabel(tile.label, tile.badge)
    Box(
        modifier = modifier
            .heightIn(min = 132.dp)
            .background(tile.accent.paleColor())
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                tile.kind.glyph(),
                color = tile.accent.strongColor(),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                tile.label,
                color = ClaroColors.Charcoal,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        tile.badge?.let { badge ->
            BadgeBubble(
                badge,
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
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
            .size(36.dp)
            .background(ClaroColors.Red, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

private fun FavoritePerson.favoriteActionLabel(): String =
    if (state == FavoriteState.READY) "Call $displayName" else "Repair $displayName favorite"

private fun FavoritePerson.favoriteDescription(): String = when (state) {
    FavoriteState.READY -> "$displayName, favorite person"
    FavoriteState.UNASSIGNED -> "Unassigned favorite. Ask your caregiver to add a person"
    FavoriteState.CONTACT_MISSING -> "$displayName, contact needs repair"
    FavoriteState.PHOTO_UNAVAILABLE -> "$displayName, photo unavailable"
}

private fun com.clarohome.core.model.CellularStatus.accessibleLabel(): String = when (availability) {
    CellularAvailability.AVAILABLE -> "Cellular signal ${signalLevel ?: 0} of 4"
    CellularAvailability.NO_SIM -> "No SIM"
    CellularAvailability.NO_SERVICE -> "No cellular service"
    CellularAvailability.AIRPLANE_MODE -> "Airplane mode"
    CellularAvailability.UNKNOWN -> "Cellular status unavailable"
}

private fun HomeTileKind.glyph(): String = when (this) {
    HomeTileKind.PHONE -> "☎"
    HomeTileKind.MESSAGES -> "✉"
    HomeTileKind.CAMERA -> "●"
    HomeTileKind.GALLERY -> "▣"
    HomeTileKind.SOS -> "SOS"
    HomeTileKind.APPS -> "••\n••"
    HomeTileKind.INSTALLED_APP -> "▦"
}

private fun TileAccent.strongColor(): Color = when (this) {
    TileAccent.GREEN -> ClaroColors.Green
    TileAccent.BLUE -> ClaroColors.Blue
    TileAccent.ORANGE -> ClaroColors.Orange
    TileAccent.RED -> ClaroColors.Red
    TileAccent.VIOLET -> ClaroColors.Violet
}

private fun TileAccent.paleColor(): Color = when (this) {
    TileAccent.GREEN -> Color(0xFFEAF7EF)
    TileAccent.BLUE -> Color(0xFFEAF1FC)
    TileAccent.ORANGE -> Color(0xFFFFF2E7)
    TileAccent.RED -> Color(0xFFFFECEF)
    TileAccent.VIOLET -> Color(0xFFF1ECFC)
}
