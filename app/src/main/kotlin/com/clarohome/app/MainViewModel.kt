package com.clarohome.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarohome.core.domain.HomeRepository
import com.clarohome.core.domain.ClaroPreferencesRepository
import com.clarohome.core.domain.CriticalActivityRepository
import com.clarohome.core.domain.StartupRecoveryCoordinator
import com.clarohome.core.domain.StartupRecoveryState
import com.clarohome.core.model.AppLanguage
import com.clarohome.core.model.CapabilityStatus
import com.clarohome.core.model.ClaroPreferences
import com.clarohome.core.model.CriticalActivity
import com.clarohome.core.model.HandoverCheck
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.OnboardingStep
import com.clarohome.core.model.ReadinessCapability
import com.clarohome.core.model.TextSize
import com.clarohome.core.model.VisualMode
import com.clarohome.feature.onboarding.settings.AccessResult
import com.clarohome.feature.onboarding.settings.ModeSwitchResult
import com.clarohome.feature.onboarding.settings.OnboardingSettingsManager
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
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesRepository: ClaroPreferencesRepository,
    private val criticalActivityRepository: CriticalActivityRepository,
    private val recoveryCoordinator: StartupRecoveryCoordinator,
    private val settingsManager: OnboardingSettingsManager,
    homeRepository: HomeRepository,
) : ViewModel() {
    private val startup = MutableStateFlow<StartupRecoveryState>(StartupRecoveryState.Recovering)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MainUiState> = combine(
        startup,
        preferencesRepository.preferences,
        homeRepository.home,
        criticalActivityRepository.activeActivities,
        message,
    ) { recovery, preferences, home, activeActivities, statusMessage ->
        MainUiState(recovery, preferences, home, activeActivities, statusMessage)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    init {
        viewModelScope.launch {
            startup.value = recoveryCoordinator.recover()
        }
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

    fun applyVisualMode(mode: VisualMode, onApplied: () -> Unit) {
        viewModelScope.launch {
            when (val result = settingsManager.requestModeSwitch(
                mode,
                uiState.value.activeActivities,
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

    fun clearMessage() {
        message.value = null
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

private fun CriticalActivity.readableName(): String =
    name.lowercase().replace('_', ' ')
