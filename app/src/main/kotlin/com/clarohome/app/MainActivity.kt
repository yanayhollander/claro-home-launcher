package com.clarohome.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clarohome.core.design.ClaroTheme
import com.clarohome.core.domain.StartupRecoveryState
import com.clarohome.core.model.AppLanguage
import com.clarohome.core.model.OnboardingStep
import com.clarohome.core.model.VisualMode
import com.clarohome.feature.onboarding.settings.AccessGateScreen
import com.clarohome.feature.onboarding.settings.AccessMethodSettingsScreen
import com.clarohome.feature.onboarding.settings.DisplayLanguageSettingsScreen
import com.clarohome.feature.onboarding.settings.OnboardingCallbacks
import com.clarohome.feature.onboarding.settings.OnboardingFlow
import com.clarohome.feature.onboarding.settings.PhoneStyleSettingsScreen
import com.clarohome.feature.onboarding.settings.PinRecoveryScreen
import com.clarohome.feature.onboarding.settings.ReadinessSettingsScreen
import com.clarohome.feature.onboarding.settings.SettingsHubScreen
import com.clarohome.presentation.classic.ClassicPhoneScreen
import com.clarohome.presentation.touch.TouchHomeScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
private data object RecoveryRoute

@Serializable
private data object OnboardingRoute

@Serializable
private data object TouchHomeRoute

@Serializable
private data object ClassicHomeRoute

@Serializable
private data object AccessGateRoute

@Serializable
private data object PinRecoveryRoute

@Serializable
private data object SettingsRoute

@Serializable
private data object PhoneStyleRoute

@Serializable
private data object DisplayLanguageRoute

@Serializable
private data object AccessMethodRoute

@Serializable
private data object ReadinessRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClaroApp()
        }
    }

    override fun onStop() {
        super.onStop()
        val viewModel = runCatching {
            androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
        }.getOrNull()
        viewModel?.lockSettings()
    }
}

@Composable
private fun ClaroApp(viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val layoutDirection =
        if (state.preferences.language == AppLanguage.HEBREW) LayoutDirection.Rtl
        else LayoutDirection.Ltr

    LaunchedEffect(state.startup) {
        if (state.startup is StartupRecoveryState.Ready) {
            val route = if (state.preferences.onboarding.currentStep == OnboardingStep.COMPLETE) {
                state.preferences.visualMode.homeRoute()
            } else {
                OnboardingRoute
            }
            navController.navigate(route) {
                popUpTo(RecoveryRoute) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        ClaroTheme(
            textSize = state.preferences.textSize,
            highContrast = state.preferences.highContrast,
        ) {
            NavHost(
                navController = navController,
                startDestination = RecoveryRoute,
            ) {
                composable<RecoveryRoute> {
                    when (val startup = state.startup) {
                        StartupRecoveryState.Recovering -> LoadingScreen()
                        is StartupRecoveryState.Failed -> FailureScreen(startup.reason)
                        is StartupRecoveryState.Ready -> LoadingScreen()
                    }
                }
                composable<OnboardingRoute> {
                    OnboardingFlow(
                        preferences = state.preferences,
                        message = state.message,
                        callbacks = OnboardingCallbacks(
                            onContinue = { next ->
                                viewModel.clearMessage()
                                viewModel.advanceOnboarding(next)
                                if (next == OnboardingStep.COMPLETE) {
                                    navController.goHome(state.preferences.visualMode)
                                }
                            },
                            onChooseMode = viewModel::chooseVisualMode,
                            onLanguageDisplay = viewModel::saveLanguageDisplay,
                            onUseDateQuestion = viewModel::useDateQuestion,
                            onConfigurePin = viewModel::configurePin,
                            onAssignments = viewModel::updateAssignments,
                            onReadiness = viewModel::setReadiness,
                            onHandover = viewModel::setHandover,
                        ),
                    )
                }
                composable<TouchHomeRoute> {
                    state.home?.let { home ->
                        TouchHomeScreen(
                            home = home,
                            onOpenSettings = {
                                navController.openSettings(viewModel)
                            },
                        )
                    }
                }
                composable<ClassicHomeRoute> {
                    state.home?.let { home ->
                        ClassicPhoneScreen(
                            home = home,
                            onOpenSettings = {
                                navController.openSettings(viewModel)
                            },
                        )
                    }
                }
                composable<AccessGateRoute> {
                    AccessGateScreen(
                        mode = state.preferences.accessProtection.mode,
                        message = state.message,
                        onUnlock = { input ->
                            viewModel.unlock(input) {
                                navController.navigate(SettingsRoute) {
                                    popUpTo(AccessGateRoute) { inclusive = true }
                                }
                            }
                        },
                        onCancel = {
                            viewModel.clearMessage()
                            navController.goHome(state.preferences.visualMode)
                        },
                        onRecoverPin = { navController.navigate(PinRecoveryRoute) },
                    )
                }
                composable<PinRecoveryRoute> {
                    PinRecoveryScreen(
                        message = state.message,
                        onRecover = { secret, pin, confirmation ->
                            viewModel.recoverPin(secret, pin, confirmation) {
                                navController.navigate(SettingsRoute) {
                                    popUpTo(AccessGateRoute) { inclusive = true }
                                }
                            }
                        },
                        onCancel = {
                            viewModel.clearMessage()
                            navController.popBackStack()
                        },
                    )
                }
                composable<SettingsRoute> {
                    ProtectedSessionMonitor(viewModel, navController)
                    SettingsHubScreen(
                        preferences = state.preferences,
                        onPhoneStyle = {
                            viewModel.recordSettingsActivity()
                            navController.navigate(PhoneStyleRoute)
                        },
                        onDisplayLanguage = {
                            viewModel.recordSettingsActivity()
                            navController.navigate(DisplayLanguageRoute)
                        },
                        onAccessMethod = {
                            viewModel.recordSettingsActivity()
                            navController.navigate(AccessMethodRoute)
                        },
                        onReadiness = {
                            viewModel.recordSettingsActivity()
                            navController.navigate(ReadinessRoute)
                        },
                        onLockAndExit = {
                            viewModel.lockSettings()
                            navController.goHome(state.preferences.visualMode)
                        },
                    )
                }
                composable<PhoneStyleRoute> {
                    ProtectedSessionMonitor(viewModel, navController)
                    PhoneStyleSettingsScreen(
                        current = state.preferences.visualMode,
                        pending = state.preferences.pendingVisualMode,
                        message = state.message,
                        onApply = { mode ->
                            viewModel.recordSettingsActivity()
                            viewModel.applyVisualMode(mode) {
                                navController.goHome(mode)
                            }
                        },
                        onCancel = {
                            viewModel.cancelDeferredModeSwitch()
                            navController.popBackStack()
                        },
                    )
                }
                composable<DisplayLanguageRoute> {
                    ProtectedSessionMonitor(viewModel, navController)
                    DisplayLanguageSettingsScreen(
                        preferences = state.preferences,
                        onApply = { language, size, contrast ->
                            viewModel.recordSettingsActivity()
                            viewModel.saveLanguageDisplay(language, size, contrast)
                            navController.popBackStack()
                        },
                        onCancel = navController::popBackStack,
                    )
                }
                composable<AccessMethodRoute> {
                    ProtectedSessionMonitor(viewModel, navController)
                    AccessMethodSettingsScreen(
                        accessMode = state.preferences.accessProtection.mode,
                        message = state.message,
                        onUseDateQuestion = {
                            viewModel.recordSettingsActivity()
                            viewModel.useDateQuestionForSettings {
                                navController.popBackStack()
                            }
                        },
                        onConfigurePin = { pin, confirmation, recovery ->
                            viewModel.recordSettingsActivity()
                            viewModel.configurePinForSettings(
                                pin,
                                confirmation,
                                recovery,
                            ) {
                                navController.popBackStack()
                            }
                        },
                        onCancel = {
                            viewModel.clearMessage()
                            navController.popBackStack()
                        },
                    )
                }
                composable<ReadinessRoute> {
                    ProtectedSessionMonitor(viewModel, navController)
                    ReadinessSettingsScreen(
                        readiness = state.preferences.onboarding.readiness,
                        onStatus = { capability, status ->
                            viewModel.recordSettingsActivity()
                            viewModel.setReadiness(capability, status)
                        },
                        onBack = navController::popBackStack,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtectedSessionMonitor(
    viewModel: MainViewModel,
    navController: NavHostController,
) {
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            if (!viewModel.isSettingsUnlocked()) {
                navController.navigate(AccessGateRoute) {
                    popUpTo(SettingsRoute) { inclusive = true }
                    launchSingleTop = true
                }
                break
            }
        }
    }
}

private fun NavHostController.openSettings(viewModel: MainViewModel) {
    viewModel.clearMessage()
    navigate(if (viewModel.isSettingsUnlocked()) SettingsRoute else AccessGateRoute)
}

private fun NavHostController.goHome(mode: VisualMode) {
    navigate(mode.homeRoute()) {
        popUpTo(graph.startDestinationId) { inclusive = true }
        launchSingleTop = true
    }
}

private fun VisualMode.homeRoute(): Any = when (this) {
    VisualMode.TOUCH -> TouchHomeRoute
    VisualMode.CLASSIC -> ClassicHomeRoute
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun FailureScreen(reason: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(reason)
    }
}
