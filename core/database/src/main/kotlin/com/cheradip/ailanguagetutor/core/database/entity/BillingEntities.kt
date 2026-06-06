package com.cheradip.ailanguagetutor.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "referral_cache")
data class ReferralCacheEntity(
    @PrimaryKey val id: Int = 1,
    val commissionPercent: Int = 20,
    val noticeText: String = "",
    val balanceUsd: Double = 0.0,
    val lifetimeEarnedUsd: Double = 0.0,
    val shareReference: String? = null,
    val lastSyncedAt: Long = 0,
)

@Entity(tableName = "applied_promo")
data class AppliedPromoEntity(
    @PrimaryKey val id: Int = 1,
    val code: String? = null,
    val discountPercent: Int? = null,
    val promoToken: String? = null,
    val productId: String? = null,
    val validatedAt: Long? = null,
)

@Entity(tableName = "subscription_entitlement")
data class SubscriptionEntitlementEntity(
    @PrimaryKey val id: Int = 1,
    val accessState: String = "trial_active",
    val isPremium: Boolean = false,
    val plan: String? = null,
    val trialStartedAt: Long? = null,
    val trialEndsAt: Long? = null,
    val expiresAt: Long? = null,
    val lastSyncedAt: Long = 0,
    val trialConsumed: Boolean = false,
)
