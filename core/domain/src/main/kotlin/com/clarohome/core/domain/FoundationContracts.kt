package com.clarohome.core.domain

import com.clarohome.core.model.EntitlementState
import com.clarohome.core.model.ClaroPreferences
import com.clarohome.core.model.CriticalActivity
import com.clarohome.core.model.BatteryStatus
import com.clarohome.core.model.CellularStatus
import com.clarohome.core.model.HomeRoleState
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.InstalledApp
import com.clarohome.core.model.FavoritePerson
import com.clarohome.core.model.PlatformReadiness
import com.clarohome.core.model.VisualMode
import kotlinx.coroutines.flow.Flow

interface VisualModeRepository {
    val visualMode: Flow<VisualMode>
    suspend fun setVisualMode(mode: VisualMode)
}

interface ClaroPreferencesRepository {
    val preferences: Flow<ClaroPreferences>
    suspend fun save(preferences: ClaroPreferences)
}

interface CriticalActivityRepository {
    val activeActivities: Flow<Set<CriticalActivity>>
}

interface HomeRepository {
    val home: Flow<HomeSnapshot>
    suspend fun setInstalledApps(apps: List<InstalledApp>)
    suspend fun setFavorite(slot: Int, person: FavoritePerson)
    suspend fun clearFavorite(slot: Int)
    suspend fun reset()
    suspend fun setAppAllowed(packageName: String, allowed: Boolean)
    suspend fun setAppOnHome(packageName: String, onHome: Boolean)
    suspend fun setHomeRoleState(state: HomeRoleState)
    suspend fun setStatus(
        dayAndDate: String,
        time: String,
        battery: BatteryStatus,
        cellular: CellularStatus,
    )
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
    PHONE_STYLE,
    DISPLAY,
    LANGUAGE,
    READINESS,
    PEOPLE_AND_SAFETY,
    CONTACT_MANAGEMENT,
    SPEED_DIAL,
    SAFETY_CONFIGURATION,
    APP_CONFIGURATION,
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
