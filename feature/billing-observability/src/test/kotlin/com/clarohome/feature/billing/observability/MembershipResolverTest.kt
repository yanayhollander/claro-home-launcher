package com.clarohome.feature.billing.observability

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class MembershipResolverTest {
    private val now = Instant.parse("2026-11-11T18:00:00Z")
    private val expiry = Instant.parse("2026-11-18T18:00:00Z")

    @Test
    fun resolvesStoreLifecycleWithoutTreatingPendingOrUnverifiedAsActive() {
        assertThat(resolve(exists = false)).isEqualTo(MembershipState.Free)
        assertThat(resolve(active = true, trial = true))
            .isEqualTo(MembershipState.Trial(expiry))
        assertThat(resolve(active = true, willRenew = false))
            .isEqualTo(MembershipState.CanceledButValid(expiry))
        assertThat(resolve(active = true, billingIssue = true, inGracePeriod = true))
            .isEqualTo(MembershipState.Grace(expiry))
        assertThat(resolve(active = true, billingIssue = true))
            .isEqualTo(MembershipState.PaymentProblem(expiry))
        assertThat(resolve(active = false, billingIssue = true))
            .isEqualTo(MembershipState.AccountHold(now))
        assertThat(resolve(active = false))
            .isEqualTo(MembershipState.Expired(expiry))
        assertThat(resolve(active = true, hasExpiration = false))
            .isEqualTo(MembershipState.Lifetime)
    }

    @Test
    fun offlineEntitlementIsConservativeAndExplicitlyUnknown() {
        val state = MembershipState.OfflineUnverified(
            cachedPremiumAvailable = true,
            lastVerifiedAt = now,
        )

        assertThat(state.premiumAvailable).isTrue()
        assertThat(state.toEntitlement()).isEqualTo(com.clarohome.core.model.EntitlementState.UNKNOWN)
    }

    private fun resolve(
        exists: Boolean = true,
        active: Boolean = true,
        willRenew: Boolean = true,
        trial: Boolean = false,
        hasExpiration: Boolean = true,
        billingIssue: Boolean = false,
        inGracePeriod: Boolean = false,
    ) = MembershipResolver.resolve(
        EntitlementSnapshot(
            exists = exists,
            active = active,
            willRenew = willRenew,
            trial = trial,
            hasExpiration = hasExpiration,
            expiresAt = expiry,
            unsubscribeDetected = false,
            billingIssueDetected = billingIssue,
            inGracePeriod = inGracePeriod,
        ),
        verifiedAt = now,
    )
}
