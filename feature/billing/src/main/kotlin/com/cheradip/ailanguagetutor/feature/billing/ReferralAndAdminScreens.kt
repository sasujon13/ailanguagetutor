package com.cheradip.ailanguagetutor.feature.billing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheradip.ailanguagetutor.core.billing.BillingRepository
import com.cheradip.ailanguagetutor.core.billing.PromoRepository
import com.cheradip.ailanguagetutor.core.billing.PromoValidation
import com.cheradip.ailanguagetutor.core.billing.ReferralRepository

@Composable
fun ReferralScreen(
    referralRepository: ReferralRepository,
    userEmail: String?,
    userWhatsapp: String?,
    modifier: Modifier = Modifier,
) {
    val policy by referralRepository.policy.collectAsStateWithLifecycle()
    val balance by referralRepository.balance.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text("Referrals & credits", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Earn ${policy.commissionPercent}% when friends subscribe using your reference.",
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Balance: $${"%.2f".format(balance.balanceUsd)}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            referralRepository.shareText(userEmail, userWhatsapp),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            policy.noticeText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
