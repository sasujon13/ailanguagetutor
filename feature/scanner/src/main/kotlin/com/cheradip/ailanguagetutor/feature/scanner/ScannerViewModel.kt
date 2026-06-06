package com.cheradip.ailanguagetutor.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.database.repository.DocumentRepository
import com.cheradip.ailanguagetutor.core.pack.LanguagePackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScannerUiState(
    val documentId: Long? = null,
    val pageCount: Int = 0,
    val pagePaths: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val imageStorage: DocumentImageStorage,
    private val languagePackRepository: LanguagePackRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun initDocument(existingId: Long?) {
        if (existingId != null) {
            viewModelScope.launch {
                val doc = documentRepository.getDocument(existingId)
                val pages = documentRepository.getPages(existingId)
                _uiState.update {
                    it.copy(
                        documentId = existingId,
                        pageCount = doc?.pageCount ?: pages.size,
                        pagePaths = pages.map { p -> p.imagePath },
                    )
                }
            }
        } else if (_uiState.value.documentId == null) {
            viewModelScope.launch {
                val activeLang = languagePackRepository.observeActive().first()
                    .firstOrNull()?.languageCode ?: "en"
                val id = documentRepository.createDocument(
                    title = "Scan ${System.currentTimeMillis()}",
                    languageCode = activeLang,
                )
                _uiState.update { it.copy(documentId = id) }
            }
        }
    }

    fun onPhotoCaptured(bytes: ByteArray) {
        val docId = _uiState.value.documentId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                val pageIndex = _uiState.value.pageCount
                val saved = imageStorage.saveCapturedBytes(docId, pageIndex, bytes)
                documentRepository.addPage(docId, saved.path, saved.width, saved.height)
                saved.path
            }.onSuccess { path ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        pageCount = it.pageCount + 1,
                        pagePaths = it.pagePaths + path,
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun onGalleryImage(bytes: ByteArray) = onPhotoCaptured(bytes)
}
