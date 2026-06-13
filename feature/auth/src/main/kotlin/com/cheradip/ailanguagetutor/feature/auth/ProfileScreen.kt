package com.cheradip.ailanguagetutor.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cheradip.ailanguagetutor.core.auth.AuthRepository
import com.cheradip.ailanguagetutor.core.auth.AuthUser
import com.cheradip.ailanguagetutor.core.locale.appString
import com.cheradip.ailanguagetutor.ui.components.CheradipScrollScreen
import com.cheradip.ailanguagetutor.ui.components.IconTextButton
import com.cheradip.ailanguagetutor.ui.components.SectionHeader
import com.cheradip.ailanguagetutor.ui.components.SettingsNavRow
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    currentUser: AuthUser?,
    authRepository: AuthRepository,
    onNavigateLogin: () -> Unit,
    onNavigateSignUp: () -> Unit,
    onNavigateReferral: () -> Unit,
    onNavigatePaywall: () -> Unit,
    onNavigateUserManual: () -> Unit,
    onLoggedOut: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val loggedIn = currentUser != null

    CheradipScrollScreen(
        modifier = modifier,
        title = appString("profile_title"),
        subtitle = if (loggedIn) appString("profile_dashboard_subtitle") else appString("profile_guest_subtitle"),
    ) {
        if (loggedIn) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            appString("profile_dashboard"),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            currentUser.email,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        currentUser.whatsapp?.takeIf { it.isNotBlank() }?.let { phone ->
                            Text(
                                phone,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Text(
                            "${appString("profile_role")}: ${currentUser.role.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(title = appString("profile_account_actions"))
            }
            item {
                SettingsNavRow(appString("settings_referrals"), Icons.Default.Share, onNavigateReferral)
            }
            item {
                SettingsNavRow(appString("settings_subscription"), Icons.Default.Star, onNavigatePaywall)
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(title = appString("profile_help"))
            }
            item {
                SettingsNavRow(appString("profile_user_manual"), Icons.Default.MenuBook, onNavigateUserManual)
            }
            item {
                IconTextButton(
                    label = appString("profile_logout"),
                    icon = Icons.AutoMirrored.Filled.Logout,
                    onClick = {
                        scope.launch {
                            authRepository.logout()
                            onLoggedOut()
                        }
                    },
                    filled = false,
                )
            }
        } else {
            item {
                Text(
                    appString("profile_sign_in_prompt"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                IconTextButton(
                    label = appString("profile_login"),
                    icon = Icons.Default.Login,
                    onClick = onNavigateLogin,
                )
            }
            item {
                IconTextButton(
                    label = appString("profile_sign_up"),
                    icon = Icons.Default.PersonAdd,
                    onClick = onNavigateSignUp,
                    filled = false,
                )
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(title = appString("profile_guest_browse"))
            }
            item {
                SettingsNavRow(
                    appString("settings_referrals"),
                    Icons.Default.Share,
                    onNavigateReferral,
                    subtitle = appString("profile_sign_in_for_referrals"),
                )
            }
            item {
                SettingsNavRow(
                    appString("settings_subscription"),
                    Icons.Default.Star,
                    onNavigatePaywall,
                    subtitle = appString("profile_sign_in_for_subscription"),
                )
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(title = appString("profile_help"))
            }
            item {
                SettingsNavRow(appString("profile_user_manual"), Icons.Default.MenuBook, onNavigateUserManual)
            }
        }
        item {
            Text(
                appString("profile_footer"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
