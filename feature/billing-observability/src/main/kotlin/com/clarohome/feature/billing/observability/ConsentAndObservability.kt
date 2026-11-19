package com.clarohome.feature.billing.observability

import android.content.Context
import android.annotation.SuppressLint
import com.clarohome.core.domain.ClaroEvent
import com.clarohome.core.domain.CrashReporter
import com.clarohome.core.domain.DiagnosticOperation
import com.clarohome.core.domain.ProductAnalytics
import com.clarohome.core.model.TextSize
import com.clarohome.core.model.VisualMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

interface ConsentStore {
    fun read(): DiagnosticsConsent
    fun write(consent: DiagnosticsConsent)
    fun clear()
}

@Singleton
class SharedPreferencesConsentStore @Inject constructor(
    @ApplicationContext context: Context,
) : ConsentStore {
    private val preferences =
        context.getSharedPreferences(OBSERVABILITY_PREFERENCES, Context.MODE_PRIVATE)

    override fun read() = DiagnosticsConsent(
        analytics = preferences.getBoolean(ANALYTICS, false),
        crashReports = preferences.getBoolean(CRASH_REPORTS, false),
        marketing = preferences.getBoolean(MARKETING, false),
    )

    // KTX edit returns Unit; consent persistence must check the commit result.
    @SuppressLint("UseKtx")
    override fun write(consent: DiagnosticsConsent) {
        check(preferences.edit()
            .putBoolean(ANALYTICS, consent.analytics)
            .putBoolean(CRASH_REPORTS, consent.crashReports)
            .putBoolean(MARKETING, consent.marketing)
            .apply {
                if (!consent.analytics && !consent.crashReports) remove(TELEMETRY_IDENTITY_KEY)
            }
            .commit()) { "Diagnostics consent could not be saved" }
    }

    @SuppressLint("UseKtx")
    override fun clear() {
        check(preferences.edit().clear().commit()) { "Diagnostics consent could not be cleared" }
    }

    private companion object {
        const val ANALYTICS = "analytics"
        const val CRASH_REPORTS = "crash_reports"
        const val MARKETING = "marketing"
    }
}

interface AnalyticsVendor {
    val destination: AnalyticsDestination
    fun setCollectionEnabled(enabled: Boolean, userId: String? = null)
    fun track(name: String, properties: Map<String, String>)
    fun reset()
}

interface CrashVendor {
    fun setCollectionEnabled(enabled: Boolean, userId: String? = null)
    fun record(exception: Throwable)
    fun deleteUnsentReports()
    fun breadcrumb(name: String, properties: Map<String, String>) = Unit
    fun context(properties: Map<String, String>) = Unit
}

data class InspectedVendorEnvelope(
    val destination: String,
    val name: String,
    val properties: Map<String, String>,
)

interface VendorNetworkInspector {
    fun onEnvelope(envelope: InspectedVendorEnvelope)
}

object NoOpVendorNetworkInspector : VendorNetworkInspector {
    override fun onEnvelope(envelope: InspectedVendorEnvelope) = Unit
}

@Singleton
class DefaultConsentController @Inject constructor(
    private val store: ConsentStore,
    private val analyticsVendors: Set<@JvmSuppressWildcards AnalyticsVendor>,
    private val crashVendor: CrashVendor,
    private val identityStore: TelemetryIdentityStore,
) : ConsentController {
    private val mutableConsent = MutableStateFlow(store.read())
    override val consent: StateFlow<DiagnosticsConsent> = mutableConsent.asStateFlow()

    init {
        applyCollectionState(mutableConsent.value)
    }

    override suspend fun update(consent: DiagnosticsConsent) {
        store.write(consent)
        // Close withdrawals immediately, but publish grants only after the SDKs are ready.
        val previous = mutableConsent.value
        mutableConsent.value = DiagnosticsConsent(
            analytics = previous.analytics && consent.analytics,
            crashReports = previous.crashReports && consent.crashReports,
            marketing = previous.marketing && consent.marketing,
        )
        applyCollectionState(consent)
        analyticsVendors.filterNot { consent.allows(it.destination) }.forEach {
            it.reset()
        }
        if (!consent.crashReports) {
            crashVendor.deleteUnsentReports()
        }
        mutableConsent.value = consent
    }

    override suspend fun withdrawAll() {
        update(DiagnosticsConsent())
    }

    override suspend fun reset() {
        store.clear()
        withdrawAll()
    }

    private fun applyCollectionState(consent: DiagnosticsConsent) {
        val identity = if (consent.analytics || consent.crashReports) identityStore.getOrCreate() else null
        analyticsVendors.forEach { vendor ->
            val usesIdentity = vendor.destination != AnalyticsDestination.META
            vendor.setCollectionEnabled(
                consent.allows(vendor.destination) && (!usesIdentity || identity != null),
                identity.takeIf { consent.analytics && usesIdentity },
            )
        }
        crashVendor.setCollectionEnabled(
            consent.crashReports && identity != null,
            identity.takeIf { consent.crashReports },
        )
        if (!consent.analytics && !consent.crashReports) identityStore.clear()
    }
}

@Singleton
class ConsentAwareObservability @Inject constructor(
    private val consentController: ConsentController,
    private val analyticsVendors: Set<@JvmSuppressWildcards AnalyticsVendor>,
    private val crashVendor: CrashVendor,
    private val networkInspector: VendorNetworkInspector,
) : ProductAnalytics, CrashReporter {
    @Volatile private var context: Map<String, String> = emptyMap()
    private val lastSessions = mutableMapOf<AnalyticsDestination, Long>()
    private val reportedFailures = mutableSetOf<Triple<DiagnosticOperation, String, StackTraceElement?>>()
    private var session = 0L

    fun initialize() {
        consentController.consent.value
    }

    fun updateContext(mode: VisualMode, textSize: TextSize) {
        context = mapOf("visual_mode" to mode.wireValue(), "text_size" to textSize.wireValue())
        if (consentController.consent.value.crashReports) crashVendor.context(context)
    }

    @Synchronized
    fun foregroundSession(id: Long) {
        if (session != id) reportedFailures.clear()
        session = id
        track(ClaroEvent.SessionStarted)
    }

    @Synchronized
    override fun track(event: ClaroEvent) {
        val consent = consentController.consent.value
        val envelope = event.envelope()
        val destinations = buildSet {
            if (consent.analytics) add(event.primaryDestination())
            if (consent.marketing && event.isMarketingConversion()) add(AnalyticsDestination.META)
        }
        destinations.forEach { destination ->
            if (event == ClaroEvent.SessionStarted && session != 0L) {
                if (lastSessions[destination] == session) return@forEach
                lastSessions[destination] = session
            }
            val properties = if (destination == AnalyticsDestination.META) {
                envelope.properties
            } else {
                context + envelope.properties + mapOf(
                    "schema_version" to "1", "environment" to BuildConfig.TELEMETRY_ENVIRONMENT,
                )
            }
            analyticsVendors.firstOrNull { it.destination == destination }?.let { vendor ->
                networkInspector.onEnvelope(
                    InspectedVendorEnvelope(
                        destination = destination.name,
                        name = envelope.name,
                        properties = properties,
                    ),
                )
                vendor.track(envelope.name, properties)
            }
        }
        if (consent.crashReports) {
            crashVendor.context(context)
            crashVendor.breadcrumb(envelope.name, envelope.properties.filterKeys {
                it != "amount" && it != "currency"
            })
        }
    }

    @Synchronized
    override fun record(exception: Throwable, operation: DiagnosticOperation) {
        if (!consentController.consent.value.crashReports) return
        if (!reportedFailures.add(Triple(operation, exception.javaClass.name, exception.stackTrace.firstOrNull()))) return
        val sanitized = SanitizedException(exception::class.java.name).also {
            it.stackTrace = exception.stackTrace
        }
        networkInspector.onEnvelope(
            InspectedVendorEnvelope(
                destination = "CRASHLYTICS",
                name = "exception",
                properties = mapOf("type" to sanitized.originalType, "operation" to operation.wireValue()),
            ),
        )
        crashVendor.context(context + ("operation" to operation.wireValue()))
        crashVendor.record(sanitized)
    }
}

class SanitizedException(
    val originalType: String,
) : RuntimeException("Sanitized exception: $originalType")

private fun DiagnosticsConsent.allows(destination: AnalyticsDestination): Boolean =
    if (destination == AnalyticsDestination.META) marketing else analytics

fun InspectedVendorEnvelope.toJson(): String = JSONObject().apply {
    put("destination", destination)
    put("name", name)
    put("properties", JSONObject(properties))
}.toString()
