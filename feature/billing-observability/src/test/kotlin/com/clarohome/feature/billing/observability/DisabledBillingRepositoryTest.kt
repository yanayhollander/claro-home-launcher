package com.clarohome.feature.billing.observability

import com.clarohome.core.model.EntitlementState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DisabledBillingRepositoryTest {
    @Test
    fun missingConfigurationNeverCreatesPlansOrEntitlement() = runTest {
        val repository = DisabledBillingRepository()

        assertThat(repository.plans.value)
            .isEqualTo(PlansState.Unavailable(PlansUnavailableReason.CONFIGURATION_MISSING))
        assertThat(repository.membership.value).isEqualTo(MembershipState.Free)
        assertThat(repository.entitlement.value).isEqualTo(EntitlementState.FREE)

        assertThat(repository.entitlement.value).isEqualTo(EntitlementState.FREE)
    }
}
