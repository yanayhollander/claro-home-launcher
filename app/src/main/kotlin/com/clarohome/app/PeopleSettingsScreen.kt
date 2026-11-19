package com.clarohome.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarohome.core.design.ClaroAppIcon
import com.clarohome.core.design.ClaroAvatar
import com.clarohome.core.design.ClaroBannerTone
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.core.design.ClaroIcon
import com.clarohome.core.design.ClaroIconKind
import com.clarohome.core.design.ClaroInfoCard
import com.clarohome.core.design.ClaroPrimaryButton
import com.clarohome.core.design.ClaroRowButton
import com.clarohome.core.design.ClaroScreenScaffold
import com.clarohome.core.design.ClaroSecondaryButton
import com.clarohome.core.design.ClaroSoftkey
import com.clarohome.core.data.homeSlotTile
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind
import com.clarohome.core.model.InstalledApp
import com.clarohome.feature.communication.ClaroContact
import com.clarohome.feature.communication.ContactNumber
import com.clarohome.feature.communication.PhoneAssignment

private val shortcutKinds = listOf(
    HomeTileKind.PHONE,
    HomeTileKind.MESSAGES,
    HomeTileKind.CAMERA,
    HomeTileKind.GALLERY,
    HomeTileKind.SOS,
    HomeTileKind.APPS,
    HomeTileKind.ALARM,
)

@Composable
fun PeopleSettingsScreen(
    homeSlots: List<HomeTile?>,
    installedApps: List<InstalledApp>,
    contacts: List<ClaroContact>,
    assignments: List<PhoneAssignment>,
    onLoadContacts: () -> Unit,
    onSetSlot: (Int, HomeTile) -> Unit,
    onClearSlot: (Int) -> Unit,
    onMoveTile: (slot: Int, moveUp: Boolean) -> Unit,
    onSetKey: (String, ClaroContact, ContactNumber) -> Unit,
    onClearKey: (String) -> Unit,
    onBack: () -> Unit,
    message: String? = null,
    onChooseWidget: ((Int) -> Unit)? = null,
) {
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    val picking = target
    if (picking == null) {
        HomeTilesEditor(
            homeSlots = homeSlots,
            onPick = { target = it },
            onMoveTile = onMoveTile,
            onBack = onBack,
            message = message,
        )
    } else {
        PeoplePickerScreen(
            target = picking,
            homeSlots = homeSlots,
            assignments = assignments,
            installedApps = installedApps,
            contacts = contacts,
            onLoadContacts = onLoadContacts,
            onSetSlot = { slot, tile ->
                onSetSlot(slot, tile)
                target = null
            },
            onClearSlot = { slot ->
                onClearSlot(slot)
                target = null
            },
            onSetKey = { key, contact, number ->
                onSetKey(key, contact, number)
                target = null
            },
            onClearKey = { key ->
                onClearKey(key)
                target = null
            },
            onCancel = { target = null },
            onChooseWidget = onChooseWidget?.let { choose ->
                { slot ->
                    target = null
                    choose(slot)
                }
            },
        )
    }
}

@Composable
private fun HomeTilesEditor(
    homeSlots: List<HomeTile?>,
    onPick: (String) -> Unit,
    onMoveTile: (slot: Int, moveUp: Boolean) -> Unit,
    onBack: () -> Unit,
    message: String?,
) {
    BackHandler(onBack = onBack)
    val tiles = homeSlots.mapIndexedNotNull { slot, tile -> tile?.let { slot to it } }
    val nextSlot = (tiles.lastOrNull()?.first ?: -1) + 1

    ClaroScreenScaffold(
        title = stringResource(R.string.app_home_tiles_and_speed_dial),
        scrollable = false,
        dockInSoftkeys = true,
        bannerText = message,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.app_done),
                onClick = onBack,
                containerColor = ClaroColors.Blue,
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
        ) {
            item(key = "add-tile") {
                ClaroRowButton(
                    title = stringResource(R.string.app_add_home_tile),
                    subtitle = stringResource(R.string.app_add_home_tile_hint),
                    leading = { ClaroIcon(ClaroIconKind.APPS, ClaroColors.Blue) },
                    onClick = { onPick("home:$nextSlot") },
                )
            }
            item(key = "home-heading") {
                SectionTitle(stringResource(R.string.app_tiles_on_home))
            }
            if (tiles.isEmpty()) {
                item(key = "empty-home") {
                    ClaroInfoCard(stringResource(R.string.app_no_tiles_on_home))
                }
            }
            itemsIndexed(tiles, key = { _, (slot, _) -> "tile-$slot" }) { index, (slot, tile) ->
                val label = tile.editorLabel()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
                ) {
                    ClaroRowButton(
                        title = label,
                        subtitle = tile.phoneNumber,
                        leading = { TileEditorIcon(tile) },
                        trailing = { ClaroIcon(ClaroIconKind.EDIT, ClaroColors.Muted, size = 22.dp) },
                        onClick = { onPick("home:$slot") },
                        modifier = Modifier.weight(1f),
                    )
                    MoveButton(
                        glyph = "▲",
                        description = stringResource(R.string.app_move_up, label),
                        enabled = index > 0,
                        onClick = { onMoveTile(slot, true) },
                    )
                    MoveButton(
                        glyph = "▼",
                        description = stringResource(R.string.app_move_down, label),
                        enabled = index < tiles.lastIndex,
                        onClick = { onMoveTile(slot, false) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PeoplePickerScreen(
    target: String,
    homeSlots: List<HomeTile?>,
    assignments: List<PhoneAssignment>,
    installedApps: List<InstalledApp>,
    contacts: List<ClaroContact>,
    onLoadContacts: () -> Unit,
    onSetSlot: (Int, HomeTile) -> Unit,
    onClearSlot: (Int) -> Unit,
    onSetKey: (String, ClaroContact, ContactNumber) -> Unit,
    onClearKey: (String) -> Unit,
    onCancel: () -> Unit,
    onChooseWidget: ((Int) -> Unit)?,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val (targetKind, targetId) = target.split(':')
    val isHomeTarget = targetKind == "home"
    val currentTile = if (isHomeTarget) homeSlots.getOrNull(targetId.toInt()) else null
    val isAssigned = currentTile != null || (!isHomeTarget && assignments.any { it.slot == targetId })
    val trimmed = query.trim()

    val choices = contacts.flatMap { contact ->
        contact.numbers.map { number -> contact to number }
    }.filter { (contact, number) ->
        trimmed.isEmpty() ||
            contact.displayName.contains(trimmed, ignoreCase = true) ||
            number.label.contains(trimmed, ignoreCase = true) ||
            number.value.contains(trimmed, ignoreCase = true)
    }
    val appChoices = installedApps
        .filter { it.isAvailable && it.label.contains(trimmed, ignoreCase = true) }
        .sortedBy { it.label.lowercase() }
    val shortcuts = shortcutKinds.filter { kind ->
        homeSlots.none { it?.kind == kind } || currentTile?.kind == kind
    }.map { kind -> kind to stringResource(kind.shortcutLabelRes()) }
        .filter { (_, label) -> label.contains(trimmed, ignoreCase = true) }

    BackHandler(onBack = onCancel)

    ClaroScreenScaffold(
        title = when {
            currentTile != null -> stringResource(R.string.app_replace_home_tile)
            isHomeTarget -> stringResource(R.string.app_add_home_tile)
            else -> stringResource(R.string.app_choose_for_key, targetId)
        },
        scrollable = false,
        dockInSoftkeys = true,
        dock = {
            ClaroSecondaryButton(
                label = stringResource(R.string.app_cancel),
                onClick = onCancel,
                softkey = ClaroSoftkey.BACK,
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
        ) {
            if (isAssigned) {
                item(key = "clear") {
                    ClaroSecondaryButton(
                        label = stringResource(
                            if (isHomeTarget) R.string.app_remove_home_tile else R.string.app_clear_selected_slot,
                        ),
                        onClick = {
                            if (isHomeTarget) onClearSlot(targetId.toInt()) else onClearKey(targetId)
                        },
                    )
                }
            }
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.app_search)) },
                    singleLine = true,
                )
            }
            if (isHomeTarget && onChooseWidget != null) {
                item(key = "widgets") {
                    ClaroRowButton(
                        title = stringResource(R.string.app_choose_widget),
                        subtitle = stringResource(R.string.app_widget_picker_description),
                        leading = { ClaroIcon(kind = ClaroIconKind.APPS, tint = ClaroColors.Blue, size = 32.dp) },
                        onClick = { onChooseWidget(targetId.toInt()) },
                    )
                }
            }

            if (isHomeTarget && shortcuts.isNotEmpty()) {
                item(key = "h-shortcuts") { SectionTitle(stringResource(R.string.app_section_shortcuts)) }
                items(shortcuts, key = { (kind, _) -> "shortcut-${kind.name}" }) { (kind, label) ->
                    ClaroRowButton(
                        title = label,
                        leading = {
                            ClaroIcon(
                                kind = kind.settingsIconKind(),
                                tint = ClaroColors.Blue,
                                size = 32.dp,
                            )
                        },
                        onClick = {
                            onSetSlot(targetId.toInt(), homeSlotTile(targetId.toInt(), kind, label))
                        },
                    )
                }
            }

            if (isHomeTarget && appChoices.isNotEmpty()) {
                item(key = "h-apps") { SectionTitle(stringResource(R.string.app_section_apps)) }
                items(appChoices, key = { "app-${it.packageName}" }) { app ->
                    ClaroRowButton(
                        title = app.label,
                        leading = { ClaroAppIcon(packageName = app.packageName, size = 40.dp) },
                        onClick = {
                            onSetSlot(
                                targetId.toInt(),
                                homeSlotTile(
                                    slot = targetId.toInt(),
                                    kind = HomeTileKind.INSTALLED_APP,
                                    label = app.label,
                                    packageName = app.packageName,
                                ),
                            )
                        },
                    )
                }
            }

            item(key = "h-contacts") { SectionTitle(stringResource(R.string.app_section_contacts)) }
            if (contacts.isEmpty()) {
                item(key = "no-contacts") {
                    Column(verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap)) {
                        ClaroInfoCard(
                            text = stringResource(R.string.app_no_contacts_available),
                            tone = ClaroBannerTone.INFO,
                        )
                        ClaroSecondaryButton(
                            label = stringResource(R.string.app_load_contacts),
                            onClick = onLoadContacts,
                            icon = ClaroIconKind.CONTACT,
                        )
                    }
                }
            } else if (choices.isEmpty()) {
                item(key = "no-match") {
                    ClaroInfoCard(
                        text = stringResource(R.string.app_no_contacts_match_search),
                        tone = ClaroBannerTone.INFO,
                    )
                }
            } else {
                items(
                    items = choices,
                    key = { (contact, number) ->
                        "${contact.lookupKey}:${number.label}:${number.value}"
                    },
                ) { (contact, number) ->
                    ClaroRowButton(
                        title = contact.displayName,
                        subtitle = "\u2066${number.label} · ${number.value}\u2069",
                        leading = {
                            ClaroAvatar(
                                name = contact.displayName,
                                size = 44.dp,
                                photoUri = contact.photoUri,
                                contactLookupKey = contact.lookupKey,
                            )
                        },
                        onClick = {
                            if (isHomeTarget) {
                                onSetSlot(
                                    targetId.toInt(),
                                    homeSlotTile(
                                        slot = targetId.toInt(),
                                        kind = HomeTileKind.CONTACT,
                                        label = contact.displayName,
                                        phoneNumber = number.value,
                                        photoUri = contact.photoUri,
                                    ),
                                )
                            } else {
                                onSetKey(targetId, contact, number)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = ClaroColors.Muted,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun TileEditorIcon(tile: HomeTile) {
    when (tile.kind) {
        HomeTileKind.INSTALLED_APP -> ClaroAppIcon(packageName = tile.packageName, size = 40.dp)
        HomeTileKind.CONTACT -> ClaroAvatar(
            name = tile.label, size = 40.dp, photoUri = tile.photoUri, contactNumber = tile.phoneNumber,
        )
        else -> ClaroIcon(tile.kind.settingsIconKind(), ClaroColors.Blue, size = 36.dp)
    }
}

@Composable
private fun HomeTile.editorLabel(): String = when (kind) {
    HomeTileKind.CONTACT, HomeTileKind.INSTALLED_APP, HomeTileKind.WIDGET -> label
    else -> stringResource(kind.shortcutLabelRes())
}

private fun HomeTileKind.shortcutLabelRes(): Int = when (this) {
    HomeTileKind.PHONE -> R.string.app_phone
    HomeTileKind.MESSAGES -> R.string.app_messages
    HomeTileKind.CAMERA -> R.string.app_camera
    HomeTileKind.GALLERY -> R.string.app_gallery
    HomeTileKind.SOS -> R.string.app_sos
    HomeTileKind.APPS -> R.string.app_apps
    HomeTileKind.ALARM -> R.string.app_alarm_clock
    HomeTileKind.CONTACT, HomeTileKind.INSTALLED_APP, HomeTileKind.WIDGET -> error("Not a built-in shortcut: $this")
}

private fun HomeTileKind.settingsIconKind(): ClaroIconKind = when (this) {
    HomeTileKind.PHONE -> ClaroIconKind.PHONE
    HomeTileKind.MESSAGES -> ClaroIconKind.MESSAGES
    HomeTileKind.CAMERA -> ClaroIconKind.CAMERA
    HomeTileKind.GALLERY -> ClaroIconKind.GALLERY
    HomeTileKind.SOS -> ClaroIconKind.SOS
    HomeTileKind.APPS, HomeTileKind.INSTALLED_APP, HomeTileKind.WIDGET -> ClaroIconKind.APPS
    HomeTileKind.CONTACT -> ClaroIconKind.CONTACT
    HomeTileKind.ALARM -> ClaroIconKind.CLOCK
}
