package com.clarohome.presentation.classic

import com.clarohome.core.model.HomeRoleState
import com.clarohome.core.testing.launcherHomeFixture
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClassicHomeContentModelTest {
    @Test
    fun classicShellKeepsPhoneAndMessagesBesideClockAndAllTilesInMenu() {
        val model = classicHomeContentModel(
            launcherHomeFixture(roleState = HomeRoleState.UNAVAILABLE),
        )

        assertThat(model.shortcutTileIds).containsExactly("phone", "messages").inOrder()
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
