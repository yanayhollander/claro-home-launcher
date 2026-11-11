package com.clarohome.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.clarohome.core.model.InstalledApp
import com.clarohome.feature.launcher.home.HomeCustomizationPolicy

@Composable
fun AppsMenuScreen(
    apps: List<InstalledApp>,
    allowCustomization: Boolean,
    onBack: () -> Unit,
    onLaunch: (InstalledApp) -> Unit,
    onSetAllowed: (InstalledApp, Boolean) -> Unit,
    onSetOnHome: (InstalledApp, Boolean) -> Unit,
    onRequestCustomize: () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var customize by rememberSaveable(allowCustomization) { mutableStateOf(allowCustomization) }
    var onHomeOnly by rememberSaveable { mutableStateOf(false) }
    var unavailableNotice by remember { mutableStateOf<String?>(null) }

    val editing = allowCustomization && customize
    val allowedApps = HomeCustomizationPolicy.allowedApps(apps)
    val visible = if (editing) {
        HomeCustomizationPolicy.orderedApps(apps)
    } else {
        allowedApps.filter { !onHomeOnly || it.isOnHome }
    }.filter {
        query.isBlank() || it.label.contains(query.trim(), ignoreCase = true)
    }

    BackHandler(onBack = onBack)

    ClaroScreenScaffold(
        title = if (editing) "Home & apps" else "Apps",
        scrollable = false,
        dock = {
            if (editing) {
                ClaroPrimaryButton(
                    label = "Done",
                    onClick = {
                        customize = false
                        onBack()
                    },
                    containerColor = ClaroColors.Blue,
                )
            } else {
                ClaroSecondaryButton(
                    label = "Edit apps",
                    onClick = {
                        if (allowCustomization) {
                            customize = true
                        } else {
                            onRequestCustomize()
                        }
                    },
                )
            }
        },
    ) {
        if (editing) {
            ClaroInfoCard(
                text = "Hiding apps here is a menu convenience only. It does not lock the device or block Android settings.",
                tone = ClaroBannerTone.INFO,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search") },
                singleLine = true,
            )
            if (visible.isEmpty()) {
                ClaroInfoCard(
                    text = if (query.isBlank()) {
                        "No eligible apps found on this device."
                    } else {
                        "No apps match your search."
                    },
                    tone = ClaroBannerTone.INFO,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
                ) {
                    items(visible, key = { it.packageName }) { app ->
                        CaregiverAppRow(
                            app = app,
                            onSetAllowed = { allowed ->
                                onSetAllowed(app, allowed)
                                if (!allowed && app.isOnHome) {
                                    onSetOnHome(app, false)
                                }
                            },
                            onSetOnHome = { onHome -> onSetOnHome(app, onHome) },
                        )
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search") },
                singleLine = true,
            )
            AppsTabStrip(
                onHomeOnly = onHomeOnly,
                onSelectAllAllowed = { onHomeOnly = false },
                onSelectOnHome = { onHomeOnly = true },
            )
            unavailableNotice?.let { notice ->
                ClaroInfoCard(
                    text = notice,
                    tone = ClaroBannerTone.ERROR,
                )
            }
            if (allowedApps.isEmpty()) {
                ClaroInfoCard(
                    text = "No allowed apps yet.",
                    tone = ClaroBannerTone.INFO,
                )
            } else if (visible.isEmpty()) {
                ClaroInfoCard(
                    text = if (query.isNotBlank()) {
                        "No apps match your search."
                    } else {
                        "No allowed apps on Home yet."
                    },
                    tone = ClaroBannerTone.INFO,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    items(visible, key = { it.packageName }) { app ->
                        SeniorAppRow(
                            app = app,
                            onLaunch = {
                                if (app.isAvailable) {
                                    unavailableNotice = null
                                    onLaunch(app)
                                } else {
                                    unavailableNotice =
                                        "${app.label} is missing or disabled. Ask a caregiver to review Edit apps."
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsTabStrip(
    onHomeOnly: Boolean,
    onSelectAllAllowed: () -> Unit,
    onSelectOnHome: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClaroColors.Charcoal),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AppsTabButton(
            label = "All allowed",
            selected = !onHomeOnly,
            onClick = onSelectAllAllowed,
            modifier = Modifier.weight(1f),
        )
        AppsTabButton(
            label = "On home",
            selected = onHomeOnly,
            onClick = onSelectOnHome,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AppsTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) ClaroColors.Blue else Color(0xFFE6EAF0),
            contentColor = if (selected) Color.White else Color(0xFF26364E),
        ),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 20.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SeniorAppRow(
    app: InstalledApp,
    onLaunch: () -> Unit,
) {
    if (app.isAvailable) {
        ClaroRowButton(
            title = app.label,
            subtitle = if (app.isOnHome) "On Home · opens outside Claro" else "Opens outside Claro",
            leading = {
                ClaroIcon(
                    kind = ClaroIconKind.APPS,
                    tint = ClaroColors.Blue,
                    size = 32.dp,
                )
            },
            onClick = onLaunch,
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ClaroColors.ErrorBg)
                .clickable(onClick = onLaunch),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ClaroIcon(
                    kind = ClaroIconKind.APPS,
                    tint = ClaroColors.Muted,
                    size = 32.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.label,
                        color = ClaroColors.Muted,
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "App is missing or disabled",
                        color = ClaroColors.ErrorInk,
                        fontSize = 17.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            HorizontalDivider(thickness = 2.dp, color = ClaroColors.Charcoal)
        }
    }
}

@Composable
private fun CaregiverAppRow(
    app: InstalledApp,
    onSetAllowed: (Boolean) -> Unit,
    onSetOnHome: (Boolean) -> Unit,
) {
    val canAddToHome = HomeCustomizationPolicy.canAddToHome(app)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClaroColors.Surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable(
                    enabled = app.isAvailable,
                    role = Role.Checkbox,
                ) { onSetAllowed(!app.isAllowed) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Checkbox(
                checked = app.isAllowed,
                onCheckedChange = null,
                enabled = app.isAvailable,
            )
            ClaroIcon(
                kind = ClaroIconKind.APPS,
                tint = if (app.isAvailable) ClaroColors.Blue else ClaroColors.Muted,
                size = 28.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    color = if (app.isAvailable) ClaroColors.Ink else ClaroColors.Muted,
                    fontSize = 22.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (!app.isAvailable) {
                    Text(
                        text = "App is missing or disabled",
                        color = ClaroColors.Red,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Show on Home",
                color = if (canAddToHome) ClaroColors.Ink else ClaroColors.Muted,
                fontSize = 19.sp,
                lineHeight = 24.sp,
            )
            Switch(
                checked = app.isOnHome,
                onCheckedChange = onSetOnHome,
                enabled = canAddToHome,
            )
        }
        HorizontalDivider(thickness = 2.dp, color = ClaroColors.Charcoal)
    }
}
