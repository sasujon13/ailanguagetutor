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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheradip.ailanguagetutor.core.billing.BillingPeriod
import com.cheradip.ailanguagetutor.core.billing.ReferralRepository

@Composable
fun PaywallScreen(
    referralRepository: ReferralRepository,
    userEmail: String?,
    userWhatsapp: String?,
    onSubscribed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val policy by referralRepository.policy.collectAsStateWithLifecycle()
    val config = uiState.config
    val activity = LocalContext.current.findActivity()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Upgrade to keep learning", style = MaterialTheme.typography.headlineSmall)
        Text(
            "30-day trial · No ads · Offline-first · Home AI on Pro/Plus",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
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

        val period = uiState.billingPeriod
        PlanOption(
            title = "Pro",
            subtitle = "Modes 1–4 · Home AI · Cloud fallback",
            playPrice = viewModel.displayPrice(PaywallPlan.PRO, period),
            fallbackActual = if (period == BillingPeriod.MONTHLY) "$2.00/mo" else "$20.00/yr",
            effectivePrice = if (period == BillingPeriod.MONTHLY) {
                "$${viewModel.effectiveMonthlyPrice()}/mo"
            } else {
                "$${viewModel.effectiveYearlyPrice()}/yr"
            },
            selected = uiState.selectedPlan == PaywallPlan.PRO,
            onSelect = { viewModel.selectPlan(PaywallPlan.PRO) },
        )
        PlanOption(
            title = "Plus",
            subtitle = "All modes including Mode 5 · Priority features",
            playPrice = viewModel.displayPrice(PaywallPlan.PLUS, period),
            fallbackActual = if (period == BillingPeriod.MONTHLY) "$5.00/mo" else "$50.00/yr",
            effectivePrice = if (period == BillingPeriod.MONTHLY) {
                "$${viewModel.effectiveMonthlyPrice()}/mo"
            } else {
                "$${viewModel.effectiveYearlyPrice()}/yr"
            },
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
                    label = { Text(config.slot1.label ?: "Launch promo") },
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
                    placeholder = { Text(config.slot2.placeholder ?: "Referrer or code") },
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

        OutlinedTextField(
            value = referralRepository.shareText(userEmail, userWhatsapp),
            onValueChange = {},
            readOnly = true,
            label = { Text("Your referral share text") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
        Text(
            text = policy.noticeText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )

        uiState.purchaseError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                activity?.let { viewModel.subscribe(it, onSubscribed) }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.purchaseInProgress && activity != null,
        ) {
            if (uiState.purchaseInProgress) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text(
                if (uiState.selectedPlan == PaywallPlan.PLUS) "Subscribe to Plus via Google Play"
                else "Subscribe to Pro via Google Play",
            )
        }
        OutlinedButton(
            onClick = { viewModel.restorePurchases(onSubscribed) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            enabled = !uiState.purchaseInProgress,
        ) { Text("Restore purchases") }
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
    playPrice: String?,
    fallbackActual: String,
    effectivePrice: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val priceLine = playPrice?.let { "$it (Play Store)" }
        ?: "$effectivePrice (est. · was $fallbackActual)"
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
                Text(priceLine, style = MaterialTheme.typography.bodyMedium)
                if (playPrice != null) {
                    Text(
                        "After promos: $effectivePrice",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
