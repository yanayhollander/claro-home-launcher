package com.clarohome.feature.onboarding.settings

import com.clarohome.core.domain.FeatureContract
import com.clarohome.core.domain.ProtectedRoute

object OnboardingSettingsContract : FeatureContract {
    override val id = "onboarding-settings"
    override val protectedRoutes = setOf(
        ProtectedRoute.SETTINGS,
        ProtectedRoute.PRIVACY,
        ProtectedRoute.RESET,
    )
}
