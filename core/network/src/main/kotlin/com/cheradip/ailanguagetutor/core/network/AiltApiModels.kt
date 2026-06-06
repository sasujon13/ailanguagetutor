package com.cheradip.ailanguagetutor.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AuthLoginRequest(
    val username: String,
    val password: String,
    val deviceId: String? = null,
)

@JsonClass(generateAdapter = true)
data class AuthLoginResponse(
    val email: String,
    val role: String = "user",
    val whatsapp: String? = null,
    val sessionToken: String? = null,
)

@JsonClass(generateAdapter = true)
data class OtpRequest(val target: String)

@JsonClass(generateAdapter = true)
data class OtpVerifyRequest(val target: String, val code: String)

@JsonClass(generateAdapter = true)
data class DeviceRegisterRequest(
    val deviceId: String,
    val model: String,
    val osVersion: String,
)

@JsonClass(generateAdapter = true)
data class DeviceRegisterResponse(
    val trialEndsAt: Long? = null,
    val trialDaysRemaining: Int = 7,
)

@JsonClass(generateAdapter = true)
data class BillingVerifyRequest(
    val purchaseToken: String,
    val productId: String,
)

@JsonClass(generateAdapter = true)
data class BillingVerifyResponse(
    val active: Boolean,
    val expiresAt: Long? = null,
    val tier: String? = null,
)

@JsonClass(generateAdapter = true)
data class PromoValidateRequest(val code: String, @Json(name = "base_price") val basePrice: Double = 1.0)

@JsonClass(generateAdapter = true)
data class PaywallSlotConfig(
    val visible: Boolean = false,
    val code: String? = null,
    @Json(name = "discountPercent") val discountPercent: Int = 0,
    @Json(name = "readOnly") val readOnly: Boolean = false,
    val label: String? = null,
    val placeholder: String? = null,
    @Json(name = "manualEntry") val manualEntry: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class PaywallConfigResponse(
    @Json(name = "showPromoSection") val showPromoSection: Boolean = false,
    val slot1: PaywallSlotConfig = PaywallSlotConfig(),
    val slot2: PaywallSlotConfig = PaywallSlotConfig(),
    @Json(name = "referralActive") val referralActive: Boolean = false,
    @Json(name = "referrerBuyerDiscountPercent") val referrerBuyerDiscountPercent: Int = 0,
    @Json(name = "maxPromoCodesAtCheckout") val maxPromoCodesAtCheckout: Int = 2,
)

@JsonClass(generateAdapter = true)
data class PromoValidateResponse(
    val code: String,
    @Json(name = "discount_percent") val discountPercent: Int,
    @Json(name = "discounted_price") val discountedPrice: String,
)

@JsonClass(generateAdapter = true)
data class ReferralPolicyResponse(
    @Json(name = "commission_percent") val commissionPercent: Int,
    @Json(name = "notice_text") val noticeText: String,
)

@JsonClass(generateAdapter = true)
data class ReferralBalanceResponse(
    @Json(name = "balance_usd") val balanceUsd: Double,
    @Json(name = "lifetime_earned_usd") val lifetimeEarnedUsd: Double,
)

@JsonClass(generateAdapter = true)
data class LanguagePackInfo(
    val code: String,
    val version: Int,
    @Json(name = "download_url") val downloadUrl: String? = null,
    @Json(name = "size_bytes") val sizeBytes: Long = 0,
)

@JsonClass(generateAdapter = true)
data class LanguageListResponse(val languages: List<LanguagePackInfo> = emptyList())

@JsonClass(generateAdapter = true)
data class AdminPromoCodeDto(
    val code: String,
    @Json(name = "discount_percent") val discountPercent: Int,
    val active: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class AdminPromoListResponse(val codes: List<AdminPromoCodeDto> = emptyList())

@JsonClass(generateAdapter = true)
data class AiActivityMetadataRequest(
    val text: String,
    @Json(name = "language_code") val languageCode: String,
)

@JsonClass(generateAdapter = true)
data class AiActivityMetadataResponse(
    val title: String,
    val summary: String? = null,
    val tags: List<String> = emptyList(),
    @Json(name = "provider_used") val providerUsed: String? = null,
)

@JsonClass(generateAdapter = true)
data class AiParagraphRequest(
    val paragraph: String,
    @Json(name = "source_lang") val sourceLang: String,
    @Json(name = "target_lang") val targetLang: String,
)

@JsonClass(generateAdapter = true)
data class AiParagraphResponse(
    val explanation: String,
    @Json(name = "provider_used") val providerUsed: String? = null,
)

/** Admin: per-provider health for free/paid AI pool (keys live on server only). */
@JsonClass(generateAdapter = true)
data class AiProviderStatusDto(
    val id: String,
    @Json(name = "display_name") val displayName: String,
    val tier: String,
    val health: String,
    @Json(name = "quota_used_percent") val quotaUsedPercent: Int = 0,
    @Json(name = "requests_today") val requestsToday: Int = 0,
    @Json(name = "quota_daily_limit") val quotaDailyLimit: Int? = null,
    @Json(name = "last_error") val lastError: String? = null,
    @Json(name = "last_used_at") val lastUsedAt: Long? = null,
    val enabled: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class AiRoutingPolicyDto(
    /** random_free | random_all | priority_fallback | paid_only */
    val mode: String = "random_free",
    @Json(name = "prefer_paid_when_free_exhausted") val preferPaidWhenFreeExhausted: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class AiProvidersStatusResponse(
    val providers: List<AiProviderStatusDto> = emptyList(),
    @Json(name = "routing_policy") val routingPolicy: AiRoutingPolicyDto = AiRoutingPolicyDto(),
    @Json(name = "total_requests_today") val totalRequestsToday: Int = 0,
    @Json(name = "free_pool_available") val freePoolAvailable: Boolean = true,
    @Json(name = "recommend_paid_upgrade") val recommendPaidUpgrade: Boolean = false,
    val summary: String? = null,
)

@JsonClass(generateAdapter = true)
data class AiRoutingPolicyUpdateRequest(
    val mode: String,
    @Json(name = "prefer_paid_when_free_exhausted") val preferPaidWhenFreeExhausted: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class AiProviderToggleRequest(val enabled: Boolean)
