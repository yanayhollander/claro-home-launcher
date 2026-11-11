package com.clarohome.feature.billing.observability

import android.content.Context
import androidx.core.content.edit
import com.clarohome.core.domain.AnalyticsEvent
import com.clarohome.core.domain.CrashReporter
import com.clarohome.core.domain.ProductAnalytics
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
        context.getSharedPreferences("billing_observability_consent", Context.MODE_PRIVATE)

    override fun read() = DiagnosticsConsent(
        analytics = preferences.getBoolean(ANALYTICS, false),
        crashReports = preferences.getBoolean(CRASH_REPORTS, false),
    )

    override fun write(consent: DiagnosticsConsent) {
        preferences.edit {
            putBoolean(ANALYTICS, consent.analytics)
            putBoolean(CRASH_REPORTS, consent.crashReports)
        }
    }

    override fun clear() {
        preferences.edit { clear() }
    }

    private companion object {
        const val ANALYTICS = "analytics"
        const val CRASH_REPORTS = "crash_reports"
    }
}

interface AnalyticsVendor {
    val destination: AnalyticsDestination
    fun setCollectionEnabled(enabled: Boolean)
    fun track(name: String, properties: Map<String, String>)
    fun reset()
}

interface CrashVendor {
    fun setCollectionEnabled(enabled: Boolean)
    fun record(exception: Throwable)
    fun deleteUnsentReports()
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
) : ConsentController {
    private val mutableConsent = MutableStateFlow(store.read())
    override val consent: StateFlow<DiagnosticsConsent> = mutableConsent.asStateFlow()

    init {
        applyCollectionState(mutableConsent.value)
    }

    override suspend fun update(consent: DiagnosticsConsent) {
        store.write(consent)
        mutableConsent.value = consent
        applyCollectionState(consent)
        if (!consent.analytics) {
            analyticsVendors.forEach(AnalyticsVendor::reset)
        }
        if (!consent.crashReports) {
            crashVendor.deleteUnsentReports()
        }
    }

    override suspend fun withdrawAll() {
        update(DiagnosticsConsent())
    }

    override suspend fun reset() {
        store.clear()
        withdrawAll()
    }

    private fun applyCollectionState(consent: DiagnosticsConsent) {
        analyticsVendors.forEach { it.setCollectionEnabled(consent.analytics) }
        crashVendor.setCollectionEnabled(consent.crashReports)
    }
}

@Singleton
class ConsentAwareObservability @Inject constructor(
    private val consentController: ConsentController,
    private val analyticsVendors: Set<@JvmSuppressWildcards AnalyticsVendor>,
    private val crashVendor: CrashVendor,
    private val networkInspector: VendorNetworkInspector,
) : ProductAnalytics, CrashReporter {
    fun track(event: ClaroEvent) {
        if (!consentController.consent.value.analytics) return
        val properties = event.properties()
        destinationsFor(event.name).forEach { destination ->
            analyticsVendors.firstOrNull { it.destination == destination }?.let { vendor ->
                networkInspector.onEnvelope(
                    InspectedVendorEnvelope(
                        destination = destination.name,
                        name = event.name.wireName,
                        properties = properties,
                    ),
                )
                vendor.track(event.name.wireName, properties)
            }
        }
    }

    override fun track(event: AnalyticsEvent) {
        val outcome = EventOutcome.entries.firstOrNull { it.wireValue == event.outcome } ?: return
        when (event.name) {
            ClaroEventName.MEMBERSHIP_SCREEN_VIEW.wireName ->
                track(ClaroEvent.MembershipScreenView(outcome))
            else -> Unit
        }
    }

    override fun record(exception: Throwable) {
        if (!consentController.consent.value.crashReports) return
        val sanitized = SanitizedException(exception::class.java.name).also {
            it.stackTrace = exception.stackTrace
        }
        networkInspector.onEnvelope(
            InspectedVendorEnvelope(
                destination = "CRASHLYTICS",
                name = "exception",
                properties = mapOf("type" to sanitized.originalType),
            ),
        )
        crashVendor.record(sanitized)
    }

    private fun destinationsFor(name: ClaroEventName): Set<AnalyticsDestination> = when (name) {
        ClaroEventName.ROLE_READINESS -> setOf(AnalyticsDestination.FIREBASE)
        ClaroEventName.SETUP_STEP_RESULT,
        ClaroEventName.TASK_FLOW_RESULT,
        ClaroEventName.MEMBERSHIP_SCREEN_VIEW,
        ClaroEventName.ACCESSIBILITY_PRESET_SELECTED,
        -> setOf(AnalyticsDestination.MIXPANEL)
    }
}

class SanitizedException(
    val originalType: String,
) : RuntimeException("Sanitized exception: $originalType")

private fun ClaroEvent.properties(): Map<String, String> = buildMap {
    put("outcome", outcome.wireValue)
    when (this@properties) {
        is ClaroEvent.SetupStepResult -> put("step", step.wireValue)
        is ClaroEvent.RoleReadiness -> put("capability", capability.wireValue)
        is ClaroEvent.TaskFlowResult -> put("task", task.wireValue)
        is ClaroEvent.AccessibilityPresetSelected -> put("preset", preset.wireValue)
        is ClaroEvent.MembershipScreenView -> Unit
    }
}

fun InspectedVendorEnvelope.toJson(): String = JSONObject().apply {
    put("destination", destination)
    put("name", name)
    put("properties", JSONObject(properties))
}.toString()
