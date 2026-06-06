package com.cheradip.ailanguagetutor.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.ai.AiModePreferenceRepository
import com.cheradip.ailanguagetutor.core.billing.CheckAppAccessUseCase
import com.cheradip.ailanguagetutor.core.model.AiEngineMode
import com.cheradip.ailanguagetutor.core.model.AiModeUiMeta
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.core.model.SubscriptionTier
import com.cheradip.ailanguagetutor.core.model.aiModeUiMeta
import com.cheradip.ailanguagetutor.core.model.availableAiModes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModeSelectionUiState(
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val processingIntent: ProcessingIntent = ProcessingIntent.ANSWER,
    val selectedMode: AiEngineMode = AiEngineMode.SMART_TUTOR,
    val selectableModes: List<AiModeUiMeta> = emptyList(),
    val showPlusUpgrade: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class ModeSelectionViewModel @Inject constructor(
    private val aiModePrefs: AiModePreferenceRepository,
    private val checkAppAccess: CheckAppAccessUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ModeSelectionUiState())
    val uiState: StateFlow<ModeSelectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val tier = checkAppAccess.subscriptionTier()
            val prefs = aiModePrefs.current()
            val modes = availableAiModes(tier).filter { it != AiEngineMode.LIGHTWEIGHT }
                .map { aiModeUiMeta(it) }
            _uiState.update {
                it.copy(
                    tier = tier,
                    processingIntent = prefs.processingIntent,
                    selectedMode = prefs.selectedMode,
                    selectableModes = modes,
                )
            }
        }
    }

    fun setIntent(intent: ProcessingIntent) {
        _uiState.update { it.copy(processingIntent = intent) }
    }

    fun selectMode(mode: AiEngineMode) {
        val tier = _uiState.value.tier
        if (mode == AiEngineMode.HIGH_ACCURACY && tier != SubscriptionTier.PLUS) {
            _uiState.update { it.copy(showPlusUpgrade = true) }
            return
        }
        _uiState.update { it.copy(selectedMode = mode, showPlusUpgrade = false) }
    }

    fun dismissPlusUpgrade() {
        _uiState.update { it.copy(showPlusUpgrade = false) }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            aiModePrefs.save(state.processingIntent, state.selectedMode)
            _uiState.update { it.copy(saved = true) }
            onDone()
        }
    }
}
