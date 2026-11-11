package com.clarohome.app

import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.CriticalActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class IntegrationContractsTest {
    @Test
    fun unavailableProjectionRemainsUnknownInsteadOfFalseZero() {
        assertEquals(BadgeValue.Unknown, projectedBadge(null))
        assertEquals(BadgeValue.Known(0), projectedBadge(0))
    }

    @Test
    fun modeSwitchDefersForEveryIntegratedCriticalFlow() {
        assertEquals(
            setOf(
                CriticalActivity.ACTIVE_CALL,
                CriticalActivity.SOS,
                CriticalActivity.PURCHASE,
            ),
            integrationBlockers(
                hasActiveCall = true,
                sosCritical = true,
                purchaseCritical = true,
            ),
        )
    }
}
