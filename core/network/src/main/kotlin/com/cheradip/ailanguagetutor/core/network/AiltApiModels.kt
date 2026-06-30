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
    val emailVerified: Boolean? = null,
    val whatsappVerified: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class OtpRequest(val target: String)

@JsonClass(generateAdapter = true)
data class OtpVerifyRequest(val target: String, val code: String)

@JsonClass(generateAdapter = true)
data class SignupInitRequest(
    val fullName: String,
    val email: String,
    val username: String,
    val password: String,
    val deviceId: String? = null,
)

@JsonClass(generateAdapter = true)
data class SignupInitResponse(
    val ok: Boolean = true,
    val message: String = "",
    val email: String = "",
    val role: String = "user",
    val sessionToken: String? = null,
)

@JsonClass(generateAdapter = true)
data class RecoverySendRequest(
    val username: String,
    val deviceId: String? = null,
)

@JsonClass(generateAdapter = true)
data class RecoverySendResponse(
    val ok: Boolean = true,
    val message: String = "",
    val requiresOtp: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class RecoveryResetRequest(
    val username: String,
    val newPassword: String,
    val otp: String? = null,
    val deviceId: String? = null,
)

@JsonClass(generateAdapter = true)
data class PasswordUpdateSendRequest(
    val currentPassword: String,
    val deviceId: String? = null,
)

@JsonClass(generateAdapter = true)
data class PasswordUpdateSendResponse(
    val ok: Boolean = true,
    val message: String = "",
    val requiresOtp: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class PasswordUpdateConfirmRequest(
    val newPassword: String,
    val currentPassword: String,
    val otp: String? = null,
    val deviceId: String? = null,
)

@JsonClass(generateAdapter = true)
data class EmailChangeSendRequest(
    val deviceId: String? = null,
)

@JsonClass(generateAdapter = true)
data class EmailChangeSendResponse(
    val ok: Boolean = true,
    val message: String = "",
    val requiresOtp: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class EmailChangeConfirmRequest(
    val newEmail: String,
    val otp: String? = null,
    val deviceId: String? = null,
)

@JsonClass(generateAdapter = true)
data class EmailChangeConfirmResponse(
    val ok: Boolean = true,
    val message: String = "",
    val email: String? = null,
)

@JsonClass(generateAdapter = true)
data class OkMessageResponse(
    val ok: Boolean = true,
    val message: String = "",
)

@JsonClass(generateAdapter = true)
data class DeviceRegisterRequest(
    val deviceId: String,
    val model: String,
    val osVersion: String,
)

@JsonClass(generateAdapter = true)
data class DeviceRegisterResponse(
    val trialEndsAt: Long? = null,
    val trialDaysRemaining: Int = 30,
)

@JsonClass(generateAdapter = true)
data class GuestAiSyncRequest(
    val deviceId: String,
    val localCount: Int,
)

@JsonClass(generateAdapter = true)
data class GuestAiRecordRequest(
    val deviceId: String,
)

@JsonClass(generateAdapter = true)
data class GuestAiUsageResponse(
    val count: Int = 0,
    val limit: Int = 99_999_999,
    val requiresLogin: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class BillingVerifyRequest(
    val purchaseToken: String,
    val productId: String,
    val slot1Code: String? = null,
    val slot2Code: String? = null,
    @Json(name = "referralBalanceUsd") val referralBalanceUsd: Double? = null,
)

@JsonClass(generateAdapter = true)
data class BillingVerifyResponse(
    val active: Boolean,
    val expiresAt: Long? = null,
    val tier: String? = null,
)

@JsonClass(generateAdapter = true)
data class PromoValidateRequest(
    val code: String,
    @Json(name = "base_price") val basePrice: Double = 1.0,
    @Json(name = "slot1_code") val slot1Code: String? = null,
)

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
    @Json(name = "min_withdrawal_usd") val minWithdrawalUsd: Double = 100.0,
)

@JsonClass(generateAdapter = true)
data class ReferralBalanceResponse(
    @Json(name = "balance_usd") val balanceUsd: Double,
    @Json(name = "pending_usd") val pendingUsd: Double = 0.0,
    @Json(name = "available_usd") val availableUsd: Double = 0.0,
    @Json(name = "lifetime_earned_usd") val lifetimeEarnedUsd: Double,
    @Json(name = "min_withdrawal_usd") val minWithdrawalUsd: Double = 100.0,
    val withdrawable: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class ReferralGiftRequest(
    @Json(name = "recipientEmail") val recipientEmail: String,
    @Json(name = "amountUsd") val amountUsd: Double,
)

@JsonClass(generateAdapter = true)
data class ReferralGiftResponse(
    val ok: Boolean,
    val message: String,
    @Json(name = "balance_usd") val balanceUsd: Double,
    @Json(name = "pending_usd") val pendingUsd: Double = 0.0,
    @Json(name = "available_usd") val availableUsd: Double = 0.0,
    @Json(name = "lifetime_earned_usd") val lifetimeEarnedUsd: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class ReferralWithdrawRequest(
    val method: String,
    val payoutDetails: String,
    @Json(name = "amountUsd") val amountUsd: Double? = null,
)

@JsonClass(generateAdapter = true)
data class ReferralWithdrawResponse(
    val ok: Boolean,
    val message: String,
    @Json(name = "balance_usd") val balanceUsd: Double,
    @Json(name = "pending_usd") val pendingUsd: Double = 0.0,
    @Json(name = "available_usd") val availableUsd: Double = 0.0,
    @Json(name = "lifetime_earned_usd") val lifetimeEarnedUsd: Double = 0.0,
    @Json(name = "withdrawal_id") val withdrawalId: Int? = null,
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
    @Json(name = "auto_apply") val autoApply: Boolean = false,
    @Json(name = "paywall_slot") val paywallSlot: Int = 2,
)

@JsonClass(generateAdapter = true)
data class AdminPromoPatchDto(
    @Json(name = "discount_percent") val discountPercent: Int? = null,
    val active: Boolean? = null,
    @Json(name = "auto_apply") val autoApply: Boolean? = null,
    @Json(name = "paywall_slot") val paywallSlot: Int? = null,
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

@JsonClass(generateAdapter = true)
data class AiStructureOcrRequest(
    @Json(name = "raw_text") val rawText: String,
    @Json(name = "content_type") val contentType: String,
    @Json(name = "language_code") val languageCode: String,
    val prompt: String,
)

@JsonClass(generateAdapter = true)
data class AiStructureOcrResponse(
    @Json(name = "structured_text") val structuredText: String,
    @Json(name = "content_type") val contentType: String? = null,
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

@JsonClass(generateAdapter = true)
data class AdminReportsResponse(
    @Json(name = "generated_at_ms") val generatedAtMs: Long = 0L,
    @Json(name = "report_settings") val reportSettings: AdminReportSettingsDto = AdminReportSettingsDto(),
    @Json(name = "language_packs") val languagePacks: AdminReportsLanguagePacks = AdminReportsLanguagePacks(),
    val users: AdminReportsUsers = AdminReportsUsers(),
    val subscriptions: AdminReportsSubscriptions = AdminReportsSubscriptions(),
    val engagement: AdminReportsEngagement = AdminReportsEngagement(),
    val referrals: AdminReportsReferrals = AdminReportsReferrals(),
    @Json(name = "promo_codes") val promoCodes: AdminReportsPromoCodes = AdminReportsPromoCodes(),
    @Json(name = "cloud_ai") val cloudAi: AdminReportsCloudAi = AdminReportsCloudAi(),
)

@JsonClass(generateAdapter = true)
data class AdminReportSettingsDto(
    @Json(name = "cloud_reports_enabled") val cloudReportsEnabled: Boolean = true,
    @Json(name = "home_ai_reports_enabled") val homeAiReportsEnabled: Boolean = true,
    @Json(name = "debug_reports_enabled") val debugReportsEnabled: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class AdminReportsLanguagePacks(
    @Json(name = "catalog_active") val catalogActive: Int = 0,
    val packs: List<AdminReportsLanguagePackRow> = emptyList(),
    @Json(name = "learning_activity_by_language")
    val learningActivityByLanguage: List<AdminReportsLanguageActivityRow> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AdminReportsLanguagePackRow(
    val code: String = "",
    val version: Int = 1,
    @Json(name = "size_bytes") val sizeBytes: Long = 0L,
)

@JsonClass(generateAdapter = true)
data class AdminReportsLanguageActivityRow(
    @Json(name = "language_code") val languageCode: String = "",
    val count: Int = 0,
)

@JsonClass(generateAdapter = true)
data class AdminReportsDebugResponse(
    @Json(name = "generated_at_ms") val generatedAtMs: Long = 0L,
    @Json(name = "language_packs") val languagePacks: List<AdminReportsLanguagePackRow> = emptyList(),
    @Json(name = "learning_activity_by_language")
    val learningActivityByLanguage: List<AdminReportsLanguageActivityRow> = emptyList(),
    @Json(name = "cloud_ai_provider_errors")
    val cloudAiProviderErrors: List<AdminReportsProviderErrorRow> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AdminReportsProviderErrorRow(
    val id: String = "",
    @Json(name = "last_error") val lastError: String? = null,
    val health: String = "",
)

@JsonClass(generateAdapter = true)
data class AdminReportSettingsPatchRequest(
    @Json(name = "cloud_reports_enabled") val cloudReportsEnabled: Boolean? = null,
    @Json(name = "home_ai_reports_enabled") val homeAiReportsEnabled: Boolean? = null,
    @Json(name = "debug_reports_enabled") val debugReportsEnabled: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class AdminReportsUsers(
    val total: Int = 0,
    val regular: Int = 0,
    val admins: Int = 0,
    @Json(name = "email_verified") val emailVerified: Int = 0,
    @Json(name = "new_last_7_days") val newLast7Days: Int = 0,
    @Json(name = "new_last_30_days") val newLast30Days: Int = 0,
)

@JsonClass(generateAdapter = true)
data class AdminReportsSubscriptions(
    @Json(name = "active_pro") val activePro: Int = 0,
    @Json(name = "active_plus") val activePlus: Int = 0,
    @Json(name = "active_total") val activeTotal: Int = 0,
)

@JsonClass(generateAdapter = true)
data class AdminReportsEngagement(
    @Json(name = "learning_activities") val learningActivities: Int = 0,
    @Json(name = "device_trials") val deviceTrials: Int = 0,
    @Json(name = "guest_ai_uses_total") val guestAiUsesTotal: Int = 0,
)

@JsonClass(generateAdapter = true)
data class AdminReportsReferrals(
    @Json(name = "pending_withdrawals") val pendingWithdrawals: Int = 0,
    @Json(name = "total_balance_usd") val totalBalanceUsd: Double = 0.0,
    @Json(name = "pending_commission_usd") val pendingCommissionUsd: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class AdminEarningsMetrics(
    val pending: Double = 0.0,
    val available: Double = 0.0,
    @Json(name = "net_pending") val netPending: Double = 0.0,
    @Json(name = "net_available") val netAvailable: Double = 0.0,
    @Json(name = "total_referral") val totalReferral: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class AdminEarningsRow(
    val label: String,
    val pending: Double = 0.0,
    val available: Double = 0.0,
    @Json(name = "net_pending") val netPending: Double = 0.0,
    @Json(name = "net_available") val netAvailable: Double = 0.0,
    @Json(name = "total_referral") val totalReferral: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class AdminEarningsReportResponse(
    val period: String,
    @Json(name = "bucket_period") val bucketPeriod: String = "",
    val from: String = "",
    val to: String = "",
    @Json(name = "generated_at_ms") val generatedAtMs: Long = 0L,
    val totals: AdminEarningsMetrics = AdminEarningsMetrics(),
    val rows: List<AdminEarningsRow> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AdminReportsPromoCodes(
    val total: Int = 0,
    val active: Int = 0,
)

@JsonClass(generateAdapter = true)
data class AdminReportsCloudAi(
    @Json(name = "routing_mode") val routingMode: String = "random_free",
    @Json(name = "total_requests_today") val totalRequestsToday: Int = 0,
    val providers: List<AdminReportsCloudAiProvider> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AdminReportsCloudAiProvider(
    val id: String,
    @Json(name = "display_name") val displayName: String,
    val tier: String,
    val health: String,
    val enabled: Boolean = true,
    @Json(name = "requests_today") val requestsToday: Int = 0,
    @Json(name = "quota_daily_limit") val quotaDailyLimit: Int? = null,
    @Json(name = "quota_used_percent") val quotaUsedPercent: Int = 0,
)

@JsonClass(generateAdapter = true)
data class LearningActivityDto(
    @Json(name = "client_id") val clientId: String,
    val title: String,
    val summary: String? = null,
    @Json(name = "activity_type") val activityType: String,
    @Json(name = "language_code") val languageCode: String,
    @Json(name = "output_language_code") val outputLanguageCode: String? = null,
    @Json(name = "input_text") val inputText: String? = null,
    @Json(name = "output_text") val outputText: String? = null,
    @Json(name = "tags_json") val tagsJson: String? = null,
    @Json(name = "is_saved") val isSaved: Boolean = false,
    @Json(name = "created_at_ms") val createdAtMs: Long,
    @Json(name = "updated_at_ms") val updatedAtMs: Long,
)

@JsonClass(generateAdapter = true)
data class LearningActivitySyncRequest(
    val activities: List<LearningActivityDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class LearningActivitySyncResponse(
    val activities: List<LearningActivityDto> = emptyList(),
    @Json(name = "server_time_ms") val serverTimeMs: Long = System.currentTimeMillis(),
)
