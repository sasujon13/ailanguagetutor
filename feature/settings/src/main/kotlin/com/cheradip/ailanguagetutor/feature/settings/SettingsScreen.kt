package com.cheradip.ailanguagetutor.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheradip.ailanguagetutor.core.audio.PronunciationEngine
import com.cheradip.ailanguagetutor.core.audio.TeenVoiceGender
import com.cheradip.ailanguagetutor.core.locale.LocalAppStrings
import com.cheradip.ailanguagetutor.core.locale.appString
import com.cheradip.ailanguagetutor.core.locale.localizedString
import com.cheradip.ailanguagetutor.core.model.DeviceLocaleHints
import com.cheradip.ailanguagetutor.core.model.GrammarDepth
import com.cheradip.ailanguagetutor.core.model.LanguageCatalogOrder
import com.cheradip.ailanguagetutor.ui.components.CheradipDropdown
import com.cheradip.ailanguagetutor.ui.components.CheradipScrollScreen
import com.cheradip.ailanguagetutor.ui.components.SearchableLanguageDropdown
import com.cheradip.ailanguagetutor.ui.components.SectionHeader
import com.cheradip.ailanguagetutor.ui.components.SettingsNavRow

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateReferral: () -> Unit = {},
    onNavigatePaywall: () -> Unit = {},
    onNavigateAdmin: () -> Unit = {},
    onNavigateAdminAi: () -> Unit = {},
    onNavigateModeSelection: () -> Unit = {},
    isAdmin: Boolean = false,
    pronunciationEngine: PronunciationEngine? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val grammarDepth by viewModel.grammarDepth.collectAsStateWithLifecycle()
    val localeUi by viewModel.localeUi.collectAsStateWithLifecycle()
    val savedGender by viewModel.voiceGender.collectAsStateWithLifecycle()
    val ttsPreviewLanguage by viewModel.ttsPreviewLanguage.collectAsStateWithLifecycle()
    val localeHints = remember { DeviceLocaleHints.current() }
    val appLanguageOptions = remember(localeUi.allowedLanguages) {
        LanguageCatalogOrder.sort(localeUi.allowedLanguages, localeHints)
    }
    val selectedAppLang = remember(localeUi.locale, appLanguageOptions) {
        appLanguageOptions.firstOrNull { it.code.equals(localeUi.locale.languageCode, ignoreCase = true) }
            ?: appLanguageOptions.firstOrNull()
    }
    val strings = LocalAppStrings.current

    CheradipScrollScreen(
        modifier = modifier,
        title = appString("settings_title"),
        subtitle = appString("settings_subtitle"),
    ) {
        item {
            SectionHeader(title = appString("app_language"))
        }
        item {
            if (appLanguageOptions.isEmpty()) {
                Text(
                    appString("app_language_active_only_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SearchableLanguageDropdown(
                    languages = appLanguageOptions,
                    selected = selectedAppLang,
                    onSelected = viewModel::selectAppLanguage,
                    label = appString("app_language"),
                    searchPlaceholder = appString("search_languages"),
                    localeHints = localeHints,
                )
            }
        }
        if (localeUi.isUpdating) {
            item {
                Text(
                    appString("updating_language"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                CircularProgressIndicator(modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(title = appString("section_teen_voice"))
        }
        item {
            CheradipDropdown(
                label = appString("voice_gender"),
                options = TeenVoiceGender.entries.toList(),
                selected = savedGender,
                onSelected = { option ->
                    viewModel.saveVoiceGender(option)
                    pronunciationEngine?.setGender(option)
                    pronunciationEngine?.preview(ttsPreviewLanguage)
                },
                optionLabel = {
                    if (it == TeenVoiceGender.MALE) localizedString("voice_teen_male", strings)
                    else localizedString("voice_teen_female", strings)
                },
                leadingIcon = Icons.Default.Person,
            )
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(title = appString("section_grammar"))
        }
        item {
            CheradipDropdown(
                label = appString("grammar_detail_level"),
                options = GrammarDepth.entries.toList(),
                selected = grammarDepth,
                onSelected = viewModel::setGrammarDepth,
                optionLabel = { it.label },
                leadingIcon = Icons.Default.MenuBook,
            )
        }
        item {
            Text(
                text = grammarDepth.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(title = appString("section_learning_ai"))
        }
        item {
            SettingsNavRow(appString("settings_ai_mode"), Icons.Default.Tune, onNavigateModeSelection, subtitle = appString("settings_ai_mode_sub"))
        }
        item {
            SettingsNavRow(appString("settings_subscription"), Icons.Default.Star, onNavigatePaywall)
        }
        item {
            SettingsNavRow(appString("settings_referrals"), Icons.Default.Share, onNavigateReferral)
        }
        if (isAdmin) {
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(title = appString("section_admin"))
            }
            item {
                SettingsNavRow(appString("settings_admin_console"), Icons.Default.AdminPanelSettings, onNavigateAdmin)
            }
            item {
                SettingsNavRow(appString("settings_admin_ai"), Icons.Default.Cloud, onNavigateAdminAi, subtitle = appString("settings_admin_ai_sub"))
            }
        }
        item {
            Text(
                appString("settings_footer"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
