package com.clarohome.feature.billing.observability

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarohome.core.design.ClaroBannerTone
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroInfoCard
import com.clarohome.core.design.ClaroPrimaryButton
import com.clarohome.core.design.ClaroScreenScaffold
import com.clarohome.core.design.ClaroSecondaryButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PlanUnselectedBg = Color(0xFFF5F7FA)
private val PlanUnselectedBorder = Color(0xFFC4CEDE)
private val PlanSelectedBg = Color(0xFFEDF3FF)

@Composable
fun BillingObservabilityScreen(
    state: BillingObservabilityUiState,
    classicShell: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPurchase: (Activity, String) -> Unit,
    onRestore: () -> Unit,
    onConsentChange: (DiagnosticsConsent) -> Unit,
    onWithdrawConsent: () -> Unit,
    onResetDiagnostics: () -> Unit = {},
) {
    val context = LocalContext.current
    var selectedPlanId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPlan by remember { mutableStateOf<StorePlan?>(null) }

    ClaroScreenScaffold(
        title = stringResource(
            if (classicShell) R.string.bill_title_classic else R.string.bill_title_touch,
        ),
        modifier = Modifier.testTag("billing-screen"),
        dock = {
            ClaroSecondaryButton(
                label = stringResource(
                    if (classicShell) R.string.bill_back_to_phone else R.string.bill_back,
                ),
                onClick = onBack,
            )
        },
    ) {
        ClaroInfoCard(
            text = stringResource(R.string.bill_core_features_free_banner),
            tone = ClaroBannerTone.INFO,
        )

        MembershipCard(state.membership)
        PurchaseResultBanner(state.purchaseResult)

        PlansSection(
            plans = state.plans,
            membership = state.membership,
            selectedPlanId = selectedPlanId,
            onSelectPlan = { selectedPlanId = it.id },
            onReviewPlan = { pendingPlan = it },
            onRefresh = onRefresh,
        )

        ClaroSecondaryButton(
            label = stringResource(R.string.bill_restore_purchases),
            onClick = onRestore,
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            thickness = 2.dp,
            color = ClaroColors.Charcoal,
        )

        Text(
            text = stringResource(R.string.bill_diagnostics_header),
            color = ClaroColors.Ink,
            fontSize = 24.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Text(
            text = stringResource(R.string.bill_diagnostics_subtitle),
            color = ClaroColors.Muted,
            fontSize = 18.sp,
            lineHeight = 23.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        ConsentToggleRow(
            label = stringResource(R.string.bill_consent_analytics),
            checked = state.consent.analytics,
            onCheckedChange = {
                onConsentChange(state.consent.copy(analytics = it))
            },
        )
        ConsentToggleRow(
            label = stringResource(R.string.bill_consent_crash_reports),
            checked = state.consent.crashReports,
            onCheckedChange = {
                onConsentChange(state.consent.copy(crashReports = it))
            },
        )
        ClaroInfoCard(
            text = stringResource(R.string.bill_diagnostics_privacy_disclosure),
            tone = ClaroBannerTone.INFO,
        )
        ClaroSecondaryButton(
            label = stringResource(R.string.bill_withdraw_consent),
            onClick = onWithdrawConsent,
        )
        if (state.consent.analytics || state.consent.crashReports) {
            ClaroSecondaryButton(
                label = stringResource(R.string.bill_reset_diagnostics),
                onClick = onResetDiagnostics,
            )
        }
    }

    pendingPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { pendingPlan = null },
            title = { Text(stringResource(R.string.bill_dialog_confirm_plan_title, plan.title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${plan.localizedPrice} ${plan.localizedPeriod.orEmpty()}".trim())
                    if (plan.trialDays != null) {
                        Text(stringResource(R.string.bill_dialog_trial_eligibility, plan.trialDays))
                    }
                    Text(plan.localizedFirstChargeText())
                    Text(plan.localizedRenewalText())
                    Text(stringResource(R.string.bill_dialog_manage_in_play))
                    Text(stringResource(R.string.bill_dialog_core_remain_free))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val activity = context.findActivity()
                        if (activity != null) onPurchase(activity, plan.id)
                        pendingPlan = null
                    },
                ) {
                    Text(
                        stringResource(
                            if (plan.productKind == ProductKind.LIFETIME) {
                                R.string.bill_dialog_buy_once
                            } else {
                                R.string.bill_dialog_continue
                            },
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPlan = null }) {
                    Text(stringResource(R.string.bill_dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun MembershipCard(membership: MembershipState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(3.dp),
        color = PlanUnselectedBg,
        border = BorderStroke(1.dp, PlanUnselectedBorder),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.bill_current_membership_label),
                color = ClaroColors.Muted,
                fontSize = 17.sp,
                lineHeight = 21.sp,
            )
            Text(
                text = membership.title(),
                color = ClaroColors.Ink,
                fontSize = 24.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = membership.detail(),
                color = ClaroColors.Ink,
                fontSize = 19.sp,
                lineHeight = 24.sp,
            )
        }
    }
}

@Composable
private fun PurchaseResultBanner(result: PurchaseResult) {
    when (result) {
        PurchaseResult.Idle -> Unit
        PurchaseResult.Purchasing -> ClaroInfoCard(
            text = stringResource(R.string.bill_purchase_waiting),
            tone = ClaroBannerTone.INFO,
        )
        PurchaseResult.Pending -> ClaroInfoCard(
            text = stringResource(R.string.bill_purchase_pending),
            tone = ClaroBannerTone.WARNING,
        )
        PurchaseResult.Canceled -> ClaroInfoCard(
            text = stringResource(R.string.bill_purchase_canceled),
            tone = ClaroBannerTone.INFO,
        )
        is PurchaseResult.Verified -> ClaroInfoCard(
            text = stringResource(R.string.bill_purchase_verified),
            tone = ClaroBannerTone.SUCCESS,
        )
        is PurchaseResult.Failed -> ClaroInfoCard(
            text = stringResource(R.string.bill_purchase_failed, result.reason.readableName()),
            tone = ClaroBannerTone.ERROR,
        )
    }
}

@Composable
private fun PlansSection(
    plans: PlansState,
    membership: MembershipState,
    selectedPlanId: String?,
    onSelectPlan: (StorePlan) -> Unit,
    onReviewPlan: (StorePlan) -> Unit,
    onRefresh: () -> Unit,
) {
    Text(
        text = stringResource(R.string.bill_plans_header),
        color = ClaroColors.Ink,
        fontSize = 24.sp,
        lineHeight = 29.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
    when (plans) {
        PlansState.Loading -> ClaroInfoCard(
            text = stringResource(R.string.bill_plans_loading),
            tone = ClaroBannerTone.INFO,
        )
        is PlansState.Unavailable -> {
            ClaroInfoCard(
                text = plans.reason.readableName(),
                tone = ClaroBannerTone.WARNING,
            )
            if (plans.reason != PlansUnavailableReason.CONFIGURATION_MISSING) {
                ClaroSecondaryButton(
                    label = stringResource(R.string.bill_retry),
                    onClick = onRefresh,
                )
            }
        }
        is PlansState.Available -> {
            val visiblePlans = plans.plans.filterNot {
                membership == MembershipState.Lifetime &&
                    it.productKind == ProductKind.LIFETIME
            }
            val activePlan = visiblePlans.firstOrNull { it.id == selectedPlanId }
                ?: visiblePlans.firstOrNull()

            visiblePlans.forEach { plan ->
                val selected = activePlan?.id == plan.id
                PlanOptionCard(
                    plan = plan,
                    selected = selected,
                    onClick = { onSelectPlan(plan) },
                )
            }

            if (activePlan != null) {
                ClaroPrimaryButton(
                    label = stringResource(R.string.bill_review_plan, activePlan.title),
                    onClick = { onReviewPlan(activePlan) },
                    containerColor = ClaroColors.Blue,
                )
            }
        }
    }
}

@Composable
private fun PlanOptionCard(
    plan: StorePlan,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(3.dp),
        color = if (selected) PlanSelectedBg else PlanUnselectedBg,
        border = BorderStroke(
            width = 2.dp,
            color = if (selected) ClaroColors.Blue else PlanUnselectedBorder,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(ClaroColors.Blue),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = plan.title,
                        color = ClaroColors.Ink,
                        fontSize = 25.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = listOfNotNull(plan.localizedPrice, plan.localizedPeriod)
                            .joinToString(" "),
                        color = ClaroColors.Ink,
                        style = TextStyle(
                            fontSize = 21.sp,
                            lineHeight = 25.sp,
                            fontWeight = FontWeight.SemiBold,
                            textDirection = TextDirection.Ltr,
                        ),
                    )
                }
                Text(
                    text = when (plan.trialEligibility) {
                        TrialEligibility.ELIGIBLE ->
                            stringResource(R.string.bill_trial_eligible, plan.trialDays ?: 7)
                        TrialEligibility.INELIGIBLE ->
                            stringResource(R.string.bill_trial_ineligible)
                        TrialEligibility.UNKNOWN ->
                            stringResource(R.string.bill_trial_unknown)
                        TrialEligibility.NOT_APPLICABLE ->
                            stringResource(R.string.bill_trial_not_applicable)
                    },
                    color = ClaroColors.Muted,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                )
                Text(
                    text = plan.localizedRenewalText(),
                    color = ClaroColors.Muted,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun ConsentToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .background(ClaroColors.Surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = ClaroColors.Ink,
            fontSize = 22.sp,
            lineHeight = 27.sp,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun StorePlan.localizedFirstChargeText(): String = when (firstChargeText) {
    DEFAULT_FIRST_CHARGE_AFTER_TRIAL ->
        stringResource(R.string.bill_plan_first_charge_after_trial)
    DEFAULT_FIRST_CHARGE_AT_ENROLLMENT ->
        stringResource(R.string.bill_plan_first_charge_at_enrollment)
    else -> firstChargeText.ifBlank {
        if (trialEligibility == TrialEligibility.ELIGIBLE) {
            stringResource(R.string.bill_plan_first_charge_after_trial)
        } else {
            stringResource(R.string.bill_plan_first_charge_at_enrollment)
        }
    }
}

@Composable
private fun StorePlan.localizedRenewalText(): String = when (renewalText) {
    DEFAULT_RENEWAL_LIFETIME ->
        stringResource(R.string.bill_plan_renewal_lifetime)
    DEFAULT_RENEWAL_SUBSCRIPTION ->
        stringResource(R.string.bill_plan_renewal_subscription)
    else -> renewalText.ifBlank {
        if (productKind == ProductKind.LIFETIME) {
            stringResource(R.string.bill_plan_renewal_lifetime)
        } else {
            stringResource(R.string.bill_plan_renewal_subscription)
        }
    }
}

@Composable
private fun MembershipState.title(): String = when (this) {
    MembershipState.Free -> stringResource(R.string.bill_membership_free_title)
    is MembershipState.Trial -> stringResource(R.string.bill_membership_trial_title)
    is MembershipState.Active -> stringResource(R.string.bill_membership_active_title)
    is MembershipState.CanceledButValid -> stringResource(R.string.bill_membership_canceled_title)
    is MembershipState.Grace -> stringResource(R.string.bill_membership_grace_title)
    is MembershipState.PaymentProblem -> stringResource(R.string.bill_membership_payment_problem_title)
    is MembershipState.AccountHold -> stringResource(R.string.bill_membership_account_hold_title)
    is MembershipState.Expired -> stringResource(R.string.bill_membership_expired_title)
    MembershipState.Lifetime -> stringResource(R.string.bill_membership_lifetime_title)
    is MembershipState.OfflineUnverified -> stringResource(R.string.bill_membership_offline_title)
}

@Composable
private fun MembershipState.detail(): String = when (this) {
    MembershipState.Free -> stringResource(R.string.bill_membership_free_detail)
    is MembershipState.Trial -> expiresAt?.let {
        stringResource(R.string.bill_membership_trial_detail, it.displayDate())
    } ?: stringResource(R.string.bill_membership_trial_detail_store_date)
    is MembershipState.Active -> renewsAt?.let {
        stringResource(R.string.bill_membership_active_detail, it.displayDate())
    } ?: stringResource(R.string.bill_membership_active_detail_store_date)
    is MembershipState.CanceledButValid -> expiresAt?.let {
        stringResource(R.string.bill_membership_canceled_detail, it.displayDate())
    } ?: stringResource(R.string.bill_membership_canceled_detail_store_date)
    is MembershipState.Grace -> stringResource(R.string.bill_membership_grace_detail)
    is MembershipState.PaymentProblem -> stringResource(R.string.bill_membership_payment_problem_detail)
    is MembershipState.AccountHold -> stringResource(R.string.bill_membership_account_hold_detail)
    is MembershipState.Expired -> stringResource(R.string.bill_membership_expired_detail)
    MembershipState.Lifetime -> stringResource(R.string.bill_membership_lifetime_detail)
    is MembershipState.OfflineUnverified ->
        if (cachedPremiumAvailable) {
            stringResource(R.string.bill_membership_offline_cached_detail)
        } else {
            stringResource(R.string.bill_membership_offline_unverified_detail)
        }
}

@Composable
private fun Instant.displayDate(): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    return DateTimeFormatter.ofPattern("d MMM uuuu", locale)
        .withZone(ZoneId.systemDefault())
        .format(this)
}

@Composable
private fun PlansUnavailableReason.readableName(): String = when (this) {
    PlansUnavailableReason.CONFIGURATION_MISSING ->
        stringResource(R.string.bill_plans_unavailable_config)
    PlansUnavailableReason.OFFLINE ->
        stringResource(R.string.bill_plans_unavailable_offline)
    PlansUnavailableReason.STORE_UNAVAILABLE ->
        stringResource(R.string.bill_plans_unavailable_store)
    PlansUnavailableReason.NO_PRODUCTS ->
        stringResource(R.string.bill_plans_unavailable_no_products)
    PlansUnavailableReason.ERROR ->
        stringResource(R.string.bill_plans_unavailable_error)
}

@Composable
private fun PurchaseFailureReason.readableName(): String = when (this) {
    PurchaseFailureReason.OFFLINE ->
        stringResource(R.string.bill_failure_offline)
    PurchaseFailureReason.STORE_UNAVAILABLE ->
        stringResource(R.string.bill_failure_store_unavailable)
    PurchaseFailureReason.ALREADY_OWNED ->
        stringResource(R.string.bill_failure_already_owned)
    PurchaseFailureReason.ACCOUNT_MISMATCH ->
        stringResource(R.string.bill_failure_account_mismatch)
    PurchaseFailureReason.CONFIGURATION_MISSING ->
        stringResource(R.string.bill_failure_configuration_missing)
    PurchaseFailureReason.UNKNOWN ->
        stringResource(R.string.bill_failure_unknown)
}

private const val DEFAULT_FIRST_CHARGE_AFTER_TRIAL =
    "First charge follows the store trial shown at enrollment"
private const val DEFAULT_FIRST_CHARGE_AT_ENROLLMENT =
    "First charge shown by Google Play at enrollment"
private const val DEFAULT_RENEWAL_LIFETIME =
    "One-time purchase; no renewal. Existing subscriptions are managed separately."
private const val DEFAULT_RENEWAL_SUBSCRIPTION =
    "Automatically renews until canceled in Google Play"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
