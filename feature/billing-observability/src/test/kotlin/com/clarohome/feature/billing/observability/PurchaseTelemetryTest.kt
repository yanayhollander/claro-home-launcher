package com.clarohome.feature.billing.observability

import com.clarohome.core.domain.ConversionKind
import com.clarohome.core.domain.TelemetryMoney
import com.clarohome.core.domain.TelemetryProduct
import com.google.common.truth.Truth.assertThat
import java.math.BigDecimal
import java.util.Currency
import org.junit.Test

class PurchaseTelemetryTest {
    @Test
    fun freeTrialNeverBecomesRevenueEvenIfAFuturePriceIsAvailable() {
        val events = VerifiedPurchaseEvidence("local-only", TelemetryProduct.MONTHLY, true, money()).conversions()

        assertThat(events.map { it.kind }).containsExactly(ConversionKind.TRIAL)
        assertThat(events.single().money).isNull()
    }

    @Test
    fun paidSubscriptionSeparatesAcquisitionFromActualRevenue() {
        val events = VerifiedPurchaseEvidence("local-only", TelemetryProduct.YEARLY, false, money()).conversions()

        assertThat(events.map { it.kind }).containsExactly(
            ConversionKind.SUBSCRIPTION, ConversionKind.PURCHASE,
        ).inOrder()
        assertThat(events.first().money).isNull()
        assertThat(events.last().money?.amount).isEqualTo(BigDecimal("12.345678"))
        assertThat(events.last().money?.currency?.currencyCode).isEqualTo("EUR")
        assertThat(events.last().envelope().properties.values).doesNotContain("local-only")
    }

    @Test
    fun lifetimeIsOnePurchaseAndUnknownOrZeroChargesAreNotGuessed() {
        assertThat(VerifiedPurchaseEvidence("local", TelemetryProduct.LIFETIME, false, money())
            .conversions().map { it.kind }).containsExactly(ConversionKind.PURCHASE)
        assertThat(VerifiedPurchaseEvidence("local", TelemetryProduct.MONTHLY, false, null)
            .conversions()).isEmpty()
        assertThat(VerifiedPurchaseEvidence(
            "local", TelemetryProduct.MONTHLY, false,
            TelemetryMoney(BigDecimal.ZERO, Currency.getInstance("EUR")),
        ).conversions()).isEmpty()
    }

    private fun money() = TelemetryMoney(BigDecimal("12.345678"), Currency.getInstance("EUR"))
}
