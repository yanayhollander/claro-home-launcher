package com.clarohome.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultProtectedRouteGuardTest {
    private val guard = DefaultProtectedRouteGuard()

    @Test
    fun lockedRouteRequiresChallenge() {
        assertThat(guard.decisionFor(ProtectedRoute.SETTINGS, isUnlocked = false))
            .isEqualTo(AccessDecision.ChallengeRequired(ProtectedRoute.SETTINGS))
    }

    @Test
    fun unlockedRouteIsAllowed() {
        assertThat(guard.decisionFor(ProtectedRoute.SETTINGS, isUnlocked = true))
            .isEqualTo(AccessDecision.Allowed)
    }
}
