package com.clarohome.presentation.touch

import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.HomeRoleState
import com.clarohome.core.testing.launcherHomeFixture
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TouchHomeContentModelTest {
    @Test
    fun touchShellKeepsCoreOrderingAndUnknownBadgeMeaning() {
        val model = touchHomeContentModel(
            launcherHomeFixture(
                roleState = HomeRoleState.AVAILABLE,
                phoneBadge = BadgeValue.Unknown,
            ),
        )

        assertThat(model.visibleTileIds)
            .containsExactly("slot-0", "slot-1", "slot-2", "slot-3").inOrder()
        assertThat(model.showsRoleRepair).isTrue()
        assertThat(model.badgeDescriptions.first())
            .isEqualTo("Phone, count unavailable, repair required")
    }
}
