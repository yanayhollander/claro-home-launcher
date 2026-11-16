package com.clarohome.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarohome.core.data.deviceAppLanguage
import com.clarohome.core.design.withClaroLanguage
import com.clarohome.core.domain.ClaroPreferencesRepository
import com.clarohome.core.domain.CriticalActivityRepository
import com.clarohome.core.domain.EmergencyNumberDetector
import com.clarohome.core.domain.ExternalAppLaunchResult
import com.clarohome.core.domain.ExternalAppLauncher
import com.clarohome.core.domain.HomeRepository
import com.clarohome.core.domain.HomeTileVisibilityResult
import com.clarohome.core.domain.NativeEmergencyDialerGateway
import com.clarohome.core.domain.PhoneCallGateway
import com.clarohome.core.domain.StartupRecoveryCoordinator
import com.clarohome.core.domain.StartupRecoveryState
import com.clarohome.core.model.AppLanguage
import com.clarohome.core.model.CapabilityStatus
import com.clarohome.core.model.ClaroPreferences
import com.clarohome.core.model.CriticalActivity
import com.clarohome.core.model.HandoverCheck
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind
import com.clarohome.core.model.OnboardingStep
import com.clarohome.core.model.ReadinessCapability
import com.clarohome.core.model.TextSize
import com.clarohome.core.model.VisualMode
import com.clarohome.feature.billing.observability.BillingObservabilityUiState
import com.clarohome.feature.billing.observability.BillingRepository
import com.clarohome.feature.billing.observability.ClaroEvent
import com.clarohome.feature.billing.observability.ConsentAwareObservability
import com.clarohome.feature.billing.observability.ConsentController
import com.clarohome.feature.billing.observability.DiagnosticsConsent
import com.clarohome.feature.launcher.home.HomeRoleController
import com.clarohome.feature.launcher.home.InstalledAppCatalog
import com.clarohome.feature.launcher.home.LauncherStatusReader
import com.clarohome.feature.onboarding.settings.AccessResult
import com.clarohome.feature.onboarding.settings.ModeSwitchResult
import com.clarohome.feature.onboarding.settings.OnboardingSettingsManager
import com.clarohome.feature.safety.media.apps.PersistedSosState
import com.clarohome.feature.safety.media.apps.SosCallOutcome
import com.clarohome.feature.safety.media.apps.SosCoordinator
import com.clarohome.feature.safety.media.apps.SosState
import com.clarohome.feature.safety.media.apps.SosReadiness
import com.clarohome.feature.safety.media.apps.TrustedContact
import com.clarohome.feature.safety.media.apps.normalizeSosDestination
import com.clarohome.feature.communication.PhoneAssignmentStore
import com.clarohome.feature.communication.PhoneAssignment
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val startup: StartupRecoveryState = StartupRecoveryState.Recovering,
    val preferences: ClaroPreferences = ClaroPreferences(),
    val home: HomeSnapshot? = null,
    val activeActivities: Set<CriticalActivity> = emptySet(),
    val message: String? = null,
    val billingObservability: BillingObservabilityUiState = BillingObservabilityUiState(),
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val savedStateHandle: SavedStateHandle,
    private val preferencesRepository: ClaroPreferencesRepository,
    private val criticalActivityRepository: CriticalActivityRepository,
    private val recoveryCoordinator: StartupRecoveryCoordinator,
    private val settingsManager: OnboardingSettingsManager,
    private val homeRepository: HomeRepository,
    private val homeRoleController: HomeRoleController,
    private val installedAppCatalog: InstalledAppCatalog,
    private val launcherStatusReader: LauncherStatusReader,
    phoneCallGateway: PhoneCallGateway,
    private val emergencyNumberDetector: EmergencyNumberDetector,
    private val phoneAssignments: PhoneAssignmentStore,
    private val emergencyDialerGateway: NativeEmergencyDialerGateway,
    private val externalAppLauncher: ExternalAppLauncher,
    private val billingRepository: BillingRepository,
    private val consentController: ConsentController,
    private val observability: ConsentAwareObservability,
) : ViewModel() {
    private val startup = MutableStateFlow<StartupRecoveryState>(StartupRecoveryState.Recovering)
    private val message = MutableStateFlow<String?>(null)
    private val sosCoordinator = SosCoordinator(phoneCallGateway, emergencyNumberDetector)
    private val mutableSosState = MutableStateFlow<SosState>(SosState.Unset())
    private val sosPreferences = appContext.getSharedPreferences("claro_sos_contact", Context.MODE_PRIVATE)

    val sosState: StateFlow<SosState> = mutableSosState
    private val billingObservability = combine(
        consentController.consent,
        billingRepository.plans,
        billingRepository.membership,
        billingRepository.purchaseResult,
    ) { consent, plans, membership, purchaseResult ->
        BillingObservabilityUiState(consent, plans, membership, purchaseResult)
    }

    val uiState: StateFlow<MainUiState> = combine(
        startup,
        preferencesRepository.preferences,
        homeRepository.home,
        criticalActivityRepository.activeActivities,
        combine(message, billingObservability) { statusMessage, billing ->
            statusMessage to billing
        },
    ) { recovery, preferences, home, activeActivities, status ->
        MainUiState(
            startup = recovery,
            preferences = preferences,
            home = home,
            activeActivities = activeActivities,
            message = status.first,
            billingObservability = status.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    init {
        restoreSos()
        viewModelScope.launch {
            syncLauncherState()
            startup.value = recoveryCoordinator.recover()
        }
        viewModelScope.launch { billingRepository.refresh() }
        viewModelScope.launch {
            homeRepository.home
                .map { it.homeSlots.count { slot -> slot != null } }
                .distinctUntilChanged()
                .collect { settingsManager.updateHomeTileCount(it) }
        }
    }

    fun advanceOnboarding(next: OnboardingStep, onCompleted: () -> Unit = {}) {
        viewModelScope.launch {
            val applied = settingsManager.advanceOnboarding(next, appContext.defaultAppReadiness())
            if (!applied) {
                message.value = localizedContext().getString(R.string.app_default_apps_required)
            } else if (next == OnboardingStep.COMPLETE) {
                onCompleted()
            }
        }
    }

    fun chooseVisualMode(mode: VisualMode) = launch { settingsManager.chooseVisualMode(mode) }

    fun saveLanguageDisplay(
        language: AppLanguage,
        size: TextSize,
        highContrast: Boolean,
        hapticFeedback: Boolean? = null,
    ) = launch {
        settingsManager.setLanguageAndDisplay(language, size, highContrast, hapticFeedback)
    }

    fun setHapticFeedback(enabled: Boolean) = launch { settingsManager.setHapticFeedback(enabled) }
    fun setCallAudio(enabled: Boolean, maximumSpeakerVolume: Boolean) =
        launch { settingsManager.setCallAudio(enabled, maximumSpeakerVolume) }

    fun useDateQuestion() = launch { settingsManager.useDateQuestion() }

    fun useDateQuestionForSettings(onSuccess: () -> Unit) {
        viewModelScope.launch {
            settingsManager.useDateQuestion()
            message.value = null
            onSuccess()
        }
    }

    fun configurePin(pin: String, confirmation: String, recovery: String) {
        viewModelScope.launch {
            when (val result = settingsManager.configurePin(pin, confirmation, recovery)) {
                AccessResult.Unlocked -> {
                    message.value = null
                    settingsManager.advanceOnboarding(OnboardingStep.PEOPLE_AND_SPEED_DIAL)
                }
                is AccessResult.Invalid -> message.value = localizeAccessInvalidReason(result.reason)
                else -> Unit
            }
        }
    }

    fun configurePinForSettings(
        pin: String,
        confirmation: String,
        recovery: String,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            when (val result = settingsManager.configurePin(pin, confirmation, recovery)) {
                AccessResult.Unlocked -> {
                    message.value = null
                    onSuccess()
                }
                is AccessResult.Invalid -> message.value = localizeAccessInvalidReason(result.reason)
                else -> Unit
            }
        }
    }

    fun updateAssignments(
        favorites: Int,
        one: Boolean,
        two: Boolean,
        sosAgreed: Boolean,
    ) = launch { settingsManager.updateSetupAssignments(favorites, one, two, sosAgreed) }

    fun setReadiness(capability: ReadinessCapability, status: CapabilityStatus) =
        launch { settingsManager.setReadiness(capability, status) }

    fun setHandover(check: HandoverCheck, checked: Boolean) =
        launch { settingsManager.setHandoverCheck(check, checked) }

    fun unlock(input: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val context = localizedContext()
            when (val result = settingsManager.unlock(input)) {
                AccessResult.Unlocked -> {
                    message.value = null
                    onSuccess()
                }
                AccessResult.Incorrect ->
                    message.value = context.getString(R.string.app_unlock_incorrect)
                is AccessResult.Cooldown ->
                    message.value = context.getString(
                        R.string.app_unlock_cooldown,
                        result.remainingSeconds.toInt(),
                    )
                is AccessResult.Invalid -> message.value = localizeAccessInvalidReason(result.reason)
            }
        }
    }

    fun recoverPin(secret: String, pin: String, confirmation: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val context = localizedContext()
            when (val result = settingsManager.recoverPin(secret, pin, confirmation)) {
                AccessResult.Unlocked -> {
                    message.value = context.getString(R.string.app_pin_changed)
                    onSuccess()
                }
                AccessResult.Incorrect ->
                    message.value = context.getString(R.string.app_recovery_secret_incorrect)
                is AccessResult.Cooldown ->
                    message.value = context.getString(
                        R.string.app_recovery_cooldown,
                        result.remainingSeconds.toInt(),
                    )
                is AccessResult.Invalid -> message.value = localizeAccessInvalidReason(result.reason)
            }
        }
    }

    fun isSettingsUnlocked(): Boolean = settingsManager.isUnlocked()

    fun lockSettings() = settingsManager.lock()

    fun applyVisualMode(
        mode: VisualMode,
        runtimeBlockers: Set<CriticalActivity>,
        onApplied: () -> Unit,
    ) {
        viewModelScope.launch {
            when (val result = settingsManager.requestModeSwitch(
                mode,
                uiState.value.activeActivities + runtimeBlockers,
            )) {
                is ModeSwitchResult.Applied -> {
                    message.value = null
                    onApplied()
                }
                is ModeSwitchResult.Deferred -> {
                    val context = localizedContext()
                    val blockers = result.blockers.joinToString { it.readableName(context) }
                    message.value = context.getString(R.string.app_phone_style_deferred, blockers)
                }
            }
        }
    }

    fun cancelDeferredModeSwitch() = launch {
        settingsManager.cancelDeferredModeSwitch()
        message.value = null
    }

    fun retryDeferredModeSwitch(
        runtimeBlockers: Set<CriticalActivity>,
        onApplied: (VisualMode) -> Unit,
    ) {
        viewModelScope.launch {
            val result = settingsManager.retryDeferredModeSwitch(
                uiState.value.activeActivities + runtimeBlockers,
            )
            if (result is ModeSwitchResult.Applied) {
                message.value = null
                onApplied(result.mode)
            }
        }
    }

    fun clearMessage() {
        message.value = null
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun refreshLauncherState() {
        viewModelScope.launch {
            syncLauncherState()
        }
    }

    fun refreshLauncherStatus() = launch { syncLauncherStatus() }

    private suspend fun syncLauncherState() {
        homeRepository.setHomeRoleState(homeRoleController.currentState())
        homeRepository.setInstalledApps(installedAppCatalog.discoverEligibleApps())
        syncLauncherStatus()
    }

    private suspend fun syncLauncherStatus() {
        launcherStatusReader.read().let { status ->
            homeRepository.setStatus(
                dayAndDate = status.dayAndDate,
                time = status.time,
                battery = status.battery,
                cellular = status.cellular,
            )
        }
    }

    fun homeRoleActionIntent(): Intent =
        homeRoleController.requestIntent() ?: homeRoleController.repairIntent()

    fun appLaunchIntent(packageName: String): Intent? =
        installedAppCatalog.launchIntent(packageName)

    fun setAppAllowed(packageName: String, allowed: Boolean) {
        viewModelScope.launch {
            homeRepository.setAppAllowed(packageName, allowed)
        }
    }

    fun setHomeSlot(slot: Int, tile: HomeTile) {
        viewModelScope.launch {
            homeRepository.setHomeSlot(slot, tile)
        }
    }

    fun setHomeTileVisibility(kind: HomeTileKind, visible: Boolean) {
        viewModelScope.launch {
            message.value = when (homeRepository.setHomeTileVisibility(kind, visible)) {
                HomeTileVisibilityResult.APPLIED -> null
                HomeTileVisibilityResult.HOME_FULL ->
                    localizedContext().getString(R.string.app_home_tiles_full)
                HomeTileVisibilityResult.APP_UNAVAILABLE ->
                    localizedContext().getString(R.string.app_missing_or_disabled_notice)
            }
        }
    }

    fun clearHomeSlot(slot: Int) {
        viewModelScope.launch {
            homeRepository.clearHomeSlot(slot)
        }
    }

    fun setMenuOrder(order: List<String>) {
        viewModelScope.launch {
            homeRepository.setMenuOrder(order)
        }
    }

    fun setSpeedDialConfigured(slot: String, configured: Boolean) {
        viewModelScope.launch {
            val onboarding = uiState.value.preferences.onboarding
            settingsManager.updateSetupAssignments(
                favoriteSlotsAssigned = onboarding.favoriteSlotsAssigned,
                speedDialOneAssigned = if (slot == "1") configured else onboarding.speedDialOneAssigned,
                speedDialTwoAssigned = if (slot == "2") configured else onboarding.speedDialTwoAssigned,
                sosRecipientAgreed = onboarding.sosRecipientAgreed,
            )
        }
    }

    fun resetClaroLocalData(onResetComplete: () -> Unit) {
        viewModelScope.launch {
            homeRepository.reset()
            sosCoordinator.configure(null)
            sosPreferences.edit { clear() }
            phoneAssignments.clear("sos")
            publishSos()
            consentController.reset()
            settingsManager.resetAll(language = deviceAppLanguage())
            refreshLauncherState()
            message.value = null
            onResetComplete()
        }
    }

    fun setAppOnHome(packageName: String, onHome: Boolean) {
        viewModelScope.launch {
            message.value = when (homeRepository.setInstalledAppHomeTile(packageName, onHome)) {
                HomeTileVisibilityResult.APPLIED -> null
                HomeTileVisibilityResult.HOME_FULL -> localizedContext().getString(R.string.app_home_tiles_full)
                HomeTileVisibilityResult.APP_UNAVAILABLE ->
                    localizedContext().getString(R.string.app_missing_or_disabled_notice)
            }
        }
    }

    fun configureSos(
        name: String,
        destination: String,
        consented: Boolean,
        contactLookupKey: String? = null,
        photoUri: String? = null,
    ): SosReadiness {
        val number = normalizeSosDestination(destination)
            ?: return SosReadiness.Blocked("Enter a valid SOS phone number.")
        if (emergencyNumberDetector.isEmergencyNumber(number)) {
            return SosReadiness.Blocked("Emergency numbers cannot be saved as trusted contacts.")
        }
        if (!consented) return SosReadiness.RecipientConsentMissing
        val contact = TrustedContact(
            contactId = contactLookupKey ?: "manual-number",
            displayName = name.trim().ifBlank { localizedContext().getString(R.string.app_sos_custom_number) },
            destination = number,
            recipientConsented = true,
            photoUri = photoUri,
        )
        if (!saveSosContact(contact)) return SosReadiness.Blocked("SOS contact could not be saved.")
        sosCoordinator.configure(contact)
        publishSos()
        viewModelScope.launch {
            if (contactLookupKey != null) {
                phoneAssignments.save(PhoneAssignment("sos", contactLookupKey, number, contact.displayName))
            } else {
                phoneAssignments.clear("sos")
            }
            settingsManager.setSosRecipientAgreed(true)
        }
        return SosReadiness.Ready
    }

    private fun saveSosContact(contact: TrustedContact): Boolean {
        val saved = sosPreferences.edit()
            .putString("name", contact.displayName)
            .putString("number", contact.destination)
            .putString("contact_id", contact.contactId)
            .putString("photo_uri", contact.photoUri)
            .putBoolean("consented", contact.recipientConsented)
            .commit()
        if (!saved) Log.e("ClaroSOS", "SOS contact could not be persisted.")
        return saved
    }

    fun startSos(testMode: Boolean, nowElapsedMillis: Long) {
        if (!sosCoordinator.start(nowElapsedMillis, testMode)) return
        publishSos()
        viewModelScope.launch {
            while (sosCoordinator.state is SosState.CountingDown) {
                kotlinx.coroutines.delay(200)
                sosCoordinator.advance(android.os.SystemClock.elapsedRealtime())
                publishSos()
            }
        }
    }

    fun cancelSos() {
        sosCoordinator.cancel()
        publishSos()
    }

    fun interruptSos() {
        sosCoordinator.interrupt()
        publishSos()
    }

    fun updateSosCallOutcome(outcome: SosCallOutcome) {
        sosCoordinator.onCallOutcome(outcome)
        publishSos()
    }

    fun openEmergencyDialer(): Boolean = emergencyDialerGateway.openEmergencyDialer()

    fun launchAllowedApp(packageName: String): ExternalAppLaunchResult =
        externalAppLauncher.launch(packageName)

    fun membershipOpened() {
        observability.track(ClaroEvent.MembershipScreenView())
    }

    fun refreshPlans() {
        viewModelScope.launch { billingRepository.refresh() }
    }

    fun purchase(activity: Activity, planId: String) {
        viewModelScope.launch { billingRepository.purchase(activity, planId) }
    }

    fun restorePurchases() {
        viewModelScope.launch { billingRepository.restore() }
    }

    fun updateConsent(consent: DiagnosticsConsent) {
        viewModelScope.launch { consentController.update(consent) }
    }

    fun withdrawConsent() {
        viewModelScope.launch { consentController.withdrawAll() }
    }

    fun resetDiagnostics() {
        viewModelScope.launch { consentController.reset() }
    }

    fun sosRemainingSeconds(nowElapsedMillis: Long): Int =
        sosCoordinator.remainingSeconds(nowElapsedMillis)

    private fun publishSos() {
        mutableSosState.value = sosCoordinator.state
        val snapshot = sosCoordinator.snapshot()
        savedStateHandle["sos_name"] = snapshot.contact?.displayName
        savedStateHandle["sos_number"] = snapshot.contact?.destination
        savedStateHandle["sos_contact_id"] = snapshot.contact?.contactId
        savedStateHandle["sos_photo_uri"] = snapshot.contact?.photoUri
        savedStateHandle["sos_consent"] = snapshot.contact?.recipientConsented ?: false
        savedStateHandle["sos_deadline"] = snapshot.countdownDeadlineElapsedMillis
        savedStateHandle["sos_activation"] = snapshot.activationId
        savedStateHandle["sos_test"] = snapshot.testMode
    }

    private fun restoreSos() {
        val persisted = sosPreferences.contains("number")
        val name = if (persisted) sosPreferences.getString("name", null) else savedStateHandle.get<String>("sos_name")
        val number = (if (persisted) sosPreferences.getString("number", null) else savedStateHandle.get<String>("sos_number"))
            ?.let(::normalizeSosDestination)
        val contact = if (name != null && number != null && !emergencyNumberDetector.isEmergencyNumber(number)) {
            TrustedContact(
                contactId = if (persisted) sosPreferences.getString("contact_id", null) ?: "manual-number"
                    else savedStateHandle.get<String>("sos_contact_id") ?: "manual-number",
                displayName = name,
                destination = number,
                recipientConsented = if (persisted) sosPreferences.getBoolean("consented", false)
                    else savedStateHandle["sos_consent"] ?: false,
                photoUri = if (persisted) sosPreferences.getString("photo_uri", null)
                    else savedStateHandle["sos_photo_uri"],
            )
        } else {
            null
        }
        if (contact != null && !persisted) saveSosContact(contact)
        sosCoordinator.restore(
            PersistedSosState(
                contact = contact,
                countdownDeadlineElapsedMillis = savedStateHandle["sos_deadline"],
                activationId = savedStateHandle["sos_activation"] ?: 0,
                testMode = savedStateHandle["sos_test"] ?: false,
            ),
        )
        mutableSosState.value = sosCoordinator.state
        viewModelScope.launch { settingsManager.setSosRecipientAgreed(contact?.recipientConsented == true) }
    }

    private fun localizedContext(): Context =
        appContext.withClaroLanguage(uiState.value.preferences.language)

    private fun localizeAccessInvalidReason(reason: String): String {
        val context = localizedContext()
        val minRecoveryMatch = MIN_RECOVERY_SECRET_REGEX.matchEntire(reason)
        if (minRecoveryMatch != null) {
            val minLength = minRecoveryMatch.groupValues[1].toIntOrNull() ?: 6
            return context.getString(R.string.app_pin_recovery_secret_min_length, minLength)
        }
        return when (reason) {
            "Use exactly 6 digits." -> context.getString(R.string.app_pin_use_six_digits)
            "PINs do not match." -> context.getString(R.string.app_pins_do_not_match)
            "PIN recovery is available only in caregiver PIN mode." ->
                context.getString(R.string.app_pin_recovery_only_pin_mode)
            "Enter the same 6-digit PIN twice." ->
                context.getString(R.string.app_pin_enter_same_twice)
            else -> reason
        }
    }

    private companion object {
        val MIN_RECOVERY_SECRET_REGEX =
            Regex("^Recovery secret must be at least (\\d+) characters\\.$")
    }
}

private fun CriticalActivity.readableName(context: Context): String = when (this) {
    CriticalActivity.ACTIVE_CALL -> context.getString(R.string.app_blocker_active_call)
    CriticalActivity.SOS -> context.getString(R.string.app_blocker_sos)
    CriticalActivity.PURCHASE -> context.getString(R.string.app_blocker_purchase)
    CriticalActivity.DESTRUCTIVE_CONFIRMATION ->
        context.getString(R.string.app_blocker_destructive_confirmation)
    CriticalActivity.EXTERNAL_SYSTEM_UI ->
        context.getString(R.string.app_blocker_external_system_ui)
}
