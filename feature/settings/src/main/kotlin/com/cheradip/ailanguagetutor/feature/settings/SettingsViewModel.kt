package com.cheradip.ailanguagetutor.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.ai.GrammarPreferenceRepository
import com.cheradip.ailanguagetutor.core.audio.PracticeLanguageRepository
import com.cheradip.ailanguagetutor.core.audio.TeenVoiceGender
import com.cheradip.ailanguagetutor.core.audio.VoicePreferenceRepository
import com.cheradip.ailanguagetutor.core.locale.AppLocaleManager
import com.cheradip.ailanguagetutor.core.model.GrammarDepth
import com.cheradip.ailanguagetutor.core.model.LanguageCatalogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val grammarPreferenceRepository: GrammarPreferenceRepository,
    private val appLocaleManager: AppLocaleManager,
    private val voicePreferenceRepository: VoicePreferenceRepository,
    practiceLanguageRepository: PracticeLanguageRepository,
) : ViewModel() {

    val grammarDepth = grammarPreferenceRepository.depth
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrammarDepth.WORD)

    val localeUi = appLocaleManager.uiState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), appLocaleManager.uiState.value)

    val voiceGender = voicePreferenceRepository.gender
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeenVoiceGender.FEMALE)

    val ttsPreviewLanguage = practiceLanguageRepository.config
        .map { it.outputLanguage }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "en")

    fun setGrammarDepth(depth: GrammarDepth) {
        viewModelScope.launch { grammarPreferenceRepository.save(depth) }
    }

    fun selectAppLanguage(entry: LanguageCatalogEntry) {
        viewModelScope.launch { appLocaleManager.selectLanguage(entry, animated = true) }
    }

    fun saveVoiceGender(gender: TeenVoiceGender) {
        viewModelScope.launch { voicePreferenceRepository.setGender(gender) }
    }
}
