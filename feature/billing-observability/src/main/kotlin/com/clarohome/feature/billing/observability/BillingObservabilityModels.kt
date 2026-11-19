package com.clarohome.feature.billing.observability

import android.app.Activity
import com.clarohome.core.model.EntitlementState
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

data class DiagnosticsConsent(
    val analytics: Boolean = false,
    val crashReports: Boolean = false,
    val marketing: Boolean = false,
)

interface ConsentController {
    val consent: StateFlow<DiagnosticsConsent>
    suspend fun update(consent: DiagnosticsConsent)
    suspend fun withdrawAll()
    suspend fun reset()
}

enum class AnalyticsDestination {
    MIXPANEL,
    FIREBASE,
    META,
}

enum class ProductKind {
    MONTHLY,
    YEARLY,
    LIFETIME,
}

enum class TrialEligibility {
    ELIGIBLE,
    INELIGIBLE,
    UNKNOWN,
    NOT_APPLICABLE,
}

data class StorePlan(
    val id: String,
    val packageId: String,
    val productKind: ProductKind,
    val title: String,
    val localizedPrice: String,
    val localizedPeriod: String?,
    val trialEligibility: TrialEligibility,
    val trialDays: Int?,
    val firstChargeText: String,
    val renewalText: String,
)

sealed interface PlansState {
    data object Loading : PlansState
    data class Available(val plans: List<StorePlan>) : PlansState
    data class Unavailable(val reason: PlansUnavailableReason) : PlansState
}

enum class PlansUnavailableReason {
    CONFIGURATION_MISSING,
    OFFLINE,
    STORE_UNAVAILABLE,
    NO_PRODUCTS,
    ERROR,
}

sealed interface PurchaseResult {
    data object Idle : PurchaseResult
    data object Purchasing : PurchaseResult
    data object Pending : PurchaseResult
    data object Canceled : PurchaseResult
    data class Verified(val membership: MembershipState) : PurchaseResult
    data class Failed(val reason: PurchaseFailureReason) : PurchaseResult
}

enum class PurchaseFailureReason {
    OFFLINE,
    STORE_UNAVAILABLE,
    ALREADY_OWNED,
    ACCOUNT_MISMATCH,
    CONFIGURATION_MISSING,
    UNKNOWN,
}

sealed interface MembershipState {
    val premiumAvailable: Boolean

    data object Free : MembershipState {
        override val premiumAvailable = false
    }

    data class Trial(
        val expiresAt: Instant?,
    ) : MembershipState {
        override val premiumAvailable = true
    }

    data class Active(
        val renewsAt: Instant?,
    ) : MembershipState {
        override val premiumAvailable = true
    }

    data class CanceledButValid(
        val expiresAt: Instant?,
    ) : MembershipState {
        override val premiumAvailable = true
    }

    data class Grace(
        val expiresAt: Instant?,
    ) : MembershipState {
        override val premiumAvailable = true
    }

    data class PaymentProblem(
        val expiresAt: Instant?,
    ) : MembershipState {
        override val premiumAvailable = true
    }

    data class AccountHold(
        val lastVerifiedAt: Instant?,
    ) : MembershipState {
        override val premiumAvailable = false
    }

    data class Expired(
        val expiredAt: Instant?,
    ) : MembershipState {
        override val premiumAvailable = false
    }

    data object Lifetime : MembershipState {
        override val premiumAvailable = true
    }

    data class OfflineUnverified(
        val cachedPremiumAvailable: Boolean,
        val lastVerifiedAt: Instant?,
    ) : MembershipState {
        override val premiumAvailable = cachedPremiumAvailable
    }
}

interface BillingRepository {
    val plans: StateFlow<PlansState>
    val membership: StateFlow<MembershipState>
    val purchaseResult: StateFlow<PurchaseResult>
    val entitlement: StateFlow<EntitlementState>

    suspend fun refresh()
    suspend fun purchase(activity: Activity, planId: String)
    suspend fun restore()
    suspend fun resetPurchaseResult()
}

data class BillingObservabilityUiState(
    val consent: DiagnosticsConsent = DiagnosticsConsent(),
    val plans: PlansState = PlansState.Loading,
    val membership: MembershipState = MembershipState.Free,
    val purchaseResult: PurchaseResult = PurchaseResult.Idle,
)
