package com.clarohome.core.data

import app.cash.turbine.test
import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.LEGACY_HOME_SLOT_COUNT
import com.clarohome.core.model.HomeRoleState
import com.clarohome.core.model.HomeTileKind
import com.clarohome.core.model.optionalHomeTileKinds
import com.clarohome.core.domain.HomeTileVisibilityResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LocalHomeRepositoryTest {
    @Test
    fun widgetInstancesPersistIndependentlyWithoutAllowlistingTheirApp() = runTest {
        val store = InMemoryHomeSlotStore()
        val repository = LocalHomeRepository(store)
        repository.setInstalledApps(
            listOf(com.clarohome.core.model.InstalledApp("com.example.gate", "Gate", false)),
        )
        for ((slot, id) in listOf(8 to 41, 9 to 42)) {
            repository.setHomeSlot(
                slot,
                homeSlotTile(
                    slot, HomeTileKind.WIDGET, "Gate widget",
                    appWidgetId = id,
                    appWidgetProvider = "com.example.gate/.GateWidget",
                ),
            )
        }
        val restored = LocalHomeRepository(store)
        restored.setInstalledApps(
            listOf(com.clarohome.core.model.InstalledApp("com.example.gate", "Gate", false)),
        )
        val home = restored.home.first()
        assertThat(home.homeSlots[8]?.appWidgetId).isEqualTo(41)
        assertThat(home.homeSlots[9]?.appWidgetId).isEqualTo(42)
        assertThat(home.homeSlots[8]?.appWidgetProvider).isEqualTo("com.example.gate/.GateWidget")
        assertThat(home.installedApps.single().isAllowed).isFalse()
        assertThat(home.installedApps.single().isOnHome).isFalse()
        assertThat(home.tiles.any { it.kind == HomeTileKind.WIDGET }).isFalse()
    }

    @Test
    fun movingAWidgetDoesNotDuplicateItsHostIdAndReplacingItClearsMetadata() = runTest {
        val store = InMemoryHomeSlotStore()
        val repository = LocalHomeRepository(store)
        val widget = homeSlotTile(
            8, HomeTileKind.WIDGET, "Gate widget",
            appWidgetId = 41,
            appWidgetProvider = "com.example.gate/.GateWidget",
        )
        repository.setHomeSlot(8, widget)
        repository.setHomeSlot(9, widget)
        assertThat(repository.home.first().homeSlots[8]).isNull()
        assertThat(repository.home.first().homeSlots[9]?.appWidgetId).isEqualTo(41)
        repository.setHomeSlot(9, homeSlotTile(9, HomeTileKind.ALARM, "Alarm"))
        val tile = LocalHomeRepository(store).home.first().homeSlots[9]
        assertThat(tile?.kind).isEqualTo(HomeTileKind.ALARM)
        assertThat(tile?.appWidgetId).isNull()
        assertThat(tile?.appWidgetProvider).isNull()
    }

    @Test
    fun installedSocialHomeTileAndPolicyPersistAndHidingKeepsAppAllowed() = runTest {
        val store = InMemoryHomeSlotStore()
        val discovery = listOf(
            com.clarohome.core.model.InstalledApp("com.whatsapp", "WhatsApp", false),
        )
        var repository = LocalHomeRepository(store)
        repository.setInstalledApps(discovery)
        assertThat(repository.setInstalledAppHomeTile("com.whatsapp", true))
            .isEqualTo(HomeTileVisibilityResult.APPLIED)
        assertThat(repository.home.first().homeTiles.any { it.packageName == "com.whatsapp" }).isTrue()

        repository = LocalHomeRepository(store)
        repository.setInstalledApps(discovery)
        assertThat(repository.home.first().installedApps.single().isAllowed).isTrue()
        assertThat(repository.home.first().installedApps.single().isOnHome).isTrue()
        repository.setInstalledAppHomeTile("com.whatsapp", false)
        repository = LocalHomeRepository(store)
        repository.setInstalledApps(discovery)
        assertThat(repository.home.first().homeTiles.any { it.packageName == "com.whatsapp" }).isFalse()
        assertThat(repository.home.first().installedApps.single().isAllowed).isTrue()
        assertThat(repository.home.first().installedApps.single().isOnHome).isFalse()
    }

    @Test
    fun addingSocialAppAfterLegacySlotsAppendsWithoutReplacingATile() = runTest {
        val repository = LocalHomeRepository(InMemoryHomeSlotStore())
        repository.setInstalledApps(
            listOf(com.clarohome.core.model.InstalledApp("com.facebook.katana", "Facebook", false)),
        )
        repeat(LEGACY_HOME_SLOT_COUNT) { slot ->
            repository.setHomeSlot(slot, homeSlotTile(slot, HomeTileKind.CONTACT, "Person $slot"))
        }
        val original = repository.home.first()
        assertThat(repository.setInstalledAppHomeTile("com.facebook.katana", true))
            .isEqualTo(HomeTileVisibilityResult.APPLIED)
        val updated = repository.home.first()
        assertThat(updated.homeTiles.dropLast(1)).isEqualTo(original.homeTiles)
        assertThat(updated.homeTiles.last().packageName).isEqualTo("com.facebook.katana")
    }

    @Test
    fun grantingHomeRoleDoesNotCreateNewCallOrMessageCounts() = runTest {
        val repository = LocalHomeRepository(InMemoryHomeSlotStore())
        repository.setHomeRoleState(HomeRoleState.GRANTED)
        val home = repository.home.first()
        assertThat(home.homeRole).isEqualTo(HomeRoleState.GRANTED)
        assertThat(home.tiles.filter { it.kind == HomeTileKind.PHONE || it.kind == HomeTileKind.MESSAGES }
            .map { it.badge }).containsExactly(BadgeValue.Unknown, BadgeValue.Unknown)
        assertThat(home.homeTiles.first { it.kind == HomeTileKind.PHONE }.badge).isEqualTo(BadgeValue.Unknown)
    }

    @Test
    fun optionalHomeTilesCanBeHiddenAndRestoredWithoutChangingMenuOrOtherTiles() = runTest {
        val store = InMemoryHomeSlotStore()
        var repository = LocalHomeRepository(store)
        repository.setHomeSlot(
            9,
            homeSlotTile(9, HomeTileKind.CONTACT, "Maya", phoneNumber = "+15550101"),
        )
        val original = repository.home.first()
        for (kind in optionalHomeTileKinds) {
            assertThat(repository.setHomeTileVisibility(kind, true))
                .isEqualTo(HomeTileVisibilityResult.APPLIED)
            assertThat(repository.home.first().homeTiles.count { it.kind == kind }).isEqualTo(1)
            assertThat(repository.setHomeTileVisibility(kind, false))
                .isEqualTo(HomeTileVisibilityResult.APPLIED)
        }
        repository = LocalHomeRepository(store)
        val hidden = repository.home.first()
        assertThat(hidden.homeTiles.map { it.kind }).containsExactly(
            HomeTileKind.PHONE,
            HomeTileKind.SOS,
            HomeTileKind.CONTACT,
        ).inOrder()
        assertThat(hidden.tiles).isEqualTo(original.tiles)
        assertThat(hidden.homeSlots[9]).isEqualTo(original.homeSlots[9])

        for (kind in optionalHomeTileKinds) repository.setHomeTileVisibility(kind, true)
        val restored = LocalHomeRepository(store).home.first()
        assertThat(restored.homeTiles.map { it.kind }).containsAtLeastElementsIn(optionalHomeTileKinds)
        assertThat(restored.tiles).isEqualTo(original.tiles)
        assertThat(restored.homeSlots[9]).isEqualTo(original.homeSlots[9])
    }

    @Test
    fun tilesBeyondLegacySlotsSurviveRecreationWithoutReplacingExistingTiles() = runTest {
        val store = InMemoryHomeSlotStore()
        val repository = LocalHomeRepository(store)
        repeat(LEGACY_HOME_SLOT_COUNT + 3) { slot ->
            repository.setHomeSlot(
                slot,
                homeSlotTile(slot, HomeTileKind.CONTACT, "Person $slot", phoneNumber = "+1555010$slot"),
            )
        }
        val original = repository.home.first()

        assertThat(repository.setHomeTileVisibility(HomeTileKind.MESSAGES, true))
            .isEqualTo(HomeTileVisibilityResult.APPLIED)
        val restored = LocalHomeRepository(store).home.first()
        assertThat(restored.homeTiles.dropLast(1)).isEqualTo(original.homeTiles)
        assertThat(restored.homeTiles.last().kind).isEqualTo(HomeTileKind.MESSAGES)
    }

    @Test
    fun movingHomeTilesSkipsEmptySlotsAndPersistsWidgetAndShortcutInOrder() = runTest {
        val store = InMemoryHomeSlotStore()
        val repository = LocalHomeRepository(store)
        val slot = LEGACY_HOME_SLOT_COUNT + 2
        val widget = homeSlotTile(slot, HomeTileKind.WIDGET, "Clock", appWidgetId = 41)
        repository.setHomeSlot(slot, widget)
        val before = repository.home.first()

        repository.moveHomeTile(slot, moveUp = true)
        val restored = LocalHomeRepository(store).home.first()
        assertThat(restored.homeTiles.dropLast(2)).isEqualTo(before.homeTiles.dropLast(2))
        assertThat(restored.homeTiles.takeLast(2).map { it.kind })
            .containsExactly(HomeTileKind.WIDGET, HomeTileKind.APPS).inOrder()
        assertThat(restored.homeSlots[4]?.appWidgetId).isEqualTo(41)
        assertThat(restored.homeSlots[5]).isNull()
        assertThat(restored.homeSlots[slot]?.id).isEqualTo("slot-$slot")
        assertThat(restored.menuOrder).isEqualTo(before.menuOrder)

        repository.moveHomeTile(slot, moveUp = false)
        assertThat(repository.home.first().homeSlots).isEqualTo(restored.homeSlots)
    }

    @Test
    fun menuOrderPersistsAndResetClearsIt() = runTest {
        val store = InMemoryHomeSlotStore()
        val repository = LocalHomeRepository(store)
        repository.setMenuOrder(listOf("camera", "phone"))
        assertThat(LocalHomeRepository(store).home.first().menuOrder)
            .containsExactly("camera", "phone").inOrder()
        repository.reset()
        assertThat(repository.home.first().menuOrder).isEmpty()
    }

    @Test
    fun initialHomeHasEssentialsWithoutMessagesAndKeepsSixMenuTiles() = runTest {
        LocalHomeRepository(InMemoryHomeSlotStore()).home.test {
            val home = awaitItem()
            assertThat(home.homeSlots).hasSize(LEGACY_HOME_SLOT_COUNT)
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
