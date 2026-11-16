package com.clarohome.app

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import com.clarohome.core.model.ActiveCall
import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.CriticalActivity
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind
import com.clarohome.core.model.CapabilityStatus
import com.clarohome.core.model.ReadinessCapability
import com.clarohome.feature.communication.CommunicationUiState
import com.clarohome.feature.communication.MessagingUiState

internal fun Intent.isHomeRequest(): Boolean =
    action == Intent.ACTION_MAIN && hasCategory(Intent.CATEGORY_HOME)

internal fun Context.defaultAppReadiness(): Map<ReadinessCapability, CapabilityStatus> {
    val manager = getSystemService(RoleManager::class.java)
    return mapOf(
        ReadinessCapability.HOME to RoleManager.ROLE_HOME,
        ReadinessCapability.PHONE to RoleManager.ROLE_DIALER,
        ReadinessCapability.MESSAGES to RoleManager.ROLE_SMS,
    ).mapValues { (_, role) ->
        when {
            manager == null -> CapabilityStatus.NEEDS_ATTENTION
            !manager.isRoleAvailable(role) -> CapabilityStatus.UNAVAILABLE
            manager.isRoleHeld(role) -> CapabilityStatus.READY
            else -> CapabilityStatus.NEEDS_ATTENTION
        }
    }
}

internal fun integrationBlockers(
    hasActiveCall: Boolean,
    sosCritical: Boolean,
    purchaseCritical: Boolean,
): Set<CriticalActivity> = buildSet {
    if (hasActiveCall) add(CriticalActivity.ACTIVE_CALL)
    if (sosCritical) add(CriticalActivity.SOS)
    if (purchaseCritical) add(CriticalActivity.PURCHASE)
}

internal fun projectedBadge(count: Int?): BadgeValue =
    count?.let(BadgeValue::Known) ?: BadgeValue.Unknown

internal fun HomeSnapshot.withCommunicationProjections(
    communication: CommunicationUiState,
    messaging: MessagingUiState,
): HomeSnapshot {
    val messageCount = if (messaging.qualification.canRead) {
        messaging.conversations.sumOf { it.unreadCount }
    } else {
        null
    }
    fun HomeTile.withProjection(): HomeTile = when (kind) {
        HomeTileKind.PHONE -> copy(badge = projectedBadge(communication.missedIncomingCount))
        HomeTileKind.MESSAGES -> copy(badge = projectedBadge(messageCount))
        else -> this
    }
    return copy(
        tiles = tiles.map { it.withProjection() },
        homeSlots = homeSlots.map { it?.withProjection() },
        activeCall = communication.call
            ?.takeIf { communication.hasActiveCall }
            ?.let { call ->
                ActiveCall(
                    displayName = call.identity.displayName
                        ?: call.identity.number
                        ?: "Private number",
                    stateLabel = call.phase.name.lowercase().replace('_', ' '),
                )
            },
    )
}
