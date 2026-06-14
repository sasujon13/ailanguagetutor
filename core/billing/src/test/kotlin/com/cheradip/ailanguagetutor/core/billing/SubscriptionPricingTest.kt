package com.cheradip.ailanguagetutor.core.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionPricingTest {

    @Test
    fun plusYearlyWithLaunch50ShowsPayableAndCompareAt() {
        val display = SubscriptionPricing.display(
            plan = SubscriptionPlan.PLUS,
            period = BillingPeriod.YEARLY,
            discountPercents = listOf(50),
        )
        assertEquals("$25/Year", display.payable)
        assertEquals("$60/Year", display.compareAt)
    }

    @Test
    fun plusYearlyWithoutPromoShowsAutoYearlyDiscount() {
        val display = SubscriptionPricing.display(
            plan = SubscriptionPlan.PLUS,
            period = BillingPeriod.YEARLY,
            discountPercents = emptyList(),
        )
        assertEquals("$50/Year", display.payable)
        assertEquals("$60/Year", display.compareAt)
    }

    @Test
    fun proYearlyWithoutPromoShowsTwentyAgainstTwentyFour() {
        val display = SubscriptionPricing.display(
            plan = SubscriptionPlan.PRO,
            period = BillingPeriod.YEARLY,
            discountPercents = emptyList(),
        )
        assertEquals("$20/Year", display.payable)
        assertEquals("$24/Year", display.compareAt)
    }

    @Test
    fun plusMonthlyWithLaunch50ShowsFiveStrikethrough() {
        val display = SubscriptionPricing.display(
            plan = SubscriptionPlan.PLUS,
            period = BillingPeriod.MONTHLY,
            discountPercents = listOf(50),
        )
        assertEquals("$2.50/Month", display.payable)
        assertEquals("$5/Month", display.compareAt)
    }

    @Test
    fun plusMonthlyWithoutPromoHidesCompareAt() {
        val display = SubscriptionPricing.display(
            plan = SubscriptionPlan.PLUS,
            period = BillingPeriod.MONTHLY,
            discountPercents = emptyList(),
        )
        assertEquals("$5/Month", display.payable)
        assertNull(display.compareAt)
    }
}
