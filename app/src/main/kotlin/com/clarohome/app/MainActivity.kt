package com.clarohome.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.clarohome.core.design.claroFocusRing
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroIcon
import com.clarohome.core.design.ClaroIconKind
import com.clarohome.core.design.ClaroLocaleContainer
import com.clarohome.core.design.ClaroSoftkey
import com.clarohome.core.design.ClaroSoftkeyHost
import com.clarohome.core.design.ClaroTheme
import com.clarohome.core.design.LocalClaroHaptics
import com.clarohome.core.design.LocalClaroSoftkeyHost
import com.clarohome.core.design.toLocale
import com.clarohome.core.design.withClaroLanguage
import java.util.Locale
import com.clarohome.core.domain.StartupRecoveryState
import com.clarohome.core.model.AppLanguage
import com.clarohome.core.model.BatteryStatus
import com.clarohome.core.model.CapabilityStatus
import com.clarohome.core.model.CellularStatus
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
import com.clarohome.feature.onboarding.settings.ResetClaroScreen
import com.clarohome.feature.onboarding.settings.SettingsHubScreen
import com.clarohome.feature.billing.observability.BillingObservabilityScreen
import com.clarohome.feature.billing.observability.PurchaseResult
import com.clarohome.feature.communication.CallOutcome
import com.clarohome.feature.communication.CommunicationRoute
import com.clarohome.feature.communication.CommunicationUiState
import com.clarohome.feature.communication.CallerIdentity
import com.clarohome.feature.communication.ContactWriteResult
import com.clarohome.feature.communication.MessagingUiState
import com.clarohome.feature.safety.media.apps.SosState
import com.clarohome.presentation.classic.ClassicIntegratedPad
import com.clarohome.presentation.classic.ClassicNumericKeypad
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
@Serializable private data class AppsRoute(val customize: Boolean = false)
@Serializable private data object LauncherMenuRoute
@Serializable private data class AccessGateRoute(val target: String = TARGET_SETTINGS)
@Serializable private data class PinRecoveryRoute(val target: String = TARGET_SETTINGS)
@Serializable private data object SettingsRoute
@Serializable private data object PhoneStyleRoute
@Serializable private data object DisplayLanguageRoute
@Serializable private data object AccessMethodRoute
@Serializable private data object ReadinessRoute
@Serializable private data object ResetRoute
@Serializable private data object PhoneRoute
@Serializable private data object MessagesRoute
@Serializable private data class SafetyRoute(
    val destination: SafetyMediaAppsDestination,
    val requiresAuth: Boolean = false,
)
@Serializable private data object MembershipRoute
@Serializable private data class PeopleSettingsRoute(val requiresAuth: Boolean)
@Serializable private data object MenuOrderRoute

private const val TARGET_SETTINGS = "settings"
private const val TARGET_SOS_SETTINGS = "sos_settings"
private const val TARGET_APPS_CUSTOMIZE = "apps_customize"
private const val TARGET_CONTACTS = "contacts"

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
    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        markPermissionRequested(CONTACTS_REQUESTED)
        communicationViewModel.refreshQualification()
        communicationViewModel.openContacts()
    }
    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        markPermissionRequested(MEDIA_REQUESTED)
    }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        markPermissionRequested(NOTIFICATIONS_REQUESTED)
        communicationViewModel.refreshQualification()
        messagingViewModel.refreshQualification()
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
                onRequestCapability = ::requestCapability,
            )
        }
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
        val qualification = communicationViewModel.state.value.qualification
        when {
            !qualification.roleHeld && roleRequest != null -> phoneRoleLauncher.launch(roleRequest)
            !qualification.roleHeld -> openDefaultAppsSettings()
            shouldOpenAppSettings(PHONE_PERMISSIONS, PHONE_PERMISSIONS_REQUESTED) ->
                openAppSettings()
            else -> requestPhonePermissions()
        }
    }

    private fun requestPhonePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
        )
        markPermissionRequested(PHONE_PERMISSIONS_REQUESTED)
        phonePermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun repairMessagesAccess() {
        val roleRequest = messagingViewModel.roleRequestIntent()
        val qualification = messagingViewModel.state.value.qualification
        when {
            !qualification.roleHeld && roleRequest != null -> smsRoleLauncher.launch(roleRequest)
            !qualification.roleHeld -> openDefaultAppsSettings()
            shouldOpenAppSettings(MESSAGE_PERMISSIONS, MESSAGE_PERMISSIONS_REQUESTED) ->
                openAppSettings()
            else -> requestMessagePermissions()
        }
    }

    private fun requestMessagePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_MMS,
        )
        markPermissionRequested(MESSAGE_PERMISSIONS_REQUESTED)
        messagePermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun requestContactsAccess() {
        if (shouldOpenAppSettings(CONTACT_PERMISSIONS, CONTACTS_REQUESTED)) {
            openAppSettings()
        } else {
            contactsPermissionLauncher.launch(CONTACT_PERMISSIONS)
        }
    }

    private fun requestMediaAccess() {
        val permissions = deviceMediaPermissions()
        if (shouldOpenAppSettings(permissions, MEDIA_REQUESTED)) {
            openAppSettings()
        } else {
            mediaPermissionLauncher.launch(permissions)
        }
    }

    private fun requestNotificationAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (shouldOpenAppSettings(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATIONS_REQUESTED)) {
            openAppSettings()
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestCapability(capability: ReadinessCapability) {
        when (capability) {
            ReadinessCapability.HOME -> {
                val intent = runCatching {
                    ViewModelProvider(this)[MainViewModel::class.java].homeRoleActionIntent()
                }.getOrNull()
                if (intent != null) startActivity(intent) else openDefaultAppsSettings()
            }
            ReadinessCapability.PHONE -> repairPhoneAccess()
            ReadinessCapability.MESSAGES -> repairMessagesAccess()
            ReadinessCapability.CONTACTS -> requestContactsAccess()
            ReadinessCapability.NOTIFICATIONS -> requestNotificationAccess()
            ReadinessCapability.MEDIA -> requestMediaAccess()
        }
    }

    private fun shouldOpenAppSettings(
        permissions: Array<String>,
        requestKey: String,
    ): Boolean =
        wasPermissionRequested(requestKey) &&
            permissions.any { permission ->
                ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED &&
                    !shouldShowRequestPermissionRationale(permission)
            }

    private fun openAppSettings() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null),
                ),
            )
        }.onFailure {
            val language = runCatching {
                ViewModelProvider(this)[MainViewModel::class.java].uiState.value.preferences.language
            }.getOrDefault(AppLanguage.ENGLISH)
            Toast.makeText(
                this,
                withClaroLanguage(language).getString(R.string.app_android_settings_unavailable),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun openDefaultAppsSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }.onFailure {
            openAppSettings()
        }
    }

    private fun markPermissionRequested(key: String) {
        getSharedPreferences(PERMISSION_HISTORY, MODE_PRIVATE).edit {
            putBoolean(key, true)
        }
    }

    private fun wasPermissionRequested(key: String): Boolean =
        getSharedPreferences(PERMISSION_HISTORY, MODE_PRIVATE).getBoolean(key, false)

    companion object {
        private const val PERMISSION_HISTORY = "permission_request_history"
        private const val PHONE_PERMISSIONS_REQUESTED = "phone"
        private const val MESSAGE_PERMISSIONS_REQUESTED = "messages"
        private const val CONTACTS_REQUESTED = "contacts"
        private const val NOTIFICATIONS_REQUESTED = "notifications"
        private const val MEDIA_REQUESTED = "media"
        private val PHONE_PERMISSIONS = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
        )
        private val MESSAGE_PERMISSIONS = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_MMS,
        )
        private val CONTACT_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
        )
    }
}

@Composable
private fun ClaroApp(
    communicationViewModel: CommunicationViewModel,
    messagingViewModel: MessagingViewModel,
    onRepairPhone: () -> Unit,
    onRepairMessages: () -> Unit,
    onRequestCapability: (ReadinessCapability) -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val communication by communicationViewModel.state.collectAsStateWithLifecycle()
    val messaging by messagingViewModel.state.collectAsStateWithLifecycle()
    val openPhoneRequest by communicationViewModel.openPhoneRequests.collectAsStateWithLifecycle()
    val sosState by viewModel.sosState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val context = LocalContext.current
    val localizedContext = remember(context, state.preferences.language) {
        context.withClaroLanguage(state.preferences.language)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
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
            platformNotice = localizedContext.getString(R.string.app_home_settings_unavailable)
        }
    }
    val launchPackage = { packageName: String ->
        val launchIntent = viewModel.appLaunchIntent(packageName)
        if (launchIntent == null) {
            platformNotice = localizedContext.getString(R.string.app_missing_or_disabled_notice)
        } else {
            try {
                context.startActivity(launchIntent)
            } catch (_: ActivityNotFoundException) {
                platformNotice = localizedContext.getString(R.string.app_could_not_open_notice)
            }
        }
    }
    val openAlarms = {
        try {
            context.startActivity(
                Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: ActivityNotFoundException) {
            platformNotice = localizedContext.getString(R.string.app_could_not_open_notice)
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
    val classicRouteOwnsDeck = currentBackStackEntry?.destination?.let { destination ->
        destination.hasRoute<ClassicHomeRoute>() ||
            destination.hasRoute<PhoneRoute>() ||
            destination.hasRoute<MessagesRoute>()
    } == true
    val isRecoveryOrOnboarding = currentBackStackEntry?.destination?.let { destination ->
        destination.hasRoute<RecoveryRoute>() ||
            destination.hasRoute<OnboardingRoute>()
    } == true
    val openPhoneFromHome = {
        val unopenedMissed = if ((communication.missedIncomingCount ?: 0) > 0) {
            communication.history.firstOrNull {
                it.outcome == CallOutcome.MISSED_INCOMING && !it.isOpened
            }
        } else {
            null
        }
        if (unopenedMissed != null) {
            communicationViewModel.openHistoryEntry(unopenedMissed.id)
        } else {
            communicationViewModel.openDialer()
        }
        navController.navigate(PhoneRoute)
    }

    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTick++
                Locale.setDefault(viewModel.uiState.value.preferences.language.toLocale())
                viewModel.refreshLauncherState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.preferences.language) {
        Locale.setDefault(state.preferences.language.toLocale())
        viewModel.refreshLauncherState()
    }

    LaunchedEffect(state.preferences.pendingVisualMode, runtimeBlockers) {
        if (state.preferences.pendingVisualMode != null && runtimeBlockers.isEmpty()) {
            viewModel.retryDeferredModeSwitch(runtimeBlockers) { mode ->
                navController.goHome(mode)
            }
        }
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
        resumeTick,
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
            if (
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CONTACTS,
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                CapabilityStatus.READY
            } else {
                CapabilityStatus.NEEDS_ATTENTION
            },
        )
        viewModel.setReadiness(
            ReadinessCapability.NOTIFICATIONS,
            if (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                CapabilityStatus.READY
            } else {
                CapabilityStatus.NEEDS_ATTENTION
            },
        )
        viewModel.setReadiness(
            ReadinessCapability.MEDIA,
            if (deviceMediaAccess(context) == DeviceMediaAccess.FULL) {
                CapabilityStatus.READY
            } else {
                CapabilityStatus.NEEDS_ATTENTION
            },
        )
    }

    ClaroLocaleContainer(language = state.preferences.language) {
        val privateNumberLabel = stringResource(R.string.app_private_number)
        val localizedHome = projectedHome?.let { home ->
            val call = home.activeCall
            if (call != null && call.displayName == "Private number") {
                home.copy(activeCall = call.copy(displayName = privateNumberLabel))
            } else {
                home
            }
        }
        ClaroTheme(
            textSize = state.preferences.textSize,
            highContrast = state.preferences.highContrast,
            hapticFeedback = state.preferences.hapticFeedback,
        ) {
            val softkeyHost = remember { ClaroSoftkeyHost() }
            val classicDeckShown = !isRecoveryOrOnboarding &&
                !classicRouteOwnsDeck &&
                state.preferences.visualMode != VisualMode.TOUCH
            CompositionLocalProvider(
                LocalClaroSoftkeyHost provides if (classicDeckShown) softkeyHost else null,
            ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(ClaroColors.Charcoal)
                    .systemBarsPadding()
                    .testTag("app-root"),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                NavHost(navController = navController, startDestination = RecoveryRoute) {
                    composable<RecoveryRoute> {
                        when (val startup = state.startup) {
                            StartupRecoveryState.Recovering -> LoadingScreen()
                            is StartupRecoveryState.Failed ->
                                FailureScreen(localizeStartupFailure(startup.reason))
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
                                onOpenPeopleSetup = {
                                    communicationViewModel.openContacts()
                                    navController.navigate(PeopleSettingsRoute(requiresAuth = false))
                                },
                                onOpenSosSetup = {
                                    communicationViewModel.openContacts()
                                    navController.navigate(
                                        SafetyRoute(
                                            destination = SafetyMediaAppsDestination.SOS_SETTINGS,
                                            requiresAuth = false,
                                        ),
                                    )
                                },
                                onRequestCapability = onRequestCapability,
                                onHandover = viewModel::setHandover,
                                onOpenAppsSetup = {
                                    navController.navigate(AppsRoute(customize = true))
                                },
                                onOpenPlans = {
                                    navController.navigate(MembershipRoute)
                                },
                                onHapticFeedbackChange = viewModel::setHapticFeedback,
                            ),
                        )
                    }
                    composable<TouchHomeRoute> {
                        localizedHome?.let { home ->
                            TouchHomeScreen(
                                home = home,
                                onUseClassicMode = { navController.openSettings(viewModel) },
                                onTileSelected = { tile ->
                                    handleTile(
                                        tile,
                                        openPhone = openPhoneFromHome,
                                        openMessages = {
                                            messagingViewModel.openInbox()
                                            navController.navigate(MessagesRoute)
                                        },
                                        openApps = { navController.navigate(AppsRoute()) },
                                        openSafetyFeature = { destination ->
                                            navController.navigate(SafetyRoute(destination))
                                        },
                                        openSettings = { navController.openSettings(viewModel) },
                                        launchApp = launchPackage,
                                        callContact = { contact ->
                                            communicationViewModel.requestCall(
                                                CallerIdentity(contact.label, contact.phoneNumber),
                                            )
                                            navController.navigate(PhoneRoute)
                                        },
                                        openAlarms = openAlarms,
                                    )
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
                        localizedHome?.let { home ->
                            ClassicPhoneScreen(
                                home = home,
                                onOpenMenu = { navController.navigate(LauncherMenuRoute) },
                                onTileSelected = { tile ->
                                    handleTile(
                                        tile,
                                        openPhone = openPhoneFromHome,
                                        openMessages = {
                                            messagingViewModel.openInbox()
                                            navController.navigate(MessagesRoute)
                                        },
                                        openApps = { navController.navigate(AppsRoute()) },
                                        openSafetyFeature = { destination ->
                                            navController.navigate(SafetyRoute(destination))
                                        },
                                        openSettings = { navController.openSettings(viewModel) },
                                        launchApp = launchPackage,
                                        callContact = { contact ->
                                            communicationViewModel.requestCall(
                                                CallerIdentity(contact.label, contact.phoneNumber),
                                            )
                                            navController.navigate(PhoneRoute)
                                        },
                                        openAlarms = openAlarms,
                                    )
                                },
                                onRepairHomeRole = launchHomeRole,
                                onReturnToCall = {
                                    communicationViewModel.returnToActiveCall()
                                    navController.navigate(PhoneRoute)
                                },
                                onDigitSelected = { digit ->
                                    communicationViewModel.openDialer()
                                    communicationViewModel.inputDigit(digit)
                                    navController.navigate(PhoneRoute)
                                },
                                onBack = navController::popBackStack,
                                onHome = { navController.goHome(state.preferences.visualMode) },
                            )
                        }
                    }
                    composable<AppsRoute> { entry ->
                        val customize = entry.toRoute<AppsRoute>().customize
                        if (customize && state.preferences.onboarding.currentStep == OnboardingStep.COMPLETE) {
                            ProtectedSessionMonitor(viewModel, navController)
                        }
                        state.home?.let { home ->
                            AppsMenuScreen(
                                apps = home.installedApps,
                                allowCustomization = customize,
                                onBack = navController::popBackStack,
                                onLaunch = { launchPackage(it.packageName) },
                                onSetAllowed = { app, allowed ->
                                    viewModel.setAppAllowed(app.packageName, allowed)
                                },
                                onSetOnHome = { app, onHome ->
                                    viewModel.setAppOnHome(app.packageName, onHome)
                                },
                                useGrid = state.preferences.visualMode == VisualMode.TOUCH,
                            )
                        }
                    }
                    composable<LauncherMenuRoute> {
                        state.home?.let { home ->
                            LauncherMenuScreen(
                                tiles = home.tiles,
                                menuOrder = home.menuOrder,
                                onBack = navController::popBackStack,
                                onContacts = {
                                    communicationViewModel.openContacts()
                                    navController.navigate(PhoneRoute)
                                },
                                onSettings = { navController.openSettings(viewModel) },
                                onTileSelected = { tile ->
                                    handleTile(
                                        tile,
                                        openPhone = openPhoneFromHome,
                                        openMessages = {
                                            messagingViewModel.openInbox()
                                            navController.navigate(MessagesRoute)
                                        },
                                        openApps = { navController.navigate(AppsRoute()) },
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
                            battery = localizedHome?.battery,
                            cellular = localizedHome?.cellular,
                            canManageContacts = viewModel.isSettingsUnlocked(),
                            onRepairPhone = onRepairPhone,
                            onEmergencyDialer = { viewModel.openEmergencyDialer() },
                            onMessageNumber = { number, _ ->
                                messagingViewModel.startNewMessage()
                                messagingViewModel.updateRecipientInput(number)
                                navController.navigate(MessagesRoute)
                            },
                            onSaveContact = { lookupKey, name, primary, secondary ->
                                communicationViewModel.saveContact(lookupKey, name, primary, secondary) { result ->
                                    platformNotice = when (result) {
                                        is ContactWriteResult.Saved -> null
                                        ContactWriteResult.PermissionDenied ->
                                            localizedContext.getString(R.string.app_contacts_permission_save)
                                        ContactWriteResult.AccountReadOnly ->
                                            localizedContext.getString(R.string.app_contact_account_read_only)
                                        is ContactWriteResult.Failed ->
                                            localizeContactFailureReason(result.reason, localizedContext)
                                    }
                                }
                            },
                            onDeleteContact = { lookupKey ->
                                communicationViewModel.deleteSelectedContact(lookupKey) { result ->
                                    platformNotice = when (result) {
                                        is ContactWriteResult.Saved -> null
                                        ContactWriteResult.PermissionDenied ->
                                            localizedContext.getString(R.string.app_contacts_permission_delete)
                                        ContactWriteResult.AccountReadOnly ->
                                            localizedContext.getString(R.string.app_contact_account_read_only)
                                        is ContactWriteResult.Failed ->
                                            localizeContactFailureReason(result.reason, localizedContext)
                                    }
                                }
                            },
                            onRequestUnlockForContacts = {
                                navController.openProtected(viewModel, TARGET_CONTACTS)
                            },
                            onHome = { navController.goHome(state.preferences.visualMode) },
                        )
                    }
                    composable<MessagesRoute> {
                        DisposableEffect(messagingViewModel) {
                            onDispose {
                                if (messagingViewModel.state.value.searchQuery.isNotEmpty()) {
                                    messagingViewModel.searchInbox("")
                                }
                            }
                        }
                        IntegratedMessagingScreen(
                            state = messaging,
                            visualMode = state.preferences.visualMode,
                            viewModel = messagingViewModel,
                            battery = localizedHome?.battery,
                            cellular = localizedHome?.cellular,
                            onRepairMessages = onRepairMessages,
                            onHome = { navController.goHome(state.preferences.visualMode) },
                        )
                    }
                    composable<SafetyRoute> { entry ->
                        val route = entry.toRoute<SafetyRoute>()
                        if (route.requiresAuth) {
                            ProtectedSessionMonitor(viewModel, navController)
                        }
                        SafetyMediaAppsScreen(
                            initialDestination = route.destination,
                            viewModel = viewModel,
                            onBack = navController::popBackStack,
                            contacts = communication.contacts,
                            onLoadContacts = {
                                onRequestCapability(ReadinessCapability.CONTACTS)
                            },
                            onOpenProtectedSosSettings = {
                                navController.openProtected(viewModel, TARGET_SOS_SETTINGS)
                            },
                            onRequestMediaAccess = {
                                onRequestCapability(ReadinessCapability.MEDIA)
                            },
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
                    composable<PeopleSettingsRoute> { entry ->
                        if (entry.toRoute<PeopleSettingsRoute>().requiresAuth) {
                            ProtectedSessionMonitor(viewModel, navController)
                        }
                        PeopleSettingsScreen(
                            homeSlots = state.home?.homeSlots.orEmpty(),
                            installedApps = state.home?.installedApps.orEmpty(),
                            contacts = communication.contacts,
                            assignments = communication.assignments,
                            onLoadContacts = {
                                onRequestCapability(ReadinessCapability.CONTACTS)
                            },
                            onSetSlot = viewModel::setHomeSlot,
                            onClearSlot = viewModel::clearHomeSlot,
                            onSetKey = { slot, contact, number ->
                                communicationViewModel.saveAssignment(slot, contact, number)
                                viewModel.setSpeedDialConfigured(slot, configured = true)
                            },
                            onClearKey = { slot ->
                                communicationViewModel.clearAssignment(slot)
                                viewModel.setSpeedDialConfigured(slot, configured = false)
                            },
                            onBack = navController::popBackStack,
                        )
                    }
                    composable<MenuOrderRoute> {
                        ProtectedSessionMonitor(viewModel, navController)
                        state.home?.let { home ->
                            MenuOrderScreen(
                                tiles = home.tiles,
                                savedOrder = home.menuOrder,
                                onOrderChanged = viewModel::setMenuOrder,
                                onBack = navController::popBackStack,
                            )
                        }
                    }
                    composable<AccessGateRoute> { entry ->
                        val target = entry.toRoute<AccessGateRoute>().target
                        DisposableEffect(Unit) {
                            onDispose { viewModel.clearMessage() }
                        }
                        AccessGateScreen(
                            mode = state.preferences.accessProtection.mode,
                            message = state.message,
                            onUnlock = { input ->
                                viewModel.unlock(input) {
                                    navController.navigateToUnlockedTarget(
                                        target = target,
                                        communicationViewModel = communicationViewModel,
                                        popGate = true,
                                    )
                                }
                            },
                            onRecoverPin = { navController.navigate(PinRecoveryRoute(target)) },
                        )
                    }
                    composable<PinRecoveryRoute> { entry ->
                        val target = entry.toRoute<PinRecoveryRoute>().target
                        PinRecoveryScreen(
                            message = state.message,
                            onRecover = { secret, pin, confirmation ->
                                viewModel.recoverPin(secret, pin, confirmation) {
                                    navController.navigateToUnlockedTarget(
                                        target = target,
                                        communicationViewModel = communicationViewModel,
                                        popGate = true,
                                    )
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
                                navController.navigate(PhoneStyleRoute)
                            },
                            onDisplayLanguage = {
                                navController.navigate(DisplayLanguageRoute)
                            },
                            onAccessMethod = {
                                navController.navigate(AccessMethodRoute)
                            },
                            onReadiness = {
                                navController.navigate(ReadinessRoute)
                            },
                            onHomeAndApps = {
                                navController.navigate(AppsRoute(customize = true))
                            },
                            onMenuOrder = {
                                navController.navigate(MenuOrderRoute)
                            },
                            onPeople = {
                                communicationViewModel.openContacts()
                                navController.navigate(PeopleSettingsRoute(requiresAuth = true))
                            },
                            onSafety = {
                                communicationViewModel.openContacts()
                                navController.navigate(
                                    SafetyRoute(
                                        destination = SafetyMediaAppsDestination.SOS_SETTINGS,
                                        requiresAuth = true,
                                    ),
                                )
                            },
                            onMembershipPrivacy = {
                                navController.navigate(MembershipRoute)
                            },
                            onDone = { navController.goHome(state.preferences.visualMode) },
                            onLockAndExit = {
                                navController.leaveSettingsForMenu(state.preferences.visualMode)
                                viewModel.lockSettings()
                            },
                            onResetClaro = {
                                navController.navigate(ResetRoute)
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
                                viewModel.saveLanguageDisplay(language, size, contrast)
                                navController.popBackStack()
                            },
                            onCancel = navController::popBackStack,
                            onApplyWithHaptics = { language, size, contrast, haptics ->
                                viewModel.saveLanguageDisplay(language, size, contrast, haptics)
                                navController.popBackStack()
                            },
                        )
                    }
                    composable<AccessMethodRoute> {
                        ProtectedSessionMonitor(viewModel, navController)
                        AccessMethodSettingsScreen(
                            accessMode = state.preferences.accessProtection.mode,
                            message = state.message,
                            onUseDateQuestion = {
                                viewModel.useDateQuestionForSettings(navController::popBackStack)
                            },
                            onConfigurePin = { pin, confirmation, recovery ->
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
                            onRequest = { capability ->
                                onRequestCapability(capability)
                            },
                            onBack = navController::popBackStack,
                        )
                    }
                    composable<ResetRoute> {
                        ProtectedSessionMonitor(viewModel, navController)
                        ResetClaroScreen(
                            hasActiveCall = communication.hasActiveCall,
                            onReturnToCall = {
                                communicationViewModel.returnToActiveCall()
                                navController.navigate(PhoneRoute)
                            },
                            onConfirmReset = {
                                communicationViewModel.clearAllAssignments()
                                messagingViewModel.clearAllDrafts()
                                viewModel.resetClaroLocalData {
                                    navController.navigate(OnboardingRoute) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            inclusive = true
                                        }
                                        launchSingleTop = true
                                    }
                                }
                            },
                            onCancel = navController::popBackStack,
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
                            Text(stringResource(R.string.app_dismiss))
                        }
                    }
                    }
                }
                if (!isRecoveryOrOnboarding && state.preferences.visualMode == VisualMode.TOUCH) {
                    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                    ProductNavigationBar(
                        onBack = {
                            if (navController.previousBackStackEntry != null && backDispatcher != null) {
                                backDispatcher.onBackPressed()
                            } else {
                                navController.goHome(state.preferences.visualMode)
                            }
                        },
                        onHome = { navController.goHome(state.preferences.visualMode) },
                    )
                } else if (!isRecoveryOrOnboarding && !classicRouteOwnsDeck) {
                    val classicBackDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                    ClassicGlobalDeck(
                        onBack = {
                            if (navController.previousBackStackEntry != null && classicBackDispatcher != null) {
                                classicBackDispatcher.onBackPressed()
                            } else {
                                navController.goHome(state.preferences.visualMode)
                            }
                        },
                        onCall = openPhoneFromHome,
                        onMenu = { navController.navigate(LauncherMenuRoute) },
                        onHome = { navController.goHome(state.preferences.visualMode) },
                        softkeyHost = softkeyHost,
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun ProductNavigationBar(
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    val haptics = LocalClaroHaptics.current
    val backIcon = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
        ClaroIconKind.ARROW_RIGHT
    } else {
        ClaroIconKind.ARROW_LEFT
    }
    Column {
        HorizontalDivider(thickness = 3.dp, color = ClaroColors.Charcoal)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .background(Color.White),
        ) {
            Button(
                onClick = {
                    haptics.softkey()
                    onBack()
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .claroFocusRing(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = ClaroColors.Charcoal,
                ),
                shape = RectangleShape,
            ) {
                ClaroIcon(
                    kind = backIcon,
                    tint = ClaroColors.Charcoal,
                    size = 24.dp,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Text(
                    stringResource(R.string.app_back),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            }
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .background(Color(0xFFD5DAE3))
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 1.dp),
            )
            Button(
                onClick = {
                    haptics.softkey()
                    onHome()
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .claroFocusRing(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = ClaroColors.Charcoal,
                ),
                shape = RectangleShape,
            ) {
                ClaroIcon(
                    kind = ClaroIconKind.HOME,
                    tint = ClaroColors.Charcoal,
                    size = 24.dp,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Text(
                    stringResource(R.string.app_home),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ClassicGlobalDeck(
    onBack: () -> Unit,
    onCall: () -> Unit,
    onMenu: () -> Unit,
    onHome: () -> Unit,
    softkeyHost: ClaroSoftkeyHost,
) {
    val view = LocalView.current
    val green = softkeyHost.actionFor(ClaroSoftkey.GREEN)
    val menu = softkeyHost.actionFor(ClaroSoftkey.MENU)
    val back = softkeyHost.actionFor(ClaroSoftkey.BACK)
    val sendKey: (Int) -> Unit = { keyCode ->
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClaroColors.Charcoal)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ClassicIntegratedPad(
            menuLabel = menu?.label ?: stringResource(R.string.app_menu),
            greenLabel = green?.label ?: "☎ ${stringResource(R.string.app_call)}",
            redLabel = "⌂ ${stringResource(R.string.app_home)}",
            backLabel = back?.label.orEmpty(),
            greenIcon = green?.let { it.icon ?: ClaroIconKind.CHECK },
            menuIcon = menu?.icon,
            greenColor = green?.color,
            greenEnabled = green?.enabled ?: true,
            menuEnabled = menu?.enabled ?: true,
            onBack = back?.onClick ?: onBack,
            onMenu = menu?.onClick ?: onMenu,
            onGreen = green?.onClick ?: onCall,
            onRed = onHome,
        )
        ClassicNumericKeypad(
            onDigitSelected = { digit ->
                sendKey(
                    when (digit) {
                        '*' -> KeyEvent.KEYCODE_STAR
                        '#' -> KeyEvent.KEYCODE_POUND
                        else -> KeyEvent.KEYCODE_0 + (digit - '0')
                    },
                )
            },
        )
    }
}

@Composable
private fun ProtectedSessionMonitor(
    viewModel: MainViewModel,
    navController: NavHostController,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            // A screen already navigating away (for example after "Lock settings") must not re-open the gate.
            if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) continue
            if (!viewModel.isSettingsUnlocked()) {
                navController.navigate(AccessGateRoute()) {
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
    openApps: () -> Unit,
    openSafetyFeature: (SafetyMediaAppsDestination) -> Unit,
    openSettings: () -> Unit,
    launchApp: (String) -> Unit,
    callContact: (HomeTile) -> Unit = {},
    openAlarms: () -> Unit = {},
) {
    when (tile.kind) {
        HomeTileKind.PHONE -> openPhone()
        HomeTileKind.MESSAGES -> openMessages()
        HomeTileKind.CAMERA -> openSafetyFeature(SafetyMediaAppsDestination.CAMERA)
        HomeTileKind.GALLERY -> openSafetyFeature(SafetyMediaAppsDestination.GALLERY)
        HomeTileKind.SOS -> openSafetyFeature(SafetyMediaAppsDestination.SOS)
        HomeTileKind.APPS -> openApps()
        HomeTileKind.INSTALLED_APP -> tile.packageName?.let(launchApp)
        HomeTileKind.CONTACT -> if (tile.phoneNumber != null) callContact(tile)
        HomeTileKind.ALARM -> openAlarms()
    }
    if (tile.label == "Settings") openSettings()
}

@Composable
private fun IntegratedCommunicationScreen(
    state: CommunicationUiState,
    visualMode: VisualMode,
    viewModel: CommunicationViewModel,
    battery: BatteryStatus?,
    cellular: CellularStatus?,
    canManageContacts: Boolean,
    onRepairPhone: () -> Unit,
    onEmergencyDialer: () -> Unit,
    onMessageNumber: (String, String?) -> Unit,
    onSaveContact: (String?, String, String, String) -> Unit,
    onDeleteContact: (String) -> Unit,
    onRequestUnlockForContacts: () -> Unit,
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
            onOpenHistoryEntry = callbacks.onOpenHistoryEntry,
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
            onMessageNumber = onMessageNumber,
            onEmergencyDialer = onEmergencyDialer,
            canManageContacts = canManageContacts,
            onSaveContact = onSaveContact,
            onDeleteContact = onDeleteContact,
            onRequestUnlockForContacts = onRequestUnlockForContacts,
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
            onOpenHistoryEntry = callbacks.onOpenHistoryEntry,
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
            battery = battery,
            cellular = cellular,
            onEmergencyDialer = onEmergencyDialer,
            onCallIdentity = viewModel::requestCall,
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
    val onOpenHistoryEntry = viewModel::openHistoryEntry
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
    battery: BatteryStatus?,
    cellular: CellularStatus?,
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
            onOpenRecipients = viewModel::openRecipientPicker,
            onCancelRecipients = viewModel::cancelRecipientPicker,
            onRecipientInputChange = viewModel::updateRecipientInput,
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
            battery = battery,
            cellular = cellular,
            onOpenRecipients = viewModel::openRecipientPicker,
            onCancelRecipients = viewModel::cancelRecipientPicker,
            onRecipientInputChange = viewModel::updateRecipientInput,
        )
    }
}

private fun NavHostController.openSettings(viewModel: MainViewModel) {
    openProtected(viewModel, TARGET_SETTINGS)
}

private fun NavHostController.openProtected(
    viewModel: MainViewModel,
    target: String,
) {
    viewModel.clearMessage()
    if (viewModel.isSettingsUnlocked()) {
        navigateToUnlockedTarget(target, communicationViewModel = null, popGate = false)
    } else {
        navigate(AccessGateRoute(target))
    }
}

private fun NavHostController.navigateToUnlockedTarget(
    target: String,
    communicationViewModel: CommunicationViewModel?,
    popGate: Boolean,
) {
    val destination: Any = when (target) {
        TARGET_SOS_SETTINGS -> {
            communicationViewModel?.openContacts()
            SafetyRoute(
                destination = SafetyMediaAppsDestination.SOS_SETTINGS,
                requiresAuth = true,
            )
        }
        TARGET_APPS_CUSTOMIZE -> AppsRoute(customize = true)
        TARGET_CONTACTS -> PhoneRoute
        else -> SettingsRoute
    }
    navigate(destination) {
        if (popGate) {
            popUpTo<AccessGateRoute> { inclusive = true }
        }
        launchSingleTop = true
    }
}

private fun NavHostController.leaveSettingsForMenu(mode: VisualMode) {
    if (mode == VisualMode.TOUCH) {
        goHome(mode)
    } else if (!popBackStack<LauncherMenuRoute>(inclusive = false)) {
        navigate(LauncherMenuRoute) {
            popUpTo<SettingsRoute> { inclusive = true }
            launchSingleTop = true
        }
    }
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

@Composable
private fun localizeStartupFailure(reason: String): String = when (reason) {
    "Unable to restore local state" -> stringResource(R.string.app_unable_to_restore_local_state)
    else -> reason
}

private fun localizeContactFailureReason(reason: String, context: Context): String = when (reason) {
    "Name and number are required" ->
        context.getString(R.string.app_contact_name_and_number_required)
    "Contact was not saved" ->
        context.getString(R.string.app_contact_not_saved)
    "Contact was saved but could not be opened" ->
        context.getString(R.string.app_contact_saved_could_not_open)
    "Contact not found" ->
        context.getString(R.string.app_contact_not_found)
    "Contact change failed" ->
        context.getString(R.string.app_contact_change_failed)
    "Replace the SOS person before deleting this contact" ->
        context.getString(R.string.app_contact_replace_sos_before_delete)
    else -> reason
}
