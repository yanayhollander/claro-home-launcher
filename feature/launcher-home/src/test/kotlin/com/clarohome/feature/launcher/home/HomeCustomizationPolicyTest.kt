package com.clarohome.feature.launcher.home

import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.InstalledApp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeCustomizationPolicyTest {
    @Test
    fun allowedAppsPutFavoritesFirstThenSortByLabel() {
        val apps = listOf(
            InstalledApp("z", "Zulu", isAllowed = true),
            InstalledApp("b", "Beta", isAllowed = false, isFavorite = true),
            InstalledApp("a", "Alpha", isAllowed = true, isFavorite = true),
            InstalledApp("c", "Charlie", isAllowed = true),
        )

        assertThat(HomeCustomizationPolicy.allowedApps(apps).map { it.packageName })
            .containsExactly("a", "c", "z")
            .inOrder()
    }

    @Test
    fun onlyAvailableAllowlistedAppsCanBecomeHomeTiles() {
        assertThat(
            HomeCustomizationPolicy.canAddToHome(
                InstalledApp("ready", "Ready", isAllowed = true, isAvailable = true),
            ),
        ).isTrue()
        assertThat(
            HomeCustomizationPolicy.canAddToHome(
                InstalledApp("blocked", "Blocked", isAllowed = false, isAvailable = true),
            ),
        ).isFalse()
        assertThat(
            HomeCustomizationPolicy.canAddToHome(
                InstalledApp("missing", "Missing", isAllowed = true, isAvailable = false),
            ),
        ).isFalse()
    }

    @Test
    fun unknownBadgeNeverLooksLikeZero() {
        assertThat(HomeCustomizationPolicy.badgeLabel("Messages", BadgeValue.Unknown))
            .isEqualTo("Messages, count unavailable, repair required")
        assertThat(HomeCustomizationPolicy.badgeLabel("Messages", BadgeValue.Known(0)))
            .isEqualTo("Messages, no new items")
    }
}
