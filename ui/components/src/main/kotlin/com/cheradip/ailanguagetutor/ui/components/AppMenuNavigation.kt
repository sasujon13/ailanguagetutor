package com.cheradip.ailanguagetutor.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cheradip.ailanguagetutor.core.locale.appString

enum class AppMenuDestination {
    HOME,
    PRACTICE,
    LEARNING,
    LANGUAGES,
    GRAMMAR,
    REFERRAL,
    PAYWALL,
    MODE_SELECTION,
    PROFILE,
    SETTINGS,
    ADMIN,
    ADMIN_AI,
}

data class AppMenuNavigation(
    val isAdmin: Boolean = false,
    val onNavigate: (AppMenuDestination) -> Unit,
)

val LocalAppMenuNavigation = compositionLocalOf<AppMenuNavigation?> { null }

@Composable
fun CheradipAppMenuButton(
    modifier: Modifier = Modifier,
) {
    val navigation = LocalAppMenuNavigation.current ?: return
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        val menuTeal = MaterialTheme.colorScheme.primary
        IconButton(
            onClick = { expanded = true },
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = menuTeal,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "App menu",
                modifier = Modifier.size(30.dp),
                tint = menuTeal,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppMenuItem(appString("nav_home"), Icons.Default.Home) {
                expanded = false
                navigation.onNavigate(AppMenuDestination.HOME)
            }
            AppMenuItem(appString("nav_practice"), Icons.AutoMirrored.Filled.MenuBook) {
                expanded = false
                navigation.onNavigate(AppMenuDestination.PRACTICE)
            }
            AppMenuItem(appString("nav_learning"), Icons.Default.Refresh) {
                expanded = false
                navigation.onNavigate(AppMenuDestination.LEARNING)
            }
            AppMenuItem(appString("nav_languages"), Icons.Default.Public) {
                expanded = false
                navigation.onNavigate(AppMenuDestination.LANGUAGES)
            }
            AppMenuItem(appString("nav_grammar"), Icons.AutoMirrored.Filled.MenuBook) {
                expanded = false
                navigation.onNavigate(AppMenuDestination.GRAMMAR)
            }
            HorizontalDivider()
            AppMenuItem(appString("settings_referrals"), Icons.Default.Share) {
                expanded = false
                navigation.onNavigate(AppMenuDestination.REFERRAL)
            }
            AppMenuItem(appString("settings_subscription"), Icons.Default.Star) {
                expanded = false
                navigation.onNavigate(AppMenuDestination.PAYWALL)
            }
            AppMenuItem(appString("settings_ai_mode"), Icons.Default.Tune) {
                expanded = false
                navigation.onNavigate(AppMenuDestination.MODE_SELECTION)
            }
            HorizontalDivider()
            AppMenuItem(appString("nav_profile"), Icons.Default.Person) {
                expanded = false
                navigation.onNavigate(AppMenuDestination.PROFILE)
            }
            AppMenuItem(appString("nav_settings"), Icons.Default.Settings) {
                expanded = false
                navigation.onNavigate(AppMenuDestination.SETTINGS)
            }
            if (navigation.isAdmin) {
                HorizontalDivider()
                AppMenuItem(appString("settings_admin_console"), Icons.Default.AdminPanelSettings) {
                    expanded = false
                    navigation.onNavigate(AppMenuDestination.ADMIN)
                }
                AppMenuItem(appString("settings_admin_ai"), Icons.Default.Cloud) {
                    expanded = false
                    navigation.onNavigate(AppMenuDestination.ADMIN_AI)
                }
            }
        }
    }
}

@Composable
private fun AppMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val menuTeal = MaterialTheme.colorScheme.primary
    DropdownMenuItem(
        text = { Text(label, color = menuTeal) },
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null, tint = menuTeal) },
    )
}
