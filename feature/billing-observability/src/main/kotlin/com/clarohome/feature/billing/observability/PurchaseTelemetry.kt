package com.clarohome.feature.billing.observability

import android.content.Context
import android.annotation.SuppressLint
import android.util.Log
import com.clarohome.core.domain.ClaroEvent
import com.clarohome.core.domain.ConversionKind
import com.clarohome.core.domain.CrashReporter
import com.clarohome.core.domain.DiagnosticOperation
import com.clarohome.core.domain.ProductAnalytics
import com.clarohome.core.domain.TelemetryMoney
import com.clarohome.core.domain.TelemetryProduct
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

internal data class VerifiedPurchaseEvidence(
    val transactionId: String,
    val product: TelemetryProduct,
    val trial: Boolean,
    val money: TelemetryMoney?,
)

internal fun VerifiedPurchaseEvidence.conversions(): List<ClaroEvent.Conversion> = when {
    trial -> listOf(ClaroEvent.Conversion(ConversionKind.TRIAL, product))
    money == null || money.amount.signum() <= 0 -> emptyList()
    product == TelemetryProduct.LIFETIME ->
        listOf(ClaroEvent.Conversion(ConversionKind.PURCHASE, product, money))
    else -> listOf(
        ClaroEvent.Conversion(ConversionKind.SUBSCRIPTION, product),
        ClaroEvent.Conversion(ConversionKind.PURCHASE, product, money),
    )
}

@Singleton
class PurchaseTelemetry @Inject constructor(
    @ApplicationContext context: Context,
    private val analytics: ProductAnalytics,
    private val consent: ConsentController,
    private val crashReporter: CrashReporter,
) {
    private val preferences = context.getSharedPreferences("telemetry_conversions", Context.MODE_PRIVATE)

    @Synchronized
    @SuppressLint("UseKtx") // KTX edit cannot report whether the deduplication marker was committed.
    internal fun record(evidence: VerifiedPurchaseEvidence) {
        val current = consent.consent.value
        if (!current.analytics && !current.marketing) return
        val events = evidence.conversions()
        if (events.isEmpty()) {
            Log.w("ClaroTelemetry", "Purchase conversion omitted: authoritative charge unavailable")
            return
        }
        val key = MessageDigest.getInstance("SHA-256")
            .digest(evidence.transactionId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val recorded = preferences.getStringSet("recorded", emptySet()).orEmpty()
        if (key in recorded) return
        // Prefer a missed conversion to duplicate revenue after a process death.
        if (!preferences.edit().putStringSet("recorded", recorded + key).commit()) {
            Log.e("ClaroTelemetry", "Purchase deduplication could not be persisted")
            crashReporter.record(
                IllegalStateException("Purchase telemetry persistence failed"), DiagnosticOperation.BILLING,
            )
            return
        }
        events.forEach(analytics::track)
    }
}
