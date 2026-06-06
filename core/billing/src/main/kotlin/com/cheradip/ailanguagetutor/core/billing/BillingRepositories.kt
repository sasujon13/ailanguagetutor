package com.cheradip.ailanguagetutor.core.billing

import android.app.Activity
import com.cheradip.ailanguagetutor.core.model.SubscriptionTier
import com.cheradip.ailanguagetutor.core.device.TrialRepository
import com.cheradip.ailanguagetutor.core.network.AiltAdminService
import com.cheradip.ailanguagetutor.core.network.AiltBillingService
import com.cheradip.ailanguagetutor.core.network.AiltPromoService
import com.cheradip.ailanguagetutor.core.network.AiltReferralService
import com.cheradip.ailanguagetutor.core.network.BillingVerifyRequest
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _policy = MutableStateFlow(
        ReferralPolicy(
            commissionPercent = 20,
            noticeText = "Valid until next notice. Cheradip may change referral rules anytime.",
        ),
    )
    val policy: StateFlow<ReferralPolicy> = _policy.asStateFlow()

    private val _balance = MutableStateFlow(ReferralBalance(0.0, 0.0))
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
                    it.balanceUsd,
                    it.lifetimeEarnedUsd,
                    it.minWithdrawalUsd,
                    it.withdrawable,
                )
            }
        }
    }

    suspend fun withdraw(method: String, payoutDetails: String): Result<String> = runCatching {
        val resp = referralService.withdraw(
            ReferralWithdrawRequest(method = method, payoutDetails = payoutDetails),
        )
        _balance.value = ReferralBalance(
            resp.balanceUsd,
            _balance.value.lifetimeEarnedUsd,
            _policy.value.minWithdrawalUsd,
            resp.balanceUsd >= _policy.value.minWithdrawalUsd,
        )
        resp.message
    }

    fun shareText(email: String?, whatsapp: String?): String {
        val ref = whatsapp ?: email ?: "your username"
        return "Use my reference when you subscribe to AI Language Tutor: $ref"
    }
}

@Singleton
class PromoRepository @Inject constructor(
    private val promoService: AiltPromoService,
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

    suspend fun validate(code: String, basePrice: Double = 1.0, slot1Code: String? = null): Result<PromoValidation> =
        runCatching {
            promoService.validate(PromoValidateRequest(code, basePrice, slot1Code))
        }.map { resp ->
            PromoValidation(resp.code, resp.discountPercent, resp.discountedPrice)
        }
}

enum class AccessState { TRIAL_ACTIVE, TRIAL_EXPIRED, SUBSCRIBED, PRO_ACTIVE, PLUS_ACTIVE }

@Singleton
class BillingRepository @Inject constructor(
    private val trialRepository: TrialRepository,
    private val playBillingManager: PlayBillingManager,
    private val billingService: AiltBillingService,
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
    ): Result<Unit> {
        val productId = PlayProductIds.productId(tier, period)
        return playBillingManager.launchSubscription(activity, productId)
            .mapCatching { purchase -> verifyAndApply(purchase, slot1Code, slot2Code) }
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
    ) {
        val productId = purchase.products.firstOrNull()
            ?: error("Purchase missing product id")
        val response = billingService.verifyPurchase(
            BillingVerifyRequest(
                purchaseToken = purchase.purchaseToken,
                productId = productId,
                slot1Code = slot1Code?.takeIf { it.isNotBlank() },
                slot2Code = slot2Code?.takeIf { it.isNotBlank() },
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
        billingRepository.accessState.value == AccessState.TRIAL_EXPIRED

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
) {
    data class PromoRow(
        val code: String,
        val discountPercent: Int,
        val active: Boolean,
        val autoApply: Boolean = false,
        val paywallSlot: Int = 2,
    )

    suspend fun listCodes(): Result<List<PromoRow>> = runCatching {
        adminService.listPromoCodes().codes.map {
            PromoRow(
                code = it.code,
                discountPercent = it.discountPercent,
                active = it.active,
                autoApply = it.autoApply,
                paywallSlot = it.paywallSlot,
            )
        }
    }

    suspend fun createCode(
        code: String,
        discountPercent: Int,
        active: Boolean = true,
        autoApply: Boolean = false,
        paywallSlot: Int = 2,
    ) = runCatching {
        adminService.createPromoCode(
            com.cheradip.ailanguagetutor.core.network.AdminPromoCodeDto(
                code = code,
                discountPercent = discountPercent,
                active = active,
                autoApply = autoApply,
                paywallSlot = paywallSlot,
            ),
        )
    }

    suspend fun updateCode(
        code: String,
        discountPercent: Int,
        active: Boolean,
        autoApply: Boolean,
        paywallSlot: Int,
    ) = runCatching {
        adminService.patchPromoCode(
            code,
            com.cheradip.ailanguagetutor.core.network.AdminPromoPatchDto(
                discountPercent = discountPercent,
                active = active,
                autoApply = autoApply,
                paywallSlot = paywallSlot,
            ),
        )
    }
}
