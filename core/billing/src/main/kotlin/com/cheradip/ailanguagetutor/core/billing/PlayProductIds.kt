package com.cheradip.ailanguagetutor.core.billing

import com.cheradip.ailanguagetutor.core.model.SubscriptionTier

enum class BillingPeriod { MONTHLY, YEARLY }

object PlayProductIds {
    const val PRO_MONTHLY = "cheradip_alt_pro_monthly"
    const val PRO_YEARLY = "cheradip_alt_pro_yearly"
    const val PLUS_MONTHLY = "cheradip_alt_plus_monthly"
    const val PLUS_YEARLY = "cheradip_alt_plus_yearly"

    /** v1 SKU — maps to Pro on server verify. */
    const val LEGACY_PREMIUM_MONTHLY = "cheradip_alt_premium_monthly"
    const val LEGACY_PREMIUM_YEARLY = "cheradip_alt_premium_yearly"

    fun allSubscriptionIds(): List<String> = listOf(
        PRO_MONTHLY,
        PRO_YEARLY,
        PLUS_MONTHLY,
        PLUS_YEARLY,
        LEGACY_PREMIUM_MONTHLY,
        LEGACY_PREMIUM_YEARLY,
    )

    fun productId(tier: SubscriptionTier, period: BillingPeriod): String = when (tier) {
        SubscriptionTier.PLUS -> when (period) {
            BillingPeriod.MONTHLY -> PLUS_MONTHLY
            BillingPeriod.YEARLY -> PLUS_YEARLY
        }
        SubscriptionTier.PRO,
        SubscriptionTier.FREE,
        -> when (period) {
            BillingPeriod.MONTHLY -> PRO_MONTHLY
            BillingPeriod.YEARLY -> PRO_YEARLY
        }
    }

    fun tierForProductId(productId: String): SubscriptionTier =
        if (productId.contains("plus", ignoreCase = true)) SubscriptionTier.PLUS else SubscriptionTier.PRO
}
