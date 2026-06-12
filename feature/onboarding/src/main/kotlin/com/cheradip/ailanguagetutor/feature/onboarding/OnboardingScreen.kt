package com.cheradip.ailanguagetutor.feature.onboarding

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.audio.PracticeLanguageRepository
import com.cheradip.ailanguagetutor.core.audio.PronunciationEngine
import com.cheradip.ailanguagetutor.core.audio.TeenVoiceGender
import com.cheradip.ailanguagetutor.core.audio.VoicePreferenceRepository
import com.cheradip.ailanguagetutor.core.locale.AppLocaleManager
import com.cheradip.ailanguagetutor.core.locale.AppStrings
import com.cheradip.ailanguagetutor.core.locale.LocalAppStrings
import com.cheradip.ailanguagetutor.core.locale.appString
import com.cheradip.ailanguagetutor.core.locale.localizedString
import com.cheradip.ailanguagetutor.core.model.DeviceLocaleHints
import com.cheradip.ailanguagetutor.core.model.LanguageCatalogEntry
import com.cheradip.ailanguagetutor.core.model.LanguageCatalogOrder
import com.cheradip.ailanguagetutor.core.pack.LanguageCatalogRepository
import com.cheradip.ailanguagetutor.core.pack.LanguagePackRepository
import com.cheradip.ailanguagetutor.ui.components.CheradipDropdown
import com.cheradip.ailanguagetutor.ui.components.CheradipScreenEdgePadding
import com.cheradip.ailanguagetutor.ui.components.CheradipProgressOverlay
import com.cheradip.ailanguagetutor.ui.components.IconTextButton
import com.cheradip.ailanguagetutor.ui.components.SearchableLanguageDropdown
import com.cheradip.ailanguagetutor.ui.components.SearchableLanguageMultiSelect
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding")

data class PackDownloadUiState(
    val isDownloading: Boolean = false,
    val progressPercent: Int = 0,
    val messageKey: String = "onboarding_downloading",
    val messageArg: String? = null,
    val error: String? = null,
)

@Singleton
class OnboardingPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyComplete = booleanPreferencesKey("onboarding_complete")
    private val keyLangs = stringPreferencesKey("selected_langs")

    suspend fun isComplete(): Boolean =
        context.onboardingDataStore.data.first()[keyComplete] == true

    suspend fun markComplete(selectedLangs: List<String>) {
        context.onboardingDataStore.edit {
            it[keyComplete] = true
            it[keyLangs] = selectedLangs.joinToString(",")
        }
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val catalogRepository: LanguageCatalogRepository,
    private val languagePackRepository: LanguagePackRepository,
    private val voicePreferenceRepository: VoicePreferenceRepository,
    private val practiceLanguageRepository: PracticeLanguageRepository,
    private val pronunciationEngine: PronunciationEngine,
    private val onboardingPreferences: OnboardingPreferences,
    private val appLocaleManager: AppLocaleManager,
) : ViewModel() {
    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step.asStateFlow()

    private val _selectedStudyLangs = MutableStateFlow<List<String>>(emptyList())
    val selectedStudyLangs: StateFlow<List<String>> = _selectedStudyLangs.asStateFlow()

    private val _allLanguages = MutableStateFlow<List<LanguageCatalogEntry>>(emptyList())
    val allLanguages: StateFlow<List<LanguageCatalogEntry>> = _allLanguages.asStateFlow()

    private val _appLanguage = MutableStateFlow<LanguageCatalogEntry?>(null)
    val appLanguage: StateFlow<LanguageCatalogEntry?> = _appLanguage.asStateFlow()

    private val _isTranslatingApp = MutableStateFlow(false)
    val isTranslatingApp: StateFlow<Boolean> = _isTranslatingApp.asStateFlow()

    private val _downloadState = MutableStateFlow(PackDownloadUiState())
    val downloadState: StateFlow<PackDownloadUiState> = _downloadState.asStateFlow()

    val localeHints: LanguageCatalogOrder.Hints = DeviceLocaleHints.current()

    init {
        pronunciationEngine.init()
        viewModelScope.launch {
            val all = LanguageCatalogOrder.sort(catalogRepository.getAll(), localeHints)
            _allLanguages.value = all
            _appLanguage.value = LanguageCatalogOrder.findEnglish(all)

            val region = LanguageCatalogOrder.findRegionLanguage(all, localeHints)
            if (region != null) {
                _selectedStudyLangs.value = listOf(region.code)
            }
            syncAppLanguageToStudyLangs()
        }
    }

    fun nextStep() = _step.update { it + 1 }

    fun setAppLanguage(entry: LanguageCatalogEntry) {
        val studyCodes = _selectedStudyLangs.value.map { it.lowercase() }
        if (studyCodes.isNotEmpty() && entry.code.lowercase() !in studyCodes) return
        _appLanguage.value = entry
        viewModelScope.launch {
            _isTranslatingApp.value = true
            appLocaleManager.applyLanguage(entry)
            _isTranslatingApp.value = false
        }
    }

    fun toggleStudyLang(code: String) {
        _selectedStudyLangs.update { current ->
            val list = current.toMutableList()
            if (list.contains(code)) list.remove(code)
            else if (list.size < LanguagePackRepository.MAX_ACTIVE_PACKS) list.add(code)
            list
        }
        syncAppLanguageToStudyLangs()
    }

    private fun syncAppLanguageToStudyLangs() {
        val study = _selectedStudyLangs.value.map { it.lowercase() }
        if (study.isEmpty()) return
        val current = _appLanguage.value?.code?.lowercase()
        if (current != null && current in study) return
        val pickCode = study.firstOrNull { it == AppStrings.DEFAULT_LANG } ?: study.first()
        _allLanguages.value.firstOrNull { it.code.equals(pickCode, ignoreCase = true) }?.let { entry ->
            setAppLanguage(entry)
        }
    }

    fun setVoice(gender: TeenVoiceGender) {
        viewModelScope.launch {
            voicePreferenceRepository.setGender(gender)
            pronunciationEngine.setGender(gender)
            val previewLang = _selectedStudyLangs.value.firstOrNull() ?: "en"
            pronunciationEngine.preview(previewLang)
        }
    }

    fun downloadPacks(onDone: () -> Unit) {
        val langs = _selectedStudyLangs.value
        if (langs.isEmpty() || _downloadState.value.isDownloading) return

        viewModelScope.launch {
            _downloadState.value = PackDownloadUiState(isDownloading = true, progressPercent = 10)

            val fakeProgressJob = launchFakeDownloadProgress()

            var failed = false
            langs.forEachIndexed { index, code ->
                _downloadState.update {
                    it.copy(
                        messageKey = "onboarding_downloading_lang",
                        messageArg = code.uppercase(),
                        progressPercent = maxOf(it.progressPercent, packProgressFloor(index, langs.size)),
                    )
                }
                val result = languagePackRepository.downloadAndActivate(code)
                if (result.isFailure) {
                    fakeProgressJob.cancel()
                    failed = true
                    _downloadState.update {
                        it.copy(
                            isDownloading = false,
                            error = result.exceptionOrNull()?.message,
                        )
                    }
                    return@launch
                }
                _downloadState.update {
                    it.copy(progressPercent = maxOf(it.progressPercent, packProgressCeiling(index, langs.size)))
                }
            }

            fakeProgressJob.cancel()
            if (!failed) {
                _downloadState.update {
                    it.copy(
                        progressPercent = 100,
                        messageKey = "onboarding_download_complete",
                        messageArg = null,
                    )
                }
                delay(700)
                practiceLanguageRepository.ensureDefaults(langs)
                onboardingPreferences.markComplete(langs)
                _downloadState.value = PackDownloadUiState()
                onDone()
            }
        }
    }

    private fun CoroutineScope.launchFakeDownloadProgress(): Job = launch {
        var percent = 10
        while (isActive && percent < 99) {
            delay(500)
            percent = fakeDownloadProgressNext(percent)
            _downloadState.update { state ->
                if (!state.isDownloading) state
                else state.copy(progressPercent = maxOf(state.progressPercent, percent))
            }
        }
    }

    private fun fakeDownloadProgressNext(current: Int): Int = when {
        current < 50 -> minOf(50, current + 5)
        current < 60 -> minOf(60, current + 4)
        current < 70 -> minOf(70, current + 3)
        current < 80 -> minOf(80, current + 2)
        else -> minOf(99, current + 1)
    }

    private fun packProgressFloor(completedIndex: Int, total: Int): Int {
        if (total <= 0) return 10
        return maxOf(10, (completedIndex * 100) / total)
    }

    private fun packProgressCeiling(completedIndex: Int, total: Int): Int {
        if (total <= 0) return 99
        val done = completedIndex + 1
        return minOf(99, maxOf(10, (done * 100) / total))
    }
}

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val selectedStudy by viewModel.selectedStudyLangs.collectAsStateWithLifecycle()
    val allLangs by viewModel.allLanguages.collectAsStateWithLifecycle()
    val appLang by viewModel.appLanguage.collectAsStateWithLifecycle()
    val isTranslatingApp by viewModel.isTranslatingApp.collectAsStateWithLifecycle()
    val localeHints = viewModel.localeHints
    var voiceGender by remember { mutableStateOf(TeenVoiceGender.FEMALE) }
    val strings = LocalAppStrings.current
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(CheradipScreenEdgePadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        when (step) {
            0 -> {
                Text(appString("onboarding_welcome"), style = MaterialTheme.typography.headlineSmall)
                Text(
                    appString("onboarding_tagline"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    appString("onboarding_intro"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { viewModel.nextStep() }) { Text(appString("continue")) }
            }
            1 -> {
                Text(appString("onboarding_choose_langs"), style = MaterialTheme.typography.titleLarge)
                Text(
                    appString("onboarding_langs_help"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(appString("onboarding_study_packs"), style = MaterialTheme.typography.titleMedium)
                Text(
                    appString("onboarding_study_packs_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SearchableLanguageMultiSelect(
                    languages = allLangs,
                    selectedCodes = selectedStudy.toSet(),
                    onToggle = { lang -> viewModel.toggleStudyLang(lang.code) },
                    maxSelection = LanguagePackRepository.MAX_ACTIVE_PACKS,
                    localeHints = localeHints,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(appString("app_language"), style = MaterialTheme.typography.titleMedium)
                Text(
                    appString("onboarding_app_language_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val appLangOptions = remember(allLangs, selectedStudy) {
                    val codes = selectedStudy.map { it.lowercase() }.toSet()
                    allLangs.filter { it.code.lowercase() in codes }
                }
                SearchableLanguageDropdown(
                    languages = appLangOptions,
                    selected = appLang,
                    onSelected = viewModel::setAppLanguage,
                    label = appString("app_language"),
                    searchPlaceholder = appString("search_languages"),
                    localeHints = localeHints,
                )
                if (isTranslatingApp) {
                    CircularProgressIndicator()
                    Text(
                        appString("updating_language"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Button(
                    onClick = { viewModel.nextStep() },
                    enabled = selectedStudy.isNotEmpty() && appLang != null && !isTranslatingApp,
                ) { Text(appString("next")) }
            }
            2 -> {
                Text(appString("onboarding_voice_title"), style = MaterialTheme.typography.titleLarge)
                CheradipDropdown(
                    label = appString("onboarding_voice_label"),
                    options = TeenVoiceGender.entries.toList(),
                    selected = voiceGender,
                    onSelected = { gender ->
                        voiceGender = gender
                        viewModel.setVoice(gender)
                    },
                    optionLabel = {
                        if (it == TeenVoiceGender.MALE) localizedString("voice_teen_male", strings)
                        else localizedString("voice_teen_female", strings)
                    },
                    leadingIcon = Icons.Default.Person,
                )
                Button(onClick = { viewModel.nextStep() }) { Text(appString("next")) }
            }
            else -> {
                Text(appString("onboarding_finish_title"), style = MaterialTheme.typography.titleLarge)
                Text(
                    "${appString("onboarding_finish_app")} ${appLang?.name ?: appString("english_us")}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                selectedStudy.forEach { code ->
                    val name = allLangs.firstOrNull { it.code == code }?.name ?: code
                    Text("· $name", style = MaterialTheme.typography.bodyMedium)
                }
                IconTextButton(
                    label = appString("onboarding_download_start"),
                    icon = Icons.Default.Download,
                    onClick = { viewModel.downloadPacks(onComplete) },
                    enabled = !downloadState.isDownloading,
                )
                downloadState.error?.let { err ->
                    Text(
                        text = err.ifBlank { appString("onboarding_download_failed") },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        }

        if (downloadState.isDownloading) {
            val message = downloadState.messageArg?.let { arg ->
                AppStrings.format(downloadState.messageKey, strings, arg)
            } ?: appString(downloadState.messageKey)
            CheradipProgressOverlay(
                progressPercent = downloadState.progressPercent,
                message = message,
            )
        }
    }
}
