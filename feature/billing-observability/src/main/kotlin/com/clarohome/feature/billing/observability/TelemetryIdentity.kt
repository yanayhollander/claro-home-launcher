package com.clarohome.feature.billing.observability

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface TelemetryIdentityStore {
    fun getOrCreate(): String?
    fun clear()
}

internal const val OBSERVABILITY_PREFERENCES = "billing_observability_consent"
internal const val TELEMETRY_IDENTITY_KEY = "installation_id"

@Singleton
class SharedPreferencesTelemetryIdentityStore @Inject constructor(
    @ApplicationContext context: Context,
) : TelemetryIdentityStore {
    private val preferences = context.getSharedPreferences(OBSERVABILITY_PREFERENCES, Context.MODE_PRIVATE)
    private var unpersistedId: String? = null

    @Synchronized
    @SuppressLint("UseKtx") // An ID must be persisted before it is sent to any destination.
    override fun getOrCreate(): String? {
        preferences.getString(TELEMETRY_IDENTITY_KEY, null)?.let { existing ->
            if (existing.matches(INSTALLATION_ID) && existing != unpersistedId) return existing
            if (existing == unpersistedId) return persist(existing)
            Log.w("ClaroTelemetry", "Discarding invalid local telemetry identity")
        }
        return persist(UUID.randomUUID().toString())
    }

    @Synchronized
    @SuppressLint("UseKtx")
    override fun clear() {
        unpersistedId = null
        if (preferences.contains(TELEMETRY_IDENTITY_KEY) &&
            !preferences.edit().remove(TELEMETRY_IDENTITY_KEY).commit()
        ) {
            Log.e("ClaroTelemetry", "Telemetry identity could not be cleared")
        }
    }

    @SuppressLint("UseKtx")
    private fun persist(id: String): String? {
        if (!preferences.edit().putString(TELEMETRY_IDENTITY_KEY, id).commit()) {
            unpersistedId = id
            Log.e("ClaroTelemetry", "Telemetry identity could not be saved; analytics and diagnostics remain disabled")
            return null
        }
        unpersistedId = null
        return id
    }

    private companion object {
        val INSTALLATION_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    }
}
