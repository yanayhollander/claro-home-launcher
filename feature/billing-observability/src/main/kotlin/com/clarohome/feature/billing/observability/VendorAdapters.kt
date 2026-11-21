package com.clarohome.feature.billing.observability

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mixpanel.android.mpmetrics.FeatureFlagOptions
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.android.mpmetrics.MixpanelOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject
import java.math.BigDecimal

data class VendorConfiguration(
    val revenueCatPublicKey: String,
    val mixpanelToken: String,
    val firebaseApplicationId: String,
    val firebaseApiKey: String,
    val firebaseProjectId: String,
    val firebaseSenderId: String,
    val mixpanelServerUrl: String = "https://api.mixpanel.com",
    val metaAppId: String = "",
    val metaClientToken: String = "",
    val metaEnabled: Boolean = false,
    val environment: String = "development",
) {
    private val validEnvironment: Boolean get() = environment in setOf("development", "staging", "production")
    val revenueCatAvailable: Boolean get() = revenueCatPublicKey.isNotBlank()
    val mixpanelAvailable: Boolean get() = validEnvironment && mixpanelToken.isNotBlank() && mixpanelServerUrl in setOf(
        "https://api.mixpanel.com", "https://api-eu.mixpanel.com", "https://api-in.mixpanel.com",
    )
    val firebaseAvailable: Boolean
        get() = validEnvironment && firebaseApplicationId.isNotBlank() && firebaseApiKey.isNotBlank() &&
            firebaseProjectId.isNotBlank() && firebaseSenderId.isNotBlank()
    val metaAvailable: Boolean
        get() = validEnvironment && metaEnabled && metaAppId.isNotBlank() && metaAppId.all { it in '0'..'9' } &&
            metaClientToken.isNotBlank()
}

enum class VendorStatus { UNCONFIGURED, DISABLED_BY_CONSENT, READY, INITIALIZATION_FAILED }

internal class VendorGuard(private val vendor: String, configured: Boolean) {
    @Volatile var status = if (configured) VendorStatus.DISABLED_BY_CONSENT else VendorStatus.UNCONFIGURED
        private set

    fun run(enabled: Boolean, action: () -> Unit) {
        if (status == VendorStatus.UNCONFIGURED) return
        try {
            action()
            status = if (enabled) VendorStatus.READY else VendorStatus.DISABLED_BY_CONSENT
        } catch (exception: RuntimeException) {
            if (exception is java.util.concurrent.CancellationException) throw exception
            status = VendorStatus.INITIALIZATION_FAILED
            Log.e("ClaroTelemetry", "$vendor failed: ${exception.javaClass.simpleName}")
        }
    }
}

@Singleton
class FirebaseBootstrap @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configuration: VendorConfiguration,
) {
    @Synchronized
    fun initialize(): FirebaseApp {
        check(configuration.firebaseAvailable) { "Firebase configuration is incomplete" }
        return FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: checkNotNull(FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId(configuration.firebaseApplicationId)
                    .setApiKey(configuration.firebaseApiKey)
                    .setProjectId(configuration.firebaseProjectId)
                    .setGcmSenderId(configuration.firebaseSenderId)
                    .build(),
            )) { "Firebase initialization failed" }
    }
}

@Singleton
class MixpanelVendorAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configuration: VendorConfiguration,
) : AnalyticsVendor {
    override val destination = AnalyticsDestination.MIXPANEL
    private val guard = VendorGuard("Mixpanel", configuration.mixpanelAvailable)
    val status: VendorStatus get() = guard.status
    private var instance: MixpanelAPI? = null
    private var enabled = false
    private var appliedContext: Map<String, String>? = null

    override fun setCollectionEnabled(enabled: Boolean, userId: String?) {
        if (this.enabled != enabled) appliedContext = null
        this.enabled = enabled
        guard.run(enabled) {
            if (enabled) {
                val mixpanel = instance ?: createInstance().also { instance = it }
                if (mixpanel.hasOptedOutTracking()) mixpanel.optInTracking()
                val identity = checkNotNull(userId)
                if (mixpanel.distinctId != identity) mixpanel.identify(identity)
            } else {
                instance?.optOutTracking()
            }
        }
    }

    override fun track(name: String, properties: Map<String, String>) {
        if (enabled) guard.run(true) {
            checkNotNull(instance).track(name, properties.toAnalyticsJson())
        }
    }

    override fun context(properties: Map<String, String>) {
        if (enabled && appliedContext != properties) guard.run(true) {
            val mixpanel = checkNotNull(instance)
            CONTEXT_PROPERTIES.forEach { mixpanel.unregisterSuperProperty(it) }
            mixpanel.registerSuperProperties(properties.toAnalyticsJson())
            mixpanel.people.set(properties.filterKeys { it in CONTEXT_PROPERTIES }.toAnalyticsJson())
            (CONTEXT_PROPERTIES - properties.keys).forEach { mixpanel.people.unset(it) }
            appliedContext = properties.toMap()
        }
    }

    override fun reset() = guard.run(false) {
        enabled = false
        appliedContext = null
        // optOutTracking clears queued events and identity; reset() would explicitly flush.
        instance?.optOutTracking()
    }

    private fun createInstance(): MixpanelAPI {
        val options = MixpanelOptions.Builder()
            .optOutTrackingDefault(true)
            .serverURL(configuration.mixpanelServerUrl)
            .featureFlagOptions(FeatureFlagOptions.Builder().enabled(false).build())
            .build()
        return checkNotNull(MixpanelAPI.getInstance(context, configuration.mixpanelToken, false, options)).apply {
            setUseIpAddressForGeolocation(false)
        }
    }
}

@Singleton
class FirebaseAnalyticsVendorAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    configuration: VendorConfiguration,
    private val bootstrap: FirebaseBootstrap,
) : AnalyticsVendor {
    override val destination = AnalyticsDestination.FIREBASE
    private val guard = VendorGuard("Firebase Analytics", configuration.firebaseAvailable)
    val status: VendorStatus get() = guard.status
    private var analytics: FirebaseAnalytics? = null
    private var enabled = false
    private var appliedContext: Map<String, String>? = null

    override fun setCollectionEnabled(enabled: Boolean, userId: String?) {
        if (this.enabled != enabled) appliedContext = null
        this.enabled = enabled
        guard.run(enabled) {
            if (!enabled && analytics == null) return@run
            val instance = analytics ?: run {
                bootstrap.initialize()
                FirebaseAnalytics.getInstance(context).also { analytics = it }
            }
            instance.setConsent(mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to if (enabled) {
                    FirebaseAnalytics.ConsentStatus.GRANTED
                } else {
                    FirebaseAnalytics.ConsentStatus.DENIED
                },
                FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
            ))
            instance.setUserId(if (enabled) checkNotNull(userId) else null)
            instance.setAnalyticsCollectionEnabled(enabled)
        }
    }

    override fun track(name: String, properties: Map<String, String>) {
        if (enabled) guard.run(true) { checkNotNull(analytics).logEvent(name, properties.toBundle()) }
    }

    override fun context(properties: Map<String, String>) {
        if (enabled && appliedContext != properties) guard.run(true) {
            val instance = checkNotNull(analytics)
            instance.setDefaultEventParameters(properties.toBundle())
            CONTEXT_PROPERTIES.forEach { key -> instance.setUserProperty(key, properties[key]) }
            appliedContext = properties.toMap()
        }
    }

    override fun reset() = guard.run(false) {
        enabled = false
        appliedContext = null
        analytics?.setDefaultEventParameters(null)
        analytics?.setAnalyticsCollectionEnabled(false)
        analytics?.setUserId(null)
        analytics?.resetAnalyticsData()
    }
}

@Singleton
class FirebaseCrashVendorAdapter @Inject constructor(
    configuration: VendorConfiguration,
    private val bootstrap: FirebaseBootstrap,
) : CrashVendor {
    private val guard = VendorGuard("Crashlytics", configuration.firebaseAvailable)
    val status: VendorStatus get() = guard.status
    private var crashlytics: FirebaseCrashlytics? = null
    private var enabled = false
    private var appliedInitialState = false

    override fun setCollectionEnabled(enabled: Boolean, userId: String?) {
        val previouslyEnabled = this.enabled
        this.enabled = enabled
        guard.run(enabled) {
            if (!enabled && crashlytics == null) {
                appliedInitialState = true
                return@run
            }
            val instance = crashlytics ?: run {
                bootstrap.initialize()
                FirebaseCrashlytics.getInstance().also { crashlytics = it }
            }
            // Never enable automatic uploads: opt-out can otherwise take effect only next launch.
            instance.setCrashlyticsCollectionEnabled(false)
            instance.setUserId(if (enabled) checkNotNull(userId) else "")
            if (!appliedInitialState && enabled) {
                instance.sendUnsentReports()
            } else if (!enabled || !previouslyEnabled) {
                // A new grant must not upload reports captured while consent was off.
                instance.deleteUnsentReports()
            }
            appliedInitialState = true
        }
    }

    override fun record(exception: Throwable) {
        if (enabled) guard.run(true) { checkNotNull(crashlytics).recordException(exception) }
    }

    override fun deleteUnsentReports() = guard.run(enabled) { crashlytics?.deleteUnsentReports() }

    override fun breadcrumb(name: String, properties: Map<String, String>) {
        if (enabled) guard.run(true) {
            crashlytics?.log("$name ${properties.entries.joinToString { "${it.key}=${it.value}" }}")
        }
    }

    override fun context(properties: Map<String, String>) {
        if (enabled) guard.run(true) {
            properties.forEach { (key, value) -> crashlytics?.setCustomKey(key, value) }
        }
    }
}

internal fun Map<String, String>.toBundle(): Bundle = Bundle().also { bundle ->
    forEach { (key, value) ->
        when (val typed = analyticsValue(key, value)) {
            is Long -> bundle.putLong(key, typed)
            is Double -> bundle.putDouble(key, typed)
            is BigDecimal -> bundle.putDouble(key, typed.toDouble())
            is Boolean -> bundle.putLong(key, if (typed) 1 else 0)
            else -> bundle.putString(key, value)
        }
    }
}

internal val CONTEXT_PROPERTIES = setOf(
    "visual_mode", "text_size", "home_tile_count", "home_contact_tile_count",
    "home_app_tile_count", "home_shortcut_tile_count", "home_widget_tile_count",
)

internal fun analyticsValue(key: String, value: String): Any = when {
    (key in CONTEXT_PROPERTIES && key.endsWith("_count")) || key == "tile_position" -> value.toLong()
    key == "amount" -> value.toBigDecimal()
    key == "test_mode" -> value.toBooleanStrict()
    else -> value
}

internal fun Map<String, String>.toAnalyticsJson(): JSONObject = JSONObject().also { json ->
    forEach { (key, value) -> json.put(key, analyticsValue(key, value)) }
}
