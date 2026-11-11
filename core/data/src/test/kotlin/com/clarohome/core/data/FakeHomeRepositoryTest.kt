package com.clarohome.core.data

import app.cash.turbine.test
import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.HomeRoleState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LocalHomeRepositoryTest {
    @Test
    fun initialStateHasFourUnassignedSlotsAndSixCoreTiles() = runTest {
        LocalHomeRepository().home.test {
            val home = awaitItem()
            assertThat(home.favorites).hasSize(4)
            assertThat(home.tiles.map { it.id }).containsExactly(
                "phone",
                "messages",
                "camera",
                "gallery",
                "sos",
                "apps",
            ).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun roleLossMakesAuthoritativeCountsUnknown() = runTest {
        val repository = LocalHomeRepository()

        repository.setHomeRoleState(HomeRoleState.AVAILABLE)

        repository.home.test {
            val home = awaitItem()
            assertThat(home.homeRole).isEqualTo(HomeRoleState.AVAILABLE)
            assertThat(home.tiles.take(2).map { it.badge })
                .containsExactly(BadgeValue.Unknown, BadgeValue.Unknown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun customizedAppIsAddedAfterProtectedCoreTiles() = runTest {
        val repository = LocalHomeRepository()
        repository.setInstalledApps(
            listOf(
                com.clarohome.core.model.InstalledApp(
                    packageName = "com.android.calculator2",
                    label = "Calculator",
                    isAllowed = false,
                ),
            ),
        )
        repository.setAppAllowed("com.android.calculator2", allowed = true)
        repository.setAppOnHome("com.android.calculator2", onHome = true)

        repository.home.test {
            val home = awaitItem()
            assertThat(home.tiles.take(6).map { it.id }).containsExactly(
                "phone",
                "messages",
                "camera",
                "gallery",
                "sos",
                "apps",
            ).inOrder()
            assertThat(home.tiles.last().id).isEqualTo("app:com.android.calculator2")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
