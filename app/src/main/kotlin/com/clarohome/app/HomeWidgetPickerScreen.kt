package com.clarohome.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.clarohome.core.design.ClaroAppIcon
import com.clarohome.core.design.ClaroBannerTone
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.core.design.ClaroInfoCard
import com.clarohome.core.design.ClaroRowButton
import com.clarohome.core.design.ClaroScreenScaffold
import com.clarohome.core.design.ClaroSecondaryButton
import com.clarohome.core.design.ClaroSoftkey
import com.clarohome.core.design.fitsCompactHomeTile
import com.clarohome.core.model.VisualMode

@Composable
internal fun HomeWidgetPickerScreen(
    visualMode: VisualMode,
    onSelect: (AppWidgetProviderInfo) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val manager = remember(context) { AppWidgetManager.getInstance(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var providers by remember { mutableStateOf(manager.installedProviders) }
    var query by rememberSaveable { mutableStateOf("") }
    DisposableEffect(lifecycle, manager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) providers = manager.installedProviders
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val classic = visualMode == VisualMode.CLASSIC
    val widthDp = (LocalConfiguration.current.screenWidthDp - if (classic) 28 else 12) / 3
    val heightDp = if (classic) 86 else 124
    val density = LocalDensity.current
    val widthPx = with(density) { widthDp.dp.roundToPx() }
    val heightPx = with(density) { heightDp.dp.roundToPx() }
    val choices = providers.filter {
        it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 &&
            it.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0
    }.map { it to it.loadLabel(context.packageManager) }
        .filter { (_, label) -> label.contains(query.trim(), ignoreCase = true) }
        .sortedBy { (_, label) -> label.lowercase() }

    BackHandler(onBack = onBack)
    ClaroScreenScaffold(
        title = stringResource(R.string.app_choose_widget),
        scrollable = false,
        dockInSoftkeys = true,
        dock = {
            ClaroSecondaryButton(
                label = stringResource(R.string.app_cancel),
                onClick = onBack,
                softkey = ClaroSoftkey.BACK,
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaroDimensions.TileGap),
        ) {
            item(key = "size") {
                ClaroInfoCard(
                    text = stringResource(R.string.app_widget_compact_notice, widthDp, heightDp),
                    tone = ClaroBannerTone.INFO,
                )
            }
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.app_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (choices.isEmpty()) {
                item(key = "empty") {
                    ClaroInfoCard(
                        text = stringResource(R.string.app_widgets_none),
                        tone = ClaroBannerTone.INFO,
                    )
                }
            }
            items(choices, key = { (info, _) -> info.provider.flattenToString() }) { (info, label) ->
                val appLabel = remember(info.provider.packageName, providers) {
                    try {
                        context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(info.provider.packageName, 0),
                        ).toString()
                    } catch (_: PackageManager.NameNotFoundException) {
                        null
                    }
                }
                val padding = AppWidgetHostView.getDefaultPaddingForWidget(context, info.provider, null)
                val fits = appLabel != null && info.fitsCompactHomeTile(
                    widthPx - padding.left - padding.right,
                    heightPx - padding.top - padding.bottom,
                )
                ClaroRowButton(
                    title = label,
                    subtitle = when {
                        appLabel == null -> stringResource(R.string.app_missing_or_disabled_notice)
                        fits -> appLabel
                        else -> stringResource(R.string.app_widget_needs_larger_tile)
                    },
                    leading = { ClaroAppIcon(packageName = info.provider.packageName, size = 40.dp) },
                    enabled = fits,
                    onClick = { onSelect(info) },
                )
            }
        }
    }
}
