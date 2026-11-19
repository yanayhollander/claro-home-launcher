package com.clarohome.app

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.clarohome.core.data.homeSlotTile
import com.clarohome.core.domain.EventOutcome
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind

internal class HomeWidgetSetup(
    private val activity: ComponentActivity,
    private val canEdit: () -> Boolean,
    private val onSetSlot: (Int, HomeTile) -> Unit,
    private val onFinished: () -> Unit,
    private val onNotice: (Int) -> Unit,
    private val onOutcome: (EventOutcome) -> Unit = {},
) {
    val host = AppWidgetHost(activity.applicationContext, HOST_ID)
    private val manager = AppWidgetManager.getInstance(activity)
    private var pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingSlot = -1
    private var pendingProvider: String? = null
    private var committing = false
    private var commitRequested = false
    private val binding = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            if (result.resultCode == Activity.RESULT_OK) configureOrCommit() else cancel(R.string.app_widget_cancelled)
        }
    }

    fun restore(state: Bundle?) {
        state ?: return
        pendingId = state.getInt("id", AppWidgetManager.INVALID_APPWIDGET_ID)
        pendingSlot = state.getInt("slot", -1)
        pendingProvider = state.getString("provider")
        committing = state.getBoolean("committing")
    }

    fun save(): Bundle = Bundle().apply {
        putInt("id", pendingId)
        putInt("slot", pendingSlot)
        putString("provider", pendingProvider)
        putBoolean("committing", committing)
    }

    fun begin(slot: Int, provider: AppWidgetProviderInfo) {
        require(slot >= 0)
        if (!canEdit()) {
            onNotice(R.string.app_widget_unlock_required)
            return
        }
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            onNotice(R.string.app_widget_setup_pending)
            return
        }
        pendingId = host.allocateAppWidgetId()
        pendingSlot = slot
        pendingProvider = provider.provider.flattenToString()
        onOutcome(EventOutcome.STARTED)
        try {
            if (manager.bindAppWidgetIdIfAllowed(pendingId, provider.profile, provider.provider, null)) {
                configureOrCommit()
            } else {
                binding.launch(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile),
                )
            }
        } catch (_: ActivityNotFoundException) {
            cancel(R.string.app_widget_setup_unavailable)
        } catch (_: SecurityException) {
            cancel(R.string.app_widget_setup_unavailable)
        }
    }

    private fun boundProvider(): AppWidgetProviderInfo? =
        manager.getAppWidgetInfo(pendingId)?.takeIf {
            it.provider.flattenToString() == pendingProvider
        }

    private fun configureOrCommit() {
        val provider = boundProvider()
        if (provider == null) {
            cancel(R.string.app_widget_setup_unavailable)
            return
        }
        if (!canEdit()) {
            cancel(R.string.app_widget_unlock_required)
            return
        }
        if (provider.configure == null) {
            commit()
        } else {
            try {
                host.startAppWidgetConfigureActivityForResult(activity, pendingId, 0, CONFIGURE_REQUEST, null)
            } catch (_: ActivityNotFoundException) {
                cancel(R.string.app_widget_setup_unavailable)
            } catch (_: SecurityException) {
                cancel(R.string.app_widget_setup_unavailable)
            }
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != CONFIGURE_REQUEST) return false
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            if (resultCode == Activity.RESULT_OK) commit() else cancel(R.string.app_widget_cancelled)
        }
        return true
    }

    private fun commit() {
        val provider = boundProvider()
        if (provider == null || pendingSlot < 0) {
            cancel(R.string.app_widget_setup_unavailable)
            return
        }
        if (!canEdit()) {
            cancel(R.string.app_widget_unlock_required)
            return
        }
        committing = true
        commitRequested = true
        onSetSlot(
            pendingSlot,
            homeSlotTile(
                slot = pendingSlot,
                kind = HomeTileKind.WIDGET,
                label = provider.loadLabel(activity.packageManager),
                appWidgetId = pendingId,
                appWidgetProvider = pendingProvider,
            ),
        )
    }

    fun reconcile(slots: List<HomeTile?>) {
        val savedIds = slots.filterNotNull().filter { it.kind == HomeTileKind.WIDGET }
            .mapNotNull { it.appWidgetId }.toSet()
        if (committing) {
            if (pendingId in savedIds) {
                onOutcome(EventOutcome.COMPLETED)
                clearPending()
                onFinished()
            } else if (!commitRequested) {
                commit()
            }
        }
        host.appWidgetIds.filter { it !in savedIds && it != pendingId }.forEach(host::deleteAppWidgetId)
    }

    private fun clearPending() {
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingSlot = -1
        pendingProvider = null
        committing = false
        commitRequested = false
    }

    private fun cancel(notice: Int) {
        onOutcome(if (notice == R.string.app_widget_cancelled) EventOutcome.CANCELED else EventOutcome.UNAVAILABLE)
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) host.deleteAppWidgetId(pendingId)
        clearPending()
        onNotice(notice)
    }

    fun destroy() {
        if (activity.isFinishing && pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            onOutcome(EventOutcome.CANCELED)
            host.deleteAppWidgetId(pendingId)
            clearPending()
        }
    }

    private companion object {
        const val HOST_ID = 62801
        const val CONFIGURE_REQUEST = 62802
    }
}
