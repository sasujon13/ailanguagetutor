package com.cheradip.ailanguagetutor.feature.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheradip.ailanguagetutor.core.billing.BillingPeriod
import com.cheradip.ailanguagetutor.core.billing.ReferralRepository
import com.cheradip.ailanguagetutor.ui.components.CheradipTopBar
import com.cheradip.ailanguagetutor.ui.components.SubscriptionPlanPrice

@Composable
fun PaywallScreen(
    referralRepository: ReferralRepository,
    isLoggedIn: Boolean,
    onRequireLogin: () -> Unit,
    onSubscribed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val policy by referralRepository.policy.collectAsStateWithLifecycle()
    val referralBalance by referralRepository.balance.collectAsStateWithLifecycle()
    val config = uiState.config
    val activity = LocalContext.current.findActivity()

    Scaffold(
        modifier = modifier,
        topBar = {
            CheradipTopBar(
                title = "Upgrade to Keep Learning",
                subtitle = "30-day's trial · No ads · Offline · AI on Pro/Plus",
            )
        },
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {
        if (!isLoggedIn) {
            Text(
                "Sign in or Create an Account before Subscribing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (!uiState.billingReady) {
            Text(
                "Connect to Google Play to subscribe. Restore works after Play connects.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.loadingConfig) {
            CircularProgressIndicator()
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.billingPeriod == BillingPeriod.MONTHLY,
                onClick = { viewModel.selectBillingPeriod(BillingPeriod.MONTHLY) },
                label = { Text("Monthly") },
            )
            FilterChip(
                selected = uiState.billingPeriod == BillingPeriod.YEARLY,
                onClick = { viewModel.selectBillingPeriod(BillingPeriod.YEARLY) },
                label = { Text("Yearly") },
            )
        }

        val proPrice = remember(uiState.billingPeriod, uiState.slot1Result, uiState.slot2Result) {
            viewModel.priceDisplay(PaywallPlan.PRO, uiState)
        }
        val plusPrice = remember(uiState.billingPeriod, uiState.slot1Result, uiState.slot2Result) {
            viewModel.priceDisplay(PaywallPlan.PLUS, uiState)
        }
        PlanOption(
            title = "Pro",
            subtitle = "Modes 1–4 · AI · Cloud fallback",
            payablePrice = proPrice.payable,
            compareAtPrice = proPrice.compareAt,
            selected = uiState.selectedPlan == PaywallPlan.PRO,
            onSelect = { viewModel.selectPlan(PaywallPlan.PRO) },
        )
        PlanOption(
            title = "Plus",
            subtitle = "All modes including Mode 5 · Priority Features",
            payablePrice = plusPrice.payable,
            compareAtPrice = plusPrice.compareAt,
            selected = uiState.selectedPlan == PaywallPlan.PLUS,
            onSelect = { viewModel.selectPlan(PaywallPlan.PLUS) },
        )

        if (config?.showPromoSection == true) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Promo codes", style = MaterialTheme.typography.titleMedium)
            if (config.slot1.visible) {
                OutlinedTextField(
                    value = uiState.slot1Code,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(config.slot1.label ?: "Launch Promo") },
                    placeholder = {
                        if (uiState.slot1Code.isBlank()) {
                            Text("Applied automatically at Checkout")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                uiState.slot1Result?.let {
                    Text(
                        "${it.discountPercent}% off (${it.code})",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (config.slot2.visible) {
                OutlinedTextField(
                    value = uiState.slot2Code,
                    onValueChange = viewModel::updateSlot2,
                    label = { Text(config.slot2.label ?: "Additional promo") },
                    placeholder = { Text(config.slot2.placeholder ?: "Referrer or Code") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = viewModel::applySlot2,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Apply second code") }
                uiState.slot2Result?.let {
                    Text(
                        "${it.discountPercent}% off (${it.code})",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            uiState.promoError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }

        Text(
            text = policy.noticeText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )

        uiState.purchaseError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        if (isLoggedIn && referralBalance.availableUsd > 0.0) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = uiState.useReferralBalance,
                    onCheckedChange = viewModel::setUseReferralBalance,
                )
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text("Use referral balance", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Available: $${"%.2f".format(referralBalance.availableUsd)}" +
                            if (uiState.useReferralBalance) {
                                " · Applying $${"%.2f".format(viewModel.referralCreditToApply(uiState))}"
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (referralBalance.pendingUsd > 0.0) {
                        Text(
                            "Pending (clears after subscription month): $${"%.2f".format(referralBalance.pendingUsd)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (!isLoggedIn) {
                    onRequireLogin()
                    return@Button
                }
                activity?.let { viewModel.subscribe(it, onSubscribed) }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.purchaseInProgress && (activity != null || !isLoggedIn),
        ) {
            if (uiState.purchaseInProgress) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text(
                when {
                    !isLoggedIn -> "Sign in to Subscribe"
                    uiState.selectedPlan == PaywallPlan.PLUS -> "Subscribe to Plus via Google Play"
                    else -> "Subscribe to Pro via Google Play"
                },
            )
        }
        OutlinedButton(
            onClick = { viewModel.restorePurchases(onSubscribed) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            enabled = !uiState.purchaseInProgress,
        ) { Text("Restore purchases") }
    }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun PlanOption(
    title: String,
    subtitle: String,
    payablePrice: String,
    compareAtPrice: String?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                SubscriptionPlanPrice(
                    payablePrice = payablePrice,
                    compareAtPrice = compareAtPrice,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
