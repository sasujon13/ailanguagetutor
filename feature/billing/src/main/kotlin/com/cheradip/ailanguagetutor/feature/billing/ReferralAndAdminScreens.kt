package com.cheradip.ailanguagetutor.feature.billing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheradip.ailanguagetutor.core.auth.AuthUser
import com.cheradip.ailanguagetutor.core.billing.ReferralRepository
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import com.cheradip.ailanguagetutor.ui.components.CheradipDropdown
import com.cheradip.ailanguagetutor.ui.components.CheradipScreenEdgePadding
import com.cheradip.ailanguagetutor.ui.components.IconTextButton
import kotlinx.coroutines.launch

private enum class WithdrawMethod(val apiValue: String, val label: String) {
    PAYPAL("paypal", "PayPal"),
    BANK("bank_transfer", "Bank transfer"),
    MOBILE("mobile_wallet", "Mobile wallet"),
}

@Composable
fun ReferralScreen(
    referralRepository: ReferralRepository,
    currentUser: AuthUser?,
    userEmail: String?,
    userWhatsapp: String?,
    onNavigateLogin: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val policy by referralRepository.policy.collectAsStateWithLifecycle()
    val balance by referralRepository.balance.collectAsStateWithLifecycle()
    var showWithdrawForm by remember { mutableStateOf(false) }
    var withdrawMethod by remember { mutableStateOf(WithdrawMethod.PAYPAL) }
    var payoutDetails by remember { mutableStateOf("") }
    var withdrawMessage by remember { mutableStateOf<String?>(null) }
    var withdrawError by remember { mutableStateOf<String?>(null) }
    var withdrawing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isLoggedIn = currentUser != null

    Column(modifier = modifier.fillMaxSize().padding(CheradipScreenEdgePadding)) {
        Text("Referrals & credits", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Earn ${policy.commissionPercent}% when friends subscribe using your reference.",
            modifier = Modifier.padding(top = 8.dp),
        )

        if (!isLoggedIn) {
            Text(
                "Sign in to view your referral balance and withdraw credits.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
            IconTextButton(
                label = "Sign in",
                icon = Icons.Default.Login,
                onClick = onNavigateLogin,
                modifier = Modifier.padding(top = 12.dp),
            )
            return@Column
        }

        Text(
            "Referral balance: $${"%.2f".format(balance.balanceUsd)}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "Lifetime earned: $${"%.2f".format(balance.lifetimeEarnedUsd)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Minimum withdrawal: $${"%.0f".format(balance.minWithdrawalUsd)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (balance.withdrawable) {
            if (!showWithdrawForm) {
                Button(
                    onClick = { showWithdrawForm = true },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text("Withdraw credits")
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Withdrawal request", style = MaterialTheme.typography.titleSmall)
                CheradipDropdown(
                    label = "Payout method",
                    options = WithdrawMethod.entries.toList(),
                    selected = withdrawMethod,
                    onSelected = { withdrawMethod = it },
                    optionLabel = { it.label },
                )
                OutlinedTextField(
                    value = payoutDetails,
                    onValueChange = { payoutDetails = it },
                    label = {
                        Text(
                            when (withdrawMethod) {
                                WithdrawMethod.PAYPAL -> "PayPal email *"
                                WithdrawMethod.BANK -> "Bank account details *"
                                WithdrawMethod.MOBILE -> "Mobile wallet number *"
                            },
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    singleLine = false,
                    minLines = 2,
                )
                withdrawError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                withdrawMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                }
                Button(
                    onClick = {
                        if (payoutDetails.isBlank()) {
                            withdrawError = "Enter payout details"
                            return@Button
                        }
                        scope.launch {
                            withdrawing = true
                            withdrawError = null
                            referralRepository.withdraw(withdrawMethod.apiValue, payoutDetails.trim())
                                .onSuccess { msg ->
                                    withdrawMessage = msg
                                    showWithdrawForm = false
                                    payoutDetails = ""
                                }
                                .onFailure { withdrawError = it.message ?: "Withdrawal failed" }
                            withdrawing = false
                        }
                    },
                    enabled = !withdrawing,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    if (withdrawing) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text("Submit withdrawal ($${"%.2f".format(balance.balanceUsd)})")
                }
                OutlinedButton(
                    onClick = { showWithdrawForm = false },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Cancel") }
            }
        } else {
            Text(
                "Withdrawal unlocks at $${"%.0f".format(balance.minWithdrawalUsd)} in credits.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Text(
            referralRepository.shareText(userEmail, userWhatsapp),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            policy.noticeText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
