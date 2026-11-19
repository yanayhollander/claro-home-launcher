package com.clarohome.feature.billing.observability

import com.clarohome.core.domain.BillingGateway
import com.clarohome.core.domain.CrashReporter
import com.clarohome.core.domain.ProductAnalytics
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingObservabilityBindings {
    @Binds
    abstract fun bindConsentStore(store: SharedPreferencesConsentStore): ConsentStore

    @Binds
    abstract fun bindTelemetryIdentityStore(store: SharedPreferencesTelemetryIdentityStore): TelemetryIdentityStore

    @Binds
    abstract fun bindConsentController(controller: DefaultConsentController): ConsentController

    @Binds
    abstract fun bindProductAnalytics(observability: ConsentAwareObservability): ProductAnalytics

    @Binds
    abstract fun bindCrashReporter(observability: ConsentAwareObservability): CrashReporter

    @Binds
    abstract fun bindCrashVendor(vendor: FirebaseCrashVendorAdapter): CrashVendor
}

@Module
@InstallIn(SingletonComponent::class)
object BillingObservabilityProviders {
    @Provides
    @Singleton
    fun provideConfiguration() = VendorConfiguration(
        revenueCatPublicKey = BuildConfig.REVENUECAT_PUBLIC_KEY,
        mixpanelToken = BuildConfig.MIXPANEL_TOKEN,
        firebaseApplicationId = BuildConfig.FIREBASE_APPLICATION_ID,
        firebaseApiKey = BuildConfig.FIREBASE_API_KEY,
        firebaseProjectId = BuildConfig.FIREBASE_PROJECT_ID,
        firebaseSenderId = BuildConfig.FIREBASE_SENDER_ID,
        mixpanelServerUrl = BuildConfig.MIXPANEL_SERVER_URL,
        metaAppId = BuildConfig.META_APP_ID,
        metaClientToken = BuildConfig.META_CLIENT_TOKEN,
        metaEnabled = BuildConfig.META_ENABLED,
        environment = BuildConfig.TELEMETRY_ENVIRONMENT,
    )

    @Provides
    @IntoSet
    fun provideMixpanelVendor(vendor: MixpanelVendorAdapter): AnalyticsVendor = vendor

    @Provides
    @IntoSet
    fun provideFirebaseVendor(vendor: FirebaseAnalyticsVendorAdapter): AnalyticsVendor = vendor

    @Provides
    @IntoSet
    fun provideMetaVendor(vendor: MetaVendorAdapter): AnalyticsVendor = vendor

    @Provides
    @Singleton
    fun provideNetworkInspector(): VendorNetworkInspector = NoOpVendorNetworkInspector

    @Provides
    @Singleton
    fun provideBillingRepository(
        configuration: VendorConfiguration,
        revenueCat: dagger.Lazy<RevenueCatBillingRepository>,
    ): BillingRepository =
        if (configuration.revenueCatAvailable) revenueCat.get() else DisabledBillingRepository()

    @Provides
    fun provideBillingGateway(repository: BillingRepository): BillingGateway =
        object : BillingGateway {
            override val entitlement = repository.entitlement
        }
}
