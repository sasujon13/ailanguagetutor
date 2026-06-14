package com.cheradip.ailanguagetutor.core.billing

import android.app.Activity
import com.cheradip.ailanguagetutor.core.model.SubscriptionTier
import com.cheradip.ailanguagetutor.core.device.TrialRepository
import com.cheradip.ailanguagetutor.core.network.AiltAdminService
import com.cheradip.ailanguagetutor.core.network.AiltBillingService
import com.cheradip.ailanguagetutor.core.network.AiltPromoService
import com.cheradip.ailanguagetutor.core.network.AiltReferralService
import com.cheradip.ailanguagetutor.core.network.BillingVerifyRequest
import com.cheradip.ailanguagetutor.core.network.AdminReportSettingsPatchRequest
import com.cheradip.ailanguagetutor.core.network.AdminReportsDebugResponse
import com.cheradip.ailanguagetutor.core.network.AdminReportsLanguageActivityRow
import com.cheradip.ailanguagetutor.core.network.AdminReportsLanguagePackRow
import com.cheradip.ailanguagetutor.core.network.AdminReportSettingsDto
import com.cheradip.ailanguagetutor.core.network.CHECK_INTERNET_CONNECTION
import com.cheradip.ailanguagetutor.core.network.NetworkErrorFormatter
import com.cheradip.ailanguagetutor.core.network.PromoValidateRequest
import com.cheradip.ailanguagetutor.core.network.ReferralWithdrawRequest
import com.android.billingclient.api.Purchase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class ReferralPolicy(
    val commissionPercent: Int,
    val noticeText: String,
    val minWithdrawalUsd: Double = 100.0,
)
data class PromoValidation(val code: String, val discountPercent: Int, val discountedPrice: String)
data class ReferralBalance(
    val balanceUsd: Double,
    val pendingUsd: Double = 0.0,
    val availableUsd: Double = 0.0,
    val lifetimeEarnedUsd: Double,
    val minWithdrawalUsd: Double = 100.0,
    val withdrawable: Boolean = false,
)

data class PaywallSlot(
    val visible: Boolean = false,
    val code: String? = null,
    val discountPercent: Int = 0,
    val readOnly: Boolean = false,
    val label: String? = null,
    val placeholder: String? = null,
    val manualEntry: Boolean = false,
)

data class PaywallConfig(
    val showPromoSection: Boolean = false,
    val slot1: PaywallSlot = PaywallSlot(),
    val slot2: PaywallSlot = PaywallSlot(),
    val referralActive: Boolean = false,
    val referrerBuyerDiscountPercent: Int = 0,
)

@Singleton
class ReferralRepository @Inject constructor(
    private val referralService: AiltReferralService,
    private val networkErrors: NetworkErrorFormatter,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _policy = MutableStateFlow(
        ReferralPolicy(
            commissionPercent = 20,
            noticeText = "Valid until next notice. Cheradip may change referral rules anytime.",
        ),
    )
    val policy: StateFlow<ReferralPolicy> = _policy.asStateFlow()

    private val _balance = MutableStateFlow(ReferralBalance(0.0, 0.0, 0.0, 0.0))
    val balance: StateFlow<ReferralBalance> = _balance.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            runCatching { referralService.policy() }.onSuccess {
                _policy.value = ReferralPolicy(
                    it.commissionPercent,
                    it.noticeText,
                    it.minWithdrawalUsd,
                )
            }
            runCatching { referralService.balance() }.onSuccess {
                _balance.value = ReferralBalance(
                    balanceUsd = it.balanceUsd,
                    pendingUsd = it.pendingUsd,
                    availableUsd = it.availableUsd,
                    lifetimeEarnedUsd = it.lifetimeEarnedUsd,
                    minWithdrawalUsd = it.minWithdrawalUsd,
                    withdrawable = it.withdrawable,
                )
            }
        }
    }

    suspend fun withdraw(method: String, payoutDetails: String): Result<String> {
        if (!networkErrors.isOnline()) {
            return Result.failure(IllegalStateException(CHECK_INTERNET_CONNECTION))
        }
        return runCatching {
            val resp = referralService.withdraw(
                ReferralWithdrawRequest(method = method, payoutDetails = payoutDetails),
            )
            _balance.value = ReferralBalance(
                balanceUsd = resp.balanceUsd,
                pendingUsd = resp.pendingUsd,
                availableUsd = resp.availableUsd,
                lifetimeEarnedUsd = resp.lifetimeEarnedUsd,
                minWithdrawalUsd = _policy.value.minWithdrawalUsd,
                withdrawable = resp.availableUsd >= _policy.value.minWithdrawalUsd,
            )
            resp.message
        }.recoverCatching { error ->
            throw IllegalStateException(networkErrors.present(error, "Withdrawal failed"))
        }
    }

    suspend fun gift(recipientEmail: String, amountUsd: Double): Result<String> {
        if (!networkErrors.isOnline()) {
            return Result.failure(IllegalStateException(CHECK_INTERNET_CONNECTION))
        }
        return runCatching {
            val resp = referralService.gift(
                com.cheradip.ailanguagetutor.core.network.ReferralGiftRequest(
                    recipientEmail = recipientEmail,
                    amountUsd = amountUsd,
                ),
            )
            _balance.value = ReferralBalance(
                balanceUsd = resp.balanceUsd,
                pendingUsd = resp.pendingUsd,
                availableUsd = resp.availableUsd,
                lifetimeEarnedUsd = resp.lifetimeEarnedUsd,
                minWithdrawalUsd = _policy.value.minWithdrawalUsd,
                withdrawable = resp.availableUsd >= _policy.value.minWithdrawalUsd,
            )
            resp.message
        }.recoverCatching { error ->
            throw IllegalStateException(networkErrors.present(error, "Gift failed"))
        }
    }

    fun defaultShareMessage(email: String?, buyerBonusPercent: Int = 20): String {
        val emailRef = email?.takeIf { it.isNotBlank() } ?: "[your email here]"
        return "Dear friends, I'm using Cheradip's AI Language Tutor to learn and practice languages, " +
            "and I highly recommend it. Subscribe using my email, $emailRef, to get an extra " +
            "$buyerBonusPercent% bonus. After signing up, you can also share your email to refer " +
            "your friends and earn rewards."
    }
}

@Singleton
class PromoRepository @Inject constructor(
    private val promoService: AiltPromoService,
    private val networkErrors: NetworkErrorFormatter,
) {
    suspend fun fetchPaywallConfig(productId: String? = null): PaywallConfig =
        runCatching { promoService.paywallConfig(productId) }.map { resp ->
            PaywallConfig(
                showPromoSection = resp.showPromoSection,
                slot1 = PaywallSlot(
                    visible = resp.slot1.visible,
                    code = resp.slot1.code,
                    discountPercent = resp.slot1.discountPercent,
                    readOnly = resp.slot1.readOnly,
                    label = resp.slot1.label,
                ),
                slot2 = PaywallSlot(
                    visible = resp.slot2.visible,
                    manualEntry = resp.slot2.manualEntry,
                    label = resp.slot2.label,
                    placeholder = resp.slot2.placeholder,
                ),
                referralActive = resp.referralActive,
                referrerBuyerDiscountPercent = resp.referrerBuyerDiscountPercent,
            )
        }.getOrElse {
            PaywallConfig(showPromoSection = false)
        }

    suspend fun validate(code: String, basePrice: Double = 1.0, slot1Code: String? = null): Result<PromoValidation> {
        if (!networkErrors.isOnline()) {
            return Result.failure(IllegalStateException(CHECK_INTERNET_CONNECTION))
        }
        return runCatching {
            promoService.validate(PromoValidateRequest(code, basePrice, slot1Code))
        }.map { resp ->
            PromoValidation(resp.code, resp.discountPercent, resp.discountedPrice)
        }.recoverCatching { error ->
            throw IllegalStateException(networkErrors.present(error, "Promo code could not be validated"))
        }
    }
}

enum class AccessState { TRIAL_ACTIVE, TRIAL_EXPIRED, SUBSCRIBED, PRO_ACTIVE, PLUS_ACTIVE }

fun AccessState.grantsLearningAccess(): Boolean = when (this) {
    AccessState.TRIAL_ACTIVE,
    AccessState.PRO_ACTIVE,
    AccessState.PLUS_ACTIVE,
    AccessState.SUBSCRIBED,
    -> true
    AccessState.TRIAL_EXPIRED -> false
}

fun AccessState.hasPaidSubscription(): Boolean = when (this) {
    AccessState.PRO_ACTIVE,
    AccessState.PLUS_ACTIVE,
    AccessState.SUBSCRIBED,
    -> true
    AccessState.TRIAL_ACTIVE,
    AccessState.TRIAL_EXPIRED,
    -> false
}

@Singleton
class BillingRepository @Inject constructor(
    private val trialRepository: TrialRepository,
    private val playBillingManager: PlayBillingManager,
    private val billingService: AiltBillingService,
    private val networkErrors: NetworkErrorFormatter,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _accessState = MutableStateFlow(AccessState.TRIAL_ACTIVE)
    val accessState: StateFlow<AccessState> = _accessState.asStateFlow()

    private val _storePrices = MutableStateFlow<Map<String, String>>(emptyMap())
    val storePrices: StateFlow<Map<String, String>> = _storePrices.asStateFlow()

    private val _billingReady = MutableStateFlow(false)
    val billingReady: StateFlow<Boolean> = _billingReady.asStateFlow()

    init {
        scope.launch {
            trialRepository.ensureTrialRegistered()
            _billingReady.value = playBillingManager.connect()
            if (_billingReady.value) {
                loadStorePrices()
                restorePurchases()
            }
            refreshAccess()
        }
    }

    suspend fun loadStorePrices() {
        playBillingManager.refreshProductDetails().onSuccess {
            _storePrices.value = PlayProductIds.allSubscriptionIds()
                .mapNotNull { id ->
                    playBillingManager.formattedPrice(id)?.takeIf { it.isNotBlank() }?.let { id to it }
                }
                .toMap()
        }
    }

    suspend fun purchaseSubscription(
        activity: Activity,
        tier: SubscriptionTier,
        period: BillingPeriod,
        slot1Code: String? = null,
        slot2Code: String? = null,
        referralBalanceUsd: Double? = null,
    ): Result<Unit> {
        val productId = PlayProductIds.productId(tier, period)
        return playBillingManager.launchSubscription(activity, productId)
            .mapCatching { purchase ->
                verifyAndApply(purchase, slot1Code, slot2Code, referralBalanceUsd)
            }
            .recoverCatching { error ->
                if (error is PurchaseCancelledException) throw error
                throw IllegalStateException(networkErrors.present(error, "Purchase failed"))
            }
    }

    suspend fun restorePurchases() {
        playBillingManager.queryActivePurchases().forEach { purchase ->
            runCatching { verifyAndApply(purchase) }
        }
    }

    private suspend fun verifyAndApply(
        purchase: Purchase,
        slot1Code: String? = null,
        slot2Code: String? = null,
        referralBalanceUsd: Double? = null,
    ) {
        if (!networkErrors.isOnline()) {
            error(CHECK_INTERNET_CONNECTION)
        }
        val productId = purchase.products.firstOrNull()
            ?: error("Purchase missing product id")
        val response = billingService.verifyPurchase(
            BillingVerifyRequest(
                purchaseToken = purchase.purchaseToken,
                productId = productId,
                slot1Code = slot1Code?.takeIf { it.isNotBlank() },
                slot2Code = slot2Code?.takeIf { it.isNotBlank() },
                referralBalanceUsd = referralBalanceUsd?.takeIf { it > 0.0 },
            ),
        )
        if (!response.active) error("Subscription verification failed")
        playBillingManager.acknowledgeIfNeeded(purchase)
        applyVerifiedTier(
            tier = response.tier ?: PlayProductIds.tierForProductId(productId).name.lowercase(),
        )
    }

    private fun applyVerifiedTier(tier: String) {
        _accessState.value = when (tier.lowercase()) {
            "plus" -> AccessState.PLUS_ACTIVE
            else -> AccessState.PRO_ACTIVE
        }
    }

    suspend fun refreshAccess() {
        val subscribed = _accessState.value in setOf(
            AccessState.SUBSCRIBED,
            AccessState.PRO_ACTIVE,
            AccessState.PLUS_ACTIVE,
        )
        if (subscribed) return
        _accessState.value = if (trialRepository.isTrialActive()) {
            AccessState.TRIAL_ACTIVE
        } else {
            AccessState.TRIAL_EXPIRED
        }
    }

    /** Debug-only bypass when Play Billing unavailable (emulator without Play Store). */
    fun unlockPremiumDev(plus: Boolean = false) {
        _accessState.value = if (plus) AccessState.PLUS_ACTIVE else AccessState.PRO_ACTIVE
    }
}

@Singleton
class CheckAppAccessUseCase @Inject constructor(
    private val billingRepository: BillingRepository,
) {
    val accessState: StateFlow<AccessState> = billingRepository.accessState

    fun requiresPaywall(): Boolean =
        !hasLearningAccess()

    fun hasLearningAccess(): Boolean =
        billingRepository.accessState.value.grantsLearningAccess()

    fun subscriptionTier(): SubscriptionTier =
        when (billingRepository.accessState.value) {
            AccessState.PLUS_ACTIVE -> SubscriptionTier.PLUS
            AccessState.PRO_ACTIVE,
            AccessState.SUBSCRIBED,
            AccessState.TRIAL_ACTIVE,
            -> SubscriptionTier.PRO
            AccessState.TRIAL_EXPIRED -> SubscriptionTier.FREE
        }
}

@Singleton
class AdminPromoRepository @Inject constructor(
    private val adminService: AiltAdminService,
    private val networkErrors: NetworkErrorFormatter,
) {
    data class PromoRow(
        val code: String,
        val discountPercent: Int,
        val active: Boolean,
        val autoApply: Boolean = false,
        val paywallSlot: Int = 2,
    )

    suspend fun listCodes(): Result<List<PromoRow>> {
        if (!networkErrors.isOnline()) {
            return Result.failure(IllegalStateException(CHECK_INTERNET_CONNECTION))
        }
        return runCatching {
            adminService.listPromoCodes().codes.map {
                PromoRow(
                    code = it.code,
                    discountPercent = it.discountPercent,
                    active = it.active,
                    autoApply = it.autoApply,
                    paywallSlot = it.paywallSlot,
                )
            }
        }.recoverCatching { error ->
            throw IllegalStateException(networkErrors.present(error, "Could not load promo codes from server"))
        }
    }

    suspend fun createCode(
        code: String,
        discountPercent: Int,
        active: Boolean = true,
        autoApply: Boolean = false,
        paywallSlot: Int = 2,
    ): Result<Unit> {
        if (!networkErrors.isOnline()) {
            return Result.failure(IllegalStateException(CHECK_INTERNET_CONNECTION))
        }
        return runCatching {
            adminService.createPromoCode(
                com.cheradip.ailanguagetutor.core.network.AdminPromoCodeDto(
                    code = code,
                    discountPercent = discountPercent,
                    active = active,
                    autoApply = autoApply,
                    paywallSlot = paywallSlot,
                ),
            )
            Unit
        }.recoverCatching { error ->
            throw IllegalStateException(networkErrors.present(error, "Could not save promo code"))
        }
    }

    suspend fun updateCode(
        code: String,
        discountPercent: Int,
        active: Boolean,
        autoApply: Boolean,
        paywallSlot: Int,
    ): Result<Unit> {
        if (!networkErrors.isOnline()) {
            return Result.failure(IllegalStateException(CHECK_INTERNET_CONNECTION))
        }
        return runCatching {
            adminService.patchPromoCode(
                code,
                com.cheradip.ailanguagetutor.core.network.AdminPromoPatchDto(
                    discountPercent = discountPercent,
                    active = active,
                    autoApply = autoApply,
                    paywallSlot = paywallSlot,
                ),
            )
            Unit
        }.recoverCatching { error ->
            throw IllegalStateException(networkErrors.present(error, "Could not update promo code"))
        }
    }
}

data class AdminReportsSnapshot(
    val generatedAtMs: Long = 0L,
    val cloudReportsEnabled: Boolean = true,
    val homeAiReportsEnabled: Boolean = true,
    val debugReportsEnabled: Boolean = true,
    val languagePackCatalogActive: Int = 0,
    val languagePackRows: List<AdminReportsLanguagePackRow> = emptyList(),
    val learningActivityByLanguage: List<AdminReportsLanguageActivityRow> = emptyList(),
    val totalUsers: Int = 0,
    val regularUsers: Int = 0,
    val adminUsers: Int = 0,
    val emailVerifiedUsers: Int = 0,
    val newUsers7Days: Int = 0,
    val newUsers30Days: Int = 0,
    val activePro: Int = 0,
    val activePlus: Int = 0,
    val activeSubscriptions: Int = 0,
    val learningActivities: Int = 0,
    val deviceTrials: Int = 0,
    val guestAiUsesTotal: Int = 0,
    val pendingWithdrawals: Int = 0,
    val referralBalanceUsd: Double = 0.0,
    val referralPendingCommissionUsd: Double = 0.0,
    val promoCodesTotal: Int = 0,
    val promoCodesActive: Int = 0,
    val cloudAiRequestsToday: Int = 0,
    val cloudAiRoutingMode: String = "",
    val cloudAiProviders: List<AdminReportsCloudProviderRow> = emptyList(),
)

data class AdminReportsCloudProviderRow(
    val id: String,
    val displayName: String,
    val tier: String,
    val health: String,
    val enabled: Boolean,
    val requestsToday: Int,
    val quotaDailyLimit: Int?,
    val quotaUsedPercent: Int,
)

data class AdminEarningsSnapshot(
    val period: String,
    val bucketPeriod: String,
    val from: String,
    val to: String,
    val generatedAtMs: Long,
    val totals: com.cheradip.ailanguagetutor.core.network.AdminEarningsMetrics,
    val rows: List<com.cheradip.ailanguagetutor.core.network.AdminEarningsRow>,
)

@Singleton
class AdminReportsRepository @Inject constructor(
    private val adminService: AiltAdminService,
    private val networkErrors: NetworkErrorFormatter,
) {
    suspend fun fetchReports(): Result<AdminReportsSnapshot> {
        if (!networkErrors.isOnline()) {
            return Result.failure(IllegalStateException(CHECK_INTERNET_CONNECTION))
        }
        return runCatching {
            val resp = adminService.reports()
            AdminReportsSnapshot(
                generatedAtMs = resp.generatedAtMs,
                cloudReportsEnabled = resp.reportSettings.cloudReportsEnabled,
                homeAiReportsEnabled = resp.reportSettings.homeAiReportsEnabled,
                debugReportsEnabled = resp.reportSettings.debugReportsEnabled,
                languagePackCatalogActive = resp.languagePacks.catalogActive,
                languagePackRows = resp.languagePacks.packs,
                learningActivityByLanguage = resp.languagePacks.learningActivityByLanguage,
                totalUsers = resp.users.total,
                regularUsers = resp.users.regular,
                adminUsers = resp.users.admins,
                emailVerifiedUsers = resp.users.emailVerified,
                newUsers7Days = resp.users.newLast7Days,
                newUsers30Days = resp.users.newLast30Days,
                activePro = resp.subscriptions.activePro,
                activePlus = resp.subscriptions.activePlus,
                activeSubscriptions = resp.subscriptions.activeTotal,
                learningActivities = resp.engagement.learningActivities,
                deviceTrials = resp.engagement.deviceTrials,
                guestAiUsesTotal = resp.engagement.guestAiUsesTotal,
                pendingWithdrawals = resp.referrals.pendingWithdrawals,
                referralBalanceUsd = resp.referrals.totalBalanceUsd,
                referralPendingCommissionUsd = resp.referrals.pendingCommissionUsd,
                promoCodesTotal = resp.promoCodes.total,
                promoCodesActive = resp.promoCodes.active,
                cloudAiRequestsToday = resp.cloudAi.totalRequestsToday,
                cloudAiRoutingMode = resp.cloudAi.routingMode,
                cloudAiProviders = resp.cloudAi.providers.map {
                    AdminReportsCloudProviderRow(
                        id = it.id,
                        displayName = it.displayName,
                        tier = it.tier,
                        health = it.health,
                        enabled = it.enabled,
                        requestsToday = it.requestsToday,
                        quotaDailyLimit = it.quotaDailyLimit,
                        quotaUsedPercent = it.quotaUsedPercent,
                    )
                },
            )
        }.recoverCatching { error ->
            throw IllegalStateException(networkErrors.present(error, "Could not load reports"))
        }
    }

    suspend fun fetchReportSettings(): Result<AdminReportSettingsDto> {
        if (!networkErrors.isOnline()) {
            return Result.failure(IllegalStateException(CHECK_INTERNET_CONNECTION))
        }
        return runCatching { adminService.reportsSettings() }
            .recoverCatching { error ->
                throw IllegalStateException(networkErrors.present(error, "Could not load report settings"))
            }
    }

    suspend fun updateReportSettings(
        cloudReportsEnabled: Boolean? = null,
        homeAiReportsEnabled: Boolean? = null,
        debugReportsEnabled: Boolean? = null,
    ): Result<AdminReportSettingsDto> {
        if (!networkErrors.isOnline()) {
            return Result.failure(IllegalStateException(CHECK_INTERNET_CONNECTION))
        }
        return runCatching {
            adminService.patchReportsSettings(
                AdminReportSettingsPatchRequest(
                    cloudReportsEnabled = cloudReportsEnabled,
                    homeAiReportsEnabled = homeAiReportsEnabled,
                    debugReportsEnabled = debugReportsEnabled,
                ),
            )
        }.recoverCatching { error ->
            throw IllegalStateException(networkErrors.present(error, "Could not update report settings"))
        }
    }

    suspend fun fetchDebugReports(): Result<AdminReportsDebugResponse> {
        if (!networkErrors.isOnline()) {
            return Result.failure(IllegalStateException(CHECK_INTERNET_CONNECTION))
        }
        return runCatching { adminService.reportsDebug() }
            .recoverCatching { error ->
                throw IllegalStateException(networkErrors.present(error, "Could not load debug reports"))
            }
    }

    suspend fun fetchEarningsReport(
        period: String,
        fromDate: String? = null,
        toDate: String? = null,
    ): Result<AdminEarningsSnapshot> {
        if (!networkErrors.isOnline()) {
            return Result.failure(IllegalStateException(CHECK_INTERNET_CONNECTION))
        }
        return runCatching {
            val resp = adminService.reportsEarnings(period, fromDate, toDate)
            AdminEarningsSnapshot(
                period = resp.period,
                bucketPeriod = resp.bucketPeriod,
                from = resp.from,
                to = resp.to,
                generatedAtMs = resp.generatedAtMs,
                totals = resp.totals,
                rows = resp.rows,
            )
        }.recoverCatching { error ->
            throw IllegalStateException(networkErrors.present(error, "Could not load earnings report"))
        }
    }
}
