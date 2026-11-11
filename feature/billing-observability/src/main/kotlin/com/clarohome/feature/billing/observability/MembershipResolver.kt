package com.clarohome.feature.billing.observability

import java.time.Instant

data class EntitlementSnapshot(
    val exists: Boolean,
    val active: Boolean,
    val willRenew: Boolean,
    val trial: Boolean,
    val hasExpiration: Boolean,
    val expiresAt: Instant?,
    val unsubscribeDetected: Boolean,
    val billingIssueDetected: Boolean,
    val inGracePeriod: Boolean,
)

object MembershipResolver {
    fun resolve(
        snapshot: EntitlementSnapshot,
        verifiedAt: Instant,
    ): MembershipState {
        if (!snapshot.exists) return MembershipState.Free
        if (snapshot.active) {
            if (!snapshot.hasExpiration) return MembershipState.Lifetime
            if (snapshot.billingIssueDetected && snapshot.inGracePeriod) {
                return MembershipState.Grace(snapshot.expiresAt)
            }
            if (snapshot.billingIssueDetected) {
                return MembershipState.PaymentProblem(snapshot.expiresAt)
            }
            if (!snapshot.willRenew || snapshot.unsubscribeDetected) {
                return MembershipState.CanceledButValid(snapshot.expiresAt)
            }
            if (snapshot.trial) return MembershipState.Trial(snapshot.expiresAt)
            return MembershipState.Active(snapshot.expiresAt)
        }
        if (snapshot.billingIssueDetected) return MembershipState.AccountHold(verifiedAt)
        return MembershipState.Expired(snapshot.expiresAt)
    }
}
