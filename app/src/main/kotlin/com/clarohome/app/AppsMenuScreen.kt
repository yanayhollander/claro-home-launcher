package com.clarohome.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.core.model.InstalledApp
import com.clarohome.feature.launcher.home.HomeCustomizationPolicy

@Composable
fun AppsMenuScreen(
    apps: List<InstalledApp>,
    onBack: () -> Unit,
    onLaunch: (InstalledApp) -> Unit,
    onSetAllowed: (InstalledApp, Boolean) -> Unit,
    onSetOnHome: (InstalledApp, Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var customize by rememberSaveable { mutableStateOf(false) }
    val visible = if (customize) {
        HomeCustomizationPolicy.orderedApps(apps)
    } else {
        HomeCustomizationPolicy.allowedApps(apps)
    }.filter {
        query.isBlank() || it.label.contains(query, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClaroColors.Charcoal)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (customize) "Home apps" else "Apps",
            modifier = Modifier.semantics { heading() },
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            if (customize) {
                "Choose which installed apps are allowed and shown on Home."
            } else {
                "Favorites and all allowed apps. Other apps keep their own controls."
            },
            color = Color.White,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search apps") },
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = ClaroDimensions.PrimaryTarget),
            ) {
                Text("Back")
            }
            Button(
                onClick = { customize = !customize },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = ClaroDimensions.PrimaryTarget),
            ) {
                Text(if (customize) "Done" else "Customize")
            }
        }
        if (visible.isEmpty()) {
            Text(
                if (query.isBlank()) "No allowed apps yet." else "No apps match your search.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        customize = customize,
                        onLaunch = { onLaunch(app) },
                        onSetAllowed = { onSetAllowed(app, it) },
                        onSetOnHome = { onSetOnHome(app, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    customize: Boolean,
    onLaunch: () -> Unit,
    onSetAllowed: (Boolean) -> Unit,
    onSetOnHome: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(app.label, color = ClaroColors.Charcoal, style = MaterialTheme.typography.bodyLarge)
        if (!app.isAvailable) {
            Text("App is missing or disabled", color = ClaroColors.Red)
        }
        if (customize) {
            Button(
                onClick = { onSetAllowed(!app.isAllowed) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ClaroDimensions.PrimaryTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (app.isAllowed) ClaroColors.Red else ClaroColors.Green,
                ),
                enabled = app.isAvailable,
            ) {
                Text(if (app.isAllowed) "Remove from allowed apps" else "Allow this app")
            }
            Button(
                onClick = { onSetOnHome(!app.isOnHome) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ClaroDimensions.PrimaryTarget),
                enabled = HomeCustomizationPolicy.canAddToHome(app),
            ) {
                Text(if (app.isOnHome) "Remove from Home" else "Add to Home")
            }
        } else {
            Button(
                onClick = onLaunch,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ClaroDimensions.PrimaryTarget),
                enabled = app.isAvailable,
            ) {
                Text("Open ${app.label}")
            }
        }
    }
}
