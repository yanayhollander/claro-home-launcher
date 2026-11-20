package com.clarohome.app

import android.graphics.Bitmap
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Environment
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.focusable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroAvatar
import com.clarohome.core.design.ClaroSoftkeyHost
import com.clarohome.core.design.ClaroCellularBars
import com.clarohome.core.design.ClaroTheme
import com.clarohome.core.design.ClaroLocaleContainer
import com.clarohome.core.design.ClaroKeyboardContainer
import com.clarohome.core.design.LocalClaroKeypadInput
import com.clarohome.core.design.LocalClaroKeypadMode
import com.clarohome.core.design.fitsCompactHomeTile
import com.clarohome.core.data.SharedPreferencesHomeSlotStore
import com.clarohome.core.data.homeSlotTile
import com.clarohome.core.design.applyClassicKeypadInput
import com.clarohome.core.design.HapticCue
import com.clarohome.core.design.RecordingClaroHaptics
import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.BatteryStatus
import com.clarohome.core.model.CapabilityStatus
import com.clarohome.core.model.CellularAvailability
import com.clarohome.core.model.CellularStatus
import com.clarohome.core.model.ClaroPreferences
import com.clarohome.core.model.AppLanguage
import com.clarohome.core.model.InstalledApp
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
import com.clarohome.feature.communication.ClaroContact
import com.clarohome.feature.communication.ClaroMessage
import com.clarohome.feature.communication.MessageDirection
import com.clarohome.feature.communication.MessageDeliveryState
import com.clarohome.feature.communication.formatMessageTimestamp
import com.clarohome.feature.communication.CommunicationRoute
import com.clarohome.feature.communication.CommunicationUiState
import com.clarohome.feature.communication.ContactNumber
import com.clarohome.feature.communication.DialerQualification
import com.clarohome.feature.communication.MessageConversation
import com.clarohome.feature.communication.MessageDraft
import com.clarohome.feature.communication.MessageRecipient
import com.clarohome.feature.communication.MessagingRoute
import com.clarohome.feature.communication.MessagingUiState
import com.clarohome.feature.communication.PhoneAssignment
import com.clarohome.feature.communication.SmsQualification
import com.clarohome.feature.safety.media.apps.SosState
import com.clarohome.feature.onboarding.settings.AccessGateScreen
import com.clarohome.feature.onboarding.settings.OnboardingCallbacks
import com.clarohome.feature.onboarding.settings.OnboardingFlow
import com.clarohome.feature.onboarding.settings.ReadinessSettingsScreen
import com.clarohome.feature.onboarding.settings.CallAudioSettingsScreen
import com.clarohome.presentation.classic.ClassicCommunicationScreen
import com.clarohome.presentation.classic.ClassicMessagingScreen
import com.clarohome.presentation.classic.ClassicPhoneScreen
import com.clarohome.presentation.classic.ClassicNumericKeypad
import com.clarohome.presentation.classic.ClassicIntegratedPad
import com.clarohome.presentation.touch.TouchCommunicationScreen
import com.clarohome.presentation.touch.TouchHomeScreen
import com.clarohome.presentation.touch.TouchMessagingScreen
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Serializable
private data class RepeatNavigationRoute(val item: Int)

@RunWith(AndroidJUnit4::class)
class StoryboardRegressionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun compactWidgetsRespectMinimumSizeAndOnlyUsePermittedResizeAxes() {
        val provider = AppWidgetProviderInfo().apply {
            minWidth = 300
            minHeight = 200
            minResizeWidth = 90
            minResizeHeight = 80
            resizeMode = AppWidgetProviderInfo.RESIZE_NONE
        }
        assertTrue(!provider.fitsCompactHomeTile(100, 100))
        provider.resizeMode = AppWidgetProviderInfo.RESIZE_HORIZONTAL
        assertTrue(!provider.fitsCompactHomeTile(100, 100))
        provider.resizeMode = AppWidgetProviderInfo.RESIZE_BOTH
        assertTrue(provider.fitsCompactHomeTile(100, 100))
        assertTrue(!provider.fitsCompactHomeTile(89, 100))
        assertTrue(!provider.fitsCompactHomeTile(100, 79))
        provider.minResizeWidth = 0
        assertTrue(!provider.fitsCompactHomeTile(100, 100))
    }

    @Test
    fun widgetPreferencesPersistTheBindingAndDiscardItWhenTheSlotIsCleared() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("widget-regression-$name", mode)
        }
        val preferences = context.getSharedPreferences("claro_home_slots", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val slots = MutableList<HomeTile?>(12) { null }
            slots[7] = homeSlotTile(
                7, HomeTileKind.WIDGET, "Example widget",
                appWidgetId = 42,
                appWidgetProvider = "com.example/.ExampleWidget",
            )
            SharedPreferencesHomeSlotStore(context).save(slots)
            assertEquals(slots, SharedPreferencesHomeSlotStore(context).load())
            slots[7] = null
            SharedPreferencesHomeSlotStore(context).save(slots)
            assertEquals(null, SharedPreferencesHomeSlotStore(context).load()[7])
            assertTrue(!preferences.contains("7.widget_id"))
            assertTrue(!preferences.contains("7.widget_provider"))
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun classicHomeScrollsOnlyTilesNotStatusClockOrDate() {
        val home = storyboardHome().copy(
            homeRole = HomeRoleState.GRANTED,
            homeSlots = (1..12).map {
                HomeTile("contact-$it", "Contact $it", TileAccent.BLUE, HomeTileKind.CONTACT)
            },
        )
        compose.setContent {
            ClaroTheme {
                Box(Modifier.height(640.dp)) {
                    ClassicPhoneScreen(home, {}, {}, {}, {})
                }
            }
        }
        val headerBounds = compose.onNodeWithTag("classic-home-fixed-header").fetchSemanticsNode().boundsInRoot
        val timeBounds = compose.onNodeWithText(home.time).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("classic-home-tiles").performScrollToNode(hasText("Contact 12"))
        compose.onNodeWithText("Contact 12").assertIsDisplayed()
        assertEquals(headerBounds, compose.onNodeWithTag("classic-home-fixed-header").fetchSemanticsNode().boundsInRoot)
        assertEquals(timeBounds, compose.onNodeWithText(home.time).fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText(home.dayAndDate).assertIsDisplayed()
    }

    @Test
    fun touchHomeScrollsTilesWithoutMovingStatusHeader() {
        val home = storyboardHome().copy(
            homeRole = HomeRoleState.GRANTED,
            homeSlots = (1..12).map {
                HomeTile("contact-$it", "Contact $it", TileAccent.BLUE, HomeTileKind.CONTACT)
            },
        )
        compose.setContent {
            ClaroTheme {
                Box(Modifier.height(440.dp)) {
                    TouchHomeScreen(home, {}, {}, {}, {})
                }
            }
        }
        val headerBounds = compose.onNodeWithTag("touch-home-fixed-header").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("touch-home-tiles").performScrollToIndex(3)
        compose.onNodeWithText("Contact 12").assertIsDisplayed()
        assertEquals(headerBounds, compose.onNodeWithTag("touch-home-fixed-header").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText(home.time).assertIsDisplayed()
        compose.onNodeWithText(home.dayAndDate).assertIsDisplayed()
    }

    @Test
    fun classicDialerHeaderOpensContactsWithoutScrolling() {
        var requests = 0
        compose.setContent {
            ClaroTheme {
                ClassicContactFixture(
                    state = classicContactState().copy(route = CommunicationRoute.DIALER, selectedContact = null),
                    unlocked = false,
                    onOpenContacts = { requests++ },
                )
            }
        }
        compose.onNodeWithContentDescription("Contacts").assertIsDisplayed().performClick()
        compose.onNodeWithText("Contacts").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test
    fun unsetSosOffersWorkingSetupWithoutAnUnconfiguredCallButton() {
        var setupRequests = 0
        var emergencyRequests = 0
        var callRequests = 0
        compose.setContent {
            ClaroLocaleContainer(AppLanguage.ENGLISH) {
                ClaroTheme {
                    SosScreen(
                        state = SosState.Unset(),
                        onStart = { _, _ -> callRequests++ },
                        onCancel = {},
                        remainingSeconds = { 0 },
                        onOpenProtectedSosSettings = { setupRequests++ },
                        onEmergencyDialer = { emergencyRequests++ },
                        hasActiveCall = false,
                        onReturnToCall = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Call in 5 seconds").assertDoesNotExist()
        compose.onNode(hasText("Set up SOS") and hasClickAction()).performClick()
        compose.onNodeWithText("Emergency services").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, setupRequests)
            assertEquals(0, emergencyRequests)
            assertEquals(0, callRequests)
        }
    }

    @Test
    fun homeSetupOffersOnlyInstalledWhatsAppAndFacebookVariants() {
        var homePackages by mutableStateOf(emptySet<String>())
        compose.setContent {
            ClaroLocaleContainer(AppLanguage.ENGLISH) {
                ClaroTheme {
                    OnboardingFlow(
                        preferences = ClaroPreferences(
                            onboarding = OnboardingProgress(currentStep = OnboardingStep.HOME_TILES),
                        ),
                        message = null,
                        callbacks = noOpOnboardingCallbacks().copy(
                            onInstalledAppVisibility = { pkg, visible ->
                                homePackages = if (visible) homePackages + pkg else homePackages - pkg
                            },
                        ),
                        homeAppPackages = homePackages,
                        installedApps = listOf(
                            InstalledApp("com.whatsapp", "WhatsApp", false),
                            InstalledApp("com.facebook.katana", "Facebook", false),
                            InstalledApp("com.facebook.lite", "Facebook Lite", false, isAvailable = false),
                            InstalledApp("com.google.android.gm", "Gmail", false),
                        ),
                    )
                }
            }
        }
        compose.onNodeWithText("Show WhatsApp on Home").performScrollTo().assertIsOff().performClick()
        compose.onNodeWithText("Show WhatsApp on Home").assertIsOn()
        compose.onNodeWithText("Show Facebook on Home").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Show Facebook Lite on Home").assertDoesNotExist()
        compose.onNodeWithText("Show Gmail on Home").assertDoesNotExist()
        compose.runOnIdle { assertEquals(setOf("com.whatsapp"), homePackages) }
    }

    @Test
    fun classicDialerShowsOlderUnreviewedMissedCallBeforeRecentReviewedCalls() {
        val recent = (1..5).map { index ->
            CallHistoryEntry(
                id = "recent-$index",
                identity = CallerIdentity("Recent $index", "202555010$index"),
                direction = CallDirection.OUTGOING,
                outcome = CallOutcome.ENDED,
                timestampMillis = 10_000L - index,
                connectedDurationMillis = 1_000,
                isOpened = true,
            )
        }
        val missed = CallHistoryEntry(
            id = "older-missed",
            identity = CallerIdentity("Needs attention", "2025550199"),
            direction = CallDirection.INCOMING,
            outcome = CallOutcome.MISSED_INCOMING,
            timestampMillis = 1_000,
            connectedDurationMillis = null,
            isOpened = false,
        )
        compose.setContent {
            ClaroTheme {
                ClassicContactFixture(
                    state = classicContactState().copy(
                        route = CommunicationRoute.DIALER,
                        selectedContact = null,
                        history = recent + missed,
                    ),
                    unlocked = false,
                )
            }
        }
        compose.onNodeWithText("Needs attention").assertIsDisplayed()
    }

    @Test
    fun classicConversationShowsArrivalDateAndTimeWithoutCarrierChargeNotice() {
        val timestamp = Instant.parse("2026-11-15T11:40:00Z").toEpochMilli()
        val expectedTime = requireNotNull(formatMessageTimestamp(timestamp, Locale.ENGLISH))
        var route by mutableStateOf(MessagingRoute.CONVERSATION)
        var draftBody by mutableStateOf("Saved draft")
        var homeRequests = 0
        var newMessages = 0
        val deletedConversations = mutableListOf<Long>()
        lateinit var backDispatcher: OnBackPressedDispatcher
        compose.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            ClaroLocaleContainer(language = AppLanguage.ENGLISH) {
                ClaroTheme {
                    ClassicMessagingScreen(
                        state = MessagingUiState(
                            route = route,
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
                            selectedConversation = MessageConversation(
                                7, MessageRecipient("+15550101", "Mira"), "Hello", timestamp, 0,
                            ),
                            conversations = listOf(
                                MessageConversation(7, MessageRecipient("+15550101", "Mira"), "Hello", timestamp, 0),
                            ),
                            messages = listOf(
                                ClaroMessage(
                                    providerId = 42,
                                    conversationId = 7,
                                    clientMessageId = null,
                                    direction = MessageDirection.INCOMING,
                                    senderOrRecipient = "+15550101",
                                    body = "Hello",
                                    timestampMillis = timestamp,
                                    isRead = true,
                                    deliveryState = MessageDeliveryState.RECEIVED,
                                ),
                            ),
                            draft = MessageDraft("reply", body = draftBody),
                        ),
                        onSearchInbox = {},
                        onOpenConversation = { route = MessagingRoute.CONVERSATION },
                        onNewMessage = { newMessages++; route = MessagingRoute.COMPOSE },
                        onSearchRecipients = {},
                        onChooseContactNumber = { _, _ -> },
                        onManualRecipient = {},
                        onBodyChange = { draftBody = it },
                        onReview = {},
                        onEdit = {},
                        onSend = {},
                        onRetry = {},
                        onConfirmDuplicateRisk = {},
                        onCancelFailure = {},
                        onRepair = {},
                        onHome = { homeRequests++ },
                        onOpenInbox = { route = MessagingRoute.INBOX },
                        onDeleteConversation = { deletedConversations += it; route = MessagingRoute.INBOX },
                    )
                }
            }
        }
        compose.onNodeWithText(expectedTime).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("message-time-42").assertIsDisplayed()
        compose.onNodeWithText("carrier charges may apply", substring = true).assertDoesNotExist()
        compose.onNodeWithText("~", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("3, DEF").assertDoesNotExist()
        compose.onNodeWithText("Saved draft").assertDoesNotExist()

        compose.onNodeWithText("Reply").performClick()
        compose.onNodeWithContentDescription("3, DEF").assertIsDisplayed()
        compose.onNodeWithText("Saved draft").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Hello").assertIsDisplayed()
        compose.onNodeWithContentDescription("3, DEF").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("Saved draft", draftBody)
            assertEquals(0, homeRequests)
            backDispatcher.onBackPressed()
        }
        compose.onNodeWithContentDescription("New message").assertIsDisplayed()
        compose.onNodeWithText("Search name or number").assertDoesNotExist()
        compose.onNodeWithText("Mira").performClick()
        compose.onNodeWithText("Hello").assertIsDisplayed()
        compose.onNodeWithText("Options").performClick()
        compose.onNodeWithText("Delete conversation").performClick()
        compose.runOnIdle { assertTrue(deletedConversations.isEmpty()) }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Hello").assertIsDisplayed()
        compose.onNodeWithText("Options").performClick()
        compose.onNodeWithText("Delete conversation").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals(listOf(7L), deletedConversations) }
        compose.onNodeWithContentDescription("New message").performClick()
        compose.runOnIdle {
            assertEquals(0, homeRequests)
            assertEquals(1, newMessages)
            assertEquals(MessagingRoute.COMPOSE, route)
        }
    }

    @Test
    fun classicFilteringPreservesMatchingRowAndDoesNotInsertLoadingCard() {
        val mira = CallerIdentity("Mira", "2125550101")
        var state by mutableStateOf(
            classicContactState().copy(
                route = CommunicationRoute.DIALER,
                digits = "2",
                selectedContact = null,
                dialerMatches = listOf(CallerIdentity("Alex", "2335550199"), mira),
                assignments = listOf(PhoneAssignment("2", "favorite", "2025550198", "Favorite contact")),
            ),
        )
        compose.setContent {
            ClaroTheme { ClassicContactFixture(state = state, unlocked = false) }
        }
        val rowId = compose.onNodeWithText("Mira").fetchSemanticsNode().id
        compose.onNodeWithText("Favorite contact").assertDoesNotExist()
        compose.onNodeWithContentDescription("2, ABC").assertIsDisplayed()
        val keypadTop = compose.onNodeWithContentDescription("3, DEF").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle {
            state = state.copy(digits = "21", dialerMatches = listOf(mira), dialerMatchesLoading = true)
        }
        compose.onNodeWithText("Mira").assertIsDisplayed()
        compose.onNodeWithText("Loading contacts…").assertDoesNotExist()
        assertEquals(rowId, compose.onNodeWithText("Mira").fetchSemanticsNode().id)
        assertEquals(keypadTop, compose.onNodeWithContentDescription("3, DEF").fetchSemanticsNode().boundsInRoot.top)
    }

    @Test
    fun touchDialerKeysStayInPlaceWhileTypingAndContactMatchesChange() {
        val mira = CallerIdentity("Mira", "3335550101")
        var state by mutableStateOf(
            CommunicationUiState(
                route = CommunicationRoute.DIALER,
                qualification = DialerQualification(true, true, true, true, true, true, true),
                assignments = listOf(PhoneAssignment("1", "favorite", "2025550198", "Favorite contact")),
            ),
        )
        compose.setContent {
            ClaroTheme {
                TouchCommunicationScreen(
                    state = state,
                    onDigit = { state = state.copy(digits = state.digits + it, dialerMatchesLoading = true) },
                    onBackspace = {},
                    onCallEnteredNumber = {},
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
            }
        }
        compose.onNodeWithText("1 · Favorite contact").assertDoesNotExist()
        compose.onNodeWithContentDescription("1").assertIsDisplayed()
        val key = compose.onNodeWithContentDescription("3")
        val initialBounds = key.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        key.performClick()
        compose.onNodeWithText("Matching contacts").assertDoesNotExist()
        assertEquals(initialBounds, key.fetchSemanticsNode().boundsInRoot)

        compose.runOnIdle {
            state = state.copy(dialerMatches = listOf(mira), dialerMatchesLoading = false)
        }
        compose.onNodeWithText("Mira").assertExists()
        assertEquals(initialBounds, key.fetchSemanticsNode().boundsInRoot)
        key.performClick()
        compose.runOnIdle {
            assertEquals("33", state.digits)
            state = state.copy(digits = "3".repeat(64), dialerMatches = emptyList(), dialerMatchesLoading = false)
        }
        compose.onNodeWithText("Matching contacts").assertDoesNotExist()
        assertEquals(initialBounds, key.fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun touchContactDeleteRequiresSeparateConfirmation() {
        val state = classicContactState()
        val deleted = mutableListOf<String>()
        compose.setContent {
            ClaroTheme {
                TouchCommunicationScreen(
                    state = state,
                    onDigit = {},
                    onBackspace = {},
                    onCallEnteredNumber = {},
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
                    canManageContacts = true,
                    onDeleteContact = { deleted.add(it) },
                )
            }
        }
        compose.onNodeWithContentDescription("Edit").assertIsDisplayed()
        val deleteIcon = compose.onNodeWithContentDescription("Delete")
        deleteIcon.assertIsDisplayed().performClick()
        compose.onNodeWithText("Delete contact").assertIsDisplayed()
        compose.runOnIdle { assertTrue(deleted.isEmpty()) }

        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete contact").assertDoesNotExist()
        compose.runOnIdle { assertTrue(deleted.isEmpty()) }

        deleteIcon.performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals(listOf("mira"), deleted) }
    }

    @Test
    fun maximumSpeakerVolumeRequiresConfirmationAndAutomaticModeCanBeDisabled() {
        var preferences by mutableStateOf(ClaroPreferences())
        compose.setContent {
            ClaroTheme {
                CallAudioSettingsScreen(
                    preferences = preferences,
                    onChange = { enabled, maximum ->
                        preferences = preferences.copy(easyCallAudio = enabled, maximumSpeakerVolume = maximum)
                    },
                    onBack = {},
                )
            }
        }
        compose.onNodeWithText("Automatic speaker and earpiece").assertIsOn()
        compose.onNodeWithText("Maximum speaker volume").assertIsOff().performClick()
        compose.runOnIdle { assertTrue(!preferences.maximumSpeakerVolume) }
        compose.onNodeWithText("Enable maximum speaker volume").performClick()
        compose.runOnIdle { assertTrue(preferences.maximumSpeakerVolume) }
        compose.onNodeWithText("Automatic speaker and earpiece").performClick()
        compose.runOnIdle {
            assertTrue(!preferences.easyCallAudio)
            assertTrue(!preferences.maximumSpeakerVolume)
        }
    }

    @Test
    fun classicDialerOptionsExcludeTextEditingActions() {
        compose.setContent {
            ClaroTheme {
                ClassicContactFixture(
                    state = classicContactState().copy(
                        route = CommunicationRoute.DIALER,
                        digits = "1234",
                        selectedContact = null,
                    ),
                    unlocked = false,
                )
            }
        }
        compose.onNodeWithText("Options").performClick()
        compose.onNodeWithText("Contacts").assertIsDisplayed()
        compose.onNodeWithText("Emergency services").assertDoesNotExist()
        compose.onNodeWithText("Delete character").assertDoesNotExist()
        compose.onNodeWithText("Clear text").assertDoesNotExist()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("1234").assertIsDisplayed()
    }

    @Test
    fun classicContactsOptionsContainOnlyRelevantContactActions() {
        compose.setContent {
            ClaroTheme {
                ClassicContactFixture(
                    state = classicContactState().copy(route = CommunicationRoute.CONTACTS, contactQuery = "a"),
                    unlocked = false,
                )
            }
        }
        compose.onNodeWithText("Options").performClick()
        compose.onNodeWithText("Add contact").assertIsDisplayed()
        compose.onNodeWithText("Delete character").assertDoesNotExist()
        compose.onNodeWithText("Clear text").assertDoesNotExist()
        compose.onNodeWithText("Emergency services").assertDoesNotExist()
    }

    @Test
    fun classicDialerSuggestionsSelectAVisiblePersonWithoutCalling() {
        val identity = CallerIdentity("Mira", "2025550101")
        var state by mutableStateOf(
            classicContactState().copy(
                route = CommunicationRoute.DIALER,
                digits = "202",
                selectedContact = null,
                dialerMatches = listOf(identity),
            ),
        )
        compose.setContent {
            ClaroTheme {
                ClassicContactFixture(
                    state = state,
                    unlocked = false,
                    onSelectDialerContact = {
                        state = state.copy(digits = it.number.orEmpty(), pendingIdentity = it, dialerMatches = emptyList())
                    },
                )
            }
        }
        compose.onNodeWithText("Matching contacts").assertIsDisplayed()
        compose.onNodeWithText("Mira").performClick()
        compose.onNodeWithTag("classic-dialer-selected-contact").assertIsDisplayed()
        compose.onNodeWithText("Mira").assertIsDisplayed()
        compose.onNodeWithText("2025550101").assertIsDisplayed()
    }

    @Test
    fun classicContactWritesRequireUnlockAndDeleteConfirmation() {
        var state by mutableStateOf(classicContactState())
        var unlocked by mutableStateOf(false)
        var unlockRequests = 0
        val deleted = mutableListOf<String>()
        compose.setContent {
            ClaroTheme {
                ClassicContactFixture(
                    state = state,
                    unlocked = unlocked,
                    onUnlock = { unlockRequests++ },
                    onDelete = { deleted += it },
                )
            }
        }
        openClassicContactOption("Edit contact")
        compose.onNodeWithTag("classic-contact-editor").assertDoesNotExist()
        openClassicContactOption("Delete contact")
        compose.runOnIdle {
            assertEquals(2, unlockRequests)
            assertTrue(deleted.isEmpty())
            state = state.copy(route = CommunicationRoute.CONTACTS)
        }
        compose.onNodeWithContentDescription("Add contact").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(3, unlockRequests)
            state = state.copy(route = CommunicationRoute.CONTACT_DETAIL)
            unlocked = true
        }
        openClassicContactOption("Delete contact")
        compose.runOnIdle { assertTrue(deleted.isEmpty()) }
        compose.onNodeWithTag("classic-cancel-delete-contact").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(deleted.isEmpty()) }
        openClassicContactOption("Delete contact")
        compose.onNodeWithTag("classic-confirm-delete-contact").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("mira"), deleted)
            state = state.copy(
                assignments = listOf(PhoneAssignment("sos", "mira", "2025550101", "Mira")),
            )
        }
        openClassicContactOption("Delete contact")
        compose.onNodeWithTag("classic-confirm-delete-contact").assertDoesNotExist()
        compose.onNodeWithTag("classic-cancel-delete-contact").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("mira"), deleted)
            state = state.copy(
                selectedContact = state.selectedContact?.copy(canEdit = false, canDelete = false),
            )
        }
        compose.onNodeWithText("Options").performClick()
        compose.onNodeWithText("Edit contact").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Delete contact").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun classicContactEditorUsesKeypadAndProtectsCanceledOrLockedDrafts() {
        var state by mutableStateOf(classicContactState())
        var unlocked by mutableStateOf(true)
        var saved: List<String?>? = null
        compose.setContent {
            ClaroTheme {
                ClaroKeyboardContainer(manualKeyboard = true) {
                    ClassicContactFixture(
                        state = state,
                        unlocked = unlocked,
                        onSave = { key, name, primary, secondary ->
                            saved = listOf(key, name, primary, secondary)
                        },
                    )
                }
            }
        }
        openClassicContactOption("Edit contact")
        compose.onNodeWithTag("classic-contact-name").performScrollTo().performTextReplacement("Mira updated")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(null, saved) }
        openClassicContactOption("Edit contact")
        compose.onNodeWithTag("classic-contact-name").assertIsDisplayed()
        compose.onNodeWithTag("classic-contact-primary").performScrollTo().performTextReplacement("2025550199")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            assertEquals(listOf("mira", "Mira", "2025550199", ""), saved)
            saved = null
            state = state.copy(route = CommunicationRoute.CONTACTS, selectedContact = null)
        }
        compose.onNodeWithContentDescription("Add contact").assertIsDisplayed().performClick()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(null, saved) }
        compose.onNodeWithTag("classic-contact-name").performScrollTo().performClick()
        compose.onNodeWithText("2").performClick()
        compose.onNodeWithText("3").performClick()
        compose.onNodeWithTag("classic-contact-primary").performScrollTo().performClick()
        compose.onNodeWithText("2").performClick()
        compose.onNodeWithText("3").performClick()
        compose.onNodeWithText("4").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            assertEquals(listOf(null, "Ad", "234", ""), saved)
            saved = null
        }
        compose.onNodeWithContentDescription("Add contact").assertIsDisplayed().performClick()
        compose.runOnIdle { unlocked = false }
        compose.onNodeWithTag("classic-contact-editor").assertDoesNotExist()
        compose.runOnIdle { assertEquals(null, saved) }
    }

    private fun openClassicContactOption(label: String) {
        compose.onNodeWithText("Options").performClick()
        compose.onNodeWithText(label).performScrollTo().performClick()
    }

    @Test
    fun classicContactDetailsStartAtTopAfterScrollingTheContactList() {
        val contacts = (1..15).map { index ->
            ClaroContact(
                lookupKey = "person-$index",
                displayName = "Person $index",
                numbers = listOf(ContactNumber("20255501${index.toString().padStart(2, '0')}", "Mobile")),
            )
        }
        var state by mutableStateOf(
            classicContactState().copy(route = CommunicationRoute.CONTACTS, contacts = contacts, selectedContact = null),
        )
        compose.setContent {
            ClaroTheme {
                ClassicContactFixture(
                    state = state,
                    unlocked = true,
                    onOpenContact = { key ->
                        state = state.copy(
                            route = CommunicationRoute.CONTACT_DETAIL,
                            selectedContact = contacts.first { it.lookupKey == key },
                        )
                    },
                )
            }
        }
        compose.onNodeWithTag("classic-phone-content").performScrollToNode(hasText("Person 15"))
        compose.onNodeWithText("Person 15").performClick()
        compose.onNodeWithText("Person 15").assertIsDisplayed()
        compose.onNodeWithTag("classic-contact-avatar").assertIsDisplayed()
        compose.onNodeWithText("Edit contact").assertDoesNotExist()
        compose.onNodeWithText("Delete contact").assertDoesNotExist()
        compose.onNodeWithText("Options").performClick()
        compose.onNodeWithText("Edit contact").assertIsDisplayed()
        compose.onNodeWithText("Delete contact").assertIsDisplayed()
    }

    @Test
    fun classicHomeHoldKeysEnterDigitsWithoutAdvertisingSpeedDial() {
        val digits = mutableListOf<Char>()
        compose.setContent {
            ClaroTheme {
                ClassicPhoneScreen(
                    home = storyboardHome(),
                    onOpenMenu = {},
                    onTileSelected = {},
                    onRepairHomeRole = {},
                    onReturnToCall = {},
                    onDigitSelected = { digits += it },
                )
            }
        }
        compose.onNodeWithContentDescription("1").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("2, ABC").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("#").performClick()
        compose.onNodeWithText("*").performClick()
        compose.onNodeWithText("*").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(listOf('1', '2', '#', '*', '+'), digits)
        }
    }

    private fun classicContactState() = CommunicationUiState(
        route = CommunicationRoute.CONTACT_DETAIL,
        qualification = DialerQualification(true, true, true, true, true, true, true),
        selectedContact = ClaroContact(
            lookupKey = "mira",
            displayName = "Mira",
            numbers = listOf(ContactNumber("2025550101", "Mobile")),
        ),
    )

    @Composable
    private fun ClassicContactFixture(
        state: CommunicationUiState,
        unlocked: Boolean,
        onUnlock: () -> Unit = {},
        onDelete: (String) -> Unit = {},
        onSave: (String?, String, String, String) -> Unit = { _, _, _, _ -> },
        onSelectDialerContact: (CallerIdentity) -> Unit = {},
        onOpenContacts: () -> Unit = {},
        onOpenContact: (String) -> Unit = {},
    ) {
        ClassicCommunicationScreen(
            state = state,
            onDigit = {},
            onBackspace = {},
            onCallEnteredNumber = {},
            onOpenContacts = onOpenContacts,
            onSearchContacts = {},
            onLoadMoreContacts = {},
            onOpenContact = onOpenContact,
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
            canManageContacts = unlocked,
            onSaveContact = { key, name, primary, secondary, _ -> onSave(key, name, primary, secondary) },
            onDeleteContact = onDelete,
            onRequestUnlockForContacts = onUnlock,
            onSelectDialerContact = onSelectDialerContact,
        )
    }

    @Test
    fun returnToCallStaysVisibleAwayFromCallControlsAndHidesAfterDisconnect() {
        var phoneScreen by mutableStateOf(false)
        var state by mutableStateOf(
            CommunicationUiState(
                route = CommunicationRoute.ACTIVE_CALL,
                qualification = DialerQualification(
                    roleAvailable = true,
                    roleHeld = true,
                    telephonySupported = true,
                    callPermissionGranted = true,
                    callLogPermissionGranted = true,
                    hasUsableCallingAccount = true,
                    phoneStatePermissionGranted = true,
                ),
                call = CallSession(
                    id = "active",
                    direction = CallDirection.OUTGOING,
                    identity = CallerIdentity("Mira", "2025550101"),
                    phase = CallPhase.CONNECTED,
                ),
            ),
        )
        var returns = 0
        compose.setContent {
            ClaroTheme {
                ActiveCallReturnBar(
                    state = state,
                    isPhoneScreen = phoneScreen,
                    onReturnToCall = { returns++ },
                )
            }
        }

        compose.onNodeWithTag("return-to-call")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(64.dp)
            .performClick()
        assertEquals(1, returns)

        compose.runOnIdle {
            phoneScreen = true
            state = state.copy(route = CommunicationRoute.CONTACTS)
        }
        compose.onNodeWithTag("return-to-call").assertIsDisplayed()

        compose.runOnIdle { state = state.copy(route = CommunicationRoute.ACTIVE_CALL) }
        compose.onNodeWithTag("return-to-call").assertDoesNotExist()

        compose.runOnIdle {
            phoneScreen = false
            state = state.copy(
                route = CommunicationRoute.CALL_ENDED,
                call = state.call?.copy(phase = CallPhase.DISCONNECTED),
            )
        }
        compose.onNodeWithTag("return-to-call").assertDoesNotExist()
    }

    @Test
    fun repeatedMenuNavigationKeepsTheEntryAndScreenMounted() {
        lateinit var navigation: NavHostController
        var mounted = 0
        var disposed = 0
        compose.setContent {
            navigation = rememberNavController()
            NavHost(navController = navigation, startDestination = ClassicHomeRoute) {
                composable<ClassicHomeRoute> { Text("Home screen") }
                composable<LauncherMenuRoute> {
                    DisposableEffect(Unit) {
                        mounted++
                        onDispose { disposed++ }
                    }
                    Text("Menu screen")
                }
            }
        }
        compose.runOnIdle { navigation.navigateIfNotCurrent(LauncherMenuRoute) }
        compose.onNodeWithText("Menu screen").assertIsDisplayed()
        val original = compose.runOnIdle { navigation.currentBackStackEntry!! }
        val originalNode = compose.onNodeWithText("Menu screen").fetchSemanticsNode().id
        repeat(3) {
            compose.runOnIdle {
                navigation.navigateIfNotCurrent(LauncherMenuRoute)
                assertSame(original, navigation.currentBackStackEntry)
            }
            assertEquals(originalNode, compose.onNodeWithText("Menu screen").fetchSemanticsNode().id)
        }
        compose.runOnIdle {
            assertEquals(1, mounted)
            assertEquals(0, disposed)
        }
    }

    @Test
    fun repeatedScreenNavigationPreservesStateButDifferentArgumentsStillNavigateBack() {
        lateinit var navigation: NavHostController
        compose.setContent {
            navigation = rememberNavController()
            NavHost(navController = navigation, startDestination = RepeatNavigationRoute(1)) {
                composable<RepeatNavigationRoute> { entry ->
                    Text("Item ${entry.toRoute<RepeatNavigationRoute>().item}")
                }
            }
        }
        compose.onNodeWithText("Item 1").assertIsDisplayed()
        val original = compose.runOnIdle { navigation.currentBackStackEntry!! }
        compose.runOnIdle {
            original.savedStateHandle["draft"] = "preserved"
            navigation.navigateIfNotCurrent(RepeatNavigationRoute(1))
            assertSame(original, navigation.currentBackStackEntry)
            assertEquals("preserved", navigation.currentBackStackEntry?.savedStateHandle?.get<String>("draft"))
            navigation.navigateIfNotCurrent(RepeatNavigationRoute(2))
        }
        compose.onNodeWithText("Item 2").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(original.id, navigation.previousBackStackEntry?.id)
            navigation.popBackStack()
        }
        compose.onNodeWithText("Item 1").assertIsDisplayed()
        compose.runOnIdle {
            assertSame(original, navigation.currentBackStackEntry)
            assertEquals("preserved", navigation.currentBackStackEntry?.savedStateHandle?.get<String>("draft"))
        }
    }

    @Test
    fun classicMenuHomeReusesExistingHomeEntry() {
        assertMenuHomeReusesEntry(VisualMode.CLASSIC)
    }

    @Test
    fun backKeepsEachClassicDeckWithItsOwnScreen() {
        lateinit var navigation: NavHostController
        var phoneRoute by mutableStateOf(CommunicationRoute.DIALER)
        compose.setContent {
            navigation = rememberNavController()
            val frame: DestinationFrame = { entry, content ->
                Box(Modifier.testTag(entry.destination.route.orEmpty())) {
                    ClaroDestinationLayout(
                        useGlobalDeck = usesClassicDestinationDeck(entry, VisualMode.CLASSIC, phoneRoute),
                        content = content,
                        controls = { host ->
                            if (host != null) Text("Shared deck", Modifier.testTag("destination-deck"))
                        },
                    )
                }
            }
            NavHost(
                navController = navigation,
                startDestination = ClassicHomeRoute,
                popEnterTransition = { fadeIn(tween(500)) },
                popExitTransition = { fadeOut(tween(500)) },
            ) {
                framedComposable<ClassicHomeRoute>(frame) { Text("Home content") }
                framedComposable<LauncherMenuRoute>(frame) { Text("Menu content") }
                framedComposable<PhoneRoute>(frame) { Text("Phone content") }
                framedComposable<MessagesRoute>(frame) { Text("Messages content") }
            }
        }

        fun assertDeck(routeTag: String, expected: Boolean) {
            val node = compose.onNode(hasTestTag("destination-deck") and hasAnyAncestor(hasTestTag(routeTag)))
            if (expected) node.assertExists() else node.assertDoesNotExist()
        }

        compose.waitForIdle()
        val homeTag = compose.runOnIdle { checkNotNull(navigation.currentDestination?.route) }
        for (destination in listOf(LauncherMenuRoute, PhoneRoute, MessagesRoute)) {
            compose.runOnIdle {
                phoneRoute = CommunicationRoute.CONTACTS
                navigation.navigate(destination)
            }
            val outgoingTag = compose.runOnIdle { checkNotNull(navigation.currentDestination?.route) }
            val hasOutgoingDeck = destination == LauncherMenuRoute
            assertDeck(outgoingTag, hasOutgoingDeck)
            compose.mainClock.autoAdvance = false
            try {
                compose.runOnIdle { assertTrue(navigation.popBackStack()) }
                compose.mainClock.advanceTimeBy(100)
                compose.onNodeWithTag(outgoingTag).assertExists()
                assertDeck(outgoingTag, hasOutgoingDeck)
                assertDeck(homeTag, true)
            } finally {
                compose.mainClock.autoAdvance = true
            }
            compose.onNodeWithTag(outgoingTag).assertDoesNotExist()
            assertDeck(homeTag, true)
        }
    }

    @Test
    fun touchMenuHomeReusesExistingHomeEntry() {
        assertMenuHomeReusesEntry(VisualMode.TOUCH)
    }

    private fun assertMenuHomeReusesEntry(mode: VisualMode) {
        lateinit var navigation: NavHostController
        val home: Any = if (mode == VisualMode.CLASSIC) ClassicHomeRoute else TouchHomeRoute
        compose.setContent {
            navigation = rememberNavController()
            NavHost(navController = navigation, startDestination = home) {
                composable<ClassicHomeRoute> { Text("Home screen") }
                composable<TouchHomeRoute> { Text("Home screen") }
                composable("menu-regression") { Text("Menu screen") }
            }
        }

        compose.onNodeWithText("Home screen").assertIsDisplayed()
        val original = compose.runOnIdle { navigation.currentBackStackEntry!! }
        compose.runOnIdle {
            original.savedStateHandle["home-marker"] = "preserved"
            navigation.navigate("menu-regression")
        }
        compose.onNodeWithText("Menu screen").assertIsDisplayed()
        compose.runOnIdle { navigation.goHome(mode) }
        compose.onNodeWithText("Home screen").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(original.id, navigation.currentBackStackEntry?.id)
            assertEquals("preserved", navigation.currentBackStackEntry?.savedStateHandle?.get<String>("home-marker"))
            navigation.goHome(mode)
            assertEquals(original.id, navigation.currentBackStackEntry?.id)
            assertEquals(null, navigation.previousBackStackEntry)
        }
    }

    @Test
    fun classicGlobalBackDeletesSelectionAndUnicodeBeforeNavigating() {
        var value by mutableStateOf(TextFieldValue(""))
        var navigations = 0
        compose.setContent {
            ClaroTheme {
                ClaroKeyboardContainer(manualKeyboard = true) {
                    val host = remember { ClaroSoftkeyHost() }
                    Column {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it },
                            modifier = Modifier.testTag("editable-search"),
                        )
                        ClassicGlobalDeck(
                            onBack = { navigations++ },
                            onCall = {},
                            onMenu = {},
                            onHome = {},
                            softkeyHost = host,
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("editable-search").performClick()
        compose.runOnIdle { value = TextFieldValue("word", TextRange(1, 3)) }
        compose.onNodeWithText("Back").performClick()
        compose.runOnIdle {
            assertEquals("wd", value.text)
            assertEquals(0, navigations)
            value = TextFieldValue("A\uD83D\uDE42B", TextRange(3))
        }
        compose.onNodeWithText("Back").performClick()
        compose.runOnIdle {
            assertEquals("AB", value.text)
            assertEquals(0, navigations)
            value = TextFieldValue("")
        }
        compose.onNodeWithText("2").performClick()
        compose.runOnIdle { assertEquals("A", value.text) }
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("2").performClick()
        compose.runOnIdle { assertEquals("A", value.text) }
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Back").performClick()
        compose.runOnIdle {
            assertEquals("", value.text)
            assertEquals(1, navigations)
        }
    }

    @Test
    fun contactAvatarLoadsPhotoAndFallsBackWhenPhotoIsUnavailable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("claro-contact-photo-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        var photoUri by mutableStateOf<String?>(Uri.fromFile(file).toString())
        try {
            compose.setContent {
                ClaroTheme {
                    Column {
                        ClaroAvatar(
                            name = "Photo contact",
                            size = 64.dp,
                            photoUri = photoUri,
                            modifier = Modifier.testTag("contact-photo"),
                        )
                        ClaroAvatar(
                            name = "Photo contact",
                            size = 64.dp,
                            modifier = Modifier.testTag("photo-fallback"),
                        )
                    }
                }
            }
            fun centerColor(tag: String): Int {
                val image = compose.onNodeWithTag(tag).captureToImage()
                return image.toPixelMap()[image.width / 2, image.height / 2].toArgb()
            }
            compose.waitUntil(5_000) {
                centerColor("contact-photo") == android.graphics.Color.RED
            }
            compose.runOnIdle {
                photoUri = Uri.fromFile(File("${file.absolutePath}.missing")).toString()
            }
            compose.waitUntil(5_000) {
                centerColor("contact-photo") == centerColor("photo-fallback")
            }
        } finally {
            assertTrue(file.delete())
        }
    }

    @Test
    fun classicFocusedSearchUsesLettersAndNumericFieldsKeepDigits() {
        var search by mutableStateOf("")
        var number by mutableStateOf("")
        var inputSessions = 0
        compose.setContent {
            InterceptPlatformTextInput({ _, _ ->
                inputSessions++
                awaitCancellation()
            }) {
                ClaroKeyboardContainer(manualKeyboard = true) {
                    val keypadInput = LocalClaroKeypadInput.current
                    Column {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.testTag("app-search"),
                        )
                        OutlinedTextField(
                            value = number,
                            onValueChange = { number = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.testTag("number-field"),
                        )
                        ClassicNumericKeypad(
                            onDigitSelected = { keypadInput?.invoke(it) },
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("app-search").performClick()
        compose.onNodeWithText("2").performClick()
        compose.runOnIdle { assertEquals("A", search) }
        compose.onNodeWithText("2").performClick()
        compose.runOnIdle { assertEquals("B", search) }
        compose.onNodeWithText("3").performClick()
        compose.runOnIdle { assertEquals("Bd", search) }
        compose.onNodeWithText("#").performTouchInput { longClick() }
        compose.onNodeWithText("אבג").assertIsDisplayed()
        compose.onNodeWithText("3").performClick()
        compose.runOnIdle { assertEquals("Bdד", search) }
        compose.onNodeWithText("3").performClick()
        compose.runOnIdle { assertEquals("Bdה", search) }
        compose.onNodeWithText("#").performTouchInput { longClick() }
        compose.onNodeWithText("ABC").assertIsDisplayed()
        compose.onNodeWithText("#").performClick()
        compose.onNodeWithText("#").performClick()
        compose.onNodeWithText("2").performClick()
        compose.runOnIdle { assertEquals("Bdה2", search) }
        compose.onNodeWithText("#").performClick()
        compose.onNodeWithText("2").performClick()
        compose.runOnIdle { assertEquals("Bdה2a", search) }

        compose.onNodeWithTag("number-field").performClick()
        compose.onNodeWithText("2").performClick()
        compose.runOnIdle {
            assertEquals("2", number)
            assertEquals(0, inputSessions)
        }
    }

    @Test
    fun classicLetterEntryReplacesSelectionWithoutLosingFollowingText() {
        val (first, tap) = applyClassicKeypadInput(
            currentValue = "word",
            digit = '2',
            fieldKey = "search",
            numericMode = false,
            previousTap = null,
            selectionStart = 1,
            selectionEnd = 3,
        )
        assertEquals("wad", first)
        val (second, _) = applyClassicKeypadInput(
            currentValue = first,
            digit = '2',
            fieldKey = "search",
            numericMode = false,
            previousTap = tap,
            selectionStart = 2,
        )
        assertEquals("wbd", second)
    }

    @Test
    fun classicSystemKeyboardRequiresExplicitOpenForEachFocusedField() {
        var first by mutableStateOf("")
        var second by mutableStateOf("")
        var inputSessions = 0
        compose.setContent {
            InterceptPlatformTextInput({ _, _ ->
                inputSessions++
                awaitCancellation()
            }) {
                ClaroKeyboardContainer(manualKeyboard = true) {
                    Column {
                        OutlinedTextField(
                            value = first,
                            onValueChange = { first = it },
                            modifier = Modifier.testTag("first-field"),
                        )
                        OutlinedTextField(
                            value = second,
                            onValueChange = { second = it },
                            modifier = Modifier.testTag("second-field"),
                        )
                        com.clarohome.presentation.classic.ClassicKeypadModeStatus(LocalClaroKeypadMode.current)
                        ClassicNumericKeypad(onDigitSelected = {})
                    }
                }
            }
        }

        compose.onNodeWithTag("first-field").performClick()
        compose.onNodeWithTag("first-field").performKeyInput { pressKey(Key.One) }
        compose.runOnIdle {
            assertEquals("1", first)
            assertEquals(0, inputSessions)
        }
        compose.onNodeWithText("#").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(0, inputSessions) }
        compose.onNodeWithText("Open keyboard").performClick()
        compose.runOnIdle { assertEquals(1, inputSessions) }

        compose.onNodeWithTag("second-field").performClick()
        compose.runOnIdle { assertEquals(1, inputSessions) }
        compose.onNodeWithText("#").performClick()
        compose.runOnIdle { assertEquals(1, inputSessions) }
        compose.onNodeWithText("Open keyboard").performClick()
        compose.runOnIdle { assertEquals(2, inputSessions) }
    }

    @Test
    fun touchKeyboardStillStartsWhenFieldIsFocused() {
        var inputSessions = 0
        compose.setContent {
            InterceptPlatformTextInput({ _, _ ->
                inputSessions++
                awaitCancellation()
            }) {
                ClaroKeyboardContainer(manualKeyboard = false) {
                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        modifier = Modifier.testTag("touch-field"),
                    )
                }
            }
        }

        compose.onNodeWithTag("touch-field").performClick()
        compose.runOnIdle { assertEquals(1, inputSessions) }
    }

    @Test
    fun signalDistinguishesUnknownStrengthFromNoServiceAndMeasuredBars() {
        var cellular by mutableStateOf(CellularStatus(CellularAvailability.UNKNOWN))
        compose.setContent {
            ClaroTheme {
                ClaroCellularBars(cellular = cellular)
            }
        }

        compose.onNodeWithContentDescription("Mobile signal: Unknown").assertExists()
        compose.onNodeWithText(" ?", useUnmergedTree = true).assertExists()

        cellular = CellularStatus(CellularAvailability.AVAILABLE)
        compose.onNodeWithContentDescription("Mobile signal: Unknown").assertExists()
        compose.onNodeWithContentDescription("Mobile signal: 0 of 4").assertDoesNotExist()

        cellular = CellularStatus(CellularAvailability.NO_SERVICE)
        compose.onNodeWithContentDescription("Mobile signal: No signal").assertExists()

        cellular = CellularStatus(CellularAvailability.AVAILABLE, 3)
        compose.onNodeWithContentDescription("Mobile signal: 3 of 4").assertExists()
        compose.onNodeWithText(" ?", useUnmergedTree = true).assertDoesNotExist()
    }

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
        compose.onNodeWithText("Continue free").assertDoesNotExist()
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
    fun onboardingBackClosesPickerThenReturnsToPreviousStep() {
        var step by mutableStateOf(OnboardingStep.LANGUAGE_AND_DISPLAY)
        lateinit var backDispatcher: OnBackPressedDispatcher
        compose.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            ClaroTheme {
                OnboardingFlow(
                    preferences = ClaroPreferences(onboarding = OnboardingProgress(currentStep = step)),
                    message = null,
                    callbacks = noOpOnboardingCallbacks(onContinue = { step = it }),
                )
            }
        }

        compose.onNodeWithText("Language", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Español").assertIsDisplayed()
        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.onNodeWithText("Language and display").assertIsDisplayed()
        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.onNodeWithText("Choose phone style").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Welcome to Claro").assertIsDisplayed()
    }

    @Test
    fun homeTileSetupKeepsChoicesWhenMovingForwardAndBack() {
        var step by mutableStateOf(OnboardingStep.HOME_TILES)
        var kinds by mutableStateOf(
            setOf(
                HomeTileKind.PHONE,
                HomeTileKind.SOS,
                HomeTileKind.CAMERA,
                HomeTileKind.GALLERY,
                HomeTileKind.APPS,
            ),
        )
        compose.setContent {
            ClaroTheme {
                OnboardingFlow(
                    preferences = ClaroPreferences(onboarding = OnboardingProgress(currentStep = step)),
                    message = null,
                    homeTileKinds = kinds,
                    callbacks = noOpOnboardingCallbacks(
                        onContinue = { step = it },
                        onHomeTileVisibility = { kind, visible ->
                            kinds = if (visible) kinds + kind else kinds - kind
                        },
                    ),
                )
            }
        }
        compose.onNodeWithContentDescription("Setup step 3 of 7").assertIsDisplayed()
        compose.onNodeWithText("Show Messages on Home").assertIsOff().performClick()
        compose.onNodeWithText("Show Messages on Home").assertIsOn().performClick()
        for (label in listOf("Show Camera on Home", "Show Gallery on Home", "Show Apps on Home")) {
            compose.onNodeWithText(label).assertIsOn().performClick()
        }
        compose.runOnIdle { assertEquals(setOf(HomeTileKind.PHONE, HomeTileKind.SOS), kinds) }

        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Settings access").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Choose Home tiles").assertIsDisplayed()
        compose.onNodeWithText("Show Messages on Home").assertIsOff()
        compose.onNodeWithText("Show Camera on Home").assertIsOff()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Language and display").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Choose Home tiles").assertIsDisplayed()
        compose.onNodeWithText("Show Gallery on Home").assertIsOff()
        compose.onNodeWithText("Show Apps on Home").assertIsOff()
    }

    @Test
    fun permissionsFinishSetupWithoutHandoverOrPlans() {
        var next: OnboardingStep? = null
        compose.setContent {
            ClaroTheme {
                OnboardingFlow(
                    preferences = ClaroPreferences(
                        onboarding = OnboardingProgress(
                            currentStep = OnboardingStep.ROLE_READINESS,
                            readiness = ReadinessCapability.entries.associateWith { CapabilityStatus.READY },
                        ),
                    ),
                    message = null,
                    callbacks = noOpOnboardingCallbacks(onContinue = { next = it }),
                )
            }
        }

        compose.onNodeWithText("Optional plans").assertDoesNotExist()
        compose.onNodeWithText("Finish setup").performClick()
        compose.runOnIdle { assertEquals(OnboardingStep.COMPLETE, next) }
    }

    @Test
    fun onboardingStartsDefaultAppSetupAndCannotFinishWithMissingDefaults() {
        var requests = 0
        var next: OnboardingStep? = null
        var defaults by mutableStateOf(
            com.clarohome.core.model.defaultAppCapabilities.associateWith { CapabilityStatus.NEEDS_ATTENTION },
        )
        compose.setContent {
            ClaroTheme {
                OnboardingFlow(
                    preferences = ClaroPreferences(
                        onboarding = OnboardingProgress(currentStep = OnboardingStep.ROLE_READINESS),
                    ),
                    message = null,
                    defaultAppReadiness = defaults,
                    callbacks = noOpOnboardingCallbacks(onContinue = { next = it }).copy(
                        onSetDefaultApps = { requests++ },
                    ),
                )
            }
        }
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onNodeWithText("Finish setup").assertIsNotEnabled()
        compose.onNodeWithText("Set Claro as default").performClick()
        compose.runOnIdle {
            assertEquals(2, requests)
            assertEquals(null, next)
            defaults = defaults + (ReadinessCapability.HOME to CapabilityStatus.READY)
        }
        compose.onNodeWithText("Finish setup").assertIsNotEnabled()
        compose.runOnIdle {
            defaults = defaults + (ReadinessCapability.PHONE to CapabilityStatus.READY)
        }
        compose.onNodeWithText("Finish setup").assertIsNotEnabled()
        compose.runOnIdle {
            defaults = defaults + (ReadinessCapability.MESSAGES to CapabilityStatus.READY)
        }
        compose.onNodeWithText("Finish setup").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(2, requests)
            assertEquals(OnboardingStep.COMPLETE, next)
        }
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
        compose.onNodeWithText("Default home", useUnmergedTree = true).performClick()
        compose.runOnIdle {
            assertEquals(listOf(ReadinessCapability.HOME, ReadinessCapability.HOME), requested)
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
    fun classicPhotoNavigationUsesUpDownAndStopsDuringDeleteConfirmation() {
        var position by mutableStateOf(1)
        var confirmingDelete by mutableStateOf(false)
        compose.setContent {
            ClaroTheme {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .galleryPhotoNavigation(
                                total = 3,
                                confirmingDelete = confirmingDelete,
                                onPrevious = { position-- },
                                onNext = { position++ },
                            )
                            .focusRequester(focusRequester)
                            .focusable(),
                    )
                    ClassicIntegratedPad()
                }
            }
        }

        compose.onNodeWithContentDescription("Navigate down").performClick()
        compose.runOnIdle { assertEquals(2, position) }
        compose.onNodeWithContentDescription("Navigate up").performClick()
        compose.runOnIdle { assertEquals(1, position) }
        compose.runOnIdle { confirmingDelete = true }
        compose.onNodeWithContentDescription("Navigate down").performClick()
        compose.runOnIdle { assertEquals(1, position) }
    }

    @Test
    fun classicUpDownTraversesHomeTilesAcrossColumns() {
        val selected = mutableListOf<String>()
        compose.setContent {
            ClaroTheme {
                ClassicPhoneScreen(
                    home = storyboardHome().copy(homeRole = HomeRoleState.GRANTED),
                    onOpenMenu = {},
                    onTileSelected = { selected += it.label },
                    onRepairHomeRole = {},
                    onReturnToCall = {},
                )
            }
        }

        for (label in listOf("Mira", "Daniel", "Leah", "Noah")) {
            compose.onNodeWithContentDescription("Navigate down").performClick()
            compose.onNodeWithText("OK").performClick()
            compose.runOnIdle { assertEquals(label, selected.lastOrNull()) }
        }
        compose.onNodeWithContentDescription("Navigate up").performClick()
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { assertEquals("Leah", selected.lastOrNull()) }
    }

    @Test
    fun classicHomeKeepsIntegratedPadAndPermanentKeypad() {
        compose.setContent {
            ClaroTheme {
                ClassicPhoneScreen(
                    home = storyboardHome(),
                    onOpenMenu = {},
                    onTileSelected = {},
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
        compose.onNodeWithText("↑").assertIsDisplayed()
        compose.onNodeWithText("↓").assertIsDisplayed()
        compose.onNodeWithText("←").assertDoesNotExist()
        compose.onNodeWithText("→").assertDoesNotExist()
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
                            canSendDtmf = true,
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
                        phoneStatePermissionGranted = true,
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
                Box(Modifier.height(640.dp)) {
                    if (mode == VisualMode.TOUCH) {
                        TouchCommunicationScreen(
                            state = baseState,
                            onDigit = {},
                            onBackspace = {},
                            onCallEnteredNumber = {},
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
        }

        compose.onNodeWithTag("touch-communication").assertIsDisplayed()
        capture("touch-dialer", "touch-communication")

        mode = VisualMode.CLASSIC
        compose.onNodeWithTag("classic-communication").assertIsDisplayed()
        capture("classic-dialer", "classic-communication")

        classicRoute = CommunicationRoute.ACTIVE_CALL
        compose.waitForIdle()
        compose.onNodeWithText("Speaker").assertIsDisplayed()
        compose.onNodeWithText("Mute").assertIsDisplayed()
        compose.onNodeWithText("Hold").assertIsDisplayed()
        compose.onNodeWithText("Swap").assertIsDisplayed()
        compose.onNodeWithText("End").assertIsDisplayed()
        capture("classic-active-call", "classic-communication")
        compose.onNodeWithTag("classic-active-caller").assertIsDisplayed()
        compose.onNodeWithTag("classic-active-avatar").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Mira").assertIsDisplayed()
        compose.onNodeWithText("202-555-0101").assertIsDisplayed()
        compose.onNodeWithText("Keypad").performClick()
        compose.onNodeWithTag("classic-active-caller").assertIsDisplayed()
        compose.onNodeWithText("Mira").assertIsDisplayed()
        compose.onNodeWithText("202-555-0101").assertIsDisplayed()
        compose.onNodeWithText("Speaker").assertIsDisplayed()
        compose.onNodeWithText("Mute").assertIsDisplayed()
        compose.onNodeWithText("End").assertIsDisplayed()
        compose.onNodeWithText("Hide keypad").performClick()

        mode = VisualMode.TOUCH
        capture("touch-active-call", "touch-communication")
        compose.onNodeWithTag("touch-active-caller").assertIsDisplayed()
        compose.onNodeWithTag("touch-active-avatar").assertIsDisplayed().assertHeightIsAtLeast(64.dp)
        compose.onNodeWithText("Mira").assertIsDisplayed()
        compose.onNodeWithText("202-555-0101").assertIsDisplayed()
        compose.onNodeWithText("Speaker").assertIsDisplayed()
        compose.onNodeWithText("Mute").assertIsDisplayed()
        compose.onNodeWithText("Hold").assertIsDisplayed()
        compose.onNodeWithText("Keypad").assertIsDisplayed()
        compose.onNodeWithText("End").assertIsDisplayed()

        classicRoute = CommunicationRoute.CALL_ENDED
        compose.waitForIdle()
        assertTrue(capture("touch-call-ended", "touch-communication").containsColor(ClaroColors.RedOnDark))

        mode = VisualMode.CLASSIC
        compose.waitForIdle()
        assertTrue(capture("classic-call-ended", "classic-communication").containsColor(ClaroColors.RedOnDark))

        classicRoute = CommunicationRoute.MISSED_CALL
        compose.waitForIdle()
        compose.onNodeWithText("Mira").assertIsDisplayed()
        capture("classic-missed-call", "classic-communication")
    }

    @Test
    fun touchMessagingMatchesInboxAndComposeStoryboard() {
        var messagingRoute by mutableStateOf(MessagingRoute.INBOX)
        var backDispatcher: androidx.activity.OnBackPressedDispatcher? = null
        compose.setContent {
            backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
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
                    onCancelFailure = { messagingRoute = MessagingRoute.INBOX },
                    onRepair = {},
                    onHome = {},
                )
            }
        }

        compose.onNodeWithText("Write a message").assertHeightIsAtLeast(64.dp)
        capture("messages-inbox", "touch-messaging")

        compose.onNodeWithText("Write a message").performClick()
        compose.onNodeWithText("Send").assertHeightIsAtLeast(64.dp)
        capture("message-compose", "touch-messaging")
        compose.runOnIdle { checkNotNull(backDispatcher).onBackPressed() }
        compose.onNodeWithText("Write a message").assertIsDisplayed()
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
        var selectedTiles = 0
        compose.setContent {
            val haptics = RecordingClaroHaptics(enabled = enabled) { recorded += it }
            ClaroTheme(hapticsOverride = haptics) {
                if (mode == VisualMode.CLASSIC) {
                    ClassicPhoneScreen(
                        home = storyboardHome(),
                        onOpenMenu = {},
                        onTileSelected = { selectedTiles++ },
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
                        onRepairHomeRole = {},
                        onReturnToCall = {},
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("5, JKL").performClick()
        compose.onNodeWithContentDescription("Navigate up").performClick()
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { assertEquals(1, selectedTiles) }
        compose.onNodeWithText("Call").performClick()

        compose.runOnIdle {
            assertEquals(
                listOf(
                    HapticCue.KEYPAD_DIGIT,
                    HapticCue.DPAD_STEP,
                    HapticCue.KEYPAD_DIGIT,
                    HapticCue.SOFTKEY,
                    HapticCue.CONFIRM_HEAVY,
                ),
                recorded,
            )
            assertEquals(2, selectedTiles)
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
    homeSlots = listOf(
        HomeTile("slot-0", "Mira", TileAccent.BLUE, HomeTileKind.CONTACT, phoneNumber = "202555010"),
        HomeTile("slot-1", "Daniel", TileAccent.BLUE, HomeTileKind.CONTACT, phoneNumber = "202555011"),
        HomeTile("slot-2", "Leah", TileAccent.BLUE, HomeTileKind.CONTACT, phoneNumber = "202555012"),
        HomeTile("slot-3", "Noah", TileAccent.BLUE, HomeTileKind.CONTACT, phoneNumber = "202555013"),
        HomeTile("slot-4", "Phone", TileAccent.GREEN, HomeTileKind.PHONE, BadgeValue.Known(1)),
        HomeTile("slot-5", "Messages", TileAccent.BLUE, HomeTileKind.MESSAGES, BadgeValue.Known(1)),
        HomeTile("slot-6", "Camera", TileAccent.ORANGE, HomeTileKind.CAMERA),
        HomeTile("slot-7", "Gallery", TileAccent.VIOLET, HomeTileKind.GALLERY),
        HomeTile("slot-8", "SOS", TileAccent.RED, HomeTileKind.SOS),
        HomeTile("slot-9", "Apps", TileAccent.BLUE, HomeTileKind.APPS),
        null,
        null,
    ),
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
    onHomeTileVisibility: (HomeTileKind, Boolean) -> Unit = { _, _ -> },
) = OnboardingCallbacks(
    onContinue = onContinue,
    onChooseMode = onChooseMode,
    onLanguageDisplay = { _, _, _ -> },
    onUseDateQuestion = {},
    onConfigurePin = { _, _, _ -> },
    onOpenPeopleSetup = {},
    onOpenSosSetup = {},
    onRequestCapability = {},
    onHomeTileVisibility = onHomeTileVisibility,
)
