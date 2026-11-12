package com.clarohome.app

import android.graphics.Bitmap
import android.os.Environment
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clarohome.core.design.ClaroLocaleContainer
import com.clarohome.core.design.ClaroTheme
import com.clarohome.core.model.AppLanguage
import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.BatteryStatus
import com.clarohome.core.model.CellularAvailability
import com.clarohome.core.model.CellularStatus
import com.clarohome.core.model.ClaroPreferences
import com.clarohome.core.model.HandoverCheck
import com.clarohome.core.model.HomeRoleState
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind
import com.clarohome.core.model.OnboardingProgress
import com.clarohome.core.model.OnboardingStep
import com.clarohome.core.model.TileAccent
import com.clarohome.feature.onboarding.settings.OnboardingCallbacks
import com.clarohome.feature.onboarding.settings.OnboardingFlow
import com.clarohome.presentation.classic.ClassicPhoneScreen
import com.clarohome.presentation.touch.TouchHomeScreen
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizationRegressionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun localeContainerKeepsActivityResultAndBackOwnersForNestedScreens() {
        var hasRegistryOwner = false
        var hasBackOwner = false
        compose.setContent {
            ClaroLocaleContainer(language = AppLanguage.HEBREW) {
                hasRegistryOwner = LocalActivityResultRegistryOwner.current != null
                hasBackOwner = LocalOnBackPressedDispatcherOwner.current != null
            }
        }
        compose.waitForIdle()
        assertEquals(true, hasRegistryOwner)
        assertEquals(true, hasBackOwner)
    }

    @Test
    fun runtimeLanguageSwitchesBetweenEnglishSpanishAndHebrewWithRtl() {
        var language by mutableStateOf(AppLanguage.ENGLISH)
        var observedDirection by mutableStateOf(LayoutDirection.Ltr)
        var showClassic by mutableStateOf(false)
        var showOnboarding by mutableStateOf(true)

        compose.setContent {
            ClaroLocaleContainer(language = language) {
                observedDirection = LocalLayoutDirection.current
                ClaroTheme {
                    when {
                        showOnboarding -> OnboardingFlow(
                            preferences = ClaroPreferences(
                                language = language,
                                onboarding = OnboardingProgress(currentStep = OnboardingStep.WELCOME),
                            ),
                            message = null,
                            callbacks = OnboardingCallbacks(
                                onContinue = {},
                                onChooseMode = {},
                                onLanguageDisplay = { _, _, _ -> },
                                onUseDateQuestion = {},
                                onConfigurePin = { _, _, _ -> },
                                onOpenPeopleSetup = {},
                                onOpenSosSetup = {},
                                onRequestCapability = {},
                                onHandover = { _: HandoverCheck, _: Boolean -> },
                            ),
                        )
                        showClassic -> ClassicPhoneScreen(
                            home = localizedHomeFixture(),
                            onOpenMenu = {},
                            onTileSelected = {},
                            onRepairHomeRole = {},
                            onReturnToCall = {},
                            onDigitSelected = {},
                            onBack = {},
                            onHome = {},
                        )
                        else -> TouchHomeScreen(
                            home = localizedHomeFixture(),
                            onUseClassicMode = {},
                            onTileSelected = {},
                            onRepairHomeRole = {},
                            onReturnToCall = {},
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("Welcome to Claro").assertIsDisplayed()
        compose.runOnIdle { assertEquals(LayoutDirection.Ltr, observedDirection) }

        language = AppLanguage.SPANISH
        compose.onNodeWithText("Te damos la bienvenida a Claro").assertIsDisplayed()
        compose.runOnIdle { assertEquals(LayoutDirection.Ltr, observedDirection) }

        showOnboarding = false
        compose.waitForIdle()
        saveCapture("touch-home-es", "touch-home")

        language = AppLanguage.HEBREW
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(LayoutDirection.Rtl, observedDirection) }
        saveCapture("touch-home-he", "touch-home")

        showClassic = true
        compose.waitForIdle()
        compose.onNodeWithText("OK").assertIsDisplayed()
        saveCapture("classic-home-he", "classic-home")
    }

    private fun saveCapture(name: String, tag: String) {
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
    }
}

private fun localizedHomeFixture() = HomeSnapshot(
    greeting = "",
    dayAndDate = "11/11",
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
