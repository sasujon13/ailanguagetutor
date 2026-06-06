package com.cheradip.ailanguagetutor.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.ai.AiModePreferenceRepository
import com.cheradip.ailanguagetutor.core.billing.CheckAppAccessUseCase
import com.cheradip.ailanguagetutor.core.model.AiEngineMode
import com.cheradip.ailanguagetutor.core.model.AiModeUiMeta
import com.cheradip.ailanguagetutor.core.model.LanguageCatalogEntry
import com.cheradip.ailanguagetutor.core.model.LanguageFlagMarker
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.core.model.SubscriptionTier
import com.cheradip.ailanguagetutor.core.model.aiModeUiMeta
import com.cheradip.ailanguagetutor.core.model.pickerAiModes
import com.cheradip.ailanguagetutor.core.audio.PracticeLanguageRepository
import com.cheradip.ailanguagetutor.core.pack.LanguageCatalogRepository
import com.cheradip.ailanguagetutor.core.pack.LanguagePackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PracticeLanguageOption(
    val code: String,
    val label: String,
    val flagEmoji: String,
)

data class ModeSelectionUiState(
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val processingIntent: ProcessingIntent = ProcessingIntent.ANSWER,
    val selectedMode: AiEngineMode = AiEngineMode.SMART_TUTOR,
    val selectableModes: List<AiModeUiMeta> = emptyList(),
    val languageOptions: List<PracticeLanguageOption> = emptyList(),
    val inputLanguage: String = "en",
    val outputLanguage: String = "en",
    val showPlusUpgrade: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class ModeSelectionViewModel @Inject constructor(
    private val aiModePrefs: AiModePreferenceRepository,
    private val checkAppAccess: CheckAppAccessUseCase,
    private val languagePackRepository: LanguagePackRepository,
    private val catalogRepository: LanguageCatalogRepository,
    private val practiceLanguageRepository: PracticeLanguageRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ModeSelectionUiState())
    val uiState: StateFlow<ModeSelectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                languagePackRepository.observeActive(),
                practiceLanguageRepository.config,
                checkAppAccess.accessState,
            ) { packs, langConfig, _ -> packs to langConfig }
                .collectLatest { (packs, langConfig) ->
                    val activeCodes = packs.map { it.languageCode }
                    if (activeCodes.isNotEmpty()) {
                        practiceLanguageRepository.ensureDefaults(activeCodes)
                    }
                    val catalog = catalogRepository.getAll()
                    val options = packs.mapNotNull { pack ->
                        toLanguageOption(pack.languageCode, catalog)
                    }
                    refreshFromPrefs(options, langConfig)
                }
        }
    }

    private suspend fun refreshFromPrefs(
        options: List<PracticeLanguageOption>,
        langConfig: com.cheradip.ailanguagetutor.core.audio.PracticeLanguageConfig,
    ) {
        val tier = checkAppAccess.subscriptionTier()
        val prefs = aiModePrefs.current()
        val modes = pickerAiModes().map { aiModeUiMeta(it) }
        val input = langConfig.inputLanguage.takeIf { code ->
            options.any { it.code.equals(code, ignoreCase = true) }
        } ?: options.firstOrNull()?.code ?: langConfig.inputLanguage
        val output = langConfig.outputLanguage.takeIf { code ->
            options.any { it.code.equals(code, ignoreCase = true) }
        } ?: options.drop(1).firstOrNull()?.code ?: input
        _uiState.update {
            it.copy(
                tier = tier,
                processingIntent = prefs.processingIntent,
                selectedMode = aiModePrefs.displaySelectedMode(prefs.selectedMode, tier),
                selectableModes = modes,
                languageOptions = options,
                inputLanguage = input,
                outputLanguage = output,
            )
        }
    }

    private fun toLanguageOption(code: String, catalog: List<LanguageCatalogEntry>): PracticeLanguageOption? {
        val entry = catalog.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: return null
        return PracticeLanguageOption(
            code = entry.code.lowercase(),
            label = LanguageFlagMarker.compact(entry),
            flagEmoji = LanguageFlagMarker.emoji(entry),
        )
    }

    fun isModeLocked(mode: AiEngineMode): Boolean =
        mode == AiEngineMode.HIGH_ACCURACY && _uiState.value.tier != SubscriptionTier.PLUS

    fun setIntent(intent: ProcessingIntent) {
        _uiState.update { it.copy(processingIntent = intent) }
    }

    fun selectMode(mode: AiEngineMode) {
        if (isModeLocked(mode)) {
            _uiState.update { it.copy(showPlusUpgrade = true) }
            return
        }
        _uiState.update { it.copy(selectedMode = mode, showPlusUpgrade = false) }
    }

    fun setInputLanguage(code: String) {
        _uiState.update { it.copy(inputLanguage = code.lowercase()) }
    }

    fun setOutputLanguage(code: String) {
        _uiState.update { it.copy(outputLanguage = code.lowercase()) }
    }

    fun dismissPlusUpgrade() {
        _uiState.update { it.copy(showPlusUpgrade = false) }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            aiModePrefs.save(state.processingIntent, state.selectedMode)
            practiceLanguageRepository.save(state.inputLanguage, state.outputLanguage)
            _uiState.update { it.copy(saved = true) }
            onDone()
        }
    }
}
