package com.cheradip.ailanguagetutor.feature.billing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheradip.ailanguagetutor.core.auth.AuthUser
import com.cheradip.ailanguagetutor.core.billing.ReferralRepository
import com.cheradip.ailanguagetutor.ui.components.CheradipDropdown
import com.cheradip.ailanguagetutor.ui.components.CheradipScrollScreen
import com.cheradip.ailanguagetutor.ui.components.IconTextButton
import com.cheradip.ailanguagetutor.ui.components.SupportActions
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
    val context = LocalContext.current
    val isLoggedIn = currentUser != null
    val defaultShareMessage = remember(userEmail) {
        referralRepository.defaultShareMessage(userEmail)
    }
    var shareDraft by remember { mutableStateOf(defaultShareMessage) }
    LaunchedEffect(defaultShareMessage) {
        if (shareDraft.isBlank() ||
            shareDraft.contains("[your email here]", ignoreCase = true) ||
            shareDraft == referralRepository.defaultShareMessage(null)
        ) {
            shareDraft = defaultShareMessage
        }
    }

    CheradipScrollScreen(
        modifier = modifier,
        title = "Referrals & credits",
        subtitle = "Earn ${policy.commissionPercent}% when friends subscribe using your reference.",
    ) {
        if (!isLoggedIn) {
            item {
                Text(
                    "Sign in to view your referral balance and withdraw credits.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                IconTextButton(
                    label = "Sign in",
                    icon = Icons.Default.Login,
                    onClick = onNavigateLogin,
                )
            }
        } else {
            item {
                Text(
                    "Referral balance: $${"%.2f".format(balance.balanceUsd)}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                Text(
                    "Lifetime earned: $${"%.2f".format(balance.lifetimeEarnedUsd)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    "Minimum withdrawal: $${"%.0f".format(balance.minWithdrawalUsd)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (balance.withdrawable) {
                if (!showWithdrawForm) {
                    item {
                        Button(onClick = { showWithdrawForm = true }) {
                            Text("Withdraw credits")
                        }
                    }
                } else {
                    item {
                        Text("Withdrawal request", style = MaterialTheme.typography.titleSmall)
                    }
                    item {
                        CheradipDropdown(
                            label = "Payout method",
                            options = WithdrawMethod.entries.toList(),
                            selected = withdrawMethod,
                            onSelected = { withdrawMethod = it },
                            optionLabel = { it.label },
                        )
                    }
                    item {
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
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            minLines = 2,
                        )
                    }
                    withdrawError?.let { err ->
                        item {
                            Text(err, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    withdrawMessage?.let { msg ->
                        item {
                            Text(msg, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    item {
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
                        ) {
                            if (withdrawing) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                            Text("Submit withdrawal ($${"%.2f".format(balance.balanceUsd)})")
                        }
                    }
                    item {
                        OutlinedButton(onClick = { showWithdrawForm = false }) {
                            Text("Cancel")
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "Withdrawal unlocks at $${"%.0f".format(balance.minWithdrawalUsd)} in credits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Text(
                    policy.noticeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        }
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = shareDraft,
                    onValueChange = { shareDraft = it },
                    label = { Text("Your Referral Share Text") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                    maxLines = 12,
                )
                if (!isLoggedIn) {
                    Text(
                        "Sign in to replace [your email here] with your referral email.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                FilledIconButton(
                    onClick = {
                        SupportActions.sharePlainText(
                            context = context,
                            text = shareDraft.ifBlank { defaultShareMessage },
                            chooserTitle = "Share referral message",
                        )
                    },
                    modifier = Modifier.size(80.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share referral message",
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
        item {
            Text(
                "Tap to share via Facebook, Messenger, WhatsApp, SMS, LinkedIn, X, email, and other apps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
        }
    }
}
