package com.clarohome.core.data

import app.cash.turbine.test
import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.HOME_SLOT_COUNT
import com.clarohome.core.model.HomeRoleState
import com.clarohome.core.model.HomeTileKind
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LocalHomeRepositoryTest {
    @Test
    fun initialHomeHasEssentialsWithoutMessagesAndKeepsSixMenuTiles() = runTest {
        LocalHomeRepository(InMemoryHomeSlotStore()).home.test {
            val home = awaitItem()
            assertThat(home.homeSlots).hasSize(HOME_SLOT_COUNT)
            assertThat(home.homeTiles.map { it.kind }).containsExactly(
                HomeTileKind.PHONE,
                HomeTileKind.CAMERA,
                HomeTileKind.GALLERY,
                HomeTileKind.SOS,
                HomeTileKind.APPS,
            ).inOrder()
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
    fun homeSlotsSurviveRecreationAndResetRestoresDefaults() = runTest {
        val store = InMemoryHomeSlotStore()
        LocalHomeRepository(store).setHomeSlot(
            9,
            homeSlotTile(9, HomeTileKind.CONTACT, "Maya", phoneNumber = "+15550101"),
        )

        val restored = LocalHomeRepository(store)
        restored.home.test {
            val tile = awaitItem().homeSlots[9]
            assertThat(tile?.label).isEqualTo("Maya")
            assertThat(tile?.phoneNumber).isEqualTo("+15550101")
            cancelAndIgnoreRemainingEvents()
        }
        restored.reset()
        LocalHomeRepository(store).home.test {
            assertThat(awaitItem().homeSlots[9]).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun builtInShortcutMovesInsteadOfDuplicatingAndSlotsCanBeEmpty() = runTest {
        val repository = LocalHomeRepository(InMemoryHomeSlotStore())
        repository.setHomeSlot(7, homeSlotTile(7, HomeTileKind.SOS, "SOS"))
        repository.clearHomeSlot(0)

        repository.home.test {
            val home = awaitItem()
            assertThat(home.homeSlots[3]).isNull()
            assertThat(home.homeSlots[0]).isNull()
            assertThat(home.homeSlots[7]?.kind).isEqualTo(HomeTileKind.SOS)
            assertThat(home.homeTiles.count { it.kind == HomeTileKind.SOS }).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun roleLossMakesAuthoritativeCountsUnknown() = runTest {
        val repository = LocalHomeRepository(InMemoryHomeSlotStore())

        repository.setHomeRoleState(HomeRoleState.AVAILABLE)

        repository.home.test {
            val home = awaitItem()
            assertThat(home.homeRole).isEqualTo(HomeRoleState.AVAILABLE)
            assertThat(home.tiles.take(2).map { it.badge })
                .containsExactly(BadgeValue.Unknown, BadgeValue.Unknown)
            assertThat(home.homeTiles.first().badge).isEqualTo(BadgeValue.Unknown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun customizedAppIsAddedAfterProtectedCoreTiles() = runTest {
        val repository = LocalHomeRepository(InMemoryHomeSlotStore())
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
