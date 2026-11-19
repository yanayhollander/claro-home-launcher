package com.clarohome.feature.billing.observability

import com.clarohome.core.domain.ClaroEvent
import com.clarohome.core.domain.EventTask
import com.clarohome.core.domain.EventOutcome
import java.util.Locale

internal fun Enum<*>.wireValue(): String = name.lowercase(Locale.ROOT)

internal data class TelemetryEnvelope(
    val name: String,
    val properties: Map<String, String> = emptyMap(),
)

internal fun ClaroEvent.envelope(): TelemetryEnvelope = when (this) {
    ClaroEvent.SessionStarted -> TelemetryEnvelope("session_started")
    is ClaroEvent.ScreenView -> TelemetryEnvelope("screen_view", mapOf("screen" to screen.wireValue()))
    is ClaroEvent.SetupStepResult -> TelemetryEnvelope(
        "setup_step_result", mapOf("step" to step.wireValue(), "outcome" to outcome.wireValue()),
    )
    ClaroEvent.SetupCompleted -> TelemetryEnvelope("setup_completed")
    is ClaroEvent.RoleReadiness -> TelemetryEnvelope(
        "role_readiness", mapOf("capability" to capability.wireValue(), "outcome" to outcome.wireValue()),
    )
    is ClaroEvent.TaskFlowResult -> TelemetryEnvelope(
        "task_flow_result", buildMap {
            put("task", task.wireValue())
            put("outcome", outcome.wireValue())
            if (task == EventTask.SOS_HANDOFF) put("test_mode", testMode.toString())
        },
    )
    is ClaroEvent.MembershipScreenView ->
        TelemetryEnvelope("membership_screen_view", mapOf("outcome" to outcome.wireValue()))
    is ClaroEvent.AccessibilityPresetSelected ->
        TelemetryEnvelope("accessibility_preset_selected", mapOf("preset" to preset.wireValue()))
    is ClaroEvent.VisualModeSelected ->
        TelemetryEnvelope("visual_mode_selected", mapOf("mode" to mode.wireValue()))
    is ClaroEvent.HomeChanged -> TelemetryEnvelope(
        "home_changed",
        buildMap {
            put("action", action.wireValue())
            put("outcome", outcome.wireValue())
            kind?.let { put("kind", it.wireValue()) }
        },
    )
    is ClaroEvent.CheckoutStarted ->
        TelemetryEnvelope("checkout_started", mapOf("product" to product.wireValue()))
    is ClaroEvent.Conversion -> TelemetryEnvelope(
        "conversion",
        buildMap {
            put("kind", kind.wireValue())
            put("product", product.wireValue())
            money?.let {
                put("amount", it.amount.toPlainString())
                put("currency", it.currency.currencyCode)
            }
        },
    )
}

internal fun ClaroEvent.primaryDestination(): AnalyticsDestination = when {
    this is ClaroEvent.RoleReadiness -> AnalyticsDestination.FIREBASE
    this is ClaroEvent.TaskFlowResult && task == EventTask.STARTUP_RECOVERY -> AnalyticsDestination.FIREBASE
    else -> AnalyticsDestination.MIXPANEL
}

internal fun ClaroEvent.isMarketingConversion(): Boolean = when (this) {
    ClaroEvent.SessionStarted, ClaroEvent.SetupCompleted,
    is ClaroEvent.CheckoutStarted, is ClaroEvent.Conversion -> true
    is ClaroEvent.MembershipScreenView -> outcome == EventOutcome.COMPLETED
    else -> false
}
