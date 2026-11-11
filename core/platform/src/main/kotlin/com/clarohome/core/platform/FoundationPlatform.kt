package com.clarohome.core.platform

import com.clarohome.core.domain.CriticalActivityRepository
import com.clarohome.core.domain.PlatformReadinessRepository
import com.clarohome.core.domain.StartupRecoveryCoordinator
import com.clarohome.core.domain.StartupRecoveryState
import com.clarohome.core.domain.VisualModeRepository
import com.clarohome.core.model.CapabilityStatus
import com.clarohome.core.model.CriticalActivity
import com.clarohome.core.model.PlatformReadiness
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Singleton
class DefaultPlatformReadinessRepository @Inject constructor() : PlatformReadinessRepository {
    override suspend fun current() = PlatformReadiness(
        home = CapabilityStatus.NEEDS_ATTENTION,
        phone = CapabilityStatus.NEEDS_ATTENTION,
        messages = CapabilityStatus.NEEDS_ATTENTION,
        contacts = CapabilityStatus.NEEDS_ATTENTION,
        notifications = CapabilityStatus.NEEDS_ATTENTION,
        media = CapabilityStatus.READY,
    )
}

@Singleton
class DefaultCriticalActivityRepository @Inject constructor() : CriticalActivityRepository {
    override val activeActivities: Flow<Set<CriticalActivity>> = flowOf(emptySet())
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

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformBindingsModule {
    @Binds
    abstract fun bindReadiness(
        repository: DefaultPlatformReadinessRepository,
    ): PlatformReadinessRepository

    @Binds
    abstract fun bindStartupRecovery(
        coordinator: DefaultStartupRecoveryCoordinator,
    ): StartupRecoveryCoordinator

    @Binds
    abstract fun bindCriticalActivityRepository(
        repository: DefaultCriticalActivityRepository,
    ): CriticalActivityRepository
}

@Module
@InstallIn(SingletonComponent::class)
object FoundationDomainModule {
    @Provides
    @Singleton
    fun provideProtectedRouteGuard(): com.clarohome.core.domain.ProtectedRouteGuard =
        com.clarohome.core.domain.DefaultProtectedRouteGuard()
}
