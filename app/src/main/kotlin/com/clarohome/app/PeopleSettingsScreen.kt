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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarohome.core.design.ClaroAvatar
import com.clarohome.core.design.ClaroBannerTone
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.core.design.ClaroIconKind
import com.clarohome.core.design.ClaroInfoCard
import com.clarohome.core.design.ClaroPrimaryButton
import com.clarohome.core.design.ClaroRowButton
import com.clarohome.core.design.ClaroScreenScaffold
import com.clarohome.core.design.ClaroSecondaryButton
import com.clarohome.core.model.FavoritePerson
import com.clarohome.core.model.FavoriteState
import com.clarohome.feature.communication.ClaroContact
import com.clarohome.feature.communication.ContactNumber
import com.clarohome.feature.communication.PhoneAssignment

@Composable
fun PeopleSettingsScreen(
    favorites: List<FavoritePerson>,
    contacts: List<ClaroContact>,
    assignments: List<PhoneAssignment>,
    onLoadContacts: () -> Unit,
    onSetFavorite: (Int, ClaroContact, ContactNumber) -> Unit,
    onClearFavorite: (Int) -> Unit,
    onSetKey: (String, ClaroContact, ContactNumber) -> Unit,
    onClearKey: (String) -> Unit,
    onBack: () -> Unit,
) {
    var target by rememberSaveable { mutableStateOf("home:0") }
    var query by rememberSaveable { mutableStateOf("") }

    val choices = contacts.flatMap { contact ->
        contact.numbers.map { number -> contact to number }
    }.filter { (contact, number) ->
        val trimmed = query.trim()
        trimmed.isEmpty() ||
            contact.displayName.contains(trimmed, ignoreCase = true) ||
            number.label.contains(trimmed, ignoreCase = true) ||
            number.value.contains(trimmed, ignoreCase = true)
    }

    BackHandler(onBack = onBack)

    ClaroScreenScaffold(
        title = "People & speed dial",
        scrollable = false,
        dock = {
            ClaroPrimaryButton(
                label = "Done",
                onClick = onBack,
                containerColor = ClaroColors.Blue,
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
        ) {
            for (index in 0 until 4) {
                val favorite = favorites.getOrNull(index)
                val unassigned = favorite == null ||
                    favorite.state == FavoriteState.UNASSIGNED ||
                    favorite.phoneNumber.isNullOrBlank()
                val selected = target == "home:$index"
                OutlinedButton(
                    onClick = { target = "home:$index" },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 84.dp),
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
                        ClaroAvatar(
                            name = favorite?.displayName,
                            size = 42.dp,
                            isUnassigned = unassigned,
                        )
                        Text(
                            text = if (unassigned) "Slot ${index + 1}" else favorite?.displayName.orEmpty(),
                            fontSize = 15.sp,
                            lineHeight = 18.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
        ) {
            listOf("1", "2").forEach { slot ->
                val assignment = assignments.firstOrNull { it.slot == slot }
                val selected = target == "key:$slot"
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
                        containerColor = if (selected) ClaroColors.PaleBlue else ClaroColors.Surface,
                        contentColor = ClaroColors.Ink,
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Hold $slot · ${assignment?.displayNameSnapshot ?: "Not set"}",
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Text(
            text = "Holding 1 or 2 calls immediately, without confirmation.",
            color = ClaroColors.Muted,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        ClaroSecondaryButton(
            label = "Clear selected slot",
            onClick = {
                val (kind, slot) = target.split(':')
                if (kind == "home") onClearFavorite(slot.toInt()) else onClearKey(slot)
            },
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search") },
            singleLine = true,
        )

        if (contacts.isEmpty()) {
            ClaroInfoCard(
                text = "No contacts are available. Allow Contacts access or add a contact in Android.",
                tone = ClaroBannerTone.INFO,
            )
            ClaroSecondaryButton(
                label = "Load contacts",
                onClick = onLoadContacts,
                icon = ClaroIconKind.CONTACT,
            )
        } else if (choices.isEmpty()) {
            ClaroInfoCard(
                text = "No contacts match your search.",
                tone = ClaroBannerTone.INFO,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
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
                            )
                        },
                        onClick = {
                            val (kind, slot) = target.split(':')
                            if (kind == "home") {
                                onSetFavorite(slot.toInt(), contact, number)
                            } else {
                                onSetKey(slot, contact, number)
                            }
                        },
                    )
                }
            }
        }
    }
}
