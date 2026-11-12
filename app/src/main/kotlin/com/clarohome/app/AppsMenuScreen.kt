package com.clarohome.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarohome.core.design.ClaroAppIcon
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
    useGrid: Boolean = false,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var unavailableAppLabel by remember { mutableStateOf<String?>(null) }

    val editing = allowCustomization
    val allowedApps = HomeCustomizationPolicy.allowedApps(apps)
    val visible = if (editing) {
        HomeCustomizationPolicy.orderedApps(apps).filter {
            query.isBlank() || it.label.contains(query.trim(), ignoreCase = true)
        }
    } else {
        allowedApps
    }

    BackHandler(onBack = onBack)

    ClaroScreenScaffold(
        title = if (editing) stringResource(R.string.app_apps_and_menu) else stringResource(R.string.app_apps),
        scrollable = false,
        dockInSoftkeys = true,
        dock = {
            if (editing) {
                ClaroPrimaryButton(
                    label = stringResource(R.string.app_done),
                    onClick = onBack,
                    containerColor = ClaroColors.Blue,
                    softkeyColor = ClaroColors.Blue,
                )
            }
        },
    ) {
        if (editing) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
            ) {
                item(key = "notice") {
                    ClaroInfoCard(
                        text = stringResource(R.string.app_apps_hiding_convenience_notice),
                        tone = ClaroBannerTone.INFO,
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
                if (visible.isEmpty()) {
                    item(key = "empty") {
                        ClaroInfoCard(
                            text = if (query.isBlank()) {
                                stringResource(R.string.app_no_eligible_apps)
                            } else {
                                stringResource(R.string.app_no_apps_match_search)
                            },
                            tone = ClaroBannerTone.INFO,
                        )
                    }
                }
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
        } else {
            unavailableAppLabel?.let { label ->
                ClaroInfoCard(
                    text = stringResource(R.string.app_unavailable_review_in_settings, label),
                    tone = ClaroBannerTone.ERROR,
                )
            }
            val launch = { app: InstalledApp ->
                if (app.isAvailable) {
                    unavailableAppLabel = null
                    onLaunch(app)
                } else {
                    unavailableAppLabel = app.label
                }
            }
            if (visible.isEmpty()) {
                AppsEmptyState()
            } else if (useGrid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
                    verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
                ) {
                    items(visible, key = { it.packageName }) { app ->
                        AppCard(app = app, onLaunch = { launch(app) })
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    items(visible, key = { it.packageName }) { app ->
                        SeniorAppRow(app = app, onLaunch = { launch(app) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCard(
    app: InstalledApp,
    onLaunch: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 136.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (app.isAvailable) ClaroColors.Surface else ClaroColors.ErrorBg)
            .border(1.dp, ClaroColors.Border, RoundedCornerShape(3.dp))
            .clickable(role = Role.Button, onClick = onLaunch)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        ClaroAppIcon(
            packageName = app.packageName,
            size = 64.dp,
            fallbackTint = if (app.isAvailable) ClaroColors.Blue else ClaroColors.Muted,
        )
        Text(
            text = app.label,
            color = if (app.isAvailable) ClaroColors.Ink else ClaroColors.Muted,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!app.isAvailable) {
            Text(
                text = stringResource(R.string.app_is_missing_or_disabled),
                color = ClaroColors.ErrorInk,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            )
        }
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
            leading = {
                ClaroAppIcon(packageName = app.packageName, size = 52.dp)
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
                ClaroAppIcon(
                    packageName = app.packageName,
                    size = 44.dp,
                    fallbackTint = ClaroColors.Muted,
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
                        text = stringResource(R.string.app_is_missing_or_disabled),
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
            ClaroAppIcon(
                packageName = app.packageName,
                size = 40.dp,
                fallbackTint = if (app.isAvailable) ClaroColors.Blue else ClaroColors.Muted,
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
                        text = stringResource(R.string.app_is_missing_or_disabled),
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
                text = stringResource(R.string.app_show_in_menu),
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

@Composable
private fun AppsEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ClaroColors.AppsPale)
            .border(1.dp, ClaroColors.Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ClaroIcon(kind = ClaroIconKind.APPS, tint = ClaroColors.Blue, size = 48.dp)
        Text(
            text = stringResource(R.string.app_apps_empty_title),
            color = ClaroColors.Ink,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.app_apps_empty_body),
            color = ClaroColors.Muted,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}
