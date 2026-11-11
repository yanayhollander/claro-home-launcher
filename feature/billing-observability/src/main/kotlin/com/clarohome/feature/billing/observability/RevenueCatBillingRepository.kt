package com.clarohome.feature.billing.observability

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import com.clarohome.core.model.EntitlementState
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.EntitlementInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PeriodType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.StoreTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.Period
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class RevenueCatBillingRepository @Inject constructor(
    @ApplicationContext context: Context,
    configuration: VendorConfiguration,
) : BillingRepository, com.clarohome.core.domain.BillingGateway {
    private val cache = context.getSharedPreferences("billing_entitlement_cache", Context.MODE_PRIVATE)
    private val purchases: Purchases = if (Purchases.isConfigured) {
        Purchases.sharedInstance
    } else {
        Purchases.configure(
            PurchasesConfiguration.Builder(context, configuration.revenueCatPublicKey)
                .diagnosticsEnabled(false)
                .automaticDeviceIdentifierCollectionEnabled(false)
                .showInAppMessagesAutomatically(false)
                .build(),
        )
    }
    private var packagesByPlanId: Map<String, Package> = emptyMap()

    private val mutablePlans = MutableStateFlow<PlansState>(PlansState.Loading)
    override val plans: StateFlow<PlansState> = mutablePlans.asStateFlow()

    private val mutableMembership = MutableStateFlow(readCachedMembership())
    override val membership: StateFlow<MembershipState> = mutableMembership.asStateFlow()

    private val mutablePurchaseResult = MutableStateFlow<PurchaseResult>(PurchaseResult.Idle)
    override val purchaseResult: StateFlow<PurchaseResult> = mutablePurchaseResult.asStateFlow()

    private val mutableEntitlement = MutableStateFlow(mutableMembership.value.toEntitlement())
    override val entitlement: StateFlow<EntitlementState> = mutableEntitlement.asStateFlow()

    init {
        purchases.updatedCustomerInfoListener = UpdatedCustomerInfoListener(::applyCustomerInfo)
    }

    override suspend fun refresh() {
        val offeringsResult = suspendCancellableCoroutine<Result<com.revenuecat.purchases.Offerings>> { continuation ->
            purchases.getOfferings(object : ReceiveOfferingsCallback {
                override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                    if (continuation.isActive) continuation.resume(Result.success(offerings))
                }

                override fun onError(error: PurchasesError) {
                    if (continuation.isActive) continuation.resume(Result.failure(RevenueCatFailure(error)))
                }
            })
        }
        offeringsResult.fold(
            onSuccess = { offerings ->
                val packages = offerings.current?.availablePackages.orEmpty()
                val mappedPlans = packages.mapNotNull(::mapPackage)
                packagesByPlanId = packages.mapNotNull { item ->
                    mapPackage(item)?.let { it.id to item }
                }.toMap()
                mutablePlans.value = if (mappedPlans.isEmpty()) {
                    PlansState.Unavailable(PlansUnavailableReason.NO_PRODUCTS)
                } else {
                    PlansState.Available(mappedPlans)
                }
            },
            onFailure = { error ->
                mutablePlans.value = PlansState.Unavailable(error.toPlansReason())
            },
        )
        refreshCustomerInfo()
    }

    override suspend fun purchase(activity: Activity, planId: String) {
        val selectedPackage = packagesByPlanId[planId]
        if (selectedPackage == null) {
            mutablePurchaseResult.value =
                PurchaseResult.Failed(PurchaseFailureReason.CONFIGURATION_MISSING)
            return
        }
        mutablePurchaseResult.value = PurchaseResult.Purchasing
        suspendCancellableCoroutine<Unit> { continuation ->
            purchases.purchase(
                PurchaseParams.Builder(activity, selectedPackage).build(),
                object : PurchaseCallback {
                    override fun onCompleted(
                        storeTransaction: StoreTransaction,
                        customerInfo: CustomerInfo,
                    ) {
                        val membership = applyCustomerInfo(customerInfo)
                        mutablePurchaseResult.value = PurchaseResult.Verified(membership)
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(error: PurchasesError, userCancelled: Boolean) {
                        mutablePurchaseResult.value = when {
                            userCancelled || error.code == PurchasesErrorCode.PurchaseCancelledError ->
                                PurchaseResult.Canceled
                            error.code == PurchasesErrorCode.PaymentPendingError ->
                                PurchaseResult.Pending
                            else -> PurchaseResult.Failed(error.toPurchaseFailure())
                        }
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                },
            )
        }
    }

    override suspend fun restore() {
        mutablePurchaseResult.value = PurchaseResult.Purchasing
        suspendCancellableCoroutine<Unit> { continuation ->
            purchases.restorePurchases(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    val membership = applyCustomerInfo(customerInfo)
                    mutablePurchaseResult.value = PurchaseResult.Verified(membership)
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(error: PurchasesError) {
                    mutablePurchaseResult.value =
                        PurchaseResult.Failed(error.toPurchaseFailure())
                    if (continuation.isActive) continuation.resume(Unit)
                }
            })
        }
    }

    override suspend fun resetPurchaseResult() {
        mutablePurchaseResult.value = PurchaseResult.Idle
    }

    private suspend fun refreshCustomerInfo() {
        suspendCancellableCoroutine<Unit> { continuation ->
            purchases.getCustomerInfo(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    applyCustomerInfo(customerInfo)
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(error: PurchasesError) {
                    if (error.code == PurchasesErrorCode.NetworkError) {
                        mutableMembership.value = MembershipState.OfflineUnverified(
                            cachedPremiumAvailable = mutableMembership.value.premiumAvailable,
                            lastVerifiedAt = cache.getLong(LAST_VERIFIED_AT, 0L)
                                .takeIf { it > 0L }
                                ?.let(Instant::ofEpochMilli),
                        )
                        mutableEntitlement.value = EntitlementState.UNKNOWN
                    }
                    if (continuation.isActive) continuation.resume(Unit)
                }
            })
        }
    }

    private fun applyCustomerInfo(customerInfo: CustomerInfo): MembershipState {
        val entitlement = customerInfo.entitlements[PREMIUM_ENTITLEMENT]
        val subscription = entitlement?.productIdentifier?.let {
            customerInfo.subscriptionsByProductIdentifier[it]
        }
        val membership = entitlement.toMembership(
            verifiedAt = customerInfo.requestDate.toInstant(),
            inGracePeriod = subscription?.gracePeriodExpiresDate
                ?.after(customerInfo.requestDate) == true,
        )
        mutableMembership.value = membership
        mutableEntitlement.value = membership.toEntitlement()
        cacheMembership(membership, customerInfo.requestDate.toInstant())
        return membership
    }

    private fun mapPackage(item: Package): StorePlan? {
        val kind = when (item.packageType) {
            PackageType.MONTHLY -> ProductKind.MONTHLY
            PackageType.ANNUAL -> ProductKind.YEARLY
            PackageType.LIFETIME -> ProductKind.LIFETIME
            else -> return null
        }
        val freePhase = item.product.defaultOption?.freePhase
        val trialDays = freePhase?.billingPeriod?.iso8601?.trialDays()
        return StorePlan(
            id = kind.name.lowercase(),
            packageId = item.identifier,
            productKind = kind,
            title = item.product.title,
            localizedPrice = item.product.price.formatted,
            localizedPeriod = item.product.period?.iso8601,
            trialEligibility = when {
                kind == ProductKind.LIFETIME -> TrialEligibility.NOT_APPLICABLE
                trialDays == REQUIRED_TRIAL_DAYS -> TrialEligibility.ELIGIBLE
                else -> TrialEligibility.INELIGIBLE
            },
            trialDays = trialDays,
            firstChargeText = if (trialDays == REQUIRED_TRIAL_DAYS) {
                "First charge follows the store trial shown at enrollment"
            } else {
                "First charge shown by Google Play at enrollment"
            },
            renewalText = if (kind == ProductKind.LIFETIME) {
                "One-time purchase; no renewal. Existing subscriptions are managed separately."
            } else {
                "Automatically renews until canceled in Google Play"
            },
        )
    }

    private fun EntitlementInfo?.toMembership(
        verifiedAt: Instant,
        inGracePeriod: Boolean,
    ): MembershipState {
        return MembershipResolver.resolve(
            snapshot = EntitlementSnapshot(
                exists = this != null,
                active = this?.isActive == true,
                willRenew = this?.willRenew == true,
                trial = this?.periodType == PeriodType.TRIAL,
                hasExpiration = this?.expirationDate != null,
                expiresAt = this?.expirationDate?.toInstant(),
                unsubscribeDetected = this?.unsubscribeDetectedAt != null,
                billingIssueDetected = this?.billingIssueDetectedAt != null,
                inGracePeriod = inGracePeriod,
            ),
            verifiedAt = verifiedAt,
        )
    }

    private fun cacheMembership(membership: MembershipState, verifiedAt: Instant) {
        cache.edit {
            putBoolean(CACHED_PREMIUM, membership.premiumAvailable)
            putLong(LAST_VERIFIED_AT, verifiedAt.toEpochMilli())
        }
    }

    private fun readCachedMembership(): MembershipState {
        if (!cache.contains(CACHED_PREMIUM)) return MembershipState.Free
        return MembershipState.OfflineUnverified(
            cachedPremiumAvailable = cache.getBoolean(CACHED_PREMIUM, false),
            lastVerifiedAt = cache.getLong(LAST_VERIFIED_AT, 0L)
                .takeIf { it > 0L }
                ?.let(Instant::ofEpochMilli),
        )
    }

    private companion object {
        const val PREMIUM_ENTITLEMENT = "premium"
        const val CACHED_PREMIUM = "cached_premium"
        const val LAST_VERIFIED_AT = "last_verified_at"
        const val REQUIRED_TRIAL_DAYS = 7
    }
}

private class RevenueCatFailure(val error: PurchasesError) : RuntimeException()

private fun String.trialDays(): Int? = runCatching {
    val period = Period.parse(this)
    if (period.years == 0 && period.months == 0) period.days else null
}.getOrNull()

private fun Throwable.toPlansReason(): PlansUnavailableReason {
    val error = (this as? RevenueCatFailure)?.error ?: return PlansUnavailableReason.ERROR
    return when (error.code) {
        PurchasesErrorCode.NetworkError -> PlansUnavailableReason.OFFLINE
        PurchasesErrorCode.StoreProblemError,
        PurchasesErrorCode.ProductNotAvailableForPurchaseError,
        -> PlansUnavailableReason.STORE_UNAVAILABLE
        PurchasesErrorCode.ConfigurationError,
        PurchasesErrorCode.InvalidCredentialsError,
        -> PlansUnavailableReason.CONFIGURATION_MISSING
        else -> PlansUnavailableReason.ERROR
    }
}

private fun PurchasesError.toPurchaseFailure(): PurchaseFailureReason = when (code) {
    PurchasesErrorCode.NetworkError -> PurchaseFailureReason.OFFLINE
    PurchasesErrorCode.StoreProblemError,
    PurchasesErrorCode.ProductNotAvailableForPurchaseError,
    -> PurchaseFailureReason.STORE_UNAVAILABLE
    PurchasesErrorCode.ProductAlreadyPurchasedError -> PurchaseFailureReason.ALREADY_OWNED
    PurchasesErrorCode.ReceiptAlreadyInUseError -> PurchaseFailureReason.ACCOUNT_MISMATCH
    PurchasesErrorCode.ConfigurationError,
    PurchasesErrorCode.InvalidCredentialsError,
    -> PurchaseFailureReason.CONFIGURATION_MISSING
    else -> PurchaseFailureReason.UNKNOWN
}
