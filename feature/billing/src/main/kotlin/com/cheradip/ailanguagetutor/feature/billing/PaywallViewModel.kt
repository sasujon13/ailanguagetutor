package com.cheradip.ailanguagetutor.feature.billing

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.auth.AuthRepository
import com.cheradip.ailanguagetutor.core.billing.hasPaidSubscription
import com.cheradip.ailanguagetutor.core.billing.BillingRepository
import com.cheradip.ailanguagetutor.core.billing.BillingPeriod
import com.cheradip.ailanguagetutor.core.billing.PaywallConfig
import com.cheradip.ailanguagetutor.core.billing.PlayProductIds
import com.cheradip.ailanguagetutor.core.billing.PromoCodes
import com.cheradip.ailanguagetutor.core.billing.PromoRepository
import com.cheradip.ailanguagetutor.core.billing.PromoValidation
import com.cheradip.ailanguagetutor.core.billing.ReferralRepository
import com.cheradip.ailanguagetutor.core.billing.SubscriptionPlan
import com.cheradip.ailanguagetutor.core.billing.SubscriptionPriceDisplay
import com.cheradip.ailanguagetutor.core.billing.SubscriptionPricing
import com.cheradip.ailanguagetutor.core.billing.PurchaseCancelledException
import com.cheradip.ailanguagetutor.core.model.SubscriptionTier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PaywallPlan { PRO, PLUS }

fun PaywallPlan.toSubscriptionPlan(): SubscriptionPlan = when (this) {
    PaywallPlan.PRO -> SubscriptionPlan.PRO
    PaywallPlan.PLUS -> SubscriptionPlan.PLUS
}

data class PaywallUiState(
    val config: PaywallConfig? = null,
    val slot1Code: String = "",
    val slot2Code: String = "",
    val slot1Result: PromoValidation? = null,
    val slot2Result: PromoValidation? = null,
    val promoError: String? = null,
    val selectedPlan: PaywallPlan = PaywallPlan.PRO,
    val billingPeriod: BillingPeriod = BillingPeriod.MONTHLY,
    val loadingConfig: Boolean = true,
    val purchaseInProgress: Boolean = false,
    val purchaseError: String? = null,
    val storePrices: Map<String, String> = emptyMap(),
    val billingReady: Boolean = false,
    val useReferralBalance: Boolean = false,
)

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val promoRepository: PromoRepository,
    private val billingRepository: BillingRepository,
    private val authRepository: AuthRepository,
    private val referralRepository: ReferralRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaywallUiState())
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser.first()
            referralRepository.refresh()
            val config = promoRepository.fetchPaywallConfig(
                PlayProductIds.productId(SubscriptionTier.PRO, BillingPeriod.MONTHLY),
            )
            val autoCode = PromoCodes.resolveAutoApplyCode(config.slot1)
            billingRepository.loadStorePrices()
            _uiState.update {
                it.copy(
                    config = config,
                    slot1Code = autoCode,
                    loadingConfig = false,
                    storePrices = billingRepository.storePrices.value,
                    billingReady = billingRepository.billingReady.value,
                )
            }
            if (autoCode.isNotBlank()) {
                applySlot1(autoCode)
            }
        }
        viewModelScope.launch {
            billingRepository.storePrices.collect { prices ->
                _uiState.update { it.copy(storePrices = prices) }
            }
        }
        viewModelScope.launch {
            billingRepository.billingReady.collect { ready ->
                _uiState.update { it.copy(billingReady = ready) }
            }
        }
    }

    fun selectPlan(plan: PaywallPlan) {
        _uiState.update { it.copy(selectedPlan = plan, purchaseError = null) }
        val autoCode = _uiState.value.config?.let { PromoCodes.resolveAutoApplyCode(it.slot1) }.orEmpty()
        if (autoCode.isNotBlank()) applySlot1(autoCode)
        val slot2 = _uiState.value.slot2Code.trim()
        if (slot2.isNotBlank()) applySlot2Internal(slot2)
    }

    fun selectBillingPeriod(period: BillingPeriod) {
        _uiState.update { it.copy(billingPeriod = period, purchaseError = null) }
        val autoCode = _uiState.value.config?.let { PromoCodes.resolveAutoApplyCode(it.slot1) }.orEmpty()
        if (autoCode.isNotBlank()) applySlot1(autoCode)
        val slot2 = _uiState.value.slot2Code.trim()
        if (slot2.isNotBlank()) applySlot2Internal(slot2)
    }

    fun updateSlot2(code: String) {
        _uiState.update { it.copy(slot2Code = code, promoError = null) }
    }

    fun applySlot2() {
        val code = _uiState.value.slot2Code.trim()
        if (code.isBlank()) return
        applySlot2Internal(code)
    }

    private fun applySlot2Internal(code: String) {
        val base = basePriceForPlan(_uiState.value.selectedPlan, _uiState.value.billingPeriod)
        val slot1 = _uiState.value.slot1Code.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            promoRepository.validate(code, base, slot1Code = slot1)
                .onSuccess { result ->
                    _uiState.update { it.copy(slot2Result = result, promoError = null) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(slot2Result = null, promoError = e.message) }
                }
        }
    }

    fun setUseReferralBalance(enabled: Boolean) {
        _uiState.update { it.copy(useReferralBalance = enabled) }
    }

    fun referralCreditToApply(state: PaywallUiState = _uiState.value): Double {
        if (!state.useReferralBalance) return 0.0
        val available = referralRepository.balance.value.availableUsd
        if (available <= 0.0) return 0.0
        val price = SubscriptionPricing.basePrice(
            state.selectedPlan.toSubscriptionPlan(),
            state.billingPeriod,
        )
        val discounted = activeDiscountPercents(state).fold(price) { acc, pct ->
            acc * (1 - pct / 100.0)
        }
        return minOf(available, discounted).let { kotlin.math.round(it * 100) / 100.0 }
    }

    fun subscribe(activity: Activity, onDone: () -> Unit) {
        val state = _uiState.value
        val tier = if (state.selectedPlan == PaywallPlan.PLUS) SubscriptionTier.PLUS else SubscriptionTier.PRO
        val referralCredit = referralCreditToApply(state)
        viewModelScope.launch {
            _uiState.update { it.copy(purchaseInProgress = true, purchaseError = null) }
            billingRepository.purchaseSubscription(
                activity = activity,
                tier = tier,
                period = state.billingPeriod,
                slot1Code = state.slot1Code.takeIf { it.isNotBlank() },
                slot2Code = state.slot2Code.takeIf { it.isNotBlank() },
                referralBalanceUsd = referralCredit.takeIf { it > 0.0 },
            )
                .onSuccess {
                    _uiState.update { it.copy(purchaseInProgress = false, useReferralBalance = false) }
                    referralRepository.refresh()
                    billingRepository.refreshAccess()
                    if (billingRepository.accessState.value.hasPaidSubscription()) {
                        onDone()
                    }
                }
                .onFailure { e ->
                    val message = when (e) {
                        is PurchaseCancelledException -> null
                        else -> e.message ?: "Purchase failed"
                    }
                    _uiState.update {
                        it.copy(purchaseInProgress = false, purchaseError = message)
                    }
                }
        }
    }

    fun restorePurchases(onRestored: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(purchaseInProgress = true, purchaseError = null) }
            billingRepository.restorePurchases()
            billingRepository.refreshAccess()
            _uiState.update { it.copy(purchaseInProgress = false) }
            if (billingRepository.accessState.value.hasPaidSubscription()) {
                onRestored()
            } else {
                _uiState.update {
                    it.copy(purchaseError = "No active subscription found on this Google account")
                }
            }
        }
    }

    fun displayPrice(plan: PaywallPlan, period: BillingPeriod): String? {
        val tier = if (plan == PaywallPlan.PLUS) SubscriptionTier.PLUS else SubscriptionTier.PRO
        val productId = PlayProductIds.productId(tier, period)
        return _uiState.value.storePrices[productId]
    }

    fun priceDisplay(plan: PaywallPlan, state: PaywallUiState = _uiState.value): SubscriptionPriceDisplay =
        SubscriptionPricing.display(
            plan = plan.toSubscriptionPlan(),
            period = state.billingPeriod,
            discountPercents = activeDiscountPercents(state),
        )

    private fun activeDiscountPercents(state: PaywallUiState): List<Int> = listOfNotNull(
        state.slot1Result?.discountPercent,
        state.slot2Result?.discountPercent,
    )

    private fun applySlot1(code: String) {
        val base = basePriceForPlan(_uiState.value.selectedPlan, _uiState.value.billingPeriod)
        viewModelScope.launch {
            promoRepository.validate(code, base)
                .onSuccess { result ->
                    _uiState.update { it.copy(slot1Result = result, slot1Code = code) }
                }
        }
    }

    private fun basePriceForPlan(plan: PaywallPlan, period: BillingPeriod) =
        SubscriptionPricing.basePrice(plan.toSubscriptionPlan(), period)
}
