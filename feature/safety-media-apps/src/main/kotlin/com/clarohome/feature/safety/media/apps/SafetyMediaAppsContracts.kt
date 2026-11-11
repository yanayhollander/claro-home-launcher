package com.clarohome.feature.safety.media.apps

import com.clarohome.core.domain.FeatureContract
import com.clarohome.core.domain.ProtectedRoute

object SafetyContract : FeatureContract {
    override val id = "safety"
    override val protectedRoutes = setOf(ProtectedRoute.SETTINGS)
}

object MediaContract : FeatureContract {
    override val id = "media"
    override val protectedRoutes = emptySet<ProtectedRoute>()
}

object AppsContract : FeatureContract {
    override val id = "apps"
    override val protectedRoutes = setOf(ProtectedRoute.SETTINGS)
}
