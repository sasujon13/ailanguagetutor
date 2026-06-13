package com.cheradip.ailanguagetutor.feature.help

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserManualUiState(
    val loading: Boolean = true,
    val blocks: List<ManualBlock> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class UserManualViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val manualRepository: ManualRepository,
) : ViewModel() {
    private val manualType: ManualType? =
        ManualType.fromId(savedStateHandle.get<String>("manualId"))

    private val _uiState = MutableStateFlow(UserManualUiState())
    val uiState: StateFlow<UserManualUiState> = _uiState.asStateFlow()

    init {
        val type = manualType
        if (type == null) {
            _uiState.value = UserManualUiState(loading = false, error = "Manual not found")
        } else {
            viewModelScope.launch {
                manualRepository.loadManual(type)
                    .onSuccess { blocks ->
                        _uiState.value = UserManualUiState(loading = false, blocks = blocks)
                    }
                    .onFailure { e ->
                        _uiState.value = UserManualUiState(
                            loading = false,
                            error = e.message ?: "Could not load manual",
                        )
                    }
            }
        }
    }
}
