package com.clarohome.core.domain

import com.clarohome.core.model.HomeTileKind
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.OnboardingStep
import com.clarohome.core.model.ReadinessCapability
import com.clarohome.core.model.TextSize
import com.clarohome.core.model.VisualMode
import java.math.BigDecimal
import java.util.Currency

enum class EventOutcome {
    STARTED, COMPLETED, CANCELED, FAILED, READY, NEEDS_ATTENTION, UNAVAILABLE,
    SUBMITTED, UNCERTAIN, PENDING, VERIFIED,
}

enum class EventTask {
    OPEN_PHONE, OPEN_MESSAGES, OPEN_SOS, OPEN_MEMBERSHIP, RESTORE_PURCHASES,
    CALL_PLACEMENT, MESSAGE_SUBMISSION, MESSAGE_DELIVERY, SOS_HANDOFF,
    EXTERNAL_APP, EMERGENCY_DIALER, PURCHASE, STARTUP_RECOVERY, WIDGET_SETUP,
}

enum class TelemetryScreen {
    HOME, ONBOARDING, RECOVERY, PHONE, MESSAGES, SETTINGS, APPS, MENU,
    MEMBERSHIP, MEDIA, SOS, HOME_TILES, WIDGET_PICKER,
    KEYPAD, RECENT_CALLS, MISSED_CALLS, CONTACTS, CONTACT_DETAIL, CALL_CONFIRMATION,
    INCOMING_CALL, ACTIVE_CALL, CALL_ENDED, MISSED_CALL, CALL_UNANSWERED, CALL_FAILED, EXTERNAL_HANDOFF,
    MESSAGE_INBOX, MESSAGE_CONVERSATION, MESSAGE_RECIPIENTS, MESSAGE_COMPOSE,
    MESSAGE_CONFIRMATION, MESSAGE_SENDING, MESSAGE_FAILED,
    SOS_COUNTDOWN, SOS_FAILED, SOS_SETTINGS, CAMERA, GALLERY,
    PHONE_STYLE, DISPLAY_SETTINGS, ACCESS_SETTINGS, PERMISSIONS, CALL_AUDIO, MENU_ORDER, RESET,
}

data class HomeTelemetryContext(
    val tileCount: Int,
    val contactTileCount: Int,
    val appTileCount: Int,
    val shortcutTileCount: Int,
    val widgetTileCount: Int,
) {
    companion object {
        fun from(slots: List<HomeTile?>): HomeTelemetryContext {
            val tiles = slots.filterNotNull()
            val contacts = tiles.count { it.kind == HomeTileKind.CONTACT }
            val apps = tiles.count { it.kind == HomeTileKind.INSTALLED_APP }
            val widgets = tiles.count { it.kind == HomeTileKind.WIDGET }
            return HomeTelemetryContext(tiles.size, contacts, apps, tiles.size - contacts - apps - widgets, widgets)
        }
    }
}

enum class HomeAction { SET, REMOVE, REORDER, VISIBILITY }
enum class ConversionKind { TRIAL, SUBSCRIPTION, PURCHASE }
enum class TelemetryProduct { MONTHLY, YEARLY, LIFETIME }
enum class DiagnosticOperation { STARTUP, CALL_PLACEMENT, MESSAGE_SUBMISSION, MESSAGE_DELIVERY, BILLING, SDK_INITIALIZATION }

data class TelemetryMoney(val amount: BigDecimal, val currency: Currency) {
    init {
        require(amount.signum() >= 0) { "Telemetry amount must not be negative" }
    }
}

sealed interface ClaroEvent {
    data object SessionStarted : ClaroEvent
    data class ScreenView(val screen: TelemetryScreen, val step: OnboardingStep? = null) : ClaroEvent
    data class SetupStepResult(val step: OnboardingStep, val outcome: EventOutcome) : ClaroEvent
    data object SetupCompleted : ClaroEvent
    data class RoleReadiness(
        val capability: ReadinessCapability,
        val outcome: EventOutcome,
    ) : ClaroEvent
    data class TaskFlowResult(
        val task: EventTask,
        val outcome: EventOutcome,
        val testMode: Boolean = false,
    ) : ClaroEvent
    data class MembershipScreenView(
        val outcome: EventOutcome = EventOutcome.COMPLETED,
    ) : ClaroEvent
    data class AccessibilityPresetSelected(val preset: TextSize) : ClaroEvent
    data class VisualModeSelected(val mode: VisualMode) : ClaroEvent
    data class HomeChanged(
        val action: HomeAction,
        val kind: HomeTileKind?,
        val outcome: EventOutcome = EventOutcome.COMPLETED,
    ) : ClaroEvent
    data class HomeTileOpened(val kind: HomeTileKind, val position: Int) : ClaroEvent {
        init {
            require(position >= 0) { "Tile position must not be negative" }
        }
    }
    data class CheckoutStarted(val product: TelemetryProduct) : ClaroEvent
    data class Conversion(
        val kind: ConversionKind,
        val product: TelemetryProduct,
        val money: TelemetryMoney? = null,
    ) : ClaroEvent {
        init {
            require(kind != ConversionKind.PURCHASE || (money != null && money.amount.signum() > 0)) {
                "A purchase conversion requires a verified positive charge"
            }
            require(kind == ConversionKind.PURCHASE || money == null) {
                "Only purchase conversions carry revenue"
            }
        }
    }
}

interface ProductAnalytics {
    fun track(event: ClaroEvent)
}

interface CrashReporter {
    fun record(exception: Throwable, operation: DiagnosticOperation = DiagnosticOperation.STARTUP)
}

object NoOpProductAnalytics : ProductAnalytics {
    override fun track(event: ClaroEvent) = Unit
}
