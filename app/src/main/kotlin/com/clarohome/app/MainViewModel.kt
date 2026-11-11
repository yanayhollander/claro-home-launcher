package com.clarohome.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarohome.core.domain.HomeRepository
import com.clarohome.core.domain.StartupRecoveryCoordinator
import com.clarohome.core.domain.StartupRecoveryState
import com.clarohome.core.domain.VisualModeRepository
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.VisualMode
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
    val visualMode: VisualMode = VisualMode.TOUCH,
    val home: HomeSnapshot? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val visualModeRepository: VisualModeRepository,
    private val recoveryCoordinator: StartupRecoveryCoordinator,
    homeRepository: HomeRepository,
) : ViewModel() {
    private val startup = MutableStateFlow<StartupRecoveryState>(StartupRecoveryState.Recovering)

    val uiState: StateFlow<MainUiState> = combine(
        startup,
        visualModeRepository.visualMode,
        homeRepository.home,
    ) { recovery, visualMode, home ->
        MainUiState(recovery, visualMode, home)
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

    fun setVisualMode(mode: VisualMode) {
        viewModelScope.launch {
            visualModeRepository.setVisualMode(mode)
        }
    }
}
