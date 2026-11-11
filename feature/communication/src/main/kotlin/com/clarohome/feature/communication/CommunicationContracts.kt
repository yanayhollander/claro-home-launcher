package com.clarohome.feature.communication

import com.clarohome.core.domain.FeatureContract
import com.clarohome.core.domain.ProtectedRoute

object PhoneContactsContract : FeatureContract {
    override val id = "phone-contacts"
    override val protectedRoutes = setOf(ProtectedRoute.SETTINGS)
}

object MessagingContract : FeatureContract {
    override val id = "messaging"
    override val protectedRoutes = emptySet<ProtectedRoute>()
}
