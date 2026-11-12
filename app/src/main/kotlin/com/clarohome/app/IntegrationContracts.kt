package com.clarohome.app

import com.clarohome.core.model.ActiveCall
import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.CriticalActivity
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind
import com.clarohome.feature.communication.CommunicationUiState
import com.clarohome.feature.communication.MessagingUiState

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
