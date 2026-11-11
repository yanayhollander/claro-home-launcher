package com.clarohome.core.platform

import android.util.Log
import com.clarohome.core.domain.AnalyticsEvent
import com.clarohome.core.domain.BillingGateway
import com.clarohome.core.domain.CrashReporter
import com.clarohome.core.domain.PlatformReadinessRepository
import com.clarohome.core.domain.ProductAnalytics
import com.clarohome.core.domain.StartupRecoveryCoordinator
import com.clarohome.core.domain.StartupRecoveryState
import com.clarohome.core.domain.VisualModeRepository
import com.clarohome.core.model.CapabilityStatus
import com.clarohome.core.model.EntitlementState
import com.clarohome.core.model.PlatformReadiness
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

@Singleton
class FakePlatformReadinessRepository @Inject constructor() : PlatformReadinessRepository {
    override suspend fun current() = PlatformReadiness(
        home = CapabilityStatus.NEEDS_ATTENTION,
        phone = CapabilityStatus.NEEDS_ATTENTION,
        messages = CapabilityStatus.NEEDS_ATTENTION,
    )
}

@Singleton
class DefaultStartupRecoveryCoordinator @Inject constructor(
    private val visualModeRepository: VisualModeRepository,
    private val readinessRepository: PlatformReadinessRepository,
) : StartupRecoveryCoordinator {
    override suspend fun recover(): StartupRecoveryState = runCatching {
        StartupRecoveryState.Ready(
            visualMode = visualModeRepository.visualMode.first(),
            readiness = readinessRepository.current(),
        )
    }.getOrElse { StartupRecoveryState.Failed("Unable to restore local state") }
}

interface VendorAnalyticsAdapter {
    fun track(event: AnalyticsEvent)
}

class MixpanelAnalyticsAdapter @Inject constructor() : VendorAnalyticsAdapter {
    override fun track(event: AnalyticsEvent) = Unit
}

class FirebaseAnalyticsAdapter @Inject constructor() : VendorAnalyticsAdapter {
    override fun track(event: AnalyticsEvent) = Unit
}

@Singleton
class DisabledProductAnalytics @Inject constructor(
    private val mixpanel: MixpanelAnalyticsAdapter,
    private val firebase: FirebaseAnalyticsAdapter,
) : ProductAnalytics {
    override fun track(event: AnalyticsEvent) {
        mixpanel.track(event)
        firebase.track(event)
    }
}

@Singleton
class DebugProductAnalytics @Inject constructor() : ProductAnalytics {
    override fun track(event: AnalyticsEvent) {
        Log.d("ClaroAnalytics", "${event.name}:${event.outcome}")
    }
}

@Singleton
class FirebaseCrashlyticsAdapter @Inject constructor() : CrashReporter {
    override fun record(exception: Throwable) = Unit
}

@Singleton
class DebugCrashReporter @Inject constructor() : CrashReporter {
    override fun record(exception: Throwable) {
        Log.e("ClaroCrashReporter", exception.message, exception)
    }
}

@Singleton
class RevenueCatBillingAdapter @Inject constructor() : BillingGateway {
    override val entitlement: Flow<EntitlementState> = flowOf(EntitlementState.FREE)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformBindingsModule {
    @Binds
    abstract fun bindReadiness(
        repository: FakePlatformReadinessRepository,
    ): PlatformReadinessRepository

    @Binds
    abstract fun bindStartupRecovery(
        coordinator: DefaultStartupRecoveryCoordinator,
    ): StartupRecoveryCoordinator

    @Binds
    abstract fun bindAnalytics(analytics: DisabledProductAnalytics): ProductAnalytics

    @Binds
    abstract fun bindCrashReporter(reporter: FirebaseCrashlyticsAdapter): CrashReporter

    @Binds
    abstract fun bindBilling(gateway: RevenueCatBillingAdapter): BillingGateway
}

@Module
@InstallIn(SingletonComponent::class)
object FoundationDomainModule {
    @Provides
    @Singleton
    fun provideProtectedRouteGuard(): com.clarohome.core.domain.ProtectedRouteGuard =
        com.clarohome.core.domain.DefaultProtectedRouteGuard()
}
