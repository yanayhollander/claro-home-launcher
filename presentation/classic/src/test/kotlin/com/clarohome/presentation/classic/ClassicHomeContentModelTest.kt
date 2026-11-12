package com.clarohome.presentation.classic

import com.clarohome.core.model.HomeRoleState
import com.clarohome.core.testing.launcherHomeFixture
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClassicHomeContentModelTest {
    @Test
    fun classicShellShowsConfiguredHomeTilesAndAllMenuTiles() {
        val model = classicHomeContentModel(
            launcherHomeFixture(roleState = HomeRoleState.UNAVAILABLE),
        )

        assertThat(model.homeTileIds)
            .containsExactly("slot-0", "slot-1", "slot-2", "slot-3").inOrder()
        assertThat(model.menuTileIds).containsExactly(
            "phone",
            "messages",
            "camera",
            "gallery",
            "sos",
            "apps",
        ).inOrder()
        assertThat(model.showsRoleRepair).isTrue()
    }
}
