package com.clarohome.core.design

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

val LocalClaroAppWidgetHost = staticCompositionLocalOf<AppWidgetHost?> { null }

fun AppWidgetProviderInfo.fitsCompactHomeTile(widthPx: Int, heightPx: Int): Boolean {
    val requiredWidth = if (resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0 && minResizeWidth > 0) {
        minResizeWidth
    } else {
        minWidth
    }
    val requiredHeight = if (resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0 && minResizeHeight > 0) {
        minResizeHeight
    } else {
        minHeight
    }
    return requiredWidth <= widthPx && requiredHeight <= heightPx
}

@Composable
fun ClaroAppWidget(
    appWidgetId: Int?,
    provider: String?,
    label: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val host = LocalClaroAppWidgetHost.current
    val info = appWidgetId?.let { AppWidgetManager.getInstance(context).getAppWidgetInfo(it) }
        ?.takeIf { it.provider.flattenToString() == provider }
    BoxWithConstraints(
        modifier = modifier.background(ClaroColors.Surface).testTag("home-widget"),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val padding = info?.let {
            AppWidgetHostView.getDefaultPaddingForWidget(context, it.provider, null)
        }
        val fits = info?.fitsCompactHomeTile(
            with(density) { maxWidth.roundToPx() } - (padding?.left ?: 0) - (padding?.right ?: 0),
            with(density) { maxHeight.roundToPx() } - (padding?.top ?: 0) - (padding?.bottom ?: 0),
        ) == true
        if (host == null || info == null || appWidgetId == null || !fits) {
            Text(
                text = stringResource(
                    if (info != null && !fits) R.string.claro_widget_too_large else R.string.claro_widget_unavailable,
                    label,
                ),
                color = ClaroColors.WarningInk,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
            )
        } else {
            val width = maxWidth.value.toInt()
            val height = maxHeight.value.toInt()
            key(appWidgetId) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        host.createView(viewContext, appWidgetId, info)
                    },
                    update = { view ->
                        view.updateAppWidgetSize(null, width, height, width, height)
                    },
                )
            }
        }
    }
}
