package com.cheradip.ailanguagetutor.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cheradip.ailanguagetutor.core.audio.PronunciationEngine
import com.cheradip.ailanguagetutor.core.audio.TeenVoiceGender
import com.cheradip.ailanguagetutor.ui.components.CheradipDropdown
import com.cheradip.ailanguagetutor.ui.components.CheradipScrollScreen
import com.cheradip.ailanguagetutor.ui.components.SectionHeader
import com.cheradip.ailanguagetutor.ui.components.SettingsNavRow

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateReferral: () -> Unit = {},
    onNavigatePaywall: () -> Unit = {},
    onNavigateAdmin: () -> Unit = {},
    onNavigateAdminAi: () -> Unit = {},
    onNavigateLogin: () -> Unit = {},
    onNavigateModeSelection: () -> Unit = {},
    isAdmin: Boolean = false,
    pronunciationEngine: PronunciationEngine? = null,
) {
    var gender by remember { mutableStateOf(TeenVoiceGender.FEMALE) }

    CheradipScrollScreen(
        modifier = modifier,
        title = "Settings",
        subtitle = "Voice, AI, account",
    ) {
        item {
            SectionHeader(title = "Teen tutor voice")
        }
        item {
            CheradipDropdown(
                label = "Voice gender",
                options = TeenVoiceGender.entries.toList(),
                selected = gender,
                onSelected = { option ->
                    gender = option
                    pronunciationEngine?.setGender(option)
                    pronunciationEngine?.speak("Hello, I am your language tutor.")
                },
                optionLabel = { if (it == TeenVoiceGender.MALE) "Teen male" else "Teen female" },
                leadingIcon = Icons.Default.Person,
            )
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(title = "Learning & AI")
        }
        item {
            SettingsNavRow("AI mode & intent", Icons.Default.Tune, onNavigateModeSelection, subtitle = "Answer vs Translation, engine mode")
        }
        item {
            SettingsNavRow("Subscription & Paywall", Icons.Default.Star, onNavigatePaywall)
        }
        item {
            SettingsNavRow("Referrals & credits", Icons.Default.Share, onNavigateReferral)
        }
        item {
            SettingsNavRow("Account / Login", Icons.Default.Login, onNavigateLogin)
        }
        if (isAdmin) {
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(title = "Admin")
            }
            item {
                SettingsNavRow("Admin console", Icons.Default.AdminPanelSettings, onNavigateAdmin)
            }
            item {
                SettingsNavRow("AI API status", Icons.Default.Cloud, onNavigateAdminAi, subtitle = "Providers, quotas, routing")
            }
        }
        item {
            Text(
                "243 languages · Offline packs · Local AI when connected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
