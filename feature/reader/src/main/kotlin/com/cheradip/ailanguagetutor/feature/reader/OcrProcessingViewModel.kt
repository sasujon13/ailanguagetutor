package com.cheradip.ailanguagetutor.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.database.repository.DocumentRepository
import com.cheradip.ailanguagetutor.core.model.DocumentPage
import com.cheradip.ailanguagetutor.core.ocr.MlKitOcrEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OcrProcessingUiState(
    val documentId: Long = 0,
    val totalPages: Int = 0,
    val processedPages: Int = 0,
    val isRunning: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class OcrProcessingViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val ocrEngine: MlKitOcrEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrProcessingUiState())
    val uiState: StateFlow<OcrProcessingUiState> = _uiState.asStateFlow()

    fun process(documentId: Long) {
        if (_uiState.value.isRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(documentId = documentId, isRunning = true, error = null) }
            runCatching {
                val pages = documentRepository.getPages(documentId)
                _uiState.update { it.copy(totalPages = pages.size) }
                pages.forEachIndexed { index, page ->
                    processPage(page)
                    _uiState.update { it.copy(processedPages = index + 1) }
                }
            }.onSuccess {
                _uiState.update { it.copy(isRunning = false, isComplete = true) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isRunning = false, error = e.message ?: "OCR failed")
                }
            }
        }
    }

    private suspend fun processPage(page: DocumentPage) {
        if (!page.ocrText.isNullOrBlank() && !page.wordMapJson.isNullOrBlank()) return
        val result = ocrEngine.recognize(page.imagePath)
        documentRepository.updatePageOcr(page.id, result.fullText, result.wordMapJson)
    }
}
