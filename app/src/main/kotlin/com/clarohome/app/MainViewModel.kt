package com.clarohome.app

import android.content.Intent
import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarohome.core.domain.EmergencyNumberDetector
import com.clarohome.core.domain.ExternalAppLaunchResult
import com.clarohome.core.domain.ExternalAppLauncher
import com.clarohome.core.domain.HomeRepository
import com.clarohome.core.domain.ClaroPreferencesRepository
import com.clarohome.core.domain.CriticalActivityRepository
import com.clarohome.core.domain.NativeEmergencyDialerGateway
import com.clarohome.core.domain.PhoneCallGateway
import com.clarohome.core.domain.StartupRecoveryCoordinator
import com.clarohome.core.domain.StartupRecoveryState
import com.clarohome.core.model.AppLanguage
import com.clarohome.core.model.CapabilityStatus
import com.clarohome.core.model.ClaroPreferences
import com.clarohome.core.model.CriticalActivity
import com.clarohome.core.model.HandoverCheck
import com.clarohome.core.model.FavoritePerson
import com.clarohome.core.model.FavoriteState
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.OnboardingStep
import com.clarohome.core.model.ReadinessCapability
import com.clarohome.core.model.TextSize
import com.clarohome.core.model.VisualMode
import com.clarohome.feature.onboarding.settings.AccessResult
import com.clarohome.feature.onboarding.settings.ModeSwitchResult
import com.clarohome.feature.onboarding.settings.OnboardingSettingsManager
import com.clarohome.feature.launcher.home.HomeRoleController
import com.clarohome.feature.launcher.home.InstalledAppCatalog
import com.clarohome.feature.launcher.home.LauncherStatusReader
import com.clarohome.feature.billing.observability.BillingObservabilityUiState
import com.clarohome.feature.billing.observability.BillingRepository
import com.clarohome.feature.billing.observability.ClaroEvent
import com.clarohome.feature.billing.observability.ConsentAwareObservability
import com.clarohome.feature.billing.observability.ConsentController
import com.clarohome.feature.billing.observability.DiagnosticsConsent
import com.clarohome.feature.safety.media.apps.PersistedSosState
import com.clarohome.feature.safety.media.apps.SosCallOutcome
import com.clarohome.feature.safety.media.apps.SosCoordinator
import com.clarohome.feature.safety.media.apps.SosState
import com.clarohome.feature.safety.media.apps.TrustedContact
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    emergencyNumberDetector: EmergencyNumberDetector,
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
    }

    fun advanceOnboarding(next: OnboardingStep) {
        viewModelScope.launch {
            settingsManager.advanceOnboarding(next)
        }
    }

    fun chooseVisualMode(mode: VisualMode) = launch { settingsManager.chooseVisualMode(mode) }

    fun saveLanguageDisplay(language: AppLanguage, size: TextSize, highContrast: Boolean) =
        launch { settingsManager.setLanguageAndDisplay(language, size, highContrast) }

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
                is AccessResult.Invalid -> message.value = result.reason
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
                is AccessResult.Invalid -> message.value = result.reason
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
            when (val result = settingsManager.unlock(input)) {
                AccessResult.Unlocked -> {
                    message.value = null
                    onSuccess()
                }
                AccessResult.Incorrect -> message.value = "That answer was not correct."
                is AccessResult.Cooldown ->
                    message.value = "Too many attempts. Try again in ${result.remainingSeconds} seconds."
                is AccessResult.Invalid -> message.value = result.reason
            }
        }
    }

    fun recoverPin(secret: String, pin: String, confirmation: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            when (val result = settingsManager.recoverPin(secret, pin, confirmation)) {
                AccessResult.Unlocked -> {
                    message.value = "PIN changed."
                    onSuccess()
                }
                AccessResult.Incorrect -> message.value = "Recovery secret was not correct."
                is AccessResult.Cooldown ->
                    message.value = "Recovery is paused for ${result.remainingSeconds} seconds."
                is AccessResult.Invalid -> message.value = result.reason
            }
        }
    }

    fun isSettingsUnlocked(): Boolean = settingsManager.isUnlocked()

    fun recordSettingsActivity() = settingsManager.recordActivity()

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
                    message.value = "Phone style will change after: " +
                        result.blockers.joinToString { it.readableName() }
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

    private suspend fun syncLauncherState() {
        homeRepository.setHomeRoleState(homeRoleController.currentState())
        homeRepository.setInstalledApps(installedAppCatalog.discoverEligibleApps())
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

    fun setFavorite(slot: Int, id: String, name: String, number: String, photoUri: String?) {
        viewModelScope.launch {
            val before = uiState.value.home?.favorites.orEmpty()
            homeRepository.setFavorite(
                slot,
                FavoritePerson(
                    id = id,
                    displayName = name,
                    phoneNumber = number,
                    photoUri = photoUri,
                    state = if (photoUri == null) {
                        FavoriteState.PHOTO_UNAVAILABLE
                    } else {
                        FavoriteState.READY
                    },
                ),
            )
            updatePeopleSetupProgress(
                before.mapIndexed { index, favorite ->
                    if (index == slot) favorite.copy(phoneNumber = number) else favorite
                },
            )
        }
    }

    fun clearFavorite(slot: Int) {
        viewModelScope.launch {
            val before = uiState.value.home?.favorites.orEmpty()
            homeRepository.clearFavorite(slot)
            updatePeopleSetupProgress(
                before.mapIndexed { index, favorite ->
                    if (index == slot) favorite.copy(phoneNumber = null) else favorite
                },
            )
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
            publishSos()
            consentController.reset()
            settingsManager.resetAll()
            refreshLauncherState()
            message.value = null
            onResetComplete()
        }
    }

    fun setAppOnHome(packageName: String, onHome: Boolean) {
        viewModelScope.launch {
            homeRepository.setAppOnHome(packageName, onHome)
        }
    }

    fun configureSos(name: String, destination: String, consented: Boolean) {
        val contact = if (name.isBlank() || destination.isBlank()) {
            null
        } else {
            TrustedContact(
                contactId = "trusted-contact",
                displayName = name.trim(),
                destination = destination.trim(),
                recipientConsented = consented,
            )
        }
        sosCoordinator.configure(contact)
        publishSos()
        viewModelScope.launch {
            val onboarding = uiState.value.preferences.onboarding
            settingsManager.updateSetupAssignments(
                favoriteSlotsAssigned = onboarding.favoriteSlotsAssigned,
                speedDialOneAssigned = onboarding.speedDialOneAssigned,
                speedDialTwoAssigned = onboarding.speedDialTwoAssigned,
                sosRecipientAgreed = contact?.recipientConsented == true,
            )
        }
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
        savedStateHandle["sos_consent"] = snapshot.contact?.recipientConsented ?: false
        savedStateHandle["sos_deadline"] = snapshot.countdownDeadlineElapsedMillis
        savedStateHandle["sos_activation"] = snapshot.activationId
        savedStateHandle["sos_test"] = snapshot.testMode
    }

    private fun restoreSos() {
        val name = savedStateHandle.get<String>("sos_name")
        val number = savedStateHandle.get<String>("sos_number")
        val contact = if (name != null && number != null) {
            TrustedContact(
                contactId = "trusted-contact",
                displayName = name,
                destination = number,
                recipientConsented = savedStateHandle["sos_consent"] ?: false,
            )
        } else {
            null
        }
        sosCoordinator.restore(
            PersistedSosState(
                contact = contact,
                countdownDeadlineElapsedMillis = savedStateHandle["sos_deadline"],
                activationId = savedStateHandle["sos_activation"] ?: 0,
                testMode = savedStateHandle["sos_test"] ?: false,
            ),
        )
        mutableSosState.value = sosCoordinator.state
    }

    private suspend fun updatePeopleSetupProgress(favorites: List<FavoritePerson>) {
        val current = uiState.value
        val assigned = favorites.count { it.phoneNumber != null }
        settingsManager.updateSetupAssignments(
            favoriteSlotsAssigned = assigned,
            speedDialOneAssigned = current.preferences.onboarding.speedDialOneAssigned,
            speedDialTwoAssigned = current.preferences.onboarding.speedDialTwoAssigned,
            sosRecipientAgreed = current.preferences.onboarding.sosRecipientAgreed,
        )
    }
}

private fun CriticalActivity.readableName(): String =
    name.lowercase().replace('_', ' ')
