package com.clarohome.app

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.annotation.SuppressLint
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.clarohome.core.design.ClaroFocusVisibility
import com.clarohome.core.design.claroFocusRing
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroIcon
import com.clarohome.core.design.ClaroIconKind
import com.clarohome.core.design.ClaroKeyboardContainer
import com.clarohome.core.design.ClaroContactPhotos
import com.clarohome.core.design.LocalClaroKeypadInput
import com.clarohome.core.design.LocalClaroDeleteText
import com.clarohome.core.design.LocalClaroKeypadMode
import com.clarohome.core.design.LocalClaroSwitchKeypadLanguage
import com.clarohome.core.design.ClaroLocaleContainer
import com.clarohome.core.design.ClaroSoftkey
import com.clarohome.core.design.ClaroSoftkeyHost
import com.clarohome.core.design.ClaroTheme
import com.clarohome.core.design.LocalClaroHaptics
import com.clarohome.core.design.LocalClaroAppWidgetHost
import com.clarohome.core.design.LocalClaroSoftkeyHost
import com.clarohome.core.design.toLocale
import com.clarohome.core.design.withClaroLanguage
import java.util.Locale
import com.clarohome.core.domain.StartupRecoveryState
import com.clarohome.core.domain.TelemetryScreen
import com.clarohome.feature.communication.MessagingRoute
import com.clarohome.core.model.AppLanguage
import com.clarohome.core.model.BatteryStatus
import com.clarohome.core.model.CapabilityStatus
import com.clarohome.core.model.CellularStatus
import com.clarohome.core.model.HomeRoleState
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind
import com.clarohome.core.model.OnboardingStep
import com.clarohome.core.model.ReadinessCapability
import com.clarohome.core.model.defaultAppCapabilities
import com.clarohome.core.model.VisualMode
import com.clarohome.feature.onboarding.settings.AccessGateScreen
import com.clarohome.feature.onboarding.settings.AccessMethodSettingsScreen
import com.clarohome.feature.onboarding.settings.DisplayLanguageSettingsScreen
import com.clarohome.feature.onboarding.settings.OnboardingCallbacks
import com.clarohome.feature.onboarding.settings.OnboardingFlow
import com.clarohome.feature.onboarding.settings.CallAudioSettingsScreen
import com.clarohome.feature.onboarding.settings.PhoneStyleSettingsScreen
import com.clarohome.feature.onboarding.settings.PinRecoveryScreen
import com.clarohome.feature.onboarding.settings.ReadinessSettingsScreen
import com.clarohome.feature.onboarding.settings.ResetClaroScreen
import com.clarohome.feature.onboarding.settings.SettingsHubScreen
import com.clarohome.feature.billing.observability.BillingObservabilityScreen
import com.clarohome.feature.billing.observability.PurchaseResult
import com.clarohome.feature.communication.CallOutcome
import com.clarohome.feature.communication.CallDirection
import com.clarohome.feature.communication.CallPhase
import com.clarohome.feature.communication.CommunicationRoute
import com.clarohome.feature.communication.CommunicationUiState
import com.clarohome.feature.communication.CallerIdentity
import com.clarohome.feature.communication.ContactWriteResult
import com.clarohome.feature.communication.MessagingUiState
import com.clarohome.feature.safety.media.apps.SosState
import com.clarohome.feature.safety.media.apps.SosCallOutcome
import com.clarohome.presentation.classic.ClassicIntegratedPad
import com.clarohome.presentation.classic.ClassicNumericKeypad
import com.clarohome.presentation.classic.ClassicKeypadModeStatus
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
@Serializable internal data object TouchHomeRoute
@Serializable internal data object ClassicHomeRoute
@Serializable private data class AppsRoute(val customize: Boolean = false)
@Serializable internal data object LauncherMenuRoute
@Serializable private data class AccessGateRoute(val target: String = TARGET_SETTINGS)
@Serializable private data class PinRecoveryRoute(val target: String = TARGET_SETTINGS)
@Serializable private data object SettingsRoute
@Serializable private data object PhoneStyleRoute
@Serializable private data object DisplayLanguageRoute
@Serializable private data object AccessMethodRoute
@Serializable private data object ReadinessRoute
@Serializable private data object ResetRoute
@Serializable internal data object PhoneRoute
@Serializable internal data object MessagesRoute
@Serializable private data class SafetyRoute(
    val destination: SafetyMediaAppsDestination,
    val requiresAuth: Boolean = false,
    val fromHomeTile: Boolean = false,
)
@Serializable private data object MembershipRoute
@Serializable private data class PeopleSettingsRoute(val requiresAuth: Boolean)
@Serializable private data class HomeWidgetPickerRoute(val slot: Int, val requiresAuth: Boolean)
@Serializable private data object MenuOrderRoute
@Serializable private data object CallAudioRoute

private val FOCUS_KEYS = setOf(
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_TAB,
)
private const val TARGET_SETTINGS = "settings"
private const val TARGET_SOS_SETTINGS = "sos_settings"
private const val TARGET_APPS_CUSTOMIZE = "apps_customize"
private const val TARGET_CONTACTS = "contacts"

internal fun CommunicationRoute.telemetryScreen(): TelemetryScreen = when (this) {
    CommunicationRoute.DIALER -> TelemetryScreen.KEYPAD
    CommunicationRoute.CONTACTS -> TelemetryScreen.CONTACTS
    CommunicationRoute.CONTACT_DETAIL -> TelemetryScreen.CONTACT_DETAIL
    CommunicationRoute.CALL_CONFIRMATION -> TelemetryScreen.CALL_CONFIRMATION
    CommunicationRoute.INCOMING_CALL -> TelemetryScreen.INCOMING_CALL
    CommunicationRoute.ACTIVE_CALL -> TelemetryScreen.ACTIVE_CALL
    CommunicationRoute.CALL_ENDED -> TelemetryScreen.CALL_ENDED
    CommunicationRoute.MISSED_CALL -> TelemetryScreen.MISSED_CALL
    CommunicationRoute.CALL_UNANSWERED -> TelemetryScreen.CALL_UNANSWERED
    CommunicationRoute.CALL_FAILED -> TelemetryScreen.CALL_FAILED
    CommunicationRoute.EXTERNAL_HANDOFF -> TelemetryScreen.EXTERNAL_HANDOFF
}

internal fun MessagingRoute.telemetryScreen(): TelemetryScreen = when (this) {
    MessagingRoute.INBOX -> TelemetryScreen.MESSAGE_INBOX
    MessagingRoute.CONVERSATION -> TelemetryScreen.MESSAGE_CONVERSATION
    MessagingRoute.RECIPIENTS -> TelemetryScreen.MESSAGE_RECIPIENTS
    MessagingRoute.COMPOSE -> TelemetryScreen.MESSAGE_COMPOSE
    MessagingRoute.CONFIRMATION -> TelemetryScreen.MESSAGE_CONFIRMATION
    MessagingRoute.SENDING -> TelemetryScreen.MESSAGE_SENDING
    MessagingRoute.FAILED -> TelemetryScreen.MESSAGE_FAILED
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode in FOCUS_KEYS) ClaroFocusVisibility.visible = true
        return super.dispatchKeyEvent(event)
    }

    private val communicationViewModel by viewModels<CommunicationViewModel>()
    private val messagingViewModel by viewModels<MessagingViewModel>()
    private val mainViewModel by viewModels<MainViewModel>()
    private var settingUpDefaults = false
    private var openHomeRequests by mutableLongStateOf(0L)
    private var widgetSetupCompletions by mutableLongStateOf(0L)
    private val homeWidgetSetup by lazy {
        HomeWidgetSetup(
            activity = this,
            canEdit = {
                val state = mainViewModel.uiState.value
                state.startup is StartupRecoveryState.Ready &&
                    (state.preferences.onboarding.currentStep != OnboardingStep.COMPLETE ||
                        mainViewModel.isSettingsUnlocked())
            },
            onSetSlot = mainViewModel::setHomeSlot,
            onOutcome = mainViewModel::widgetSetupResult,
            onFinished = { widgetSetupCompletions++ },
            onNotice = { message ->
                val context = withClaroLanguage(mainViewModel.uiState.value.preferences.language)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            },
        )
    }
    private val homeRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        mainViewModel.refreshLauncherState()
        if (settingUpDefaults) finishDefaultRoleRequest(RoleManager.ROLE_HOME)
    }
    private val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        communicationViewModel.refreshQualification()
        communicationViewModel.openContacts()
        if (settingUpDefaults) continueDefaultAppSetup()
    }
    private val phoneRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        communicationViewModel.refreshQualification()
        if (settingUpDefaults && !isRoleHeld(RoleManager.ROLE_DIALER)) {
            settingUpDefaults = false
        } else {
            requestPhonePermissions()
        }
    }
    private val messagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        messagingViewModel.refreshQualification()
        messagingViewModel.openInbox()
        if (settingUpDefaults) continueDefaultAppSetup()
    }
    private val smsRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        messagingViewModel.refreshQualification()
        if (settingUpDefaults && !isRoleHeld(RoleManager.ROLE_SMS)) {
            settingUpDefaults = false
        } else {
            requestMessagePermissions()
        }
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
        homeWidgetSetup.restore(savedInstanceState?.getBundle("widget-setup"))
        settingUpDefaults = savedInstanceState?.getBoolean("setting-up-defaults") == true
        openHomeRequests = savedInstanceState?.getLong("open-home-requests") ?: 0L
        if (savedInstanceState == null && intent.isHomeRequest()) openHomeRequests++
        communicationViewModel.handleDialIntent(intent)
        messagingViewModel.handleSendToIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.BLACK,
                android.graphics.Color.BLACK,
            ),
        )
        setContent {
            ClaroApp(
                communicationViewModel = communicationViewModel,
                messagingViewModel = messagingViewModel,
                onRepairPhone = ::repairPhoneAccess,
                onRepairMessages = ::repairMessagesAccess,
                onRequestCapability = ::requestCapability,
                onSetDefaultApps = ::setupDefaultApps,
                openHomeRequest = openHomeRequests,
                widgetSetup = homeWidgetSetup,
                widgetSetupCompletions = widgetSetupCompletions,
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("setting-up-defaults", settingUpDefaults)
        outState.putLong("open-home-requests", openHomeRequests)
        outState.putBundle("widget-setup", homeWidgetSetup.save())
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        homeWidgetSetup.host.startListening()
    }

    override fun onStop() {
        homeWidgetSetup.host.stopListening()
        super.onStop()
    }

    override fun onDestroy() {
        homeWidgetSetup.destroy()
        super.onDestroy()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!homeWidgetSetup.onActivityResult(requestCode, resultCode)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun isRoleHeld(role: String): Boolean =
        getSystemService(RoleManager::class.java)?.isRoleHeld(role) == true

    private fun setupDefaultApps() {
        if (settingUpDefaults) return
        settingUpDefaults = true
        continueDefaultAppSetup()
    }

    private fun finishDefaultRoleRequest(role: String) {
        if (isRoleHeld(role)) continueDefaultAppSetup() else settingUpDefaults = false
    }

    private fun continueDefaultAppSetup() {
        val readiness = defaultAppReadiness()
        val next = defaultAppCapabilities.firstOrNull {
            readiness[it] == CapabilityStatus.NEEDS_ATTENTION
        }
        if (next == null) {
            settingUpDefaults = false
            mainViewModel.refreshLauncherState()
            communicationViewModel.refreshQualification()
            messagingViewModel.refreshQualification()
        } else {
            try {
                val manager = getSystemService(RoleManager::class.java)
                val role = when (next) {
                    ReadinessCapability.HOME -> RoleManager.ROLE_HOME
                    ReadinessCapability.PHONE -> RoleManager.ROLE_DIALER
                    ReadinessCapability.MESSAGES -> RoleManager.ROLE_SMS
                    else -> error("Unsupported default app: $next")
                }
                if (manager == null) {
                    settingUpDefaults = false
                    openDefaultAppsSettings()
                    return
                }
                val request = manager.createRequestRoleIntent(role)
                when (next) {
                    ReadinessCapability.HOME -> homeRoleLauncher.launch(request)
                    ReadinessCapability.PHONE -> phoneRoleLauncher.launch(request)
                    ReadinessCapability.MESSAGES -> smsRoleLauncher.launch(request)
                    else -> error("Unsupported default app: $next")
                }
            } catch (_: ActivityNotFoundException) {
                settingUpDefaults = false
                openDefaultAppsSettings()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.isHomeRequest()) openHomeRequests++
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
                try {
                    homeRoleLauncher.launch(mainViewModel.homeRoleActionIntent())
                } catch (_: ActivityNotFoundException) {
                    openDefaultAppsSettings()
                }
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
    onSetDefaultApps: () -> Unit,
    openHomeRequest: Long,
    widgetSetup: HomeWidgetSetup,
    widgetSetupCompletions: Long,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val communication by communicationViewModel.state.collectAsStateWithLifecycle()
    val messaging by messagingViewModel.state.collectAsStateWithLifecycle()
    val openPhoneRequest by communicationViewModel.openPhoneRequests.collectAsStateWithLifecycle()
    val openMessageRequest by messagingViewModel.openMessageRequests.collectAsStateWithLifecycle()
    val sosState by viewModel.sosState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val homeTileScrollState = rememberScrollState()
    val dialerScrollState = rememberScrollState()
    var dialerOptionsOpen by rememberSaveable { mutableStateOf(false) }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    var touchDialerScreen by remember(communication.dialerSearchGeneration) {
        mutableStateOf<TelemetryScreen?>(null)
    }
    val destination = currentBackStackEntry?.destination
    val screen = when {
        destination == null -> null
        destination.hasRoute<TouchHomeRoute>() || destination.hasRoute<ClassicHomeRoute>() -> TelemetryScreen.HOME
        destination.hasRoute<OnboardingRoute>() -> TelemetryScreen.ONBOARDING
        destination.hasRoute<RecoveryRoute>() -> TelemetryScreen.RECOVERY
        destination.hasRoute<PhoneRoute>() -> if (
            communication.route == CommunicationRoute.DIALER && state.preferences.visualMode == VisualMode.TOUCH
        ) touchDialerScreen else communication.route.telemetryScreen()
        destination.hasRoute<MessagesRoute>() -> messaging.route.telemetryScreen()
        destination.hasRoute<MembershipRoute>() -> TelemetryScreen.MEMBERSHIP
        destination.hasRoute<AppsRoute>() -> TelemetryScreen.APPS
        destination.hasRoute<LauncherMenuRoute>() -> TelemetryScreen.MENU
        destination.hasRoute<PeopleSettingsRoute>() -> TelemetryScreen.HOME_TILES
        destination.hasRoute<HomeWidgetPickerRoute>() -> TelemetryScreen.WIDGET_PICKER
        destination.hasRoute<SettingsRoute>() -> TelemetryScreen.SETTINGS
        destination.hasRoute<PhoneStyleRoute>() -> TelemetryScreen.PHONE_STYLE
        destination.hasRoute<DisplayLanguageRoute>() -> TelemetryScreen.DISPLAY_SETTINGS
        destination.hasRoute<AccessMethodRoute>() -> TelemetryScreen.ACCESS_SETTINGS
        destination.hasRoute<ReadinessRoute>() -> TelemetryScreen.PERMISSIONS
        destination.hasRoute<CallAudioRoute>() -> TelemetryScreen.CALL_AUDIO
        destination.hasRoute<MenuOrderRoute>() -> TelemetryScreen.MENU_ORDER
        destination.hasRoute<ResetRoute>() -> TelemetryScreen.RESET
        else -> null
    }
    val onboardingStep = state.preferences.onboarding.currentStep.takeIf { screen == TelemetryScreen.ONBOARDING }
    LaunchedEffect(currentBackStackEntry?.id, screen, onboardingStep, state.billingObservability.consent) {
        val entry = currentBackStackEntry
        if (entry != null && screen != null) viewModel.screenViewed(entry.id, screen, onboardingStep)
    }
    val context = LocalContext.current
    LaunchedEffect(state.startup, state.home?.homeSlots) {
        if (state.startup is StartupRecoveryState.Ready) {
            state.home?.let { widgetSetup.reconcile(it.homeSlots) }
        }
    }
    LaunchedEffect(widgetSetupCompletions) {
        if (widgetSetupCompletions > 0 && navController.currentDestination?.hasRoute<HomeWidgetPickerRoute>() == true) {
            navController.popBackStack()
        }
    }
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
        } catch (_: SecurityException) {
            platformNotice = localizedContext.getString(R.string.app_could_not_open_notice)
        }
    }
    val projectedHome = state.home?.withCommunicationProjections(communication, messaging)
    val runtimeBlockers = integrationBlockers(
        hasActiveCall = communication.hasActiveCall,
        sosCritical = sosState is SosState.CountingDown ||
            (sosState as? SosState.Calling)?.let { it.outcome != SosCallOutcome.ENDED } == true,
        purchaseCritical =
            state.billingObservability.purchaseResult == PurchaseResult.Purchasing ||
                state.billingObservability.purchaseResult == PurchaseResult.Pending,
    )
    var sosCallId by remember((sosState as? SosState.Calling)?.activationId) { mutableStateOf<String?>(null) }
    LaunchedEffect(sosState, communication.call) {
        val sos = sosState as? SosState.Calling ?: return@LaunchedEffect
        val call = communication.call ?: return@LaunchedEffect
        if (call.direction != CallDirection.OUTGOING ||
            !android.telephony.PhoneNumberUtils.compare(call.identity.number, sos.contact.destination)
        ) return@LaunchedEffect
        if (sosCallId == null && call.phase != CallPhase.DISCONNECTED) sosCallId = call.id
        if (sosCallId != call.id) return@LaunchedEffect
        val outcome = when (call.phase) {
            CallPhase.DIALING -> SosCallOutcome.DIALING
            CallPhase.RINGING -> SosCallOutcome.RINGING
            CallPhase.CONNECTED, CallPhase.ON_HOLD -> SosCallOutcome.CONNECTED
            CallPhase.DISCONNECTED -> when (call.outcome) {
                CallOutcome.UNANSWERED_OUTGOING -> SosCallOutcome.NO_ANSWER
                CallOutcome.FAILED -> SosCallOutcome.UNKNOWN_FAILURE
                else -> SosCallOutcome.ENDED
            }
            else -> return@LaunchedEffect
        }
        if (sos.outcome != outcome) viewModel.updateSosCallOutcome(outcome)
    }
    val isSharedDialer = currentBackStackEntry?.destination?.hasRoute<PhoneRoute>() == true &&
        communication.route == CommunicationRoute.DIALER
    LaunchedEffect(isSharedDialer) {
        dialerOptionsOpen = false
        dialerScrollState.scrollTo(0)
    }
    LaunchedEffect(dialerOptionsOpen) { dialerScrollState.scrollTo(0) }
    val isRecoveryOrOnboarding = currentBackStackEntry?.destination?.let { destination ->
        destination.hasRoute<RecoveryRoute>() ||
            destination.hasRoute<OnboardingRoute>() ||
            state.preferences.onboarding.isInitialSetup &&
            (destination.hasRoute<PeopleSettingsRoute>() || destination.hasRoute<SafetyRoute>())
    } == true
    val openPhoneFromHome = {
        communicationViewModel.openDialer(showMissedCalls = true)
        navController.navigateIfNotCurrent(PhoneRoute)
    }
    val navigateHome = {
        if (state.preferences.onboarding.isInitialSetup) {
            navController.navigateIfNotCurrent(OnboardingRoute)
        } else {
            navController.goHome(state.preferences.visualMode)
        }
    }

    var resumeTick by remember { mutableIntStateOf(0) }
    val defaultApps = remember(
        resumeTick, projectedHome?.homeRole,
        communication.qualification.roleHeld, messaging.qualification.roleHeld,
    ) { context.defaultAppReadiness() }
    LaunchedEffect(defaultApps) { viewModel.trackReadiness(defaultApps) }
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

    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(5_000L)
                viewModel.refreshLauncherStatus()
            }
        }
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

    var handledOpenHomeRequest by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(openHomeRequest, state.startup) {
        if (state.startup is StartupRecoveryState.Ready && openHomeRequest > handledOpenHomeRequest) {
            handledOpenHomeRequest = openHomeRequest
            if (state.preferences.onboarding.currentStep == OnboardingStep.COMPLETE) {
                navController.goHome(state.preferences.visualMode)
            }
        }
    }

    var handledOpenPhoneRequest by rememberSaveable { mutableLongStateOf(0L) }
    var handledOpenMessageRequest by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(openMessageRequest, state.startup) {
        if (state.startup is StartupRecoveryState.Ready && openMessageRequest > handledOpenMessageRequest) {
            handledOpenMessageRequest = openMessageRequest
            navController.navigateIfNotCurrent(MessagesRoute)
        }
    }
    LaunchedEffect(communication.route, openPhoneRequest, state.startup) {
        if (
            state.startup is StartupRecoveryState.Ready &&
            (communication.route == CommunicationRoute.INCOMING_CALL ||
                communication.route == CommunicationRoute.ACTIVE_CALL ||
                openPhoneRequest > handledOpenPhoneRequest)
        ) {
            handledOpenPhoneRequest = openPhoneRequest
            navController.navigateIfNotCurrent(PhoneRoute)
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
            ClaroContactPhotos {
            ClaroKeyboardContainer(
                manualKeyboard = state.preferences.visualMode == VisualMode.CLASSIC &&
                    !isRecoveryOrOnboarding,
            ) {
            CompositionLocalProvider(
                LocalClaroAppWidgetHost provides widgetSetup.host,
            ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .systemBarsPadding()
                    .background(ClaroColors.Charcoal)
                    .testTag("app-root"),
            ) {
                ActiveCallReturnBar(
                    state = communication,
                    isPhoneScreen = currentBackStackEntry?.destination?.hasRoute<PhoneRoute>() == true,
                    onReturnToCall = {
                        communicationViewModel.returnToActiveCall()
                        navController.navigateIfNotCurrent(PhoneRoute)
                    },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                val frame by rememberUpdatedState<DestinationFrame> { entry, content ->
                    val destination = entry.destination
                    val isSetup = destination.hasRoute<OnboardingRoute>()
                    val hideNavigation = destination.hasRoute<RecoveryRoute>() ||
                        state.preferences.onboarding.isInitialSetup &&
                        (isSetup || destination.hasRoute<PeopleSettingsRoute>() || destination.hasRoute<SafetyRoute>())
                    val isPhone = destination.hasRoute<PhoneRoute>()
                    val isDialer = isPhone && communication.route == CommunicationRoute.DIALER
                    val isHome = destination.hasRoute<ClassicHomeRoute>()
                    val mode = state.preferences.visualMode
                    val useGlobalDeck = !hideNavigation &&
                        usesClassicDestinationDeck(entry, mode, communication.route)
                    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                    val goHome = navigateHome
                    val navigateBack = {
                        if (navController.previousBackStackEntry != null && backDispatcher != null) {
                            backDispatcher.onBackPressed()
                        } else {
                            goHome()
                        }
                    }
                    ClaroDestinationLayout(useGlobalDeck, content) { host ->
                        if (!hideNavigation && (mode == VisualMode.TOUCH || isSetup)) {
                            ProductNavigationBar(onBack = navigateBack, onHome = goHome)
                        } else if (host != null) {
                            ClassicGlobalDeck(
                                onBack = {
                                    if (isDialer && dialerOptionsOpen) {
                                        dialerOptionsOpen = false
                                    } else if (isDialer && communication.digits.isNotEmpty()) {
                                        communicationViewModel.backspace()
                                    } else {
                                        navigateBack()
                                    }
                                },
                                onCall = if (isDialer) communicationViewModel::requestEnteredNumberCall else openPhoneFromHome,
                                onMenu = {
                                    if (isDialer) dialerOptionsOpen = !dialerOptionsOpen
                                    else navController.navigateIfNotCurrent(LauncherMenuRoute)
                                },
                                onHome = goHome,
                                softkeyHost = host,
                                screenKey = entry.id,
                                scrollState = when {
                                    isHome -> homeTileScrollState
                                    isDialer -> dialerScrollState
                                    else -> null
                                },
                                menuLabelOverride = if (isDialer) {
                                    stringResource(com.clarohome.presentation.classic.R.string.classic_options)
                                } else null,
                                keypadEnabled = !isDialer || !dialerOptionsOpen,
                                callEnabled = !isDialer || !dialerOptionsOpen,
                                allowInternationalPrefix = !communication.hasActiveCall &&
                                    (isHome || isDialer && communication.digits.isEmpty() && !communication.awaitingOutgoingCall),
                                onDigitSelected = when {
                                    isDialer -> communicationViewModel::inputDigit
                                    isHome -> { digit ->
                                        if (navController.currentDestination?.hasRoute<PhoneRoute>() != true) {
                                            communicationViewModel.openDialer()
                                        }
                                        communicationViewModel.inputDigit(digit)
                                        navController.navigateIfNotCurrent(PhoneRoute)
                                    }
                                    else -> null
                                },
                            )
                        }
                    }
                }
                val destinationFrame: DestinationFrame = remember { { entry, content -> frame(entry, content) } }
                NavHost(
                    navController = navController,
                    startDestination = RecoveryRoute,
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None },
                ) {
                    framedComposable<RecoveryRoute>(destinationFrame) {
                        when (val startup = state.startup) {
                            StartupRecoveryState.Recovering -> LoadingScreen()
                            is StartupRecoveryState.Failed ->
                                FailureScreen(localizeStartupFailure(startup.reason))
                            is StartupRecoveryState.Ready -> LoadingScreen()
                        }
                    }
                    framedComposable<OnboardingRoute>(destinationFrame) {
                        AppOnboardingFlow(
                            state = state,
                            communication = communication,
                            viewModel = viewModel,
                            communicationViewModel = communicationViewModel,
                            defaultAppReadiness = defaultApps,
                            callbacks = OnboardingCallbacks(
                                onDiagnosticsConsent = viewModel::saveOnboardingDiagnosticsConsent,
                                onContinue = { next ->
                                    viewModel.clearMessage()
                                    viewModel.advanceOnboarding(next) {
                                        navController.goHome(state.preferences.visualMode)
                                    }
                                },
                                onChooseMode = viewModel::chooseVisualMode,
                                onLanguageDisplay = viewModel::saveLanguageDisplay,
                                onUseDateQuestion = viewModel::useDateQuestion,
                                onConfigurePin = viewModel::configurePin,
                                onOpenPeopleSetup = {
                                    communicationViewModel.openContacts()
                                    navController.navigateIfNotCurrent(PeopleSettingsRoute(requiresAuth = false))
                                },
                                onOpenSosSetup = {
                                    communicationViewModel.openContacts()
                                    navController.navigateIfNotCurrent(
                                        SafetyRoute(
                                            destination = SafetyMediaAppsDestination.SOS_SETTINGS,
                                            requiresAuth = false,
                                        ),
                                    )
                                },
                                onRequestCapability = onRequestCapability,
                                onHomeTileVisibility = viewModel::setHomeTileVisibility,
                                onHapticFeedbackChange = viewModel::setHapticFeedback,
                                onSetDefaultApps = onSetDefaultApps,
                                onInstalledAppVisibility = viewModel::setAppOnHome,
                            ),
                        )
                    }
                    framedComposable<TouchHomeRoute>(destinationFrame) {
                        localizedHome?.let { home ->
                            TouchHomeScreen(
                                home = home,
                                onUseClassicMode = { navController.openSettings(viewModel) },
                                onTileSelected = { tile ->
                                    viewModel.homeTileOpened(tile)
                                    handleTile(
                                        tile,
                                        openPhone = openPhoneFromHome,
                                        openMessages = {
                                            messagingViewModel.openInbox()
                                            navController.navigateIfNotCurrent(MessagesRoute)
                                        },
                                        openApps = { navController.navigateIfNotCurrent(AppsRoute()) },
                                        openSafetyFeature = { destination ->
                                            navController.navigateIfNotCurrent(SafetyRoute(destination, fromHomeTile = true))
                                        },
                                        openSettings = { navController.openSettings(viewModel) },
                                        launchApp = launchPackage,
                                        callContact = { contact ->
                                            communicationViewModel.requestCall(
                                                CallerIdentity(contact.label, contact.phoneNumber, photoUri = contact.photoUri),
                                            )
                                            navController.navigateIfNotCurrent(PhoneRoute)
                                        },
                                        openAlarms = openAlarms,
                                    )
                                },
                                onRepairHomeRole = launchHomeRole,
                                onReturnToCall = {
                                    communicationViewModel.returnToActiveCall()
                                    navController.navigateIfNotCurrent(PhoneRoute)
                                },
                            )
                        }
                    }
                    framedComposable<ClassicHomeRoute>(destinationFrame) {
                        localizedHome?.let { home ->
                            ClassicPhoneScreen(
                                home = home,
                                showControls = false,
                                tileScrollState = homeTileScrollState,
                                onOpenMenu = { navController.navigateIfNotCurrent(LauncherMenuRoute) },
                                onTileSelected = { tile ->
                                    viewModel.homeTileOpened(tile)
                                    handleTile(
                                        tile,
                                        openPhone = openPhoneFromHome,
                                        openMessages = {
                                            messagingViewModel.openInbox()
                                            navController.navigateIfNotCurrent(MessagesRoute)
                                        },
                                        openApps = { navController.navigateIfNotCurrent(AppsRoute()) },
                                        openSafetyFeature = { destination ->
                                            navController.navigateIfNotCurrent(SafetyRoute(destination, fromHomeTile = true))
                                        },
                                        openSettings = { navController.openSettings(viewModel) },
                                        launchApp = launchPackage,
                                        callContact = { contact ->
                                            communicationViewModel.requestCall(
                                                CallerIdentity(contact.label, contact.phoneNumber, photoUri = contact.photoUri),
                                            )
                                            navController.navigateIfNotCurrent(PhoneRoute)
                                        },
                                        openAlarms = openAlarms,
                                    )
                                },
                                onRepairHomeRole = launchHomeRole,
                                onReturnToCall = {
                                    communicationViewModel.returnToActiveCall()
                                    navController.navigateIfNotCurrent(PhoneRoute)
                                },
                                onBack = navController::popBackStack,
                                onHome = navigateHome,
                            )
                        }
                    }
                    framedComposable<AppsRoute>(destinationFrame) { entry ->
                        val customize = entry.toRoute<AppsRoute>().customize
                        if (customize && state.preferences.onboarding.currentStep == OnboardingStep.COMPLETE) {
                            ProtectedSessionMonitor(viewModel, navController)
                        }
                        state.home?.let { home ->
                            AppsMenuScreen(
                                apps = home.installedApps,
                                message = state.message,
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
                    framedComposable<LauncherMenuRoute>(destinationFrame) {
                        projectedHome?.let { home ->
                            LauncherMenuScreen(
                                tiles = home.tiles,
                                menuOrder = home.menuOrder,
                                onBack = navController::popBackStack,
                                onContacts = {
                                    communicationViewModel.openContacts(fromDialer = false)
                                    navController.navigateIfNotCurrent(PhoneRoute)
                                },
                                onSettings = { navController.openSettings(viewModel) },
                                onTileSelected = { tile ->
                                    handleTile(
                                        tile,
                                        openPhone = openPhoneFromHome,
                                        openMessages = {
                                            messagingViewModel.openInbox()
                                            navController.navigateIfNotCurrent(MessagesRoute)
                                        },
                                        openApps = { navController.navigateIfNotCurrent(AppsRoute()) },
                                        openSafetyFeature = { destination ->
                                            navController.navigateIfNotCurrent(SafetyRoute(destination))
                                        },
                                        openSettings = { navController.openSettings(viewModel) },
                                        launchApp = launchPackage,
                                    )
                                },
                                onUseTouchStyle = { navController.openSettings(viewModel) },
                            )
                        }
                    }
                    framedComposable<PhoneRoute>(destinationFrame) {
                        IntegratedCommunicationScreen(
                            state = communication,
                            visualMode = state.preferences.visualMode,
                            viewModel = communicationViewModel,
                            sharedDialerControls = true,
                            dialerOptionsOpen = dialerOptionsOpen,
                            onDialerOptionsChange = { dialerOptionsOpen = it },
                            onDialerScreenChanged = { touchDialerScreen = it },
                            dialerScrollState = dialerScrollState,
                            battery = localizedHome?.battery,
                            cellular = localizedHome?.cellular,
                            canManageContacts = viewModel.isSettingsUnlocked(),
                            onRepairPhone = onRepairPhone,
                            onMessageNumber = { number, name ->
                                messagingViewModel.startMessageTo(number, name)
                                navController.navigateIfNotCurrent(MessagesRoute)
                            },
                            onSaveContact = saveContact@{ lookupKey, name, primary, secondary, photoUri ->
                                if (!viewModel.isSettingsUnlocked()) {
                                    navController.openProtected(viewModel, TARGET_CONTACTS)
                                    return@saveContact
                                }
                                communicationViewModel.saveContact(lookupKey, name, primary, secondary, photoUri) { result ->
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
                            onDeleteContact = deleteContact@{ lookupKey ->
                                if (!viewModel.isSettingsUnlocked()) {
                                    navController.openProtected(viewModel, TARGET_CONTACTS)
                                    return@deleteContact
                                }
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
                            onHome = navigateHome,
                        )
                    }
                    framedComposable<MessagesRoute>(destinationFrame) {
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
                            onHome = navigateHome,
                        )
                    }
                    framedComposable<SafetyRoute>(destinationFrame) { entry ->
                        val route = entry.toRoute<SafetyRoute>()
                        if (route.requiresAuth) {
                            ProtectedSessionMonitor(viewModel, navController)
                        }
                        SafetyMediaAppsScreen(
                            initialDestination = route.destination,
                            onScreenViewed = { safetyScreen ->
                                if (navController.currentBackStackEntry?.id == entry.id) {
                                    viewModel.screenViewed(entry.id, safetyScreen)
                                }
                            },
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
                            hasActiveCall = communication.hasActiveCall,
                            onReturnToCall = {
                                communicationViewModel.returnToActiveCall()
                                navController.navigateIfNotCurrent(PhoneRoute)
                            },
                            onSearchContacts = communicationViewModel::searchContacts,
                            onLoadMoreContacts = communicationViewModel::loadMoreContacts,
                            hasMoreContacts = communication.contactsNextOffset != null && !communication.contactsLoading,
                        )
                    }
                    framedComposable<MembershipRoute>(destinationFrame) {
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
                    framedComposable<PeopleSettingsRoute>(destinationFrame) { entry ->
                        if (entry.toRoute<PeopleSettingsRoute>().requiresAuth) {
                            ProtectedSessionMonitor(viewModel, navController)
                        }
                        PeopleSettingsScreen(
                            homeSlots = state.home?.homeSlots.orEmpty(),
                            message = state.message,
                            installedApps = state.home?.installedApps.orEmpty(),
                            contacts = communication.contacts,
                            assignments = communication.assignments,
                            onLoadContacts = {
                                onRequestCapability(ReadinessCapability.CONTACTS)
                            },
                            onSetSlot = viewModel::setHomeSlot,
                            onClearSlot = viewModel::clearHomeSlot,
                            onMoveTile = viewModel::moveHomeTile,
                            onSetKey = { slot, contact, number ->
                                communicationViewModel.saveAssignment(slot, contact, number)
                                viewModel.setSpeedDialConfigured(slot, configured = true)
                            },
                            onClearKey = { slot ->
                                communicationViewModel.clearAssignment(slot)
                                viewModel.setSpeedDialConfigured(slot, configured = false)
                            },
                            onBack = navController::popBackStack,
                            onChooseWidget = { slot ->
                                navController.navigateIfNotCurrent(
                                    HomeWidgetPickerRoute(slot, entry.toRoute<PeopleSettingsRoute>().requiresAuth),
                                )
                            },
                        )
                    }
                    framedComposable<HomeWidgetPickerRoute>(destinationFrame) { entry ->
                        val route = entry.toRoute<HomeWidgetPickerRoute>()
                        if (route.requiresAuth) ProtectedSessionMonitor(viewModel, navController)
                        HomeWidgetPickerScreen(
                            visualMode = state.preferences.visualMode,
                            onSelect = { widgetSetup.begin(route.slot, it) },
                            onBack = navController::popBackStack,
                        )
                    }
                    framedComposable<MenuOrderRoute>(destinationFrame) {
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
                    framedComposable<AccessGateRoute>(destinationFrame) { entry ->
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
                            onRecoverPin = { navController.navigateIfNotCurrent(PinRecoveryRoute(target)) },
                        )
                    }
                    framedComposable<PinRecoveryRoute>(destinationFrame) { entry ->
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
                    framedComposable<SettingsRoute>(destinationFrame) {
                        ProtectedSessionMonitor(viewModel, navController)
                        SettingsHubScreen(
                            preferences = state.preferences,
                            appVersion = viewModel.appVersion,
                            telemetryUserId = state.telemetryUserId,
                            onCallAudio = { navController.navigateIfNotCurrent(CallAudioRoute) },
                            onPhoneStyle = {
                                navController.navigateIfNotCurrent(PhoneStyleRoute)
                            },
                            onDisplayLanguage = {
                                navController.navigateIfNotCurrent(DisplayLanguageRoute)
                            },
                            onAccessMethod = {
                                navController.navigateIfNotCurrent(AccessMethodRoute)
                            },
                            onReadiness = {
                                navController.navigateIfNotCurrent(ReadinessRoute)
                            },
                            onHomeAndApps = {
                                navController.navigateIfNotCurrent(AppsRoute(customize = true))
                            },
                            onMenuOrder = {
                                navController.navigateIfNotCurrent(MenuOrderRoute)
                            },
                            onPeople = {
                                communicationViewModel.openContacts()
                                navController.navigateIfNotCurrent(PeopleSettingsRoute(requiresAuth = true))
                            },
                            onSafety = {
                                communicationViewModel.openContacts()
                                navController.navigateIfNotCurrent(
                                    SafetyRoute(
                                        destination = SafetyMediaAppsDestination.SOS_SETTINGS,
                                        requiresAuth = true,
                                    ),
                                )
                            },
                            onMembershipPrivacy = {
                                navController.navigateIfNotCurrent(MembershipRoute)
                            },
                            onDone = { navController.goHome(state.preferences.visualMode) },
                            onLockAndExit = {
                                navController.leaveSettingsForMenu(state.preferences.visualMode)
                                viewModel.lockSettings()
                            },
                            onResetClaro = {
                                navController.navigateIfNotCurrent(ResetRoute)
                            },
                        )
                    }
                    framedComposable<CallAudioRoute>(destinationFrame) {
                        ProtectedSessionMonitor(viewModel, navController)
                        CallAudioSettingsScreen(
                            preferences = state.preferences,
                            onChange = viewModel::setCallAudio,
                            onBack = navController::popBackStack,
                        )
                    }
                    framedComposable<PhoneStyleRoute>(destinationFrame) {
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
                    framedComposable<DisplayLanguageRoute>(destinationFrame) {
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
                    framedComposable<AccessMethodRoute>(destinationFrame) {
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
                    framedComposable<ReadinessRoute>(destinationFrame) {
                        ProtectedSessionMonitor(viewModel, navController)
                        ReadinessSettingsScreen(
                            readiness = state.preferences.onboarding.readiness,
                            onRequest = { capability ->
                                onRequestCapability(capability)
                            },
                            onBack = navController::popBackStack,
                        )
                    }
                    framedComposable<ResetRoute>(destinationFrame) {
                        ProtectedSessionMonitor(viewModel, navController)
                        ResetClaroScreen(
                            hasActiveCall = communication.hasActiveCall,
                            onReturnToCall = {
                                communicationViewModel.returnToActiveCall()
                                navController.navigateIfNotCurrent(PhoneRoute)
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
            }
                }
            }
            }
        }
    }
}

internal typealias DestinationFrame = @Composable (NavBackStackEntry, @Composable () -> Unit) -> Unit

internal fun usesClassicDestinationDeck(
    entry: NavBackStackEntry,
    mode: VisualMode,
    phoneRoute: CommunicationRoute,
): Boolean = entry.destination.run {
    mode == VisualMode.CLASSIC && !hasRoute<RecoveryRoute>() && !hasRoute<OnboardingRoute>() &&
        !hasRoute<MessagesRoute>() && (!hasRoute<PhoneRoute>() || phoneRoute == CommunicationRoute.DIALER)
}

internal inline fun <reified T : Any> NavGraphBuilder.framedComposable(
    noinline frame: DestinationFrame,
    crossinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable<T> { entry -> frame(entry) { content(entry) } }
}

@Composable
internal fun ClaroDestinationLayout(
    useGlobalDeck: Boolean,
    content: @Composable () -> Unit,
    controls: @Composable (ClaroSoftkeyHost?) -> Unit,
) {
    // Outgoing destinations must keep their own controls until NavHost removes their content.
    val host = remember { ClaroSoftkeyHost() }.takeIf { useGlobalDeck }
    CompositionLocalProvider(LocalClaroSoftkeyHost provides host) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) { content() }
            controls(host)
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
internal fun ClassicGlobalDeck(
    onBack: () -> Unit,
    onCall: () -> Unit,
    onMenu: () -> Unit,
    onHome: () -> Unit,
    softkeyHost: ClaroSoftkeyHost,
    screenKey: Any? = null,
    onDigitSelected: ((Char) -> Unit)? = null,
    scrollState: ScrollState? = null,
    menuLabelOverride: String? = null,
    keypadEnabled: Boolean = true,
    callEnabled: Boolean = true,
    allowInternationalPrefix: Boolean = false,
) {
    val view = LocalView.current
    val keypadInput = LocalClaroKeypadInput.current
    val deleteText = LocalClaroDeleteText.current
    val keypadMode = LocalClaroKeypadMode.current.takeIf { onDigitSelected == null }
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
            .testTag("classic-global-deck")
            .background(ClaroColors.Charcoal)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ClassicIntegratedPad(
            menuLabel = menuLabelOverride ?: menu?.label ?: stringResource(R.string.app_menu),
            greenLabel = green?.label ?: "☎ ${stringResource(R.string.app_call)}",
            redLabel = "⌂ ${stringResource(R.string.app_home)}",
            backLabel = back?.label.orEmpty(),
            greenIcon = green?.let { it.icon ?: ClaroIconKind.CHECK },
            menuIcon = menu?.icon,
            greenColor = green?.color,
            greenEnabled = callEnabled && (green?.enabled ?: true),
            menuEnabled = menu?.enabled ?: true,
            onBack = {
                if (back != null) {
                    back.onClick()
                } else if (deleteText?.invoke() != true) {
                    onBack()
                }
            },
            onMenu = menu?.onClick ?: onMenu,
            onGreen = green?.onClick ?: onCall,
            onRed = onHome,
            screenKey = screenKey,
            scrollState = scrollState,
        )
        ClassicKeypadModeStatus(mode = keypadMode)
        ClassicNumericKeypad(
            mode = keypadMode,
            onSwitchLanguage = LocalClaroSwitchKeypadLanguage.current
                .takeIf { keypadMode != null },
            allowInternationalPrefix = allowInternationalPrefix,
            onDigitSelected = if (keypadEnabled) {
                { digit ->
                    if (onDigitSelected != null) {
                        onDigitSelected(digit)
                    } else if (keypadInput != null) {
                        keypadInput(digit)
                    } else {
                        sendKey(
                            when (digit) {
                                '*' -> KeyEvent.KEYCODE_STAR
                                '#' -> KeyEvent.KEYCODE_POUND
                                else -> KeyEvent.KEYCODE_0 + (digit - '0')
                            },
                        )
                    }
                }
            } else {
                null
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
        HomeTileKind.WIDGET -> Unit
    }
    if (tile.label == "Settings") openSettings()
}

@Composable
internal fun ActiveCallReturnBar(
    state: CommunicationUiState,
    isPhoneScreen: Boolean,
    onReturnToCall: () -> Unit,
) {
    val showingCall = isPhoneScreen &&
        state.route in setOf(CommunicationRoute.ACTIVE_CALL, CommunicationRoute.INCOMING_CALL)
    if (!state.hasActiveCall || showingCall) return

    Button(
        onClick = onReturnToCall,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .testTag("return-to-call"),
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = ClaroColors.Green,
            contentColor = Color.White,
        ),
    ) {
        Text(
            text = stringResource(R.string.app_return_to_call),
            style = MaterialTheme.typography.titleLarge,
        )
    }
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
    onMessageNumber: (String, String?) -> Unit,
    onSaveContact: (String?, String, String, String, String?) -> Unit,
    onDeleteContact: (String) -> Unit,
    onRequestUnlockForContacts: () -> Unit,
    onHome: () -> Unit,
    sharedDialerControls: Boolean = false,
    dialerOptionsOpen: Boolean = false,
    onDialerOptionsChange: ((Boolean) -> Unit)? = null,
    dialerScrollState: ScrollState? = null,
    onDialerScreenChanged: (TelemetryScreen) -> Unit = {},
) {
    val callbacks = CommunicationCallbacks(viewModel, onRepairPhone, onHome)
    val returnToDialer = viewModel::returnFromContactsToDialer
    val canReturnToDialer = state.route == CommunicationRoute.CONTACTS && state.contactsOpenedFromDialer
    BackHandler(enabled = canReturnToDialer, onBack = returnToDialer)
    if (visualMode == VisualMode.TOUCH) {
        TouchCommunicationScreen(
            state = state,
            onDigit = callbacks.onDigit,
            onBackspace = callbacks.onBackspace,
            onCallEnteredNumber = callbacks.onCallEnteredNumber,
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
            canManageContacts = canManageContacts,
            onSaveContact = onSaveContact,
            onDeleteContact = onDeleteContact,
            onRequestUnlockForContacts = onRequestUnlockForContacts,
            onSelectDialerContact = viewModel::selectDialerContact,
            onDialerScreenChanged = onDialerScreenChanged,
        )
    } else {
        ClassicCommunicationScreen(
            state = state,
            onDigit = callbacks.onDigit,
            onBackspace = callbacks.onBackspace,
            onCallEnteredNumber = callbacks.onCallEnteredNumber,
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
            onCallIdentity = viewModel::requestCall,
            canManageContacts = canManageContacts,
            onSaveContact = onSaveContact,
            onDeleteContact = onDeleteContact,
            onRequestUnlockForContacts = onRequestUnlockForContacts,
            onSelectDialerContact = viewModel::selectDialerContact,
            sharedDialerControls = sharedDialerControls,
            dialerOptionsOpen = dialerOptionsOpen,
            onDialerOptionsChange = onDialerOptionsChange,
            dialerScrollState = dialerScrollState,
            onContactsBack = returnToDialer.takeIf { canReturnToDialer },
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
    val onOpenContacts: () -> Unit = {
        if (viewModel.state.value.route == CommunicationRoute.DIALER) {
            viewModel.openContacts(fromDialer = true)
        } else {
            viewModel.openContacts()
        }
    }
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
            onReview = viewModel::sendDraft,
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
            onOpenInbox = viewModel::openInbox,
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
            onReview = viewModel::sendDraft,
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
            onOpenInbox = viewModel::openInbox,
            onDeleteConversation = viewModel::deleteConversation,
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
        navigateIfNotCurrent(AccessGateRoute(target))
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
    if (!popGate) {
        navigateIfNotCurrent(destination)
        return
    }
    navigate(destination) {
        popUpTo<AccessGateRoute> { inclusive = true }
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

internal fun NavHostController.navigateIfNotCurrent(route: Any) {
    val entry = currentBackStackEntry
    if (entry?.destination?.hasRoute(route::class) == true && entry.toRoute<Any>(route::class) == route) return

    navigate(route)
}

internal fun NavHostController.goHome(mode: VisualMode) {
    val alreadyHome = when (mode) {
        VisualMode.TOUCH -> currentDestination?.hasRoute<TouchHomeRoute>()
        VisualMode.CLASSIC -> currentDestination?.hasRoute<ClassicHomeRoute>()
    } == true
    if (alreadyHome) return

    val returnedHome = when (mode) {
        VisualMode.TOUCH -> popBackStack<TouchHomeRoute>(inclusive = false)
        VisualMode.CLASSIC -> popBackStack<ClassicHomeRoute>(inclusive = false)
    }
    if (returnedHome) return

    navigate(mode.homeRoute()) {
        popUpTo(graph.id) { inclusive = false }
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
