package com.clarohome.feature.billing.observability

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.clarohome.core.design.ClaroDimensions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    onResetDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    var pendingPlan by remember { mutableStateOf<StorePlan?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ClaroDimensions.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (classicShell) "Caregiver membership" else "Membership and privacy")
        Text("Claro Home, Phone, Messages and SOS always remain free.")
        MembershipCard(state.membership)
        PurchaseResultCard(state.purchaseResult)
        PlansCard(
            plans = state.plans,
            membership = state.membership,
            onRefresh = onRefresh,
            onSelectPlan = { pendingPlan = it },
        )
        Button(
            onClick = onRestore,
            modifier = Modifier
                .fillMaxWidth()
                .height(ClaroDimensions.PrimaryTarget),
        ) {
            Text("Restore purchases")
        }
        HorizontalDivider()
        Text("Optional diagnostics")
        Text("Off by default. Purchases still use Google Play and RevenueCat when configured.")
        ConsentRow(
            label = "Share product analytics",
            checked = state.consent.analytics,
            onCheckedChange = {
                onConsentChange(state.consent.copy(analytics = it))
            },
        )
        ConsentRow(
            label = "Share crash reports",
            checked = state.consent.crashReports,
            onCheckedChange = {
                onConsentChange(state.consent.copy(crashReports = it))
            },
        )
        Text(
            "Diagnostics never include names, phone numbers, messages, contacts, photos, " +
                "dialed digits, recovery values or SOS destinations.",
        )
        Button(
            onClick = onWithdrawConsent,
            modifier = Modifier
                .fillMaxWidth()
                .height(ClaroDimensions.PrimaryTarget),
        ) {
            Text("Withdraw diagnostics consent")
        }
        Button(
            onClick = onResetDiagnostics,
            modifier = Modifier
                .fillMaxWidth()
                .height(ClaroDimensions.PrimaryTarget),
        ) {
            Text("Reset diagnostics settings")
        }
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(ClaroDimensions.PrimaryTarget),
        ) {
            Text(if (classicShell) "Back to phone" else "Back home")
        }
        Spacer(Modifier.height(16.dp))
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
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Current membership")
            Text(membership.title())
            Text(membership.detail())
        }
    }
}

@Composable
private fun PurchaseResultCard(result: PurchaseResult) {
    if (result == PurchaseResult.Idle) return
    Card(Modifier.fillMaxWidth()) {
        Text(
            text = when (result) {
                PurchaseResult.Idle -> ""
                PurchaseResult.Purchasing -> "Waiting for Google Play"
                PurchaseResult.Pending -> "Purchase pending. Premium starts only after verification."
                PurchaseResult.Canceled -> "Purchase canceled. Nothing was charged by Claro."
                is PurchaseResult.Verified -> "Purchase verified."
                is PurchaseResult.Failed -> "Purchase not completed: ${result.reason.readableName()}."
            },
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun PlansCard(
    plans: PlansState,
    membership: MembershipState,
    onRefresh: () -> Unit,
    onSelectPlan: (StorePlan) -> Unit,
) {
    Text("Plans")
    when (plans) {
        PlansState.Loading -> Text("Loading localized Google Play plans...")
        is PlansState.Unavailable -> {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(plans.reason.readableName())
                    Text("Continue free is always available.")
                    Button(onClick = onRefresh) { Text("Retry") }
                }
            }
        }
        is PlansState.Available -> plans.plans
            .filterNot {
                membership == MembershipState.Lifetime &&
                    it.productKind == ProductKind.LIFETIME
            }
            .forEach { plan ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(plan.title)
                    Text(plan.localizedPrice)
                    plan.localizedPeriod?.let { Text(it) }
                    when (plan.trialEligibility) {
                        TrialEligibility.ELIGIBLE ->
                            Text("${plan.trialDays ?: 7}-day trial available")
                        TrialEligibility.INELIGIBLE ->
                            Text("Google Play does not show a trial for this account")
                        TrialEligibility.UNKNOWN ->
                            Text("Google Play confirms trial eligibility at enrollment")
                        TrialEligibility.NOT_APPLICABLE ->
                            Text("One-time purchase; no subscription or renewal")
                    }
                    Text(plan.renewalText)
                    Button(
                        onClick = { onSelectPlan(plan) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ClaroDimensions.PrimaryTarget),
                    ) {
                        Text("Choose ${plan.title}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsentRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ClaroDimensions.PrimaryTarget),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.widthIn(max = 260.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
        "Store products are not configured. Fake plans are available in development."
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
