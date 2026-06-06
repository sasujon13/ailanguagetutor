package com.cheradip.ailanguagetutor.feature.billing

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.billing.BillingPeriod
import com.cheradip.ailanguagetutor.core.billing.BillingRepository
import com.cheradip.ailanguagetutor.core.billing.PaywallConfig
import com.cheradip.ailanguagetutor.core.billing.PlayProductIds
import com.cheradip.ailanguagetutor.core.billing.PromoRepository
import com.cheradip.ailanguagetutor.core.billing.PromoValidation
import com.cheradip.ailanguagetutor.core.billing.PurchaseCancelledException
import com.cheradip.ailanguagetutor.core.model.SubscriptionTier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PaywallPlan { PRO, PLUS }

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
)

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val promoRepository: PromoRepository,
    private val billingRepository: BillingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaywallUiState())
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = promoRepository.fetchPaywallConfig(
                PlayProductIds.productId(SubscriptionTier.PRO, BillingPeriod.MONTHLY),
            )
            val slot1 = if (config.slot1.visible) config.slot1.code.orEmpty() else ""
            billingRepository.loadStorePrices()
            _uiState.update {
                it.copy(
                    config = config,
                    slot1Code = slot1,
                    loadingConfig = false,
                    storePrices = billingRepository.storePrices.value,
                    billingReady = billingRepository.billingReady.value,
                )
            }
            if (slot1.isNotBlank()) {
                applySlot1(slot1)
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
    }

    fun selectBillingPeriod(period: BillingPeriod) {
        _uiState.update { it.copy(billingPeriod = period, purchaseError = null) }
    }

    fun updateSlot2(code: String) {
        _uiState.update { it.copy(slot2Code = code, promoError = null) }
    }

    fun applySlot2() {
        val code = _uiState.value.slot2Code.trim()
        if (code.isBlank()) return
        val base = basePriceForPlan(_uiState.value.selectedPlan)
        viewModelScope.launch {
            promoRepository.validate(code, base)
                .onSuccess { result ->
                    _uiState.update { it.copy(slot2Result = result, promoError = null) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(slot2Result = null, promoError = e.message) }
                }
        }
    }

    fun subscribe(activity: Activity, onDone: () -> Unit) {
        val state = _uiState.value
        val tier = if (state.selectedPlan == PaywallPlan.PLUS) SubscriptionTier.PLUS else SubscriptionTier.PRO
        viewModelScope.launch {
            _uiState.update { it.copy(purchaseInProgress = true, purchaseError = null) }
            billingRepository.purchaseSubscription(activity, tier, state.billingPeriod)
                .onSuccess {
                    _uiState.update { it.copy(purchaseInProgress = false) }
                    onDone()
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
            _uiState.update { it.copy(purchaseInProgress = false) }
            when (billingRepository.accessState.value) {
                com.cheradip.ailanguagetutor.core.billing.AccessState.PRO_ACTIVE,
                com.cheradip.ailanguagetutor.core.billing.AccessState.PLUS_ACTIVE,
                com.cheradip.ailanguagetutor.core.billing.AccessState.SUBSCRIBED,
                -> onRestored()
                else -> _uiState.update {
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

    fun effectiveMonthlyPrice(): String {
        val state = _uiState.value
        val base = basePriceForPlan(state.selectedPlan)
        val discount = combinedDiscountPercent()
        val price = base * (100 - discount) / 100.0
        return "$%.2f".format(price)
    }

    fun effectiveYearlyPrice(): String {
        val state = _uiState.value
        val base = yearlyBaseForPlan(state.selectedPlan)
        val discount = combinedDiscountPercent()
        val price = base * (100 - discount) / 100.0
        return "$%.2f".format(price)
    }

    private fun combinedDiscountPercent(): Int =
        listOfNotNull(
            _uiState.value.slot1Result?.discountPercent,
            _uiState.value.slot2Result?.discountPercent,
        ).sum().coerceAtMost(90)

    private fun applySlot1(code: String) {
        val base = basePriceForPlan(_uiState.value.selectedPlan)
        viewModelScope.launch {
            promoRepository.validate(code, base)
                .onSuccess { result ->
                    _uiState.update { it.copy(slot1Result = result) }
                }
        }
    }

    private fun basePriceForPlan(plan: PaywallPlan) = when (plan) {
        PaywallPlan.PRO -> 2.0
        PaywallPlan.PLUS -> 5.0
    }

    private fun yearlyBaseForPlan(plan: PaywallPlan) = when (plan) {
        PaywallPlan.PRO -> 20.0
        PaywallPlan.PLUS -> 50.0
    }
}
