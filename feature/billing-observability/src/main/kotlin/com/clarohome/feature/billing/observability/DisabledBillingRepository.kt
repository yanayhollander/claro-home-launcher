package com.clarohome.feature.billing.observability

import android.app.Activity
import com.clarohome.core.model.EntitlementState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DisabledBillingRepository : BillingRepository {
    private val mutablePlans = MutableStateFlow<PlansState>(
        PlansState.Unavailable(PlansUnavailableReason.CONFIGURATION_MISSING),
    )
    override val plans: StateFlow<PlansState> = mutablePlans.asStateFlow()

    private val mutableMembership = MutableStateFlow<MembershipState>(MembershipState.Free)
    override val membership: StateFlow<MembershipState> = mutableMembership.asStateFlow()

    private val mutablePurchaseResult = MutableStateFlow<PurchaseResult>(PurchaseResult.Idle)
    override val purchaseResult: StateFlow<PurchaseResult> = mutablePurchaseResult.asStateFlow()

    private val mutableEntitlement = MutableStateFlow(EntitlementState.FREE)
    override val entitlement: StateFlow<EntitlementState> = mutableEntitlement.asStateFlow()

    override suspend fun refresh() = Unit

    override suspend fun purchase(activity: Activity, planId: String) {
        mutablePurchaseResult.value =
            PurchaseResult.Failed(PurchaseFailureReason.CONFIGURATION_MISSING)
    }

    override suspend fun restore() {
        mutablePurchaseResult.value =
            PurchaseResult.Failed(PurchaseFailureReason.CONFIGURATION_MISSING)
    }

    override suspend fun resetPurchaseResult() {
        mutablePurchaseResult.value = PurchaseResult.Idle
    }
}

internal fun MembershipState.toEntitlement(): EntitlementState = when {
    this is MembershipState.OfflineUnverified -> EntitlementState.UNKNOWN
    premiumAvailable -> EntitlementState.PREMIUM
    else -> EntitlementState.FREE
}
