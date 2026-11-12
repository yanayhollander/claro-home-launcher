package com.clarohome.core.model

enum class VisualMode {
    TOUCH,
    CLASSIC,
}

enum class AppLanguage {
    ENGLISH,
    SPANISH,
    HEBREW,
}

enum class TextSize {
    STANDARD,
    LARGE,
    HUGE,
}

enum class SettingsAccessMode {
    DATE_QUESTION,
    CAREGIVER_PIN,
}

enum class OnboardingStep {
    WELCOME,
    PHONE_STYLE,
    LANGUAGE_AND_DISPLAY,
    SETTINGS_ACCESS,
    PEOPLE_AND_SPEED_DIAL,
    SOS_CONTACT,
    ROLE_READINESS,
    HANDOVER,
    COMPLETE,
}

enum class ReadinessCapability {
    HOME,
    PHONE,
    MESSAGES,
    CONTACTS,
    NOTIFICATIONS,
    MEDIA,
}

enum class HandoverCheck {
    OUTGOING_CALL,
    INCOMING_CALL,
    SMS_SEND,
    SMS_RECEIVE,
    DEFAULT_SIM,
    RINGTONE,
    HOME_RETURN,
    SOS_TEST,
}

enum class CriticalActivity {
    ACTIVE_CALL,
    SOS,
    PURCHASE,
    DESTRUCTIVE_CONFIRMATION,
    EXTERNAL_SYSTEM_UI,
}

data class SecretVerifier(
    val saltBase64: String,
    val hashBase64: String,
    val iterations: Int,
)

data class CooldownState(
    val wallClockDeadlineMillis: Long = 0,
    val elapsedRealtimeDeadlineMillis: Long = 0,
    val bootId: String = "",
)

data class AccessProtection(
    val mode: SettingsAccessMode = SettingsAccessMode.DATE_QUESTION,
    val pinVerifier: SecretVerifier? = null,
    val recoveryVerifier: SecretVerifier? = null,
    val failedUnlockAttempts: Int = 0,
    val failedRecoveryAttempts: Int = 0,
    val unlockCooldown: CooldownState = CooldownState(),
    val recoveryCooldown: CooldownState = CooldownState(),
)

data class OnboardingProgress(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val completedSteps: Set<OnboardingStep> = emptySet(),
    val favoriteSlotsAssigned: Int = 0,
    val speedDialOneAssigned: Boolean = false,
    val speedDialTwoAssigned: Boolean = false,
    val sosRecipientAgreed: Boolean = false,
    val readiness: Map<ReadinessCapability, CapabilityStatus> =
        ReadinessCapability.entries.associateWith { CapabilityStatus.NEEDS_ATTENTION },
    val handoverChecks: Set<HandoverCheck> = emptySet(),
)

data class ClaroPreferences(
    val visualMode: VisualMode = VisualMode.TOUCH,
    val pendingVisualMode: VisualMode? = null,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val textSize: TextSize = TextSize.STANDARD,
    val highContrast: Boolean = false,
    val onboarding: OnboardingProgress = OnboardingProgress(),
    val accessProtection: AccessProtection = AccessProtection(),
    val hapticFeedback: Boolean = true,
)

const val HOME_SLOT_COUNT = 12

data class HomeTile(
    val id: String,
    val label: String,
    val accent: TileAccent,
    val kind: HomeTileKind,
    val badge: BadgeValue? = null,
    val packageName: String? = null,
    val isCore: Boolean = true,
    val phoneNumber: String? = null,
    val photoUri: String? = null,
)

enum class HomeTileKind {
    PHONE,
    MESSAGES,
    CAMERA,
    GALLERY,
    SOS,
    APPS,
    INSTALLED_APP,
    CONTACT,
    ALARM,
}

enum class TileAccent {
    GREEN,
    BLUE,
    ORANGE,
    RED,
    VIOLET,
}

sealed interface BadgeValue {
    data class Known(val count: Int) : BadgeValue {
        init {
            require(count >= 0)
        }
    }

    data object Unknown : BadgeValue
}

data class BatteryStatus(
    val percentage: Int,
    val isCharging: Boolean,
) {
    init {
        require(percentage in 0..100)
    }
}

enum class CellularAvailability {
    AVAILABLE,
    NO_SIM,
    NO_SERVICE,
    AIRPLANE_MODE,
    UNKNOWN,
}

data class CellularStatus(
    val availability: CellularAvailability,
    val signalLevel: Int? = null,
) {
    init {
        require(signalLevel == null || signalLevel in 0..4)
    }
}

enum class HomeRoleState {
    GRANTED,
    AVAILABLE,
    UNAVAILABLE,
    UNKNOWN,
}

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isAllowed: Boolean,
    val isFavorite: Boolean = false,
    val isOnHome: Boolean = false,
    val isAvailable: Boolean = true,
)

data class ActiveCall(
    val displayName: String,
    val stateLabel: String,
)

data class HomeSnapshot(
    val greeting: String,
    val dayAndDate: String,
    val time: String,
    val battery: BatteryStatus,
    val cellular: CellularStatus,
    val homeSlots: List<HomeTile?>,
    val tiles: List<HomeTile>,
    val installedApps: List<InstalledApp>,
    val homeRole: HomeRoleState,
    val activeCall: ActiveCall? = null,
    val menuOrder: List<String> = emptyList(),
) {
    val homeTiles: List<HomeTile> get() = homeSlots.filterNotNull()
}

enum class CapabilityStatus {
    READY,
    NEEDS_ATTENTION,
    UNAVAILABLE,
}

data class PlatformReadiness(
    val home: CapabilityStatus,
    val phone: CapabilityStatus,
    val messages: CapabilityStatus,
    val contacts: CapabilityStatus = CapabilityStatus.NEEDS_ATTENTION,
    val notifications: CapabilityStatus = CapabilityStatus.NEEDS_ATTENTION,
    val media: CapabilityStatus = CapabilityStatus.NEEDS_ATTENTION,
)

enum class EntitlementState {
    FREE,
    PREMIUM,
    UNKNOWN,
}
