package com.clarohome.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clarohome.core.design.ClaroLocaleContainer
import com.clarohome.core.design.ClaroTheme
import com.clarohome.core.model.AppLanguage
import com.clarohome.core.model.ClaroPreferences
import com.clarohome.feature.onboarding.settings.SettingsHubScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAppInfoTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun userIdCopiesOnlyOnEveryThirdTap() {
        val userId = "12345678-1234-4234-8234-123456789abc"
        val copies = mutableListOf<String?>()
        showSettings(userId, copies)

        val id = compose.onNodeWithTag("settings-user-id").performScrollTo()
        repeat(2) {
            id.performClick()
            compose.runOnIdle { assertEquals(emptyList<String>(), copies) }
        }
        id.performClick()
        compose.runOnIdle { assertEquals(listOf(userId), copies) }

        repeat(2) { id.performClick() }
        compose.runOnIdle { assertEquals(listOf(userId), copies) }
        id.performClick()
        compose.runOnIdle { assertEquals(listOf(userId, userId), copies) }
        compose.onNodeWithTag("settings-app-version").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Version 2.3.4").assertIsDisplayed()
    }

    @Test
    fun missingUserIdCannotBeCopiedAndVersionRemainsVisible() {
        showSettings(userId = null, copies = mutableListOf())

        compose.onNodeWithTag("settings-user-id").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Unavailable — analytics and crash reporting may be off").assertIsDisplayed()
        compose.onNodeWithTag("settings-app-version").performScrollTo().assertIsDisplayed()
    }

    private fun showSettings(userId: String?, copies: MutableList<String?>) {
        compose.setContent {
            val platformClipboard = LocalClipboard.current
            val clipboard = remember(platformClipboard) {
                object : Clipboard by platformClipboard {
                    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                        copies += clipEntry?.clipData?.getItemAt(0)?.text?.toString()
                    }
                }
            }
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                ClaroLocaleContainer(language = AppLanguage.ENGLISH) {
                    ClaroTheme {
                        SettingsHubScreen(
                            preferences = ClaroPreferences(),
                            appVersion = "2.3.4",
                            telemetryUserId = userId,
                            onPhoneStyle = {},
                            onDisplayLanguage = {},
                            onAccessMethod = {},
                            onReadiness = {},
                            onHomeAndApps = {},
                            onPeople = {},
                            onSafety = {},
                            onMembershipPrivacy = {},
                            onDone = {},
                            onLockAndExit = {},
                        )
                    }
                }
            }
        }
    }
}
