package com.clarohome.feature.billing.observability

import android.app.Activity
import com.clarohome.core.model.EntitlementState
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

data class DiagnosticsConsent(
    val analytics: Boolean = false,
    val crashReports: Boolean = false,
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
}

enum class ClaroEventName(val wireName: String) {
    SETUP_STEP_RESULT("setup_step_result"),
    ROLE_READINESS("role_readiness"),
    TASK_FLOW_RESULT("task_flow_result"),
    MEMBERSHIP_SCREEN_VIEW("membership_screen_view"),
    ACCESSIBILITY_PRESET_SELECTED("accessibility_preset_selected"),
}

enum class EventOutcome(val wireValue: String) {
    STARTED("started"),
    COMPLETED("completed"),
    CANCELED("canceled"),
    FAILED("failed"),
    READY("ready"),
    NEEDS_ATTENTION("needs_attention"),
    UNAVAILABLE("unavailable"),
}

enum class EventStep(val wireValue: String) {
    WELCOME("welcome"),
    VISUAL_MODE("visual_mode"),
    FAVORITES("favorites"),
    SOS("sos"),
    PERMISSIONS("permissions"),
    HANDOVER("handover"),
}

enum class EventCapability(val wireValue: String) {
    HOME("home"),
    PHONE("phone"),
    MESSAGES("messages"),
    CONTACTS("contacts"),
    NOTIFICATIONS("notifications"),
    MEDIA("media"),
}

enum class EventTask(val wireValue: String) {
    OPEN_PHONE("open_phone"),
    OPEN_MESSAGES("open_messages"),
    OPEN_SOS("open_sos"),
    OPEN_MEMBERSHIP("open_membership"),
    RESTORE_PURCHASES("restore_purchases"),
}

enum class AccessibilityPreset(val wireValue: String) {
    STANDARD("standard"),
    LARGE("large"),
    HUGE("huge"),
    HIGH_CONTRAST("high_contrast"),
}

sealed interface ClaroEvent {
    val name: ClaroEventName
    val outcome: EventOutcome

    data class SetupStepResult(
        val step: EventStep,
        override val outcome: EventOutcome,
    ) : ClaroEvent {
        override val name = ClaroEventName.SETUP_STEP_RESULT
    }

    data class RoleReadiness(
        val capability: EventCapability,
        override val outcome: EventOutcome,
    ) : ClaroEvent {
        override val name = ClaroEventName.ROLE_READINESS
    }

    data class TaskFlowResult(
        val task: EventTask,
        override val outcome: EventOutcome,
    ) : ClaroEvent {
        override val name = ClaroEventName.TASK_FLOW_RESULT
    }

    data class MembershipScreenView(
        override val outcome: EventOutcome = EventOutcome.COMPLETED,
    ) : ClaroEvent {
        override val name = ClaroEventName.MEMBERSHIP_SCREEN_VIEW
    }

    data class AccessibilityPresetSelected(
        val preset: AccessibilityPreset,
        override val outcome: EventOutcome = EventOutcome.COMPLETED,
    ) : ClaroEvent {
        override val name = ClaroEventName.ACCESSIBILITY_PRESET_SELECTED
    }
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
