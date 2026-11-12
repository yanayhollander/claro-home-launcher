package com.clarohome.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.clarohome.core.data.homeSlotTile
import com.clarohome.core.model.HOME_SLOT_COUNT
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind
import com.clarohome.core.model.InstalledApp
import com.clarohome.feature.communication.ClaroContact
import com.clarohome.feature.communication.ContactNumber
import com.clarohome.feature.communication.PhoneAssignment

private const val SLOT_COLUMNS = 3

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
    onSetKey: (String, ClaroContact, ContactNumber) -> Unit,
    onClearKey: (String) -> Unit,
    onBack: () -> Unit,
) {
    var target by rememberSaveable { mutableStateOf("home:0") }
    var query by rememberSaveable { mutableStateOf("") }
    val (targetKind, targetId) = target.split(':')
    val isHomeTarget = targetKind == "home"
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
    val shortcuts = shortcutKinds.map { kind -> kind to stringResource(kind.shortcutLabelRes()) }
        .filter { (_, label) -> label.contains(trimmed, ignoreCase = true) }

    BackHandler(onBack = onBack)

    ClaroScreenScaffold(
        title = stringResource(R.string.app_home_tiles_and_speed_dial),
        scrollable = false,
        dockInSoftkeys = true,
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
            items(HOME_SLOT_COUNT / SLOT_COLUMNS, key = { "slot-row-$it" }) { rowIndex ->
                Row(horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap)) {
                    for (column in 0 until SLOT_COLUMNS) {
                        val index = rowIndex * SLOT_COLUMNS + column
                        SlotPickerCell(
                            slot = index,
                            tile = homeSlots.getOrNull(index),
                            selected = target == "home:$index",
                            modifier = Modifier.weight(1f),
                            onClick = { target = "home:$index" },
                        )
                    }
                }
            }
            item(key = "keys") {
                Row(horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap)) {
                    listOf("1", "2").forEach { slot ->
                        val assignment = assignments.firstOrNull { it.slot == slot }
                        val selected = target == "key:$slot"
                        val assignedName = assignment?.displayNameSnapshot
                            ?: stringResource(R.string.app_not_set)
                        OutlinedButton(
                            onClick = { target = "key:$slot" },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = ClaroDimensions.PrimaryTarget),
                            shape = RoundedCornerShape(3.dp),
                            border = BorderStroke(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) ClaroColors.Blue else ClaroColors.Border,
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    if (selected) ClaroColors.PaleBlue else ClaroColors.Surface,
                                contentColor = ClaroColors.Ink,
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.app_speed_dial_hold_slot,
                                    slot,
                                    assignedName,
                                ),
                                fontSize = 18.sp,
                                lineHeight = 22.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            item(key = "notice") {
                Text(
                    text = stringResource(R.string.app_speed_dial_immediate_notice),
                    color = ClaroColors.Muted,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item(key = "clear") {
                ClaroSecondaryButton(
                    label = stringResource(R.string.app_clear_selected_slot),
                    onClick = {
                        if (isHomeTarget) onClearSlot(targetId.toInt()) else onClearKey(targetId)
                    },
                )
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
                        leading = { ClaroAvatar(name = contact.displayName, size = 44.dp) },
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
private fun SlotPickerCell(
    slot: Int,
    tile: HomeTile?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 92.dp),
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) ClaroColors.Blue else ClaroColors.Border,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) ClaroColors.PaleBlue else ClaroColors.Surface,
            contentColor = ClaroColors.Ink,
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (tile?.kind) {
                null -> ClaroAvatar(name = null, size = 40.dp, isUnassigned = true)
                HomeTileKind.INSTALLED_APP -> ClaroAppIcon(packageName = tile.packageName, size = 40.dp)
                HomeTileKind.CONTACT -> ClaroAvatar(name = tile.label, size = 40.dp)
                else -> ClaroIcon(
                    kind = tile.kind.settingsIconKind(),
                    tint = ClaroColors.Blue,
                    size = 36.dp,
                )
            }
            Text(
                text = when (tile?.kind) {
                    null -> stringResource(R.string.app_home_slot, slot + 1)
                    HomeTileKind.CONTACT, HomeTileKind.INSTALLED_APP -> tile.label
                    else -> stringResource(tile.kind.shortcutLabelRes())
                },
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun HomeTileKind.shortcutLabelRes(): Int = when (this) {
    HomeTileKind.PHONE -> R.string.app_phone
    HomeTileKind.MESSAGES -> R.string.app_messages
    HomeTileKind.CAMERA -> R.string.app_camera
    HomeTileKind.GALLERY -> R.string.app_gallery
    HomeTileKind.SOS -> R.string.app_sos
    HomeTileKind.APPS -> R.string.app_apps
    HomeTileKind.ALARM -> R.string.app_alarm_clock
    HomeTileKind.CONTACT, HomeTileKind.INSTALLED_APP -> error("Not a built-in shortcut: $this")
}

private fun HomeTileKind.settingsIconKind(): ClaroIconKind = when (this) {
    HomeTileKind.PHONE -> ClaroIconKind.PHONE
    HomeTileKind.MESSAGES -> ClaroIconKind.MESSAGES
    HomeTileKind.CAMERA -> ClaroIconKind.CAMERA
    HomeTileKind.GALLERY -> ClaroIconKind.GALLERY
    HomeTileKind.SOS -> ClaroIconKind.SOS
    HomeTileKind.APPS, HomeTileKind.INSTALLED_APP -> ClaroIconKind.APPS
    HomeTileKind.CONTACT -> ClaroIconKind.CONTACT
    HomeTileKind.ALARM -> ClaroIconKind.CLOCK
}
