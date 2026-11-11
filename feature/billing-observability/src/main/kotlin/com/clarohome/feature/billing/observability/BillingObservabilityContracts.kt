package com.clarohome.feature.billing.observability

import com.clarohome.core.domain.FeatureContract
import com.clarohome.core.domain.ProtectedRoute

object BillingContract : FeatureContract {
    override val id = "billing"
    override val protectedRoutes = setOf(ProtectedRoute.MEMBERSHIP)
}

object ObservabilityContract : FeatureContract {
    override val id = "observability"
    override val protectedRoutes = setOf(ProtectedRoute.PRIVACY)
}
