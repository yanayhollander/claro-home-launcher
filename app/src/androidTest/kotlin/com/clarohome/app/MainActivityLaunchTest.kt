package com.clarohome.app

import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun coldStartWalksHonestOnboardingToHomeAndProtectedSos() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "datastore/claro_settings.pb").delete()

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasText("Welcome to Claro"))
                .fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodes(androidx.compose.ui.test.hasTestTag("touch-home"))
                    .fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodes(androidx.compose.ui.test.hasTestTag("classic-home"))
                    .fetchSemanticsNodes().isNotEmpty()
        }

        if (
            rule.onAllNodes(androidx.compose.ui.test.hasText("Welcome to Claro"))
                .fetchSemanticsNodes().isNotEmpty()
        ) {
            rule.onNodeWithText("Your people. One touch away.").assertIsDisplayed()
            saveRootScreenshot("live-01-welcome")

            rule.onNodeWithText("Set up Claro").performClick()
            rule.onNodeWithText("Choose a style").assertIsNotEnabled()
            rule.onNodeWithText("Touch Home").performClick()
            saveRootScreenshot("live-02-style-choice")
            rule.onNodeWithText("Use Touch Home").performClick()

            rule.onNodeWithText("Save and continue").performClick()
            rule.onNodeWithText("Save access method").performClick()
            rule.onNodeWithText("Continue with setup incomplete").performClick()
            rule.onNodeWithText("Continue with SOS incomplete").performClick()

            rule.onNodeWithText("Fake grant").assertDoesNotExist()
            saveRootScreenshot("live-03-honest-permissions")
            rule.onNodeWithText("Continue to handover").performClick()

            saveRootScreenshot("live-04-handover")
            rule.onNodeWithText("Finish setup").performClick()
        }

        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasTestTag("touch-home"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Phone").assertIsDisplayed()
        rule.onNodeWithText("Messages").assertIsDisplayed()
        saveRootScreenshot("live-05-touch-home")

        rule.onNodeWithText("SOS").performScrollTo().performClick()
        rule.onNodeWithText("Call for help").assertIsDisplayed()
        rule.onNodeWithText("Save trusted person").assertDoesNotExist()
        rule.onNodeWithText("Call in 5 seconds").assertIsNotEnabled()
        saveRootScreenshot("live-06-sos-senior")
    }

    private fun saveRootScreenshot(name: String) {
        rule.waitForIdle()
        val raw = rule.onNodeWithTag("app-root").captureToImage().asAndroidBitmap()
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
