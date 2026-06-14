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

    @POST("auth/signup/init")
    suspend fun signupInit(@Body body: SignupInitRequest): SignupInitResponse

    @POST("auth/recovery/send")
    suspend fun recoverySend(@Body body: RecoverySendRequest): RecoverySendResponse

    @POST("auth/recovery/reset")
    suspend fun recoveryReset(@Body body: RecoveryResetRequest): OkMessageResponse

    @POST("auth/password/update/send")
    suspend fun passwordUpdateSend(@Body body: PasswordUpdateSendRequest): PasswordUpdateSendResponse

    @POST("auth/password/update/confirm")
    suspend fun passwordUpdateConfirm(@Body body: PasswordUpdateConfirmRequest): OkMessageResponse

    @POST("auth/email/change/send")
    suspend fun emailChangeSend(@Body body: EmailChangeSendRequest): EmailChangeSendResponse

    @POST("auth/email/change/confirm")
    suspend fun emailChangeConfirm(@Body body: EmailChangeConfirmRequest): EmailChangeConfirmResponse
}

interface AiltDeviceService {
    @POST("device/register")
    suspend fun register(@Body body: DeviceRegisterRequest): DeviceRegisterResponse

    @POST("device/guest-ai-usage/sync")
    suspend fun syncGuestAiUsage(@Body body: GuestAiSyncRequest): GuestAiUsageResponse

    @POST("device/guest-ai-usage/record")
    suspend fun recordGuestAiUsage(@Body body: GuestAiRecordRequest): GuestAiUsageResponse
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

    @POST("referral/withdraw")
    suspend fun withdraw(@Body body: ReferralWithdrawRequest): ReferralWithdrawResponse
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

    @PATCH("admin/promo-codes/{code}")
    suspend fun patchPromoCode(
        @Path("code") code: String,
        @Body body: AdminPromoPatchDto,
    ): AdminPromoCodeDto

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

    /** Platform metrics — admin login required. */
    @GET("admin/reports")
    suspend fun reports(): AdminReportsResponse
}

interface AiltAiService {
    @POST("ai/activity-metadata")
    suspend fun activityMetadata(@Body body: AiActivityMetadataRequest): AiActivityMetadataResponse

    @POST("ai/explain-paragraph")
    suspend fun explainParagraph(@Body body: AiParagraphRequest): AiParagraphResponse

    @POST("ai/structure-ocr")
    suspend fun structureOcr(@Body body: AiStructureOcrRequest): AiStructureOcrResponse
}

interface AiltLearningService {
    @POST("learning/sync")
    suspend fun sync(@Body body: LearningActivitySyncRequest): LearningActivitySyncResponse
}
