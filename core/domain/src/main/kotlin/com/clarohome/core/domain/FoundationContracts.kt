package com.clarohome.core.domain

import com.clarohome.core.model.EntitlementState
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.PlatformReadiness
import com.clarohome.core.model.VisualMode
import kotlinx.coroutines.flow.Flow

interface VisualModeRepository {
    val visualMode: Flow<VisualMode>
    suspend fun setVisualMode(mode: VisualMode)
}

interface HomeRepository {
    val home: Flow<HomeSnapshot>
}

interface PlatformReadinessRepository {
    suspend fun current(): PlatformReadiness
}

sealed interface StartupRecoveryState {
    data object Recovering : StartupRecoveryState
    data class Ready(
        val visualMode: VisualMode,
        val readiness: PlatformReadiness,
    ) : StartupRecoveryState
    data class Failed(val reason: String) : StartupRecoveryState
}

interface StartupRecoveryCoordinator {
    suspend fun recover(): StartupRecoveryState
}

enum class ProtectedRoute {
    SETTINGS,
    MEMBERSHIP,
    PRIVACY,
    RESET,
}

sealed interface AccessDecision {
    data object Allowed : AccessDecision
    data class ChallengeRequired(val route: ProtectedRoute) : AccessDecision
}

interface ProtectedRouteGuard {
    fun decisionFor(route: ProtectedRoute, isUnlocked: Boolean): AccessDecision
}

class DefaultProtectedRouteGuard : ProtectedRouteGuard {
    override fun decisionFor(route: ProtectedRoute, isUnlocked: Boolean): AccessDecision =
        if (isUnlocked) AccessDecision.Allowed else AccessDecision.ChallengeRequired(route)
}

data class AnalyticsEvent(
    val name: String,
    val outcome: String,
)

interface ProductAnalytics {
    fun track(event: AnalyticsEvent)
}

interface CrashReporter {
    fun record(exception: Throwable)
}

interface BillingGateway {
    val entitlement: Flow<EntitlementState>
}

interface FeatureContract {
    val id: String
    val protectedRoutes: Set<ProtectedRoute>
}
