package com.cheradip.ailanguagetutor.feature.help

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.cheradip.ailanguagetutor.core.locale.appString
import com.cheradip.ailanguagetutor.ui.components.CheradipScrollScreen
import com.cheradip.ailanguagetutor.ui.components.SectionHeader
import com.cheradip.ailanguagetutor.ui.components.SettingsNavRow

@Composable
fun UserManualHubScreen(
    isAdmin: Boolean,
    onOpenManual: (ManualType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val manuals = ManualType.visibleToUser(isAdmin)

    CheradipScrollScreen(
        modifier = modifier,
        title = appString("manual_hub_title"),
        subtitle = if (isAdmin) {
            appString("manual_hub_subtitle_admin")
        } else {
            appString("manual_hub_subtitle_user")
        },
    ) {
        item {
            Text(
                appString("manual_hub_intro"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            SectionHeader(title = appString("manual_hub_available"))
        }
        manuals.forEach { manual ->
            item {
                SettingsNavRow(
                    label = appString(manual.titleKey),
                    icon = when (manual) {
                        ManualType.USER -> Icons.Default.MenuBook
                        ManualType.ADMIN -> Icons.Default.AdminPanelSettings
                        ManualType.DEVELOPER -> Icons.Default.Code
                    },
                    onClick = { onOpenManual(manual) },
                    subtitle = appString(manual.subtitleKey),
                )
            }
        }
    }
}
