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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
        title = if (classicShell) "Membership" else "Membership & privacy",
        modifier = Modifier.testTag("billing-screen"),
        dock = {
            ClaroSecondaryButton(
                label = if (classicShell) "Back to phone" else "Back",
                onClick = onBack,
            )
        },
    ) {
        ClaroInfoCard(
            text = "Calls, messages, SOS and your home stay free.",
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
            label = "Restore purchases",
            onClick = onRestore,
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            thickness = 2.dp,
            color = ClaroColors.Charcoal,
        )

        Text(
            text = "Optional diagnostics",
            color = ClaroColors.Ink,
            fontSize = 24.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Text(
            text = "Off by default. Change your choices anytime.",
            color = ClaroColors.Muted,
            fontSize = 18.sp,
            lineHeight = 23.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        ConsentToggleRow(
            label = "Usage analytics",
            checked = state.consent.analytics,
            onCheckedChange = {
                onConsentChange(state.consent.copy(analytics = it))
            },
        )
        ConsentToggleRow(
            label = "Crash reports",
            checked = state.consent.crashReports,
            onCheckedChange = {
                onConsentChange(state.consent.copy(crashReports = it))
            },
        )
        ClaroInfoCard(
            text = "Diagnostics never include names, phone numbers, messages, contacts, photos, dialed digits, recovery values or SOS destinations.",
            tone = ClaroBannerTone.INFO,
        )
        ClaroSecondaryButton(
            label = "Turn both off",
            onClick = onWithdrawConsent,
        )
        if (state.consent.analytics || state.consent.crashReports) {
            ClaroSecondaryButton(
                label = "Reset diagnostics settings",
                onClick = onResetDiagnostics,
            )
        }
    }

    pendingPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { pendingPlan = null },
            title = { Text("Confirm ${plan.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${plan.localizedPrice} ${plan.localizedPeriod.orEmpty()}".trim())
                    if (plan.trialDays != null) {
                        Text("${plan.trialDays}-day trial when Google Play shows eligibility.")
                    }
                    Text(plan.firstChargeText)
                    Text(plan.renewalText)
                    Text("Cancel or manage subscriptions in Google Play.")
                    Text("Core phone and safety features remain free.")
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
                    Text(if (plan.productKind == ProductKind.LIFETIME) "Buy once" else "Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPlan = null }) { Text("Cancel") }
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
                text = "Current membership",
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
            text = "Waiting for Google Play…",
            tone = ClaroBannerTone.INFO,
        )
        PurchaseResult.Pending -> ClaroInfoCard(
            text = "Purchase pending. Premium starts only after verification.",
            tone = ClaroBannerTone.WARNING,
        )
        PurchaseResult.Canceled -> ClaroInfoCard(
            text = "Purchase canceled. Nothing was charged by Claro.",
            tone = ClaroBannerTone.INFO,
        )
        is PurchaseResult.Verified -> ClaroInfoCard(
            text = "Purchase verified.",
            tone = ClaroBannerTone.SUCCESS,
        )
        is PurchaseResult.Failed -> ClaroInfoCard(
            text = "Purchase not completed: ${result.reason.readableName()}.",
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
        text = "Plans",
        color = ClaroColors.Ink,
        fontSize = 24.sp,
        lineHeight = 29.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
    when (plans) {
        PlansState.Loading -> ClaroInfoCard(
            text = "Loading localized Google Play plans…",
            tone = ClaroBannerTone.INFO,
        )
        is PlansState.Unavailable -> {
            ClaroInfoCard(
                text = plans.reason.readableName(),
                tone = ClaroBannerTone.WARNING,
            )
            if (plans.reason != PlansUnavailableReason.CONFIGURATION_MISSING) {
                ClaroSecondaryButton(
                    label = "Retry",
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
                    label = "Review ${activePlan.title}",
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
                            "${plan.trialDays ?: 7}-day trial available"
                        TrialEligibility.INELIGIBLE ->
                            "Google Play does not show a trial for this account"
                        TrialEligibility.UNKNOWN ->
                            "Google Play confirms trial eligibility at enrollment"
                        TrialEligibility.NOT_APPLICABLE ->
                            "One-time purchase · no subscription or renewal"
                    },
                    color = ClaroColors.Muted,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                )
                Text(
                    text = plan.renewalText,
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

private fun MembershipState.title(): String = when (this) {
    MembershipState.Free -> "Free"
    is MembershipState.Trial -> "Premium trial"
    is MembershipState.Active -> "Premium active"
    is MembershipState.CanceledButValid -> "Canceled; premium remains active"
    is MembershipState.Grace -> "Premium grace period"
    is MembershipState.PaymentProblem -> "Payment problem"
    is MembershipState.AccountHold -> "Account hold"
    is MembershipState.Expired -> "Premium expired"
    MembershipState.Lifetime -> "Lifetime premium"
    is MembershipState.OfflineUnverified -> "Offline; membership not verified"
}

private fun MembershipState.detail(): String = when (this) {
    MembershipState.Free -> "Caregiver conveniences are not active."
    is MembershipState.Trial -> "Trial ends ${expiresAt.displayDate()}."
    is MembershipState.Active -> "Next renewal ${renewsAt.displayDate()}."
    is MembershipState.CanceledButValid -> "Available until ${expiresAt.displayDate()}."
    is MembershipState.Grace -> "Google Play is allowing temporary premium access."
    is MembershipState.PaymentProblem -> "Manage payment in Google Play."
    is MembershipState.AccountHold -> "Premium creation is paused; core features still work."
    is MembershipState.Expired -> "Continue free, choose a plan or restore purchases."
    MembershipState.Lifetime -> "One-time purchase verified. No renewal."
    is MembershipState.OfflineUnverified ->
        if (cachedPremiumAvailable) {
            "Cached premium remains available while verification is unavailable."
        } else {
            "Connect to verify purchases. Core features still work."
        }
}

private fun Instant?.displayDate(): String {
    if (this == null) return "when shown by Google Play"
    return DateTimeFormatter.ofPattern("d MMM uuuu")
        .withZone(ZoneId.systemDefault())
        .format(this)
}

private fun PlansUnavailableReason.readableName(): String = when (this) {
    PlansUnavailableReason.CONFIGURATION_MISSING ->
        "Store products are not configured. Purchases are unavailable; core features remain free."
    PlansUnavailableReason.OFFLINE -> "Internet is needed to load or buy plans."
    PlansUnavailableReason.STORE_UNAVAILABLE -> "Google Play is unavailable."
    PlansUnavailableReason.NO_PRODUCTS -> "No approved plans are available."
    PlansUnavailableReason.ERROR -> "Plans could not be loaded."
}

private fun PurchaseFailureReason.readableName(): String =
    name.lowercase().replace('_', ' ')

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
