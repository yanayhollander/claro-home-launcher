package com.clarohome.app

import android.content.ContextWrapper
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clarohome.feature.launcher.home.PackageManagerInstalledAppCatalog
import com.clarohome.feature.communication.AndroidTelecomGateway
import com.clarohome.feature.communication.CallerIdentity
import com.clarohome.feature.communication.ACTION_OPEN_MESSAGES
import com.clarohome.feature.communication.EXTRA_CONVERSATION_ID
import com.clarohome.feature.communication.incomingMessageNotification
import com.clarohome.feature.communication.messageNotificationIntent
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun typingFromClassicHomeKeepsTheSameKeypadMounted() {
        assumeTrue(
            rule.onAllNodes(androidx.compose.ui.test.hasTestTag("classic-home"))
                .fetchSemanticsNodes().isNotEmpty(),
        )
        val deck = rule.onNodeWithTag("classic-global-deck").assertIsDisplayed().fetchSemanticsNode()
        rule.onNodeWithContentDescription("3, DEF").performClick()
        rule.onNodeWithTag("classic-communication").assertIsDisplayed()
        val afterFirstDigit = rule.onNodeWithTag("classic-global-deck").fetchSemanticsNode()
        assertEquals(deck.id, afterFirstDigit.id)
        assertEquals(deck.boundsInRoot, afterFirstDigit.boundsInRoot)
        rule.onNodeWithContentDescription("4, GHI").performClick()
        rule.onNodeWithText("34").assertIsDisplayed()
        val afterSecondDigit = rule.onNodeWithTag("classic-global-deck").fetchSemanticsNode()
        assertEquals(deck.id, afterSecondDigit.id)
        assertEquals(deck.boundsInRoot, afterSecondDigit.boundsInRoot)
        rule.onNodeWithText(rule.activity.getString(R.string.app_home)).performClick()
        rule.onNodeWithTag("classic-home").assertIsDisplayed()
    }

    @Test
    fun classicHomeAndMenuKeepTheSameDeckMountedAtTheSamePosition() {
        assumeTrue(
            rule.onAllNodes(androidx.compose.ui.test.hasTestTag("classic-home"))
                .fetchSemanticsNodes().isNotEmpty(),
        )
        val deck = rule.onNodeWithTag("classic-global-deck").assertIsDisplayed().fetchSemanticsNode()
        val menuLabel = rule.activity.getString(R.string.app_menu)
        val homeLabel = rule.activity.getString(R.string.app_home)
        repeat(3) {
            rule.onNodeWithText(menuLabel).performClick()
            rule.waitForIdle()
            val menuDeck = rule.onNodeWithTag("classic-global-deck").assertIsDisplayed().fetchSemanticsNode()
            assertEquals(deck.id, menuDeck.id)
            assertEquals(deck.boundsInRoot, menuDeck.boundsInRoot)
            rule.onNodeWithText(homeLabel).performClick()
            rule.onNodeWithTag("classic-home").assertIsDisplayed()
            val homeDeck = rule.onNodeWithTag("classic-global-deck").fetchSemanticsNode()
            assertEquals(deck.id, homeDeck.id)
            assertEquals(deck.boundsInRoot, homeDeck.boundsInRoot)
        }
    }

    @Test
    fun galleryUsesExplicitNewestFirstSortAndCapsReturnedPhotos() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = object : ContentProvider() {
            override fun onCreate(): Boolean = true
            override fun getType(uri: Uri): String = MediaStore.Images.Media.CONTENT_TYPE
            override fun query(
                uri: Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?,
            ): Cursor {
                val ids = if (sortOrder == "date_added DESC, _id DESC") (305 downTo 1) else (1..305)
                return MatrixCursor(requireNotNull(projection)).apply {
                    ids.forEach { addRow(arrayOf<Any>(it.toLong(), "photo-$it.jpg")) }
                }
            }

            override fun query(
                uri: Uri,
                projection: Array<out String>?,
                queryArgs: Bundle?,
                cancellationSignal: CancellationSignal?,
            ): Cursor = query(
                uri, projection, null, null,
                queryArgs?.getString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER),
            )

            override fun insert(uri: Uri, values: ContentValues?): Uri =
                error("Read-only test provider")
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
                error("Read-only test provider")
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
                error("Read-only test provider")
        }
        provider.attachInfo(context, ProviderInfo().apply { authority = MediaStore.AUTHORITY })
        val resolver = ContentResolver.wrap(provider)
        val galleryContext = object : ContextWrapper(context) {
            override fun getContentResolver(): ContentResolver = resolver
        }
        val photos = queryDevicePhotos(galleryContext)
        assertEquals(300, photos.size)
        assertEquals(305L, photos.first().id)
        assertEquals(6L, photos.last().id)
    }

    @Test
    fun smsNotificationShowsPreviewAndKeepsConversationIntentsDistinct() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notification = incomingMessageNotification(context, "Mira", "Hello", 7)
        assertEquals("Mira", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("Hello", notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals("Hello", notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        assertTrue(notification.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT) == null)
        val intent = messageNotificationIntent(context, 7)
        assertEquals(ACTION_OPEN_MESSAGES, intent.action)
        assertEquals("com.clarohome.app.MainActivity", intent.component?.className)
        assertEquals(7L, intent.getLongExtra(EXTRA_CONVERSATION_ID, -1))
        assertTrue(!intent.filterEquals(messageNotificationIntent(context, 8)))
    }

    @Test
    fun ongoingCallNotificationPostsWithoutForegroundServiceOrFullScreenIntent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        assumeTrue(NotificationManagerCompat.from(context).areNotificationsEnabled())
        val manager = context.getSystemService(NotificationManager::class.java)
        val gateway = AndroidTelecomGateway(context)
        val notification = gateway.ongoingCallNotification(
            callId = "claro-notification-regression",
            identity = CallerIdentity("Notification check", null),
        )
        manager.createNotificationChannel(
            NotificationChannel(notification.channelId, "Calls", NotificationManager.IMPORTANCE_LOW),
        )
        val id = "claro-notification-regression".hashCode()
        try {
            manager.notify(id, notification)
            rule.waitUntil(5_000) { manager.activeNotifications.any { it.id == id } }
            assertEquals(null, notification.fullScreenIntent)
            assertEquals(1, notification.actions.size)
            assertTrue(notification.contentIntent != null)
        } finally {
            manager.cancel(id)
        }
    }

    @Test
    fun appCatalogIncludesLauncherActivitiesWithoutDefaultCategory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        // Use Claro's launcher (which has no DEFAULT category) as an external app fixture.
        val catalogContext = object : ContextWrapper(targetContext) {
            override fun getPackageName(): String = instrumentation.context.packageName
        }

        val apps = PackageManagerInstalledAppCatalog(catalogContext).discoverEligibleApps()

        assertTrue(
            "Launcher apps without DEFAULT must be discoverable for Home tiles",
            apps.any { it.packageName == targetContext.packageName },
        )
    }

    @Test
    fun coldStartWalksHonestOnboardingToHomeAndProtectedSos() {

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
            rule.onNodeWithText("Use Classic Phone").assertIsEnabled()
            rule.onNodeWithText("Touch Home").performClick()
            saveRootScreenshot("live-02-style-choice")
            rule.onNodeWithText("Use Touch Home").performClick()

            rule.onNodeWithText("Continue").performClick()
            rule.onNodeWithText("Continue").performClick()
            rule.onNodeWithText("Save access method").performClick()
            rule.onNodeWithText("Continue").performClick()
            rule.onNodeWithText("Skip SOS for now").performClick()

            rule.onNodeWithText("Fake grant").assertDoesNotExist()
            saveRootScreenshot("live-03-honest-permissions")
            if (!com.clarohome.core.model.defaultAppsConfigured(rule.activity.defaultAppReadiness())) {
                rule.onNodeWithText("Finish setup").assertIsNotEnabled()
                return
            }
            rule.onNodeWithText("Finish setup").performClick()
        }

        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasTestTag("touch-home"))
                .fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodes(androidx.compose.ui.test.hasTestTag("classic-home"))
                    .fetchSemanticsNodes().isNotEmpty()
        }
        if (
            rule.onAllNodes(androidx.compose.ui.test.hasTestTag("touch-home"))
                .fetchSemanticsNodes().isEmpty()
        ) {
            // Existing installation already uses Classic; its data is never reset by tests.
            saveRootScreenshot("live-05-classic-home")
            return
        }
        rule.onNodeWithText("Phone").assertIsDisplayed()
        rule.onNodeWithText("Messages").assertIsDisplayed()
        saveRootScreenshot("live-05-touch-home")

        rule.onNodeWithText("SOS").performScrollTo().performClick()
        rule.onNodeWithTag("sos-screen").assertIsDisplayed()
        rule.onNodeWithText("Save trusted person").assertDoesNotExist()
        rule.onNodeWithText("Call in 5 seconds").assertDoesNotExist()
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
