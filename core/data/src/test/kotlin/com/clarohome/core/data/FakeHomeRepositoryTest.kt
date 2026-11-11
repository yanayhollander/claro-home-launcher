package com.clarohome.core.data

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FakeHomeRepositoryTest {
    @Test
    fun fixtureHasFourPeopleAndSixCoreTiles() = runTest {
        FakeHomeRepository().home.test {
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
            awaitComplete()
        }
    }
}
