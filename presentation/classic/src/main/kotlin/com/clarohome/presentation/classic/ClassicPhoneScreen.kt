package com.clarohome.presentation.classic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.clarohome.core.model.HomeRoleState
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind
import com.clarohome.feature.launcher.home.HomeCustomizationPolicy

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
) {
    val phoneTile = home.tiles.firstOrNull { it.kind == HomeTileKind.PHONE }
    val messagesTile = home.tiles.firstOrNull { it.kind == HomeTileKind.MESSAGES }
    val sosTile = home.tiles.firstOrNull { it.kind == HomeTileKind.SOS }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClaroColors.Charcoal)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFFF7F9FC), RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClassicStatusBar(home)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        home.time,
                        modifier = Modifier.semantics { heading() },
                        color = ClaroColors.Charcoal,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(home.dayAndDate, color = ClaroColors.Charcoal)
                }
                phoneTile?.let {
                    ShortcutButton(it, Modifier.weight(0.7f)) { onTileSelected(it) }
                }
                messagesTile?.let {
                    ShortcutButton(it, Modifier.weight(0.7f)) { onTileSelected(it) }
                }
            }
            if (home.homeRole != HomeRoleState.GRANTED) {
                RoleRepairRow(home.homeRole, onRepairHomeRole)
            }
            home.activeCall?.let { activeCall ->
                Button(
                    onClick = onReturnToCall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ClaroDimensions.PrimaryTarget),
                    colors = ButtonDefaults.buttonColors(containerColor = ClaroColors.Green),
                ) {
                    Text("Return to call · ${activeCall.displayName} · ${activeCall.stateLabel}")
                }
            }
            FavoriteRow(home.favorites, onFavoriteSelected)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                messagesTile?.let { tile ->
                    LargeClassicAction(
                        label = "✉ Messages",
                        color = Color(0xFFF1F4F8),
                        textColor = ClaroColors.Charcoal,
                        modifier = Modifier.weight(1f),
                        onClick = { onTileSelected(tile) },
                    )
                }
                sosTile?.let { tile ->
                    LargeClassicAction(
                        label = "⚠ SOS",
                        color = ClaroColors.Red,
                        textColor = Color.White,
                        modifier = Modifier.weight(1f),
                        onClick = { onTileSelected(tile) },
                    )
                }
            }
        }
        IntegratedControlPad(
            onCall = { phoneTile?.let(onTileSelected) },
            onHome = {},
            onMenu = onOpenMenu,
        )
        NumericKeypad()
    }
}

@Composable
private fun ClassicStatusBar(home: HomeSnapshot) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Claro", color = ClaroColors.Charcoal, modifier = Modifier.weight(1f))
        Text(home.cellular.accessibleLabel(), color = ClaroColors.Charcoal)
        LinearProgressIndicator(
            progress = { home.battery.percentage / 100f },
            modifier = Modifier
                .weight(0.6f)
                .semantics {
                    contentDescription = "Battery ${home.battery.percentage} percent"
                },
            color = ClaroColors.Green,
            trackColor = Color(0xFFCBD5E1),
        )
        Text("${home.battery.percentage}%", color = ClaroColors.Charcoal)
    }
}

@Composable
private fun ShortcutButton(
    tile: HomeTile,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = ClaroDimensions.PrimaryTarget)
            .background(Color(0xFFEAF1FC))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = HomeCustomizationPolicy.badgeLabel(tile.label, tile.badge)
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (tile.kind == HomeTileKind.PHONE) "☎" else "✉",
            color = ClaroColors.Blue,
            style = MaterialTheme.typography.titleLarge,
        )
        tile.badge?.let { badge ->
            ClassicBadge(badge, Modifier.align(Alignment.TopEnd))
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
            .size(34.dp)
            .background(ClaroColors.Red, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RoleRepairRow(roleState: HomeRoleState, onRepairHomeRole: () -> Unit) {
    Button(
        onClick = onRepairHomeRole,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ClaroDimensions.PrimaryTarget),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3B61F)),
    ) {
        Text(
            when (roleState) {
                HomeRoleState.AVAILABLE -> "Choose Claro as Home"
                HomeRoleState.UNAVAILABLE -> "Home role unavailable · Open settings"
                HomeRoleState.UNKNOWN -> "Home status unknown · Repair"
                HomeRoleState.GRANTED -> ""
            },
            color = ClaroColors.Charcoal,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FavoriteRow(
    favorites: List<FavoritePerson>,
    onFavoriteSelected: (FavoritePerson) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        favorites.take(4).forEach { person ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 82.dp)
                    .background(Color(0xFFF1F4F8))
                    .clickable(role = Role.Button) { onFavoriteSelected(person) }
                    .semantics {
                        contentDescription = "${person.displayName}, favorite person"
                        role = Role.Button
                    }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("●", color = ClaroColors.Blue, style = MaterialTheme.typography.titleLarge)
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
private fun LargeClassicAction(
    label: String,
    color: Color,
    textColor: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ClaroDimensions.PrimaryTarget),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = textColor),
        shape = RoundedCornerShape(2.dp),
    ) {
        Text(label, textAlign = TextAlign.Center)
    }
}

@Composable
private fun IntegratedControlPad(
    onCall: () -> Unit,
    onHome: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(0.8f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DeckButton("Back", Color(0xFF435066), Modifier.fillMaxWidth()) {}
            DeckButton("☎ Call", ClaroColors.Green, Modifier.fillMaxWidth(), onCall)
        }
        Column(
            modifier = Modifier.weight(1.2f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            DeckButton("↑", Color(0xFF435066), Modifier.fillMaxWidth()) {}
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                DeckButton("←", Color(0xFF435066), Modifier.weight(1f)) {}
                DeckButton("OK", Color(0xFF64748B), Modifier.weight(1f)) {}
                DeckButton("→", Color(0xFF435066), Modifier.weight(1f)) {}
            }
            DeckButton("↓", Color(0xFF435066), Modifier.fillMaxWidth()) {}
        }
        Column(
            modifier = Modifier.weight(0.8f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DeckButton("Menu", Color(0xFF435066), Modifier.fillMaxWidth(), onMenu)
            DeckButton("⌂ Home", ClaroColors.Red, Modifier.fillMaxWidth(), onHome)
        }
    }
}

@Composable
private fun DeckButton(
    label: String,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ClaroDimensions.PrimaryTarget),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(label, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NumericKeypad() {
    val keys = listOf(
        listOf("1\nMira", "2\nDaniel", "3\nDEF"),
        listOf("4\nGHI", "5\nJKL", "6\nMNO"),
        listOf("7\nPQRS", "8\nTUV", "9\nWXYZ"),
        listOf("*\n+", "0\nspace", "#\nabc / 123"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEach { key ->
                    Button(
                        onClick = {},
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = ClaroDimensions.PrimaryTarget),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF344054)),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            key,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private fun com.clarohome.core.model.CellularStatus.accessibleLabel(): String = when (availability) {
    CellularAvailability.AVAILABLE -> "Signal ${signalLevel ?: 0} of 4"
    CellularAvailability.NO_SIM -> "No SIM"
    CellularAvailability.NO_SERVICE -> "No service"
    CellularAvailability.AIRPLANE_MODE -> "Airplane mode"
    CellularAvailability.UNKNOWN -> "Signal unknown"
}
