package com.clarohome.feature.billing.observability

import android.app.Activity
import com.clarohome.core.model.EntitlementState
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeBillingRepository(
    initialMembership: MembershipState = MembershipState.Free,
) : BillingRepository {
    private val mutablePlans = MutableStateFlow<PlansState>(
        PlansState.Available(
            listOf(
                StorePlan(
                    id = "monthly",
                    packageId = "\$rc_monthly",
                    productKind = ProductKind.MONTHLY,
                    title = "Monthly",
                    localizedPrice = "Price shown by Google Play",
                    localizedPeriod = "per month",
                    trialEligibility = TrialEligibility.UNKNOWN,
                    trialDays = 7,
                    firstChargeText = "First charge shown by Google Play at enrollment",
                    renewalText = "Renews monthly until canceled in Google Play",
                ),
                StorePlan(
                    id = "yearly",
                    packageId = "\$rc_annual",
                    productKind = ProductKind.YEARLY,
                    title = "Yearly",
                    localizedPrice = "Price shown by Google Play",
                    localizedPeriod = "per year",
                    trialEligibility = TrialEligibility.UNKNOWN,
                    trialDays = 7,
                    firstChargeText = "First charge shown by Google Play at enrollment",
                    renewalText = "Renews yearly until canceled in Google Play",
                ),
                StorePlan(
                    id = "lifetime",
                    packageId = "\$rc_lifetime",
                    productKind = ProductKind.LIFETIME,
                    title = "Lifetime",
                    localizedPrice = "Price shown by Google Play",
                    localizedPeriod = null,
                    trialEligibility = TrialEligibility.NOT_APPLICABLE,
                    trialDays = null,
                    firstChargeText = "One-time purchase",
                    renewalText = "No renewal. Active subscriptions are managed separately.",
                ),
            ),
        ),
    )
    override val plans: StateFlow<PlansState> = mutablePlans.asStateFlow()

    private val mutableMembership = MutableStateFlow(initialMembership)
    override val membership: StateFlow<MembershipState> = mutableMembership.asStateFlow()

    private val mutablePurchaseResult = MutableStateFlow<PurchaseResult>(PurchaseResult.Idle)
    override val purchaseResult: StateFlow<PurchaseResult> = mutablePurchaseResult.asStateFlow()

    private val mutableEntitlement = MutableStateFlow(initialMembership.toEntitlement())
    override val entitlement: StateFlow<EntitlementState> = mutableEntitlement.asStateFlow()

    override suspend fun refresh() = Unit

    override suspend fun purchase(activity: Activity, planId: String) {
        mutablePurchaseResult.value = PurchaseResult.Purchasing
        val membership = when (planId) {
            "monthly", "yearly" -> MembershipState.Trial(Instant.now().plusSeconds(7 * 24 * 60 * 60))
            "lifetime" -> MembershipState.Lifetime
            else -> {
                mutablePurchaseResult.value =
                    PurchaseResult.Failed(PurchaseFailureReason.UNKNOWN)
                return
            }
        }
        mutableMembership.value = membership
        mutableEntitlement.value = membership.toEntitlement()
        mutablePurchaseResult.value = PurchaseResult.Verified(membership)
    }

    override suspend fun restore() {
        mutablePurchaseResult.value =
            PurchaseResult.Verified(mutableMembership.value)
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
