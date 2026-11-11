package com.clarohome.app

import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroTheme
import com.clarohome.core.design.HapticCue
import com.clarohome.core.design.RecordingClaroHaptics
import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.BatteryStatus
import com.clarohome.core.model.CapabilityStatus
import com.clarohome.core.model.CellularAvailability
import com.clarohome.core.model.CellularStatus
import com.clarohome.core.model.ClaroPreferences
import com.clarohome.core.model.FavoritePerson
import com.clarohome.core.model.HandoverCheck
import com.clarohome.core.model.HomeRoleState
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind
import com.clarohome.core.model.OnboardingProgress
import com.clarohome.core.model.OnboardingStep
import com.clarohome.core.model.ReadinessCapability
import com.clarohome.core.model.SettingsAccessMode
import com.clarohome.core.model.TileAccent
import com.clarohome.core.model.VisualMode
import com.clarohome.feature.billing.observability.BillingObservabilityScreen
import com.clarohome.feature.billing.observability.BillingObservabilityUiState
import com.clarohome.feature.billing.observability.PlansState
import com.clarohome.feature.billing.observability.PlansUnavailableReason
import com.clarohome.feature.communication.AudioRoute
import com.clarohome.feature.communication.CallCapabilities
import com.clarohome.feature.communication.CallDirection
import com.clarohome.feature.communication.CallHistoryEntry
import com.clarohome.feature.communication.CallOutcome
import com.clarohome.feature.communication.CallPhase
import com.clarohome.feature.communication.CallSession
import com.clarohome.feature.communication.CallerIdentity
import com.clarohome.feature.communication.CommunicationRoute
import com.clarohome.feature.communication.CommunicationUiState
import com.clarohome.feature.communication.DialerQualification
import com.clarohome.feature.communication.MessageConversation
import com.clarohome.feature.communication.MessageDraft
import com.clarohome.feature.communication.MessageRecipient
import com.clarohome.feature.communication.MessagingRoute
import com.clarohome.feature.communication.MessagingUiState
import com.clarohome.feature.communication.PhoneAssignment
import com.clarohome.feature.communication.SmsQualification
import com.clarohome.feature.onboarding.settings.AccessGateScreen
import com.clarohome.feature.onboarding.settings.OnboardingCallbacks
import com.clarohome.feature.onboarding.settings.OnboardingFlow
import com.clarohome.feature.onboarding.settings.ReadinessSettingsScreen
import com.clarohome.presentation.classic.ClassicCommunicationScreen
import com.clarohome.presentation.classic.ClassicPhoneScreen
import com.clarohome.presentation.touch.TouchCommunicationScreen
import com.clarohome.presentation.touch.TouchHomeScreen
import com.clarohome.presentation.touch.TouchMessagingScreen
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoryboardRegressionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun onboardingRequiresAnExplicitVisualModeChoice() {
        var step by mutableStateOf(OnboardingStep.WELCOME)
        var chosenMode by mutableStateOf(VisualMode.TOUCH)
        compose.setContent {
            ClaroTheme {
                OnboardingFlow(
                    preferences = ClaroPreferences(
                        visualMode = chosenMode,
                        onboarding = OnboardingProgress(currentStep = step),
                    ),
                    message = null,
                    callbacks = noOpOnboardingCallbacks(
                        onContinue = { step = it },
                        onChooseMode = { chosenMode = it },
                    ),
                )
            }
        }

        compose.onNodeWithText("Your people. One touch away.").assertIsDisplayed()
        capture("onboarding-welcome", "onboarding-screen")
        compose.onNodeWithText("Set up Claro").performClick()

        compose.onNodeWithText("Touch Home").assertIsDisplayed()
        compose.onNodeWithText("Classic Phone").assertIsDisplayed()
        compose.onNodeWithText("Use Classic Phone").assertIsEnabled()
        compose.onNodeWithText("Touch Home").performClick()
        compose.onNodeWithText("Use Touch Home").assertHeightIsAtLeast(64.dp)
        capture("onboarding-mode-choice", "onboarding-screen")
    }

    @Test
    fun readinessUsesAndroidRepairActionWithoutFakeGrant() {
        val requested = mutableListOf<ReadinessCapability>()
        var readiness by mutableStateOf(
            ReadinessCapability.entries.associateWith { CapabilityStatus.NEEDS_ATTENTION },
        )
        compose.setContent {
            ClaroTheme {
                ReadinessSettingsScreen(
                    readiness = readiness,
                    onRequest = { requested += it },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Fake grant").assertDoesNotExist()
        compose.onAllNodesWithText("Allow or repair", useUnmergedTree = true)[0].performClick()
        compose.runOnIdle {
            assertEquals(listOf(ReadinessCapability.HOME), requested)
            assertEquals(CapabilityStatus.NEEDS_ATTENTION, readiness[ReadinessCapability.HOME])
        }
        capture("onboarding-permissions", "onboarding-screen")

        readiness = readiness + (ReadinessCapability.HOME to CapabilityStatus.READY)
        compose.onNodeWithText("Allowed · service test separate", substring = true).assertIsDisplayed()
    }

    @Test
    fun protectedSettingsUsesDateChallengeAndLargeActions() {
        compose.setContent {
            ClaroTheme {
                AccessGateScreen(
                    mode = SettingsAccessMode.DATE_QUESTION,
                    message = null,
                    onUnlock = {},
                    onRecoverPin = {},
                )
            }
        }

        compose.onNodeWithText("Today's date?").assertIsDisplayed()
        compose.onNodeWithText("Forgot PIN?").assertDoesNotExist()
        compose.onNodeWithText("Unlock").assertHeightIsAtLeast(64.dp)
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        capture("protected-settings", "onboarding-screen")
    }

    @Test
    fun touchHomeMatchesStoryboardPaletteAndTargets() {
        compose.setContent {
            ClaroTheme {
                TouchHomeScreen(
                    home = storyboardHome(),
                    onUseClassicMode = {},
                    onTileSelected = {},
                    onFavoriteSelected = {},
                    onRepairHomeRole = {},
                    onReturnToCall = {},
                )
            }
        }

        compose.onNodeWithTag("touch-home").assertIsDisplayed()
        listOf("Mira", "Daniel", "Leah", "Noah", "Phone", "Messages", "Camera", "Gallery", "SOS", "Apps")
            .forEach { label ->
                compose.onNodeWithText(label).assertIsDisplayed()
            }
        val bitmap = capture("touch-home", "touch-home")
        assertTrue(bitmap.containsColor(ClaroColors.Charcoal))
        assertTrue(bitmap.containsColor(ClaroColors.PaleGreen))
        assertTrue(bitmap.containsColor(ClaroColors.PaleBlue))
        assertTrue(bitmap.containsColor(ClaroColors.PaleRed))
    }

    @Test
    fun classicHomeKeepsIntegratedPadAndPermanentKeypad() {
        compose.setContent {
            ClaroTheme {
                ClassicPhoneScreen(
                    home = storyboardHome(),
                    onOpenMenu = {},
                    onTileSelected = {},
                    onFavoriteSelected = {},
                    onRepairHomeRole = {},
                    onReturnToCall = {},
                    onDigitSelected = {},
                    onBack = {},
                    onHome = {},
                )
            }
        }

        compose.onNodeWithTag("classic-home").assertIsDisplayed()
        compose.onNodeWithText("OK").assertIsDisplayed()
        compose.onNodeWithText("Call").assertIsDisplayed()
        compose.onNodeWithText("Home").assertIsDisplayed()
        val bitmap = capture("classic-home", "classic-home")
        assertTrue(bitmap.containsColor(ClaroColors.Charcoal))
        assertTrue(bitmap.containsColor(ClaroColors.Green))
        assertTrue(bitmap.containsColor(ClaroColors.Red))
    }

    @Test
    fun touchAndClassicCommunicationMatchStoryboardLayouts() {
        var classicRoute by mutableStateOf(CommunicationRoute.DIALER)
        var mode by mutableStateOf(VisualMode.TOUCH)
        compose.setContent {
            ClaroTheme {
                val baseState = CommunicationUiState(
                    route = classicRoute,
                    digits = "",
                    pendingIdentity = CallerIdentity("Mira", "202-555-0101"),
                    call = CallSession(
                        id = "1",
                        direction = CallDirection.INCOMING,
                        identity = CallerIdentity("Mira", "202-555-0101"),
                        phase = if (classicRoute == CommunicationRoute.ACTIVE_CALL) {
                            CallPhase.CONNECTED
                        } else {
                            CallPhase.DISCONNECTED
                        },
                        capabilities = CallCapabilities(
                            canMute = true,
                            canHold = true,
                            canChangeAudioRoute = true,
                        ),
                        audioRoute = AudioRoute.EARPIECE,
                        connectedAtElapsedMillis = if (classicRoute == CommunicationRoute.ACTIVE_CALL) 1_000L else null,
                        outcome = if (classicRoute == CommunicationRoute.MISSED_CALL) {
                            CallOutcome.MISSED_INCOMING
                        } else {
                            null
                        },
                    ),
                    qualification = DialerQualification(
                        roleAvailable = true,
                        roleHeld = true,
                        telephonySupported = true,
                        callPermissionGranted = true,
                        callLogPermissionGranted = true,
                        hasUsableCallingAccount = true,
                    ),
                    assignments = listOf(
                        PhoneAssignment("1", "mira", "2025550101", "Mira"),
                        PhoneAssignment("2", "daniel", "2025550103", "Daniel"),
                    ),
                    history = listOf(
                        CallHistoryEntry(
                            id = "missed-1",
                            identity = CallerIdentity("Mira", "202-555-0101"),
                            direction = CallDirection.INCOMING,
                            outcome = CallOutcome.MISSED_INCOMING,
                            timestampMillis = 1_000L,
                            connectedDurationMillis = null,
                            isOpened = false,
                        ),
                    ),
                )
                if (mode == VisualMode.TOUCH) {
                    TouchCommunicationScreen(
                        state = baseState,
                        onDigit = {},
                        onBackspace = {},
                        onCallEnteredNumber = {},
                        onDirectCall = {},
                        onOpenContacts = {},
                        onSearchContacts = {},
                        onLoadMoreContacts = {},
                        onOpenContact = {},
                        onChooseNumber = {},
                        onConfirmCall = {},
                        onCancelCall = {},
                        onAnswer = {},
                        onDecline = {},
                        onEnd = {},
                        onToggleMute = {},
                        onAudioRoute = {},
                        onToggleHold = {},
                        onSwap = {},
                        onRepairPhone = {},
                        onHome = {},
                    )
                } else {
                    ClassicCommunicationScreen(
                        state = baseState,
                        onDigit = {},
                        onBackspace = {},
                        onCallEnteredNumber = {},
                        onDirectCall = {},
                        onOpenContacts = {},
                        onSearchContacts = {},
                        onLoadMoreContacts = {},
                        onOpenContact = {},
                        onChooseNumber = {},
                        onConfirmCall = {},
                        onCancelCall = {},
                        onAnswer = {},
                        onDecline = {},
                        onEnd = {},
                        onToggleMute = {},
                        onAudioRoute = {},
                        onToggleHold = {},
                        onSwap = {},
                        onRepairPhone = {},
                        onHome = {},
                        battery = BatteryStatus(82, false),
                        cellular = CellularStatus(CellularAvailability.AVAILABLE, 4),
                    )
                }
            }
        }

        compose.onNodeWithTag("touch-communication").assertIsDisplayed()
        capture("touch-dialer", "touch-communication")

        mode = VisualMode.CLASSIC
        compose.onNodeWithTag("classic-communication").assertIsDisplayed()
        capture("classic-dialer", "classic-communication")

        classicRoute = CommunicationRoute.ACTIVE_CALL
        compose.waitForIdle()
        capture("classic-active-call", "classic-communication")

        classicRoute = CommunicationRoute.MISSED_CALL
        compose.waitForIdle()
        compose.onNodeWithText("Mira").assertIsDisplayed()
        capture("classic-missed-call", "classic-communication")
    }

    @Test
    fun touchMessagingMatchesInboxAndComposeStoryboard() {
        var messagingRoute by mutableStateOf(MessagingRoute.INBOX)
        compose.setContent {
            ClaroTheme {
                TouchMessagingScreen(
                    state = MessagingUiState(
                        route = messagingRoute,
                        qualification = SmsQualification(
                            roleAvailable = true,
                            roleHeld = true,
                            telephonySupported = true,
                            sendPermissionGranted = true,
                            readPermissionGranted = true,
                            receivePermissionGranted = true,
                            notificationPermissionGranted = true,
                            hasUsableDefaultAccount = true,
                        ),
                        conversations = listOf(
                            MessageConversation(
                                id = 1L,
                                recipient = MessageRecipient("202-555-0101", "Mira"),
                                snippet = "I'll come by after lunch.",
                                timestampMillis = 1_000L,
                                unreadCount = 1,
                            ),
                            MessageConversation(
                                id = 2L,
                                recipient = MessageRecipient("202-555-0103", "Daniel"),
                                snippet = "See you on Sunday.",
                                timestampMillis = 900L,
                                unreadCount = 0,
                            ),
                        ),
                        draft = MessageDraft(
                            id = "draft-1",
                            recipient = MessageRecipient("202-555-0101", "Mira"),
                            body = "",
                        ),
                        recipientQuery = "202-555-0101",
                    ),
                    onSearchInbox = {},
                    onOpenConversation = {},
                    onNewMessage = { messagingRoute = MessagingRoute.COMPOSE },
                    onSearchRecipients = {},
                    onLoadMoreRecipients = {},
                    onChooseContactNumber = { _, _ -> },
                    onManualRecipient = {},
                    onBodyChange = {},
                    onReview = {},
                    onEdit = {},
                    onSend = {},
                    onRetry = {},
                    onConfirmDuplicateRisk = {},
                    onCancelFailure = {},
                    onRepair = {},
                    onHome = {},
                )
            }
        }

        compose.onNodeWithText("Write a message").assertHeightIsAtLeast(64.dp)
        capture("messages-inbox", "touch-messaging")

        compose.onNodeWithText("Write a message").performClick()
        compose.onNodeWithText("Review").assertHeightIsAtLeast(64.dp)
        compose.onNodeWithText("Cancel · keep draft").assertHeightIsAtLeast(64.dp)
        capture("message-compose", "touch-messaging")
    }

    @Test
    fun billingUnconfiguredNeverInventsFakePlans() {
        compose.setContent {
            ClaroTheme {
                BillingObservabilityScreen(
                    state = BillingObservabilityUiState(
                        plans = PlansState.Unavailable(PlansUnavailableReason.CONFIGURATION_MISSING),
                    ),
                    classicShell = false,
                    onBack = {},
                    onRefresh = {},
                    onPurchase = { _, _ -> },
                    onRestore = {},
                    onConsentChange = {},
                    onWithdrawConsent = {},
                    onResetDiagnostics = {},
                )
            }
        }

        compose.onNodeWithText("Fake plans", substring = true).assertDoesNotExist()
        compose.onNodeWithText(
            "Store products are not configured. Purchases are unavailable; core features remain free.",
        ).assertIsDisplayed()
        capture("membership-privacy", "billing-screen")
    }

    @Test
    fun twoGKeypadAndControlsTriggerCrispHapticCuesAndRespectToggle() {
        var enabled by mutableStateOf(true)
        var mode by mutableStateOf(VisualMode.CLASSIC)
        val recorded = mutableListOf<HapticCue>()
        compose.setContent {
            val haptics = RecordingClaroHaptics(enabled = enabled) { recorded += it }
            ClaroTheme(hapticsOverride = haptics) {
                if (mode == VisualMode.CLASSIC) {
                    ClassicPhoneScreen(
                        home = storyboardHome(),
                        onOpenMenu = {},
                        onTileSelected = {},
                        onFavoriteSelected = {},
                        onRepairHomeRole = {},
                        onReturnToCall = {},
                        onDigitSelected = {},
                        onBack = {},
                        onHome = {},
                    )
                } else {
                    TouchHomeScreen(
                        home = storyboardHome(),
                        onUseClassicMode = {},
                        onTileSelected = {},
                        onFavoriteSelected = {},
                        onRepairHomeRole = {},
                        onReturnToCall = {},
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("5, JKL").performClick()
        compose.onNodeWithContentDescription("Navigate up").performClick()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Call").performClick()

        compose.runOnIdle {
            assertEquals(
                listOf(
                    HapticCue.KEYPAD_DIGIT,
                    HapticCue.DPAD_STEP,
                    HapticCue.KEYPAD_DIGIT,
                    HapticCue.CONFIRM_HEAVY,
                ),
                recorded,
            )
            recorded.clear()
            mode = VisualMode.TOUCH
        }

        compose.onNodeWithText("Phone").performClick()
        compose.onNodeWithText("SOS").performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(HapticCue.SOFTKEY, HapticCue.CONFIRM_HEAVY),
                recorded,
            )
            recorded.clear()
            enabled = false
        }

        compose.onNodeWithText("Messages").performClick()
        compose.runOnIdle {
            assertTrue(recorded.isEmpty())
        }
    }

    private fun capture(name: String, tag: String): Bitmap {
        val raw = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val targetWidth = 540
        val scaled = if (raw.width > targetWidth) {
            val targetHeight = (raw.height.toFloat() * targetWidth / raw.width).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(raw, targetWidth, targetHeight, true)
        } else {
            raw
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "claro-review",
        ).apply { mkdirs() }
        FileOutputStream(File(directory, "$name.png")).use {
            scaled.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return raw
    }
}

private fun Bitmap.containsColor(expected: Color, tolerance: Int = 14): Boolean {
    val target = intArrayOf(
        (expected.red * 255).toInt(),
        (expected.green * 255).toInt(),
        (expected.blue * 255).toInt(),
    )
    val stepX = (width / 40).coerceAtLeast(1)
    val stepY = (height / 60).coerceAtLeast(1)
    for (y in 0 until height step stepY) {
        for (x in 0 until width step stepX) {
            val pixel = getPixel(x, y)
            if (
                kotlin.math.abs(android.graphics.Color.red(pixel) - target[0]) <= tolerance &&
                kotlin.math.abs(android.graphics.Color.green(pixel) - target[1]) <= tolerance &&
                kotlin.math.abs(android.graphics.Color.blue(pixel) - target[2]) <= tolerance
            ) {
                return true
            }
        }
    }
    return false
}

private fun storyboardHome() = HomeSnapshot(
    greeting = "",
    dayAndDate = "Wed 11",
    time = "9:41",
    battery = BatteryStatus(82, false),
    cellular = CellularStatus(CellularAvailability.AVAILABLE, 4),
    favorites = listOf("Mira", "Daniel", "Leah", "Noah").mapIndexed { index, name ->
        FavoritePerson(index.toString(), name, "20255501$index")
    },
    tiles = listOf(
        HomeTile("phone", "Phone", TileAccent.GREEN, HomeTileKind.PHONE, BadgeValue.Known(1)),
        HomeTile("messages", "Messages", TileAccent.BLUE, HomeTileKind.MESSAGES, BadgeValue.Known(1)),
        HomeTile("camera", "Camera", TileAccent.ORANGE, HomeTileKind.CAMERA),
        HomeTile("gallery", "Gallery", TileAccent.VIOLET, HomeTileKind.GALLERY),
        HomeTile("sos", "SOS", TileAccent.RED, HomeTileKind.SOS),
        HomeTile("apps", "Apps", TileAccent.BLUE, HomeTileKind.APPS),
    ),
    installedApps = emptyList(),
    homeRole = HomeRoleState.GRANTED,
)

private fun noOpOnboardingCallbacks(
    onContinue: (OnboardingStep) -> Unit = {},
    onChooseMode: (VisualMode) -> Unit = {},
) = OnboardingCallbacks(
    onContinue = onContinue,
    onChooseMode = onChooseMode,
    onLanguageDisplay = { _, _, _ -> },
    onUseDateQuestion = {},
    onConfigurePin = { _, _, _ -> },
    onOpenPeopleSetup = {},
    onOpenSosSetup = {},
    onRequestCapability = {},
    onHandover = { _: HandoverCheck, _: Boolean -> },
)
