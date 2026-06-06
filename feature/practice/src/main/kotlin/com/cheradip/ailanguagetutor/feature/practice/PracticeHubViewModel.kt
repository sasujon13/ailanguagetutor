package com.cheradip.ailanguagetutor.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.ai.AiPrefetchCoordinator
import com.cheradip.ailanguagetutor.core.ai.GrammarPreferenceRepository
import com.cheradip.ailanguagetutor.core.ai.UnifiedTextPipeline
import com.cheradip.ailanguagetutor.core.audio.PracticeLanguageConfig
import com.cheradip.ailanguagetutor.core.audio.PracticeLanguageRepository
import com.cheradip.ailanguagetutor.core.audio.PronunciationEngine
import com.cheradip.ailanguagetutor.core.database.repository.LearningActivityRepository
import com.cheradip.ailanguagetutor.core.model.GrammarDepth
import com.cheradip.ailanguagetutor.core.model.InputSource
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.core.model.LanguageCatalogEntry
import com.cheradip.ailanguagetutor.core.model.LanguageFlagMarker
import com.cheradip.ailanguagetutor.core.pack.LanguageCatalogRepository
import com.cheradip.ailanguagetutor.core.pack.LanguagePackRepository
import com.cheradip.ailanguagetutor.core.speech.CalibrationContent
import com.cheradip.ailanguagetutor.core.speech.CalibrationMatcher
import com.cheradip.ailanguagetutor.core.speech.CalibrationTier
import com.cheradip.ailanguagetutor.core.speech.LanguageCalibrationStatus
import com.cheradip.ailanguagetutor.core.speech.ListeningState
import com.cheradip.ailanguagetutor.core.speech.SpeechToTextEngine
import com.cheradip.ailanguagetutor.core.speech.VoiceCalibrationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SpeechCaptureMode { NONE, VOICE_INPUT, CALIBRATION }

data class CalibrationUiState(
    val languageCode: String = "en",
    val tier: CalibrationTier = CalibrationTier.WORD,
    val itemIndex: Int = 0,
    val listening: Boolean = false,
    val partialText: String = "",
    val lastScore: Float? = null,
    val message: String? = null,
)

data class PracticeHubUiState(
    val typedInput: String = "",
    val aiOutput: String? = null,
    val aiLoading: Boolean = false,
    val aiIntent: ProcessingIntent? = null,
    val isListening: Boolean = false,
    val partialSpeech: String = "",
    val speechError: String? = null,
    val activeLanguageCodes: List<String> = emptyList(),
    val calibrationStatuses: List<LanguageCalibrationStatus> = emptyList(),
    val calibration: CalibrationUiState = CalibrationUiState(),
    val speechMode: SpeechCaptureMode = SpeechCaptureMode.NONE,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PracticeHubViewModel @Inject constructor(
    private val pronunciationEngine: PronunciationEngine,
    private val speechEngine: SpeechToTextEngine,
    private val voiceCalibrationRepository: VoiceCalibrationRepository,
    private val languagePackRepository: LanguagePackRepository,
    private val catalogRepository: LanguageCatalogRepository,
    private val learningActivityRepository: LearningActivityRepository,
    private val unifiedTextPipeline: UnifiedTextPipeline,
    private val aiPrefetchCoordinator: AiPrefetchCoordinator,
    private val practiceLanguageRepository: PracticeLanguageRepository,
    grammarPreferenceRepository: GrammarPreferenceRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PracticeHubUiState())
    val uiState: StateFlow<PracticeHubUiState> = _uiState.asStateFlow()

    private val grammarDepth = grammarPreferenceRepository.depth
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrammarDepth.WORD)

    val practiceLanguages = practiceLanguageRepository.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PracticeLanguageConfig())

    private val _languageOptions = MutableStateFlow<List<PracticeLanguageOption>>(emptyList())
    val languageOptions: StateFlow<List<PracticeLanguageOption>> = _languageOptions.asStateFlow()

    init {
        pronunciationEngine.init()
        viewModelScope.launch {
            speechEngine.state.collect(::handleListeningState)
        }
        viewModelScope.launch {
            languagePackRepository.observeActive()
                .map { packs -> packs.map { it.languageCode.lowercase() }.distinct() }
                .distinctUntilChanged()
                .collectLatest { codes ->
                    val catalog = catalogRepository.getAll()
                    _languageOptions.value = codes.mapNotNull { code ->
                        toLanguageOption(code, catalog)
                    }
                    if (codes.isNotEmpty()) {
                        practiceLanguageRepository.ensureDefaults(codes)
                    }
                    _uiState.update { state ->
                        state.copy(
                            activeLanguageCodes = codes,
                            calibration = state.calibration.copy(
                                languageCode = resolveCalibrationLanguage(state.calibration.languageCode, codes),
                            ),
                        )
                    }
                    if (codes.isNotEmpty()) {
                        voiceCalibrationRepository.observe(codes).collect { statuses ->
                            _uiState.update { it.copy(calibrationStatuses = statuses) }
                        }
                    } else {
                        _uiState.update { it.copy(calibrationStatuses = emptyList()) }
                    }
                }
        }
        viewModelScope.launch {
            practiceLanguageRepository.config.collect { config ->
                _uiState.update { state ->
                    val codes = state.activeLanguageCodes
                    if (codes.isEmpty()) return@update state
                    state.copy(
                        calibration = state.calibration.copy(
                            languageCode = config.inputLanguage.lowercase().takeIf { it in codes }
                                ?: codes.firstOrNull()
                                ?: state.calibration.languageCode,
                        ),
                    )
                }
            }
        }
    }

    private fun resolveCalibrationLanguage(current: String, codes: List<String>): String =
        current.takeIf { it in codes } ?: codes.firstOrNull() ?: current

    override fun onCleared() {
        speechEngine.destroy()
        super.onCleared()
    }

    fun setInputLanguage(code: String) {
        viewModelScope.launch {
            val output = practiceLanguages.value.outputLanguage
            practiceLanguageRepository.save(code.lowercase(), output)
        }
    }

    fun setOutputLanguage(code: String) {
        viewModelScope.launch {
            val input = practiceLanguages.value.inputLanguage
            practiceLanguageRepository.save(input, code.lowercase())
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

    fun speak(text: String) {
        val lang = practiceLanguages.value.outputLanguage
        pronunciationEngine.speak(text, lang)
    }

    fun clearSpeechError() {
        _uiState.update { it.copy(speechError = null) }
    }

    fun setSpeechError(message: String) {
        _uiState.update { it.copy(speechError = message) }
    }

    fun updateTypedInput(text: String) {
        val langs = practiceLanguages.value
        _uiState.update { it.copy(typedInput = text) }
        aiPrefetchCoordinator.schedulePracticeWarm(
            text = text,
            languageCode = langs.inputLanguage,
            targetLang = langs.outputLanguage,
            grammarDepth = grammarDepth.value,
        )
    }

    fun processTypedInput(sourceLang: String, targetLang: String) {
        val text = _uiState.value.typedInput.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(aiLoading = true) }
            val result = runCatching {
                unifiedTextPipeline.process(
                    text = text,
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                    inputSource = InputSource.TYPED,
                )
            }.getOrNull()
            _uiState.update {
                it.copy(
                    aiLoading = false,
                    aiOutput = result?.output ?: "Could not process. Check subscription and network.",
                    aiIntent = result?.intent,
                )
            }
            learningActivityRepository.record(
                title = "Practice: typed",
                activityType = "practice_typed",
                languageCode = sourceLang,
                summary = result?.output?.take(120),
            )
        }
    }

    fun processTypedInputWithSavedLanguages() {
        val langs = practiceLanguages.value
        processTypedInput(sourceLang = langs.inputLanguage, targetLang = langs.outputLanguage)
    }

    fun startVoiceInput() {
        val inputLang = practiceLanguages.value.inputLanguage.lowercase()
        val allowed = _uiState.value.calibrationStatuses
            .firstOrNull { it.languageCode == inputLang }
            ?.sayAllowed == true
        if (!allowed) {
            _uiState.update {
                it.copy(
                    speechError = "Complete paragraph calibration for ${inputLang.uppercase()} " +
                        "in Voice Setup below before using Say.",
                )
            }
            return
        }
        if (!speechEngine.isAvailable()) {
            _uiState.update { it.copy(speechError = "Speech recognition is not available on this device.") }
            return
        }
        _uiState.update {
            it.copy(
                speechMode = SpeechCaptureMode.VOICE_INPUT,
                isListening = true,
                speechError = null,
                partialSpeech = "",
            )
        }
        speechEngine.startListening(inputLang)
    }

    fun stopVoiceInput() {
        speechEngine.stopListening()
        _uiState.update {
            it.copy(
                isListening = false,
                speechMode = SpeechCaptureMode.NONE,
                calibration = it.calibration.copy(listening = false),
            )
        }
    }

    fun selectCalibrationLanguage(code: String) {
        _uiState.update {
            it.copy(
                calibration = it.calibration.copy(
                    languageCode = code.lowercase(),
                    tier = CalibrationTier.WORD,
                    itemIndex = 0,
                    message = null,
                    lastScore = null,
                    partialText = "",
                ),
            )
        }
    }

    fun selectCalibrationTier(tier: CalibrationTier) {
        _uiState.update {
            it.copy(
                calibration = it.calibration.copy(
                    tier = tier,
                    itemIndex = 0,
                    message = null,
                    lastScore = null,
                    partialText = "",
                ),
            )
        }
    }

    fun startCalibrationMic() {
        if (!speechEngine.isAvailable()) {
            _uiState.update { it.copy(speechError = "Speech recognition is not available on this device.") }
            return
        }
        val lang = _uiState.value.calibration.languageCode
        _uiState.update {
            it.copy(
                speechMode = SpeechCaptureMode.CALIBRATION,
                speechError = null,
                calibration = it.calibration.copy(
                    listening = true,
                    message = null,
                    partialText = "",
                    lastScore = null,
                ),
            )
        }
        speechEngine.startListening(lang)
    }

    fun recordPractice(mode: String) {
        viewModelScope.launch {
            learningActivityRepository.record(
                title = "Practice: $mode",
                activityType = mode,
                languageCode = practiceLanguages.value.inputLanguage,
                summary = "Interactive practice session",
            )
        }
    }

    private fun handleListeningState(state: ListeningState) {
        when (state) {
            ListeningState.Idle -> {
                _uiState.update {
                    it.copy(
                        isListening = false,
                        calibration = it.calibration.copy(listening = false),
                    )
                }
            }
            ListeningState.Listening -> {
                _uiState.update {
                    it.copy(
                        isListening = it.speechMode == SpeechCaptureMode.VOICE_INPUT,
                        calibration = it.calibration.copy(
                            listening = it.speechMode == SpeechCaptureMode.CALIBRATION,
                        ),
                    )
                }
            }
            is ListeningState.Partial -> {
                _uiState.update {
                    when (it.speechMode) {
                        SpeechCaptureMode.VOICE_INPUT -> it.copy(partialSpeech = state.text, isListening = true)
                        SpeechCaptureMode.CALIBRATION -> it.copy(
                            calibration = it.calibration.copy(partialText = state.text, listening = true),
                        )
                        SpeechCaptureMode.NONE -> it
                    }
                }
            }
            is ListeningState.Final -> {
                when (_uiState.value.speechMode) {
                    SpeechCaptureMode.VOICE_INPUT -> {
                        _uiState.update {
                            it.copy(
                                typedInput = state.text,
                                partialSpeech = "",
                                isListening = false,
                                speechMode = SpeechCaptureMode.NONE,
                            )
                        }
                        updateTypedInput(state.text)
                    }
                    SpeechCaptureMode.CALIBRATION -> evaluateCalibration(state.text)
                    SpeechCaptureMode.NONE -> Unit
                }
            }
            is ListeningState.Error -> {
                _uiState.update {
                    it.copy(
                        speechError = state.message,
                        isListening = false,
                        speechMode = SpeechCaptureMode.NONE,
                        calibration = it.calibration.copy(listening = false),
                    )
                }
            }
        }
    }

    private fun evaluateCalibration(spoken: String) {
        val cal = _uiState.value.calibration
        val pack = CalibrationContent.packFor(cal.languageCode)
        val reference = CalibrationContent.prompt(pack, cal.tier, cal.itemIndex)
        if (reference == null) {
            _uiState.update {
                it.copy(
                    speechMode = SpeechCaptureMode.NONE,
                    calibration = cal.copy(listening = false, message = "Calibration step complete."),
                )
            }
            return
        }
        val score = CalibrationMatcher.score(reference, spoken)
        val ok = CalibrationMatcher.isMatch(reference, spoken, cal.tier)
        if (!ok) {
            _uiState.update {
                it.copy(
                    speechMode = SpeechCaptureMode.NONE,
                    calibration = cal.copy(
                        listening = false,
                        partialText = spoken,
                        lastScore = score,
                        message = "Matched ${(score * 100).toInt()}% — read the prompt again and tap the mic.",
                    ),
                )
            }
            return
        }

        val nextIndex = cal.itemIndex + 1
        val itemCount = CalibrationContent.itemCount(cal.tier)
        if (nextIndex < itemCount) {
            _uiState.update {
                it.copy(
                    speechMode = SpeechCaptureMode.NONE,
                    calibration = cal.copy(
                        itemIndex = nextIndex,
                        listening = false,
                        partialText = "",
                        lastScore = score,
                        message = "${CalibrationContent.tierLabel(cal.tier)} ${nextIndex}/$itemCount — keep going!",
                    ),
                )
            }
            return
        }

        viewModelScope.launch {
            voiceCalibrationRepository.markTierComplete(cal.languageCode, cal.tier)
        }
        val nextTier = when (cal.tier) {
            CalibrationTier.WORD -> CalibrationTier.SENTENCE
            CalibrationTier.SENTENCE -> CalibrationTier.PARAGRAPH
            CalibrationTier.PARAGRAPH -> null
        }
        if (nextTier != null) {
            _uiState.update {
                it.copy(
                    speechMode = SpeechCaptureMode.NONE,
                    calibration = cal.copy(
                        tier = nextTier,
                        itemIndex = 0,
                        listening = false,
                        partialText = "",
                        lastScore = score,
                        message = "${CalibrationContent.tierLabel(cal.tier)} complete ✓ — next: ${CalibrationContent.tierLabel(nextTier)}.",
                    ),
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    speechMode = SpeechCaptureMode.NONE,
                    calibration = cal.copy(
                        listening = false,
                        partialText = "",
                        lastScore = score,
                        message = "All calibration complete for ${cal.languageCode.uppercase()} ✓ Say is now enabled.",
                    ),
                )
            }
        }
    }
}
