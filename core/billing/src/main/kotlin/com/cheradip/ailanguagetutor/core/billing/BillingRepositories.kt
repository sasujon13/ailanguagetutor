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

data class ReferralPolicy(val commissionPercent: Int, val noticeText: String)
data class PromoValidation(val code: String, val discountPercent: Int, val discountedPrice: String)
data class ReferralBalance(val balanceUsd: Double, val lifetimeEarnedUsd: Double)

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
        scope.launch {
            runCatching { referralService.policy() }.onSuccess {
                _policy.value = ReferralPolicy(it.commissionPercent, it.noticeText)
            }
            runCatching { referralService.balance() }.onSuccess {
                _balance.value = ReferralBalance(it.balanceUsd, it.lifetimeEarnedUsd)
            }
        }
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
    private val localCodes = mapOf(
        "LAUNCH20" to 20,
        "LAUNCH50" to 50,
        "CHERADIP10" to 10,
        "WELCOME" to 15,
    )

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
            PaywallConfig(
                showPromoSection = true,
                slot1 = PaywallSlot(
                    visible = true,
                    code = "LAUNCH50",
                    discountPercent = 50,
                    readOnly = true,
                    label = "Launch promo (applied automatically)",
                ),
                slot2 = PaywallSlot(
                    visible = true,
                    manualEntry = true,
                    label = "Additional promo code",
                    placeholder = "Referrer email, WELCOME10…",
                ),
                referralActive = true,
                referrerBuyerDiscountPercent = 20,
            )
        }

    suspend fun validate(code: String, basePrice: Double = 1.0): Result<PromoValidation> {
        runCatching {
            promoService.validate(PromoValidateRequest(code, basePrice))
        }.onSuccess { resp ->
            return Result.success(
                PromoValidation(resp.code, resp.discountPercent, resp.discountedPrice),
            )
        }
        val percent = localCodes[code.uppercase()]
            ?: return Result.failure(IllegalArgumentException("Invalid promo code"))
        val discounted = basePrice * (100 - percent) / 100.0
        return Result.success(
            PromoValidation(
                code = code.uppercase(),
                discountPercent = percent,
                discountedPrice = "$%.2f".format(discounted),
            ),
        )
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
    ): Result<Unit> {
        val productId = PlayProductIds.productId(tier, period)
        return playBillingManager.launchSubscription(activity, productId)
            .mapCatching { purchase -> verifyAndApply(purchase) }
    }

    suspend fun restorePurchases() {
        playBillingManager.queryActivePurchases().forEach { purchase ->
            runCatching { verifyAndApply(purchase) }
        }
    }

    private suspend fun verifyAndApply(purchase: Purchase) {
        val productId = purchase.products.firstOrNull()
            ?: error("Purchase missing product id")
        val response = billingService.verifyPurchase(
            BillingVerifyRequest(
                purchaseToken = purchase.purchaseToken,
                productId = productId,
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
    suspend fun listCodes() = runCatching { adminService.listPromoCodes().codes }

    suspend fun createCode(code: String, discountPercent: Int) =
        runCatching {
            adminService.createPromoCode(
                com.cheradip.ailanguagetutor.core.network.AdminPromoCodeDto(code, discountPercent),
            )
        }
}
