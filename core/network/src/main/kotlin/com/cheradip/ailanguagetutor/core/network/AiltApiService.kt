package com.cheradip.ailanguagetutor.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Cheradip AI Language Tutor API — base: https://ailt.cheradip.com/api/ailt/
 * Implemented in server/cloud-api/ (self-hosted via Cloudflare Tunnel).
 */
interface AiltAuthService {
    @POST("auth/login")
    suspend fun login(@Body body: AuthLoginRequest): AuthLoginResponse

    @POST("auth/register")
    suspend fun register(@Body body: OtpRequest): Unit

    @POST("auth/verify-email")
    suspend fun verifyEmail(@Body body: OtpVerifyRequest): AuthLoginResponse

    @POST("auth/verify-whatsapp")
    suspend fun verifyWhatsApp(@Body body: OtpVerifyRequest): AuthLoginResponse
}

interface AiltDeviceService {
    @POST("device/register")
    suspend fun register(@Body body: DeviceRegisterRequest): DeviceRegisterResponse
}

interface AiltBillingService {
    @POST("billing/verify")
    suspend fun verifyPurchase(@Body body: BillingVerifyRequest): BillingVerifyResponse
}

interface AiltPromoService {
    @GET("promo/paywall-config")
    suspend fun paywallConfig(
        @Query("productId") productId: String? = null,
    ): PaywallConfigResponse

    @POST("promo/validate")
    suspend fun validate(@Body body: PromoValidateRequest): PromoValidateResponse
}

interface AiltReferralService {
    @GET("referral/policy")
    suspend fun policy(): ReferralPolicyResponse

    @GET("referral/balance")
    suspend fun balance(): ReferralBalanceResponse
}

interface AiltLanguageService {
    @GET("languages/list")
    suspend fun list(): LanguageListResponse

    @GET("languages/{code}/download")
    suspend fun downloadInfo(@Path("code") code: String): LanguagePackInfo
}

interface AiltAdminService {
    @GET("admin/promo-codes")
    suspend fun listPromoCodes(): AdminPromoListResponse

    @POST("admin/promo-codes")
    suspend fun createPromoCode(@Body body: AdminPromoCodeDto): AdminPromoCodeDto

    /** AI provider pool status — admin only. */
    @GET("admin/ai/providers")
    suspend fun aiProviderStatus(): AiProvidersStatusResponse

    @PATCH("admin/ai/routing")
    suspend fun updateAiRouting(@Body body: AiRoutingPolicyUpdateRequest): AiRoutingPolicyDto

    @PATCH("admin/ai/providers/{id}")
    suspend fun toggleAiProvider(
        @Path("id") providerId: String,
        @Body body: AiProviderToggleRequest,
    ): AiProviderStatusDto
}

interface AiltAiService {
    @POST("ai/activity-metadata")
    suspend fun activityMetadata(@Body body: AiActivityMetadataRequest): AiActivityMetadataResponse

    @POST("ai/explain-paragraph")
    suspend fun explainParagraph(@Body body: AiParagraphRequest): AiParagraphResponse
}
