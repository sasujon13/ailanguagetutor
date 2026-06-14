package com.cheradip.ailanguagetutor.core.billing

import kotlin.math.abs
import kotlin.math.roundToInt

enum class SubscriptionPlan { PRO, PLUS }

data class SubscriptionPriceDisplay(
    val payable: String,
    val compareAt: String?,
)

object SubscriptionPricing {

    fun basePrice(plan: SubscriptionPlan, period: BillingPeriod): Double = when (plan) {
        SubscriptionPlan.PRO -> if (period == BillingPeriod.YEARLY) 20.0 else 2.0
        SubscriptionPlan.PLUS -> if (period == BillingPeriod.YEARLY) 50.0 else 5.0
    }

    /** Reference list price: monthly rate, or 12× monthly for yearly. */
    fun compareAtAmount(plan: SubscriptionPlan, period: BillingPeriod): Double = when (plan) {
        SubscriptionPlan.PRO -> if (period == BillingPeriod.YEARLY) 24.0 else 2.0
        SubscriptionPlan.PLUS -> if (period == BillingPeriod.YEARLY) 60.0 else 5.0
    }

    fun discountedPrice(base: Double, discountPercents: List<Int>): Double {
        var price = base
        discountPercents.filter { it > 0 }.forEach { pct ->
            price *= (100 - pct) / 100.0
        }
        return price
    }

    fun display(
        plan: SubscriptionPlan,
        period: BillingPeriod,
        discountPercents: List<Int>,
    ): SubscriptionPriceDisplay {
        val payableAmount = discountedPrice(basePrice(plan, period), discountPercents)
        val compareAtAmount = compareAtAmount(plan, period)
        val compareAt = compareAtAmount.takeIf { it > payableAmount + 0.001 }
            ?.let { formatSubscriptionPrice(it, period) }
        return SubscriptionPriceDisplay(
            payable = formatSubscriptionPrice(payableAmount, period),
            compareAt = compareAt,
        )
    }

    fun formatSubscriptionPrice(amount: Double, period: BillingPeriod): String {
        val suffix = if (period == BillingPeriod.MONTHLY) "Month" else "Year"
        return "$${formatUsdAmount(amount)}/$suffix"
    }

    fun formatUsdAmount(amount: Double): String =
        if (abs(amount - amount.roundToInt()) < 0.001) {
            amount.roundToInt().toString()
        } else {
            "%.2f".format(amount)
        }
}
