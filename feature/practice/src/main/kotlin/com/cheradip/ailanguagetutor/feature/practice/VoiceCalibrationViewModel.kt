package com.cheradip.ailanguagetutor.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.audio.PracticeLanguageRepository
import com.cheradip.ailanguagetutor.core.pack.LanguagePackRepository
import com.cheradip.ailanguagetutor.core.speech.CalibrationContent
import com.cheradip.ailanguagetutor.core.speech.CalibrationMatcher
import com.cheradip.ailanguagetutor.core.speech.CalibrationTier
import com.cheradip.ailanguagetutor.core.speech.LanguageCalibrationStatus
import com.cheradip.ailanguagetutor.core.speech.ListeningState
import com.cheradip.ailanguagetutor.core.speech.SpeechToTextEngine
import com.cheradip.ailanguagetutor.core.speech.VoiceCalibrationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoiceCalibrationUiState(
    val activeLanguageCodes: List<String> = emptyList(),
    val calibrationStatuses: List<LanguageCalibrationStatus> = emptyList(),
    val calibration: CalibrationUiState = CalibrationUiState(),
    val speechError: String? = null,
)

@HiltViewModel
class VoiceCalibrationViewModel @Inject constructor(
    private val speechEngine: SpeechToTextEngine,
    private val voiceCalibrationRepository: VoiceCalibrationRepository,
    private val languagePackRepository: LanguagePackRepository,
    private val practiceLanguageRepository: PracticeLanguageRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(VoiceCalibrationUiState())
    val uiState: StateFlow<VoiceCalibrationUiState> = _uiState.asStateFlow()

    private var listening = false

    init {
        viewModelScope.launch {
            speechEngine.state.collect(::handleListeningState)
        }
        viewModelScope.launch {
            languagePackRepository.observeActive()
                .map { packs -> packs.map { it.languageCode.lowercase() }.distinct() }
                .distinctUntilChanged()
                .collectLatest { codes ->
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
                speechError = null,
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
                speechError = null,
            )
        }
    }

    fun startCalibrationMic() {
        if (!speechEngine.isAvailable()) {
            _uiState.update { it.copy(speechError = "Speech recognition is not available on this device.") }
            return
        }
        val lang = _uiState.value.calibration.languageCode
        listening = true
        _uiState.update {
            it.copy(
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

    fun stopCalibrationMic() {
        speechEngine.stopListening()
        listening = false
        _uiState.update {
            it.copy(calibration = it.calibration.copy(listening = false))
        }
    }

    private fun handleListeningState(state: ListeningState) {
        if (!listening) return
        when (state) {
            ListeningState.Idle -> {
                listening = false
                _uiState.update {
                    it.copy(calibration = it.calibration.copy(listening = false))
                }
            }
            ListeningState.Listening -> {
                _uiState.update {
                    it.copy(calibration = it.calibration.copy(listening = true))
                }
            }
            is ListeningState.Partial -> {
                _uiState.update {
                    it.copy(calibration = it.calibration.copy(partialText = state.text, listening = true))
                }
            }
            is ListeningState.Final -> evaluateCalibration(state.text)
            is ListeningState.Error -> {
                listening = false
                _uiState.update {
                    it.copy(
                        speechError = state.message,
                        calibration = it.calibration.copy(listening = false),
                    )
                }
            }
        }
    }

    private fun evaluateCalibration(spoken: String) {
        listening = false
        val cal = _uiState.value.calibration
        val pack = CalibrationContent.packFor(cal.languageCode)
        val reference = CalibrationContent.prompt(pack, cal.tier, cal.itemIndex)
        if (reference == null) {
            _uiState.update {
                it.copy(
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
