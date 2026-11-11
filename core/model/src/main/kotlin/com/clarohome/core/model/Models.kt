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
)

data class FavoritePerson(
    val id: String,
    val displayName: String,
)

data class HomeTile(
    val id: String,
    val label: String,
    val accent: TileAccent,
)

enum class TileAccent {
    GREEN,
    BLUE,
    ORANGE,
    RED,
    VIOLET,
}

data class HomeSnapshot(
    val greeting: String,
    val favorites: List<FavoritePerson>,
    val tiles: List<HomeTile>,
)

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
