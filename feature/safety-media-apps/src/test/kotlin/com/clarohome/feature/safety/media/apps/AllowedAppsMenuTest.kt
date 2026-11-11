package com.clarohome.feature.safety.media.apps

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AllowedAppsMenuTest {
    private val menu = AllowedAppsMenu(
        listOf(
            AllowedApp("com.example.weather", "Weather", favorite = true),
            AllowedApp("com.example.music", "Music", favorite = false),
            AllowedApp(
                "com.example.missing",
                "Missing app",
                favorite = true,
                installed = false,
            ),
        ),
    )

    @Test
    fun searchNeverReturnsAppsOutsideAllowlist() {
        assertThat(menu.search("", AppListFilter.ALL_ALLOWED).map { it.packageName })
            .containsExactly(
                "com.example.missing",
                "com.example.music",
                "com.example.weather",
            )
            .inOrder()
        assertThat(menu.open("com.example.not-allowed"))
            .isEqualTo(AllowedAppOpenResult.NotAllowed)
    }

    @Test
    fun favoritesFilterAndSearchUseOnlyAllowedFavorites() {
        assertThat(menu.search("wea", AppListFilter.FAVORITES).map { it.label })
            .containsExactly("Weather")
        assertThat(menu.search("music", AppListFilter.FAVORITES)).isEmpty()
    }

    @Test
    fun missingAllowedAppReturnsRepairStateInsteadOfLaunch() {
        assertThat(menu.open("com.example.missing")).isEqualTo(
            AllowedAppOpenResult.Missing(
                AllowedApp(
                    "com.example.missing",
                    "Missing app",
                    favorite = true,
                    installed = false,
                ),
            ),
        )
    }
}
