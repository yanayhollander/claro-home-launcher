package com.clarohome.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarohome.core.design.ClaroAppIcon
import com.clarohome.core.design.ClaroBannerTone
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.claroFocusRing
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.core.design.ClaroIcon
import com.clarohome.core.design.ClaroIconKind
import com.clarohome.core.design.ClaroInfoCard
import com.clarohome.core.design.ClaroPrimaryButton
import com.clarohome.core.design.ClaroScreenScaffold
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind

internal const val MENU_KEY_CONTACTS = "contacts"
internal const val MENU_KEY_SETTINGS = "settings"

/**
 * Default Menu order is Contacts, built-in tiles, installed apps, then Settings. A saved order wins
 * for the keys it knows about; tiles it does not know (for example a newly allowed app) keep their
 * default relative position after the saved ones, and stale keys are dropped.
 */
internal fun orderedMenuKeys(tiles: List<HomeTile>, saved: List<String>): List<String> {
    val defaults = listOf(MENU_KEY_CONTACTS) +
        tiles.filter { it.isCore }.map { it.id } +
        tiles.filterNot { it.isCore }.map { it.id } +
        MENU_KEY_SETTINGS
    val known = defaults.toSet()
    val kept = saved.filter { it in known }.distinct()
    return kept + defaults.filterNot { it in kept }
}

@Composable
fun MenuOrderScreen(
    tiles: List<HomeTile>,
    savedOrder: List<String>,
    onOrderChanged: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val tilesByKey = tiles.associateBy { it.id }
    val keys = orderedMenuKeys(tiles, savedOrder)
    val contactsLabel = stringResource(R.string.app_contacts)
    val settingsLabel = stringResource(R.string.app_settings)

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in keys.indices) return
        onOrderChanged(keys.toMutableList().apply { add(target, removeAt(index)) })
    }

    BackHandler(onBack = onBack)

    ClaroScreenScaffold(
        title = stringResource(R.string.app_menu_order_title),
        scrollable = false,
        dockInSoftkeys = true,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.app_done),
                onClick = onBack,
                softkeyColor = ClaroColors.Blue,
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
        ) {
            item(key = "hint") {
                ClaroInfoCard(
                    text = stringResource(R.string.app_menu_order_hint),
                    tone = ClaroBannerTone.INFO,
                )
            }
            items(keys.size, key = { keys[it] }) { index ->
                val key = keys[index]
                val tile = tilesByKey[key]
                val label = when (key) {
                    MENU_KEY_CONTACTS -> contactsLabel
                    MENU_KEY_SETTINGS -> settingsLabel
                    else -> tile?.localizedLabel().orEmpty()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ClaroColors.RowSurface)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .heightIn(min = 60.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when {
                        key == MENU_KEY_CONTACTS -> ClaroIcon(kind = ClaroIconKind.CONTACT, tint = ClaroColors.Blue, size = 32.dp)
                        key == MENU_KEY_SETTINGS -> ClaroIcon(kind = ClaroIconKind.SETTINGS, tint = ClaroColors.Blue, size = 32.dp)
                        tile?.kind == HomeTileKind.INSTALLED_APP -> ClaroAppIcon(
                            packageName = tile.packageName,
                            size = 40.dp,
                            fallbackTint = tile.kind.toClaroIconTint(),
                        )
                        tile != null -> ClaroIcon(
                            kind = tile.kind.toClaroIconKind(),
                            tint = tile.kind.toClaroIconTint(),
                            size = 32.dp,
                        )
                    }
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        color = ClaroColors.Ink,
                        fontSize = 22.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                    )
                    MoveButton(
                        glyph = "▲",
                        description = stringResource(R.string.app_move_up, label),
                        enabled = index > 0,
                        onClick = { move(index, -1) },
                    )
                    MoveButton(
                        glyph = "▼",
                        description = stringResource(R.string.app_move_down, label),
                        enabled = index < keys.lastIndex,
                        onClick = { move(index, 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MoveButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(52.dp)
            .claroFocusRing()
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ClaroColors.Blue,
            contentColor = androidx.compose.ui.graphics.Color.White,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Text(text = glyph, fontSize = 22.sp)
    }
}
