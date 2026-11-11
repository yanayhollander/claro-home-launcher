package com.clarohome.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroTheme
import com.clarohome.core.domain.StartupRecoveryState
import com.clarohome.core.model.AppLanguage
import com.clarohome.core.model.CapabilityStatus
import com.clarohome.core.model.HomeRoleState
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind
import com.clarohome.core.model.OnboardingStep
import com.clarohome.core.model.ReadinessCapability
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
import com.clarohome.feature.billing.observability.BillingObservabilityScreen
import com.clarohome.feature.billing.observability.PurchaseResult
import com.clarohome.feature.communication.CommunicationRoute
import com.clarohome.feature.communication.CommunicationUiState
import com.clarohome.feature.communication.CallerIdentity
import com.clarohome.feature.communication.MessagingUiState
import com.clarohome.feature.safety.media.apps.SosState
import com.clarohome.presentation.classic.ClassicPhoneScreen
import com.clarohome.presentation.classic.ClassicCommunicationScreen
import com.clarohome.presentation.classic.ClassicMessagingScreen
import com.clarohome.presentation.touch.TouchHomeScreen
import com.clarohome.presentation.touch.TouchCommunicationScreen
import com.clarohome.presentation.touch.TouchMessagingScreen
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
@Serializable private data object PhoneRoute
@Serializable private data object MessagesRoute
@Serializable private data class SafetyRoute(val destination: SafetyMediaAppsDestination)
@Serializable private data object MembershipRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val communicationViewModel by viewModels<CommunicationViewModel>()
    private val messagingViewModel by viewModels<MessagingViewModel>()
    private val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        communicationViewModel.refreshQualification()
        communicationViewModel.openContacts()
    }
    private val phoneRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        communicationViewModel.refreshQualification()
        requestPhonePermissions()
    }
    private val messagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        messagingViewModel.refreshQualification()
        messagingViewModel.openInbox()
    }
    private val smsRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        messagingViewModel.refreshQualification()
        requestMessagePermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        communicationViewModel.handleDialIntent(intent)
        messagingViewModel.handleSendToIntent(intent)
        enableEdgeToEdge()
        setContent {
            ClaroApp(
                communicationViewModel = communicationViewModel,
                messagingViewModel = messagingViewModel,
                onRepairPhone = ::repairPhoneAccess,
                onRepairMessages = ::repairMessagesAccess,
            )
        }
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
        communicationViewModel.handleDialIntent(intent)
        messagingViewModel.handleSendToIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        communicationViewModel.refreshQualification()
        messagingViewModel.refreshQualification()
    }

    private fun repairPhoneAccess() {
        val roleRequest = communicationViewModel.roleRequestIntent()
        if (!communicationViewModel.state.value.qualification.roleHeld && roleRequest != null) {
            phoneRoleLauncher.launch(roleRequest)
        } else {
            requestPhonePermissions()
        }
    }

    private fun requestPhonePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        phonePermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun repairMessagesAccess() {
        val roleRequest = messagingViewModel.roleRequestIntent()
        if (!messagingViewModel.state.value.qualification.roleHeld && roleRequest != null) {
            smsRoleLauncher.launch(roleRequest)
        } else {
            requestMessagePermissions()
        }
    }

    private fun requestMessagePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_MMS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        messagePermissionLauncher.launch(permissions.toTypedArray())
    }
}

@Composable
private fun ClaroApp(
    communicationViewModel: CommunicationViewModel,
    messagingViewModel: MessagingViewModel,
    onRepairPhone: () -> Unit,
    onRepairMessages: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val communication by communicationViewModel.state.collectAsStateWithLifecycle()
    val messaging by messagingViewModel.state.collectAsStateWithLifecycle()
    val openPhoneRequest by communicationViewModel.openPhoneRequests.collectAsStateWithLifecycle()
    val sosState by viewModel.sosState.collectAsStateWithLifecycle()
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
    val projectedHome = state.home?.withCommunicationProjections(communication, messaging)
    val runtimeBlockers = integrationBlockers(
        hasActiveCall = communication.hasActiveCall,
        sosCritical = sosState is SosState.CountingDown || sosState is SosState.Calling,
        purchaseCritical =
            state.billingObservability.purchaseResult == PurchaseResult.Purchasing ||
                state.billingObservability.purchaseResult == PurchaseResult.Pending,
    )

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

    LaunchedEffect(communication.route, openPhoneRequest) {
        if (
            communication.route == CommunicationRoute.INCOMING_CALL ||
            openPhoneRequest > 0
        ) {
            navController.navigate(PhoneRoute) { launchSingleTop = true }
        }
    }

    LaunchedEffect(
        projectedHome?.homeRole,
        communication.qualification,
        messaging.qualification,
    ) {
        viewModel.setReadiness(
            ReadinessCapability.HOME,
            when (projectedHome?.homeRole) {
                HomeRoleState.GRANTED -> CapabilityStatus.READY
                HomeRoleState.UNAVAILABLE -> CapabilityStatus.UNAVAILABLE
                else -> CapabilityStatus.NEEDS_ATTENTION
            },
        )
        viewModel.setReadiness(
            ReadinessCapability.PHONE,
            if (communication.qualification.canOwnCalls) {
                CapabilityStatus.READY
            } else if (!communication.qualification.telephonySupported) {
                CapabilityStatus.UNAVAILABLE
            } else {
                CapabilityStatus.NEEDS_ATTENTION
            },
        )
        viewModel.setReadiness(
            ReadinessCapability.MESSAGES,
            if (messaging.qualification.canSend) {
                CapabilityStatus.READY
            } else if (!messaging.qualification.telephonySupported) {
                CapabilityStatus.UNAVAILABLE
            } else {
                CapabilityStatus.NEEDS_ATTENTION
            },
        )
        viewModel.setReadiness(
            ReadinessCapability.CONTACTS,
            if (communication.qualification.roleHeld) {
                CapabilityStatus.READY
            } else {
                CapabilityStatus.NEEDS_ATTENTION
            },
        )
        viewModel.setReadiness(
            ReadinessCapability.NOTIFICATIONS,
            if (messaging.qualification.notificationPermissionGranted) {
                CapabilityStatus.READY
            } else {
                CapabilityStatus.NEEDS_ATTENTION
            },
        )
        viewModel.setReadiness(ReadinessCapability.MEDIA, CapabilityStatus.READY)
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
                        projectedHome?.let { home ->
                            TouchHomeScreen(
                                home = home,
                                onUseClassicMode = { navController.openSettings(viewModel) },
                                onTileSelected = { tile ->
                                    handleTile(
                                        tile,
                                        openPhone = {
                                            communicationViewModel.openDialer()
                                            navController.navigate(PhoneRoute)
                                        },
                                        openMessages = {
                                            messagingViewModel.openInbox()
                                            navController.navigate(MessagesRoute)
                                        },
                                        openSafetyFeature = { destination ->
                                            navController.navigate(SafetyRoute(destination))
                                        },
                                        openSettings = { navController.openSettings(viewModel) },
                                        launchApp = launchPackage,
                                    )
                                },
                                onFavoriteSelected = { favorite ->
                                    platformNotice = if (favorite.phoneNumber == null) {
                                        "Ask your caregiver to add a person."
                                    } else {
                                        communicationViewModel.requestCall(
                                            CallerIdentity(
                                                displayName = favorite.displayName,
                                                number = favorite.phoneNumber,
                                            ),
                                        )
                                        navController.navigate(PhoneRoute)
                                        null
                                    }
                                },
                                onRepairHomeRole = launchHomeRole,
                                onReturnToCall = {
                                    communicationViewModel.returnToActiveCall()
                                    navController.navigate(PhoneRoute)
                                },
                            )
                        }
                    }
                    composable<ClassicHomeRoute> {
                        projectedHome?.let { home ->
                            ClassicPhoneScreen(
                                home = home,
                                onOpenMenu = { navController.navigate(LauncherMenuRoute) },
                                onTileSelected = { tile ->
                                    handleTile(
                                        tile,
                                        openPhone = {
                                            communicationViewModel.openDialer()
                                            navController.navigate(PhoneRoute)
                                        },
                                        openMessages = {
                                            messagingViewModel.openInbox()
                                            navController.navigate(MessagesRoute)
                                        },
                                        openSafetyFeature = { destination ->
                                            navController.navigate(SafetyRoute(destination))
                                        },
                                        openSettings = { navController.openSettings(viewModel) },
                                        launchApp = launchPackage,
                                    )
                                },
                                onFavoriteSelected = { favorite ->
                                    favorite.phoneNumber?.let { number ->
                                        communicationViewModel.requestCall(
                                            CallerIdentity(favorite.displayName, number),
                                        )
                                        navController.navigate(PhoneRoute)
                                    } ?: run {
                                        platformNotice = "Ask your caregiver to add a person."
                                    }
                                },
                                onRepairHomeRole = launchHomeRole,
                                onReturnToCall = {
                                    communicationViewModel.returnToActiveCall()
                                    navController.navigate(PhoneRoute)
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
                                        openPhone = {
                                            communicationViewModel.openDialer()
                                            navController.navigate(PhoneRoute)
                                        },
                                        openMessages = {
                                            messagingViewModel.openInbox()
                                            navController.navigate(MessagesRoute)
                                        },
                                        openSafetyFeature = { destination ->
                                            navController.navigate(SafetyRoute(destination))
                                        },
                                        openSettings = { navController.openSettings(viewModel) },
                                        launchApp = launchPackage,
                                    )
                                },
                                onUseTouchStyle = { navController.openSettings(viewModel) },
                            )
                        }
                    }
                    composable<PhoneRoute> {
                        IntegratedCommunicationScreen(
                            state = communication,
                            visualMode = state.preferences.visualMode,
                            viewModel = communicationViewModel,
                            onRepairPhone = onRepairPhone,
                            onHome = { navController.goHome(state.preferences.visualMode) },
                        )
                    }
                    composable<MessagesRoute> {
                        IntegratedMessagingScreen(
                            state = messaging,
                            visualMode = state.preferences.visualMode,
                            viewModel = messagingViewModel,
                            onRepairMessages = onRepairMessages,
                            onHome = { navController.goHome(state.preferences.visualMode) },
                        )
                    }
                    composable<SafetyRoute> { entry ->
                        SafetyMediaAppsScreen(
                            initialDestination = entry.toRoute<SafetyRoute>().destination,
                            visualMode = state.preferences.visualMode,
                            viewModel = viewModel,
                            onBack = navController::popBackStack,
                        )
                    }
                    composable<MembershipRoute> {
                        LaunchedEffect(Unit) { viewModel.membershipOpened() }
                        BillingObservabilityScreen(
                            state = state.billingObservability,
                            classicShell = state.preferences.visualMode == VisualMode.CLASSIC,
                            onBack = navController::popBackStack,
                            onRefresh = viewModel::refreshPlans,
                            onPurchase = viewModel::purchase,
                            onRestore = viewModel::restorePurchases,
                            onConsentChange = viewModel::updateConsent,
                            onWithdrawConsent = viewModel::withdrawConsent,
                            onResetDiagnostics = viewModel::resetDiagnostics,
                        )
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
                            onHomeAndApps = {
                                viewModel.recordSettingsActivity()
                                navController.navigate(AppsRoute)
                            },
                            onPeopleAndSafety = {
                                viewModel.recordSettingsActivity()
                                navController.navigate(
                                    SafetyRoute(SafetyMediaAppsDestination.SOS),
                                )
                            },
                            onMembershipPrivacy = {
                                viewModel.recordSettingsActivity()
                                navController.navigate(MembershipRoute)
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
                                viewModel.applyVisualMode(mode, runtimeBlockers) {
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
    openPhone: () -> Unit,
    openMessages: () -> Unit,
    openSafetyFeature: (SafetyMediaAppsDestination) -> Unit,
    openSettings: () -> Unit,
    launchApp: (String) -> Unit,
) {
    when (tile.kind) {
        HomeTileKind.PHONE -> openPhone()
        HomeTileKind.MESSAGES -> openMessages()
        HomeTileKind.CAMERA -> openSafetyFeature(SafetyMediaAppsDestination.CAMERA)
        HomeTileKind.GALLERY -> openSafetyFeature(SafetyMediaAppsDestination.GALLERY)
        HomeTileKind.SOS -> openSafetyFeature(SafetyMediaAppsDestination.SOS)
        HomeTileKind.APPS -> openSafetyFeature(SafetyMediaAppsDestination.APPS)
        HomeTileKind.INSTALLED_APP -> tile.packageName?.let(launchApp)
    }
    if (tile.label == "Settings") openSettings()
}

@Composable
private fun IntegratedCommunicationScreen(
    state: CommunicationUiState,
    visualMode: VisualMode,
    viewModel: CommunicationViewModel,
    onRepairPhone: () -> Unit,
    onHome: () -> Unit,
) {
    val callbacks = CommunicationCallbacks(viewModel, onRepairPhone, onHome)
    if (visualMode == VisualMode.TOUCH) {
        TouchCommunicationScreen(
            state = state,
            onDigit = callbacks.onDigit,
            onBackspace = callbacks.onBackspace,
            onCallEnteredNumber = callbacks.onCallEnteredNumber,
            onDirectCall = callbacks.onDirectCall,
            onOpenContacts = callbacks.onOpenContacts,
            onSearchContacts = callbacks.onSearchContacts,
            onLoadMoreContacts = callbacks.onLoadMoreContacts,
            onOpenContact = callbacks.onOpenContact,
            onChooseNumber = callbacks.onChooseNumber,
            onConfirmCall = callbacks.onConfirmCall,
            onCancelCall = callbacks.onCancelCall,
            onAnswer = callbacks.onAnswer,
            onDecline = callbacks.onDecline,
            onEnd = callbacks.onEnd,
            onToggleMute = callbacks.onToggleMute,
            onAudioRoute = callbacks.onAudioRoute,
            onToggleHold = callbacks.onToggleHold,
            onSwap = callbacks.onSwap,
            onRepairPhone = callbacks.onRepair,
            onHome = callbacks.onHome,
        )
    } else {
        ClassicCommunicationScreen(
            state = state,
            onDigit = callbacks.onDigit,
            onBackspace = callbacks.onBackspace,
            onCallEnteredNumber = callbacks.onCallEnteredNumber,
            onDirectCall = callbacks.onDirectCall,
            onOpenContacts = callbacks.onOpenContacts,
            onSearchContacts = callbacks.onSearchContacts,
            onLoadMoreContacts = callbacks.onLoadMoreContacts,
            onOpenContact = callbacks.onOpenContact,
            onChooseNumber = callbacks.onChooseNumber,
            onConfirmCall = callbacks.onConfirmCall,
            onCancelCall = callbacks.onCancelCall,
            onAnswer = callbacks.onAnswer,
            onDecline = callbacks.onDecline,
            onEnd = callbacks.onEnd,
            onToggleMute = callbacks.onToggleMute,
            onAudioRoute = callbacks.onAudioRoute,
            onToggleHold = callbacks.onToggleHold,
            onSwap = callbacks.onSwap,
            onRepairPhone = callbacks.onRepair,
            onHome = callbacks.onHome,
        )
    }
}

private class CommunicationCallbacks(
    viewModel: CommunicationViewModel,
    onRepairPhone: () -> Unit,
    onHome: () -> Unit,
) {
    val onDigit = viewModel::inputDigit
    val onBackspace = viewModel::backspace
    val onCallEnteredNumber = viewModel::requestEnteredNumberCall
    val onDirectCall = viewModel::directCallAssignment
    val onOpenContacts = viewModel::openContacts
    val onSearchContacts = viewModel::searchContacts
    val onLoadMoreContacts = viewModel::loadMoreContacts
    val onOpenContact = viewModel::openContact
    val onChooseNumber = viewModel::chooseContactNumber
    val onConfirmCall = viewModel::confirmOutgoingCall
    val onCancelCall = viewModel::cancelConfirmation
    val onAnswer = viewModel::answer
    val onDecline = viewModel::decline
    val onEnd = viewModel::end
    val onToggleMute = viewModel::toggleMute
    val onAudioRoute = viewModel::selectAudioRoute
    val onToggleHold = viewModel::toggleHold
    val onSwap = viewModel::swapCall
    val onRepair = onRepairPhone
    val onHome = onHome
}

@Composable
private fun IntegratedMessagingScreen(
    state: MessagingUiState,
    visualMode: VisualMode,
    viewModel: MessagingViewModel,
    onRepairMessages: () -> Unit,
    onHome: () -> Unit,
) {
    if (visualMode == VisualMode.TOUCH) {
        TouchMessagingScreen(
            state = state,
            onSearchInbox = viewModel::searchInbox,
            onOpenConversation = viewModel::openConversation,
            onNewMessage = viewModel::startNewMessage,
            onSearchRecipients = viewModel::searchRecipients,
            onLoadMoreRecipients = viewModel::loadMoreRecipients,
            onChooseContactNumber = viewModel::chooseContactNumber,
            onManualRecipient = viewModel::enterManualRecipient,
            onBodyChange = viewModel::updateBody,
            onReview = viewModel::review,
            onEdit = viewModel::editAfterReview,
            onSend = viewModel::send,
            onRetry = viewModel::retry,
            onConfirmDuplicateRisk = viewModel::confirmDuplicateRiskAndSendAgain,
            onCancelFailure = viewModel::cancelFailure,
            onRepair = onRepairMessages,
            onHome = onHome,
        )
    } else {
        ClassicMessagingScreen(
            state = state,
            onSearchInbox = viewModel::searchInbox,
            onOpenConversation = viewModel::openConversation,
            onNewMessage = viewModel::startNewMessage,
            onSearchRecipients = viewModel::searchRecipients,
            onChooseContactNumber = viewModel::chooseContactNumber,
            onManualRecipient = viewModel::enterManualRecipient,
            onBodyChange = viewModel::updateBody,
            onReview = viewModel::review,
            onEdit = viewModel::editAfterReview,
            onSend = viewModel::send,
            onRetry = viewModel::retry,
            onConfirmDuplicateRisk = viewModel::confirmDuplicateRiskAndSendAgain,
            onCancelFailure = viewModel::cancelFailure,
            onRepair = onRepairMessages,
            onHome = onHome,
        )
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
