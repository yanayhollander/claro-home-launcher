package com.clarohome.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroTheme
import com.clarohome.core.domain.StartupRecoveryState
import com.clarohome.core.model.AppLanguage
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind
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

@Serializable private data object RecoveryRoute
@Serializable private data object OnboardingRoute
@Serializable private data object TouchHomeRoute
@Serializable private data object ClassicHomeRoute
@Serializable private data object AppsRoute
@Serializable private data object LauncherMenuRoute
@Serializable private data object AccessGateRoute
@Serializable private data object PinRecoveryRoute
@Serializable private data object SettingsRoute
@Serializable private data object PhoneStyleRoute
@Serializable private data object DisplayLanguageRoute
@Serializable private data object AccessMethodRoute
@Serializable private data object ReadinessRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ClaroApp() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { ViewModelProvider(this)[MainViewModel::class.java] }
            .getOrNull()
            ?.lockSettings()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
private fun ClaroApp(viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val layoutDirection =
        if (state.preferences.language == AppLanguage.HEBREW) LayoutDirection.Rtl
        else LayoutDirection.Ltr
    var platformNotice by rememberSaveable { mutableStateOf<String?>(null) }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshLauncherState()
    }
    val launchHomeRole = {
        try {
            roleLauncher.launch(viewModel.homeRoleActionIntent())
        } catch (_: ActivityNotFoundException) {
            platformNotice = "Home settings are unavailable on this device."
        }
    }
    val launchPackage = { packageName: String ->
        val launchIntent = viewModel.appLaunchIntent(packageName)
        if (launchIntent == null) {
            platformNotice = "This app is missing or disabled."
        } else {
            try {
                context.startActivity(launchIntent)
            } catch (_: ActivityNotFoundException) {
                platformNotice = "This app could not be opened."
            }
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshLauncherState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            Box(Modifier.fillMaxSize()) {
                NavHost(navController = navController, startDestination = RecoveryRoute) {
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
                                onUseClassicMode = { navController.openSettings(viewModel) },
                                onTileSelected = { tile ->
                                    handleTile(
                                        tile,
                                        openApps = { navController.navigate(AppsRoute) },
                                        openSettings = { navController.openSettings(viewModel) },
                                        launchApp = launchPackage,
                                    )
                                },
                                onFavoriteSelected = {
                                    platformNotice = if (it.phoneNumber == null) {
                                        "Ask your caregiver to add a person."
                                    } else {
                                        "Phone confirmation is not available yet."
                                    }
                                },
                                onRepairHomeRole = launchHomeRole,
                                onReturnToCall = {
                                    platformNotice = "No active-call provider is connected yet."
                                },
                            )
                        }
                    }
                    composable<ClassicHomeRoute> {
                        state.home?.let { home ->
                            ClassicPhoneScreen(
                                home = home,
                                onOpenMenu = { navController.navigate(LauncherMenuRoute) },
                                onTileSelected = { tile ->
                                    handleTile(
                                        tile,
                                        openApps = { navController.navigate(AppsRoute) },
                                        openSettings = { navController.openSettings(viewModel) },
                                        launchApp = launchPackage,
                                    )
                                },
                                onFavoriteSelected = {
                                    platformNotice = "Phone confirmation is not available yet."
                                },
                                onRepairHomeRole = launchHomeRole,
                                onReturnToCall = {
                                    platformNotice = "No active-call provider is connected yet."
                                },
                            )
                        }
                    }
                    composable<AppsRoute> {
                        state.home?.let { home ->
                            AppsMenuScreen(
                                apps = home.installedApps,
                                onBack = navController::popBackStack,
                                onLaunch = { launchPackage(it.packageName) },
                                onSetAllowed = { app, allowed ->
                                    viewModel.setAppAllowed(app.packageName, allowed)
                                },
                                onSetOnHome = { app, onHome ->
                                    viewModel.setAppOnHome(app.packageName, onHome)
                                },
                            )
                        }
                    }
                    composable<LauncherMenuRoute> {
                        state.home?.let { home ->
                            LauncherMenuScreen(
                                tiles = home.tiles,
                                onBack = navController::popBackStack,
                                onTileSelected = { tile ->
                                    handleTile(
                                        tile,
                                        openApps = { navController.navigate(AppsRoute) },
                                        openSettings = { navController.openSettings(viewModel) },
                                        launchApp = launchPackage,
                                    )
                                },
                                onUseTouchStyle = { navController.openSettings(viewModel) },
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
                                viewModel.applyVisualMode(mode) { navController.goHome(mode) }
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
                                viewModel.useDateQuestionForSettings(navController::popBackStack)
                            },
                            onConfigurePin = { pin, confirmation, recovery ->
                                viewModel.recordSettingsActivity()
                                viewModel.configurePinForSettings(
                                    pin,
                                    confirmation,
                                    recovery,
                                    navController::popBackStack,
                                )
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
                platformNotice?.let { notice ->
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(ClaroColors.Red)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            notice,
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(
                            onClick = { platformNotice = null },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Dismiss")
                        }
                    }
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

private fun handleTile(
    tile: HomeTile,
    openApps: () -> Unit,
    openSettings: () -> Unit,
    launchApp: (String) -> Unit,
) {
    when (tile.kind) {
        HomeTileKind.APPS -> openApps()
        HomeTileKind.INSTALLED_APP -> tile.packageName?.let(launchApp)
        else -> if (tile.label == "Settings") openSettings()
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
