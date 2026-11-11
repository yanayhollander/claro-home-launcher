package com.clarohome.feature.billing.observability

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mixpanel.android.mpmetrics.MPConfig
import com.mixpanel.android.mpmetrics.MixpanelAPI
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

data class VendorConfiguration(
    val revenueCatPublicKey: String,
    val mixpanelToken: String,
    val firebaseApplicationId: String,
    val firebaseApiKey: String,
    val firebaseProjectId: String,
    val firebaseSenderId: String,
) {
    val revenueCatAvailable: Boolean
        get() = revenueCatPublicKey.isNotBlank()

    val mixpanelAvailable: Boolean
        get() = mixpanelToken.isNotBlank()

    val firebaseAvailable: Boolean
        get() = firebaseApplicationId.isNotBlank() &&
            firebaseApiKey.isNotBlank() &&
            firebaseProjectId.isNotBlank() &&
            firebaseSenderId.isNotBlank()
}

@Singleton
class MixpanelVendorAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configuration: VendorConfiguration,
) : AnalyticsVendor {
    override val destination = AnalyticsDestination.MIXPANEL
    private var instance: MixpanelAPI? = null

    override fun setCollectionEnabled(enabled: Boolean) {
        if (!configuration.mixpanelAvailable) return
        if (!enabled && instance == null) return
        val mixpanel = instance ?: createInstance().also { instance = it }
        if (enabled) {
            mixpanel.optInTracking()
        } else {
            mixpanel.optOutTracking()
        }
    }

    override fun track(name: String, properties: Map<String, String>) {
        instance?.track(name, JSONObject(properties))
    }

    override fun reset() {
        instance?.reset()
        instance?.optOutTracking()
    }

    private fun createInstance(): MixpanelAPI {
        MPConfig.getInstance(context, configuration.mixpanelToken).apply {
            setTrackAutomaticEvents(false)
            setUseIpAddressForGeolocation(false)
        }
        return MixpanelAPI.getInstance(
            context,
            configuration.mixpanelToken,
            false,
            true,
        )
    }
}

@Singleton
class FirebaseAnalyticsVendorAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configuration: VendorConfiguration,
) : AnalyticsVendor {
    override val destination = AnalyticsDestination.FIREBASE
    private var analytics: FirebaseAnalytics? = null

    override fun setCollectionEnabled(enabled: Boolean) {
        if (!configuration.firebaseAvailable) return
        if (!enabled && analytics == null) return
        val firebaseAnalytics = analytics ?: initializeFirebase()?.also { analytics = it } ?: return
        firebaseAnalytics.setAnalyticsCollectionEnabled(enabled)
        if (!enabled) {
            firebaseAnalytics.resetAnalyticsData()
        }
    }

    override fun track(name: String, properties: Map<String, String>) {
        analytics?.logEvent(name, Bundle().apply {
            properties.forEach { (key, value) -> putString(key, value) }
        })
    }

    override fun reset() {
        analytics?.resetAnalyticsData()
        analytics?.setAnalyticsCollectionEnabled(false)
    }

    private fun initializeFirebase(): FirebaseAnalytics? {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId(configuration.firebaseApplicationId)
                    .setApiKey(configuration.firebaseApiKey)
                    .setProjectId(configuration.firebaseProjectId)
                    .setGcmSenderId(configuration.firebaseSenderId)
                    .build(),
            ) ?: return null
        }
        return FirebaseAnalytics.getInstance(context).also {
            it.setAnalyticsCollectionEnabled(false)
        }
    }
}

@Singleton
class FirebaseCrashVendorAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configuration: VendorConfiguration,
) : CrashVendor {
    private var crashlytics: FirebaseCrashlytics? = null

    override fun setCollectionEnabled(enabled: Boolean) {
        if (!configuration.firebaseAvailable) return
        if (!enabled && crashlytics == null) return
        val instance = crashlytics ?: initializeFirebase()?.also { crashlytics = it } ?: return
        instance.setCrashlyticsCollectionEnabled(enabled)
    }

    override fun record(exception: Throwable) {
        crashlytics?.recordException(exception)
    }

    override fun deleteUnsentReports() {
        crashlytics?.deleteUnsentReports()
    }

    private fun initializeFirebase(): FirebaseCrashlytics? {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId(configuration.firebaseApplicationId)
                    .setApiKey(configuration.firebaseApiKey)
                    .setProjectId(configuration.firebaseProjectId)
                    .setGcmSenderId(configuration.firebaseSenderId)
                    .build(),
            ) ?: return null
        }
        return FirebaseCrashlytics.getInstance().also {
            it.setCrashlyticsCollectionEnabled(false)
        }
    }
}
