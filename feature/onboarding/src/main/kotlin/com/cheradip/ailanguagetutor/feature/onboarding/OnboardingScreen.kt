package com.cheradip.ailanguagetutor.feature.onboarding

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.cheradip.ailanguagetutor.core.audio.PronunciationEngine
import com.cheradip.ailanguagetutor.core.audio.TeenVoiceGender
import com.cheradip.ailanguagetutor.core.audio.VoicePreferenceRepository
import com.cheradip.ailanguagetutor.core.model.LanguageCatalogEntry
import com.cheradip.ailanguagetutor.core.pack.LanguageCatalogRepository
import com.cheradip.ailanguagetutor.core.pack.LanguagePackRepository
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import com.cheradip.ailanguagetutor.ui.components.CheradipDropdown
import com.cheradip.ailanguagetutor.ui.components.IconTextButton
import com.cheradip.ailanguagetutor.ui.components.SearchableLanguageMultiSelect
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding")

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
    private val pronunciationEngine: PronunciationEngine,
    private val onboardingPreferences: OnboardingPreferences,
) : ViewModel() {
    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step.asStateFlow()

    private val _selectedLangs = MutableStateFlow<List<String>>(emptyList())
    val selectedLangs: StateFlow<List<String>> = _selectedLangs.asStateFlow()

    private val _languages = MutableStateFlow<List<LanguageCatalogEntry>>(emptyList())
    val languages: StateFlow<List<LanguageCatalogEntry>> = _languages.asStateFlow()

    init {
        pronunciationEngine.init()
        viewModelScope.launch {
            _languages.value = catalogRepository.getAll()
        }
    }

    fun nextStep() = _step.update { it + 1 }

    fun toggleLang(code: String) {
        _selectedLangs.update { current ->
            val list = current.toMutableList()
            if (list.contains(code)) list.remove(code)
            else if (list.size < 3) list.add(code)
            list
        }
    }

    fun setVoice(gender: TeenVoiceGender) {
        viewModelScope.launch {
            voicePreferenceRepository.setGender(gender)
            pronunciationEngine.setGender(gender)
            pronunciationEngine.speak("Hello, I am your language tutor.")
        }
    }

    fun downloadPacks(onDone: () -> Unit) {
        viewModelScope.launch {
            _selectedLangs.value.forEach { languagePackRepository.downloadAndActivate(it) }
            onboardingPreferences.markComplete(_selectedLangs.value)
            onDone()
        }
    }
}

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val selected by viewModel.selectedLangs.collectAsStateWithLifecycle()
    val langs by viewModel.languages.collectAsStateWithLifecycle()
    var voiceGender by remember { mutableStateOf(TeenVoiceGender.FEMALE) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (step) {
            0 -> {
                Text("Welcome to AI Language Tutor", style = MaterialTheme.typography.headlineSmall)
                Text("243 languages · Scan · Read · Learn offline")
                Button(onClick = { viewModel.nextStep() }) { Text("Continue") }
            }
            1 -> {
                Text("Choose 1–3 languages", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Search and pick from the dropdown (243 languages).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SearchableLanguageMultiSelect(
                    languages = langs,
                    selectedCodes = selected.toSet(),
                    onToggle = { lang -> viewModel.toggleLang(lang.code) },
                    maxSelection = 3,
                )
                Button(
                    onClick = { viewModel.nextStep() },
                    enabled = selected.isNotEmpty(),
                ) { Text("Next") }
            }
            2 -> {
                Text("Teen tutor voice", style = MaterialTheme.typography.titleLarge)
                CheradipDropdown(
                    label = "Voice",
                    options = TeenVoiceGender.entries.toList(),
                    selected = voiceGender,
                    onSelected = { gender ->
                        voiceGender = gender
                        viewModel.setVoice(gender)
                    },
                    optionLabel = { if (it == TeenVoiceGender.MALE) "Teen male" else "Teen female" },
                    leadingIcon = Icons.Default.Person,
                )
                Button(onClick = { viewModel.nextStep() }) { Text("Next") }
            }
            else -> {
                Text("Download language packs", style = MaterialTheme.typography.titleLarge)
                selected.forEach { Text("· $it") }
                IconTextButton(
                    label = "Download & Get started",
                    icon = Icons.Default.Download,
                    onClick = { viewModel.downloadPacks(onComplete) },
                )
            }
        }
    }
}
