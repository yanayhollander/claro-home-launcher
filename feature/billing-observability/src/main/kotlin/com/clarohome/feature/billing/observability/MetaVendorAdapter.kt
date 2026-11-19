package com.clarohome.feature.billing.observability

import android.content.Context
import android.app.Application
import android.os.Bundle
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaVendorAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configuration: VendorConfiguration,
) : AnalyticsVendor {
    override val destination = AnalyticsDestination.META
    private val guard = VendorGuard("Meta App Events", configuration.metaAvailable)
    val status: VendorStatus get() = guard.status
    private var logger: AppEventsLogger? = null
    private var enabled = false
    private var activationRegistered = false

    @Suppress("DEPRECATION")
    override fun setCollectionEnabled(enabled: Boolean, userId: String?) {
        this.enabled = enabled
        guard.run(enabled) {
            check(userId == null) { "Shared analytics identity must not be sent to Meta" }
            if (!enabled) return@run
            if (logger == null) {
                FacebookSdk.setApplicationId(configuration.metaAppId)
                FacebookSdk.setClientToken(configuration.metaClientToken)
                FacebookSdk.setAutoInitEnabled(false)
                FacebookSdk.setAutoLogAppEventsEnabled(false)
                FacebookSdk.setAdvertiserIDCollectionEnabled(false)
                FacebookSdk.setAutoLogMetaDataEnabled(false)
                FacebookSdk.setAutoLogProductImprovementEnabled(false)
                FacebookSdk.setCodelessDebugLogEnabled(false)
                // The provider is removed so even persisted SDK overrides cannot initialize pre-consent.
                FacebookSdk.sdkInitialize(context)
                FacebookSdk.fullyInitialize()
                logger = AppEventsLogger.newLogger(context)
            }
        }
    }

    override fun track(name: String, properties: Map<String, String>) {
        if (!enabled) return
        guard.run(true) {
            val instance = checkNotNull(logger)
            when (name) {
                "session_started" -> {
                    if (!activationRegistered) {
                        val application = context.applicationContext
                        check(application is Application)
                        AppEventsLogger.activateApp(application)
                        activationRegistered = true
                    }
                    instance.logEvent(AppEventsConstants.EVENT_NAME_ACTIVATED_APP)
                }
                "setup_completed" -> instance.logEvent(
                    AppEventsConstants.EVENT_NAME_COMPLETED_TUTORIAL,
                    Bundle().apply {
                        putInt(AppEventsConstants.EVENT_PARAM_SUCCESS, 1)
                        putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, "initial_setup")
                    },
                )
                "membership_screen_view" -> instance.logEvent(
                    AppEventsConstants.EVENT_NAME_VIEWED_CONTENT,
                    Bundle().apply { putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, "membership") },
                )
                "checkout_started" -> instance.logEvent(
                    AppEventsConstants.EVENT_NAME_INITIATED_CHECKOUT,
                    productParameters(properties),
                )
                "conversion" -> when (properties["kind"]) {
                    "trial" -> instance.logEvent(
                        AppEventsConstants.EVENT_NAME_START_TRIAL, productParameters(properties),
                    )
                    "subscription" -> instance.logEvent(
                        AppEventsConstants.EVENT_NAME_SUBSCRIBE, productParameters(properties),
                    )
                    "purchase" -> {
                        val amount = checkNotNull(properties["amount"]).toBigDecimal()
                        val currency = Currency.getInstance(checkNotNull(properties["currency"]))
                        instance.logPurchase(amount, currency, productParameters(properties))
                    }
                    else -> error("Unsupported Meta conversion")
                }
                else -> error("Event is not allowed for Meta")
            }
        }
    }

    override fun reset() = guard.run(false) {
        enabled = false
        if (logger != null) {
            FacebookSdk.setAutoLogAppEventsEnabled(false)
            FacebookSdk.setAdvertiserIDCollectionEnabled(false)
            FacebookSdk.setAutoInitEnabled(false)
            AppEventsLogger.clearUserData()
        }
        // The SDK has no supported queue-erasure API. Enabling this adapter requires the
        // documented consent/withdrawal release review; do not claim this clears queued data.
    }

    private fun productParameters(properties: Map<String, String>): Bundle = Bundle().apply {
        val product = checkNotNull(properties["product"])
        check(product in setOf("monthly", "yearly", "lifetime"))
        putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, product)
        putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, "product")
        putInt(AppEventsConstants.EVENT_PARAM_NUM_ITEMS, 1)
    }
}
