package com.cheradip.ailanguagetutor.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.database.repository.DocumentRepository
import com.cheradip.ailanguagetutor.core.image.BitmapUtils
import com.cheradip.ailanguagetutor.core.image.CleanParams
import com.cheradip.ailanguagetutor.core.image.CropParams
import com.cheradip.ailanguagetutor.core.image.CropPreset
import com.cheradip.ailanguagetutor.core.image.EditHistoryEntry
import com.cheradip.ailanguagetutor.core.image.EditHistorySnapshot
import com.cheradip.ailanguagetutor.core.image.EditStage
import com.cheradip.ailanguagetutor.core.image.ExportOptions
import com.cheradip.ailanguagetutor.core.image.GrayParams
import com.cheradip.ailanguagetutor.core.image.PageEditState
import com.cheradip.ailanguagetutor.core.image.QuadPoints
import com.cheradip.ailanguagetutor.core.image.ScanEditEngine
import com.cheradip.ailanguagetutor.core.image.ScanExportService
import com.cheradip.ailanguagetutor.core.image.ScanTool
import com.cheradip.ailanguagetutor.core.image.TransitionParams
import com.cheradip.ailanguagetutor.core.image.parsePageEditStateJson
import com.cheradip.ailanguagetutor.core.image.toJson
import com.cheradip.ailanguagetutor.core.pack.LanguagePackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ScannerPageItem(
    val id: Long,
    val imagePath: String,
    val originalPath: String,
    val pageIndex: Int,
)

data class ScannerUiState(
    val documentId: Long? = null,
    val pageCount: Int = 0,
    val pages: List<ScannerPageItem> = emptyList(),
    val selectedPageId: Long? = null,
    val isSaving: Boolean = false,
    val isProcessingPreview: Boolean = false,
    val error: String? = null,
    val activeTool: ScanTool? = null,
    val previewPath: String? = null,
    val compareOriginal: Boolean = false,
    val beforeAfterSlider: Float = 0.5f,
    val showExportDialog: Boolean = false,
    val showExportPreview: Boolean = false,
    val exportPreviewPaths: List<String> = emptyList(),
    val exportMessage: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val editHistory: List<Pair<Int, String>> = emptyList(),
    val draftCrop: CropParams = CropParams(),
    val draftTransition: TransitionParams = TransitionParams(),
    val draftClean: CleanParams = CleanParams(),
    val draftGray: GrayParams = GrayParams(),
    val exportOptions: ExportOptions = ExportOptions(),
    val cropSkewDegrees: Float = 0f,
    val appliedStages: Set<EditStage> = emptySet(),
    val showVersionCompare: Boolean = false,
    val versionCompareBeforePath: String? = null,
    val versionCompareAfterPath: String? = null,
    val versionCompareLabel: String = "",
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val imageStorage: DocumentImageStorage,
    private val languagePackRepository: LanguagePackRepository,
    private val scanEditEngine: ScanEditEngine,
    private val scanExportService: ScanExportService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val editStates = mutableMapOf<Long, PageEditState>()
    private var previewJob: Job? = null
    private var documentSourceType: String = "scan"

    fun initDocument(existingId: Long?, sourceType: String = "scan") {
        documentSourceType = sourceType
        if (existingId != null) {
            viewModelScope.launch { loadDocument(existingId) }
        } else if (_uiState.value.documentId == null) {
            viewModelScope.launch {
                val activeLang = languagePackRepository.observeActive().first()
                    .firstOrNull()?.languageCode ?: "en"
                val titlePrefix = if (documentSourceType == "import") "Upload" else "Scan"
                val id = documentRepository.createDocument(
                    title = "$titlePrefix ${System.currentTimeMillis()}",
                    languageCode = activeLang,
                    sourceType = documentSourceType,
                )
                _uiState.update { it.copy(documentId = id) }
            }
        }
    }

    private suspend fun loadDocument(documentId: Long) {
        val pages = documentRepository.getPages(documentId)
        val items = pages.map { page ->
            val original = page.originalImagePath ?: page.imagePath
            editStates[page.id] = parsePageEditStateJson(
                pageId = page.id,
                json = page.editStateJson,
                originalPath = original,
                workingPath = page.imagePath,
            )
            ScannerPageItem(page.id, page.imagePath, original, page.pageIndex)
        }
        _uiState.update {
            it.copy(
                documentId = documentId,
                pageCount = pages.size,
                pages = items,
                selectedPageId = items.lastOrNull()?.id,
            )
        }
        items.lastOrNull()?.id?.let { refreshPreview(it) }
    }

    fun onPhotoCaptured(bytes: ByteArray) {
        val docId = _uiState.value.documentId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                val pageIndex = _uiState.value.pageCount
                val saved = imageStorage.saveCapturedBytes(docId, pageIndex, bytes)
                val pageId = documentRepository.addPage(
                    documentId = docId,
                    imagePath = saved.path,
                    width = saved.width,
                    height = saved.height,
                    originalImagePath = saved.originalPath,
                )
                editStates[pageId] = PageEditState(
                    pageId = pageId,
                    originalPath = saved.originalPath,
                    workingPath = saved.path,
                    history = listOf(
                        EditHistoryEntry(
                            stage = EditStage.ORIGINAL,
                            label = "Original",
                            snapshot = EditHistorySnapshot(),
                        ),
                    ),
                    historyIndex = 0,
                )
                ScannerPageItem(pageId, saved.path, saved.originalPath, pageIndex)
            }.onSuccess { item ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        pageCount = it.pageCount + 1,
                        pages = it.pages + item,
                        selectedPageId = item.id,
                        activeTool = ScanTool.CROP,
                    )
                }
                autoDetectEdges(item.id)
                refreshPreview(item.id)
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun onGalleryImage(bytes: ByteArray) = onPhotoCaptured(bytes)

    fun selectPage(pageId: Long) {
        _uiState.update { it.copy(selectedPageId = pageId, activeTool = null) }
        syncDraftFromState(pageId)
        refreshPreview(pageId)
    }

    fun openTool(tool: ScanTool) {
        if (tool == ScanTool.SAVE) {
            _uiState.update { it.copy(showExportDialog = true, activeTool = ScanTool.SAVE) }
            return
        }
        val pageId = _uiState.value.selectedPageId
        _uiState.update { it.copy(activeTool = tool, beforeAfterSlider = 0.5f) }
        pageId ?: return
        if (tool == ScanTool.ORIGINAL) {
            refreshPreview(pageId, forceOriginal = true)
        } else {
            refreshPreview(pageId)
        }
    }

    fun closeToolPanel() {
        _uiState.update { it.copy(activeTool = null, compareOriginal = false) }
        _uiState.value.selectedPageId?.let { refreshPreview(it) }
    }

    fun setCompareOriginal(holding: Boolean) {
        _uiState.update { it.copy(compareOriginal = holding) }
        val pageId = _uiState.value.selectedPageId ?: return
        if (holding) {
            val original = editStates[pageId]?.originalPath
            _uiState.update { it.copy(previewPath = original) }
        } else {
            refreshPreview(pageId)
        }
    }

    fun setBeforeAfterSlider(value: Float) {
        _uiState.update { it.copy(beforeAfterSlider = value.coerceIn(0f, 1f)) }
        _uiState.value.selectedPageId?.let { refreshPreview(it) }
    }

    fun revertCurrentTool() {
        val tool = _uiState.value.activeTool ?: return
        if (tool == ScanTool.ORIGINAL || tool == ScanTool.SAVE) return
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        editStates[pageId] = scanEditEngine.resetCurrentToolDraft(state, tool)
        syncDraftFromState(pageId)
        refreshPreview(pageId)
    }

    fun revertCurrentEffect() {
        undo()
    }

    fun revertToOriginal() {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        editStates[pageId] = scanEditEngine.revertAll(state)
        syncDraftFromState(pageId)
        refreshPreview(pageId)
        persistIfApplied(pageId)
    }

    fun compareWithHistory(index: Int) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        val label = state.history.getOrNull(index)?.label ?: "Version $index"
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingPreview = true) }
            runCatching {
                val historical = scanEditEngine.stateAtHistory(state, index)
                val beforeBmp = withContext(Dispatchers.Default) { scanEditEngine.renderApplied(historical) }
                val tool = _uiState.value.activeTool?.takeUnless { it == ScanTool.SAVE }
                val afterBmp = withContext(Dispatchers.Default) { scanEditEngine.renderPreview(state, tool, null) }
                val docId = _uiState.value.documentId ?: 0
                val dir = imageStorage.createWorkingCopy(docId, pageId).parentFile!!
                val beforeFile = File(dir, "compare_before_$pageId.jpg")
                val afterFile = File(dir, "compare_after_$pageId.jpg")
                withContext(Dispatchers.IO) {
                    BitmapUtils.save(beforeBmp, beforeFile.absolutePath)
                    BitmapUtils.save(afterBmp, afterFile.absolutePath)
                }
                beforeFile.absolutePath to afterFile.absolutePath
            }.onSuccess { (before, after) ->
                _uiState.update {
                    it.copy(
                        isProcessingPreview = false,
                        showVersionCompare = true,
                        versionCompareBeforePath = before,
                        versionCompareAfterPath = after,
                        versionCompareLabel = label,
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isProcessingPreview = false) }
            }
        }
    }

    fun dismissVersionCompare() {
        _uiState.update {
            it.copy(
                showVersionCompare = false,
                versionCompareBeforePath = null,
                versionCompareAfterPath = null,
                versionCompareLabel = "",
            )
        }
    }

    fun restoreCropBoundaries() {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        editStates[pageId] = scanEditEngine.restoreCropBoundaries(state)
        syncDraftFromState(pageId)
        refreshPreview(pageId)
    }

    fun restoreOriginalColors() {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        editStates[pageId] = scanEditEngine.restoreOriginalColors(state)
        syncDraftFromState(pageId)
        refreshPreview(pageId)
    }

    fun jumpToStage(stage: EditStage) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        if (stage == EditStage.ORIGINAL) {
            revertToOriginal()
            return
        }
        val index = state.history.indexOfLast { it.stage == stage }
        if (index >= 0) {
            jumpToHistoryStage(index)
        }
    }

    fun updateCropSkew() {
        val corners = _uiState.value.draftCrop.corners
        val skew = scanEditEngine.detectSkew(corners)
        _uiState.update { it.copy(cropSkewDegrees = skew) }
    }

    fun jumpToHistoryStage(index: Int) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        editStates[pageId] = scanEditEngine.jumpToHistory(state, index)
        syncDraftFromState(pageId)
        refreshPreview(pageId)
        persistIfApplied(pageId)
    }

    fun dismissExportPreview() {
        _uiState.update { it.copy(showExportPreview = false, exportPreviewPaths = emptyList()) }
    }

    fun updateDraftCrop(block: (CropParams) -> CropParams) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        val updated = state.copy(draftCrop = block(state.draftCrop))
        editStates[pageId] = updated
        _uiState.update { it.copy(draftCrop = updated.draftCrop) }
        updateCropSkew()
        refreshPreview(pageId)
    }

    fun updateDraftTransition(block: (TransitionParams) -> TransitionParams) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        val updated = state.copy(draftTransition = block(state.draftTransition))
        editStates[pageId] = updated
        _uiState.update { it.copy(draftTransition = updated.draftTransition) }
        refreshPreview(pageId)
    }

    fun updateDraftClean(block: (CleanParams) -> CleanParams) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        val updated = state.copy(draftClean = block(state.draftClean))
        editStates[pageId] = updated
        _uiState.update { it.copy(draftClean = updated.draftClean) }
        refreshPreview(pageId)
    }

    fun updateDraftGray(block: (GrayParams) -> GrayParams) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        val updated = state.copy(draftGray = block(state.draftGray))
        editStates[pageId] = updated
        _uiState.update { it.copy(draftGray = updated.draftGray) }
        refreshPreview(pageId)
    }

    fun updateExportOptions(block: (ExportOptions) -> ExportOptions) {
        _uiState.update { it.copy(exportOptions = block(it.exportOptions)) }
    }

    fun dismissExportDialog() {
        _uiState.update { it.copy(showExportDialog = false, activeTool = null) }
    }

    fun autoDetectEdges(pageId: Long? = null) {
        val targetId = pageId ?: _uiState.value.selectedPageId ?: return
        viewModelScope.launch {
            val state = editStates[targetId] ?: return@launch
            val scanType = _uiState.value.draftTransition.scanType
            val bitmap = withContext(Dispatchers.IO) { BitmapUtils.load(state.originalPath) }
            val corners = scanEditEngine.autoDetectCrop(bitmap, scanType)
            updateDraftCrop { it.copy(corners = corners, preset = CropPreset.RECTANGLE) }
            updateDraftTransition { it.copy(corners = corners, autoDetect = true) }
            updateCropSkew()
        }
    }

    fun applyCropPreset(preset: CropPreset) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { BitmapUtils.load(state.originalPath) }
            val corners = when (preset) {
                CropPreset.FREEFORM -> state.draftCrop.corners
                CropPreset.RECTANGLE -> state.draftCrop.corners.toAxisAlignedRectangle()
                else -> scanEditEngine.presetCrop(preset, bitmap.width, bitmap.height)
            }
            updateDraftCrop { it.copy(preset = preset, corners = corners) }
        }
    }

    fun applyCurrentTool() {
        val tool = _uiState.value.activeTool ?: return
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                val updated = when (tool) {
                    ScanTool.ORIGINAL -> scanEditEngine.revertAll(state)
                    else -> scanEditEngine.applyTool(state, tool)
                }
                val rendered = withContext(Dispatchers.Default) {
                    scanEditEngine.renderApplied(updated)
                }
                val docId = _uiState.value.documentId ?: error("No document")
                val outFile = imageStorage.createWorkingCopy(docId, updated.pageId)
                val saved = withContext(Dispatchers.IO) {
                    BitmapUtils.save(rendered, outFile.absolutePath)
                }
                val finalState = updated.copy(workingPath = saved.path)
                editStates[pageId] = finalState
                documentRepository.updatePageImage(
                    pageId = pageId,
                    imagePath = saved.path,
                    width = saved.width,
                    height = saved.height,
                    editStateJson = finalState.toJson(),
                )
                finalState
            }.onSuccess { finalState ->
                _uiState.update { stateUi ->
                    stateUi.copy(
                        isSaving = false,
                        pages = stateUi.pages.map { p ->
                            if (p.id == pageId) p.copy(imagePath = finalState.workingPath) else p
                        },
                        canUndo = finalState.historyIndex >= 0,
                        canRedo = finalState.historyIndex < finalState.history.lastIndex,
                        editHistory = historyChips(finalState),
                        activeTool = if (tool == ScanTool.ORIGINAL) null else stateUi.activeTool,
                    )
                }
                syncDraftFromState(pageId)
                refreshPreview(pageId)
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun revertAllEdits() = applyCurrentToolWithOriginal()

    private fun applyCurrentToolWithOriginal() {
        _uiState.update { it.copy(activeTool = ScanTool.ORIGINAL) }
        applyCurrentTool()
    }

    fun undo() {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        editStates[pageId] = scanEditEngine.undo(state)
        syncDraftFromState(pageId)
        refreshPreview(pageId)
        persistIfApplied(pageId)
    }

    fun redo() {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        editStates[pageId] = scanEditEngine.redo(state)
        syncDraftFromState(pageId)
        refreshPreview(pageId)
        persistIfApplied(pageId)
    }

    fun exportDocument() {
        val options = _uiState.value.exportOptions
        val paths = _uiState.value.pages.map { it.imagePath }
        if (paths.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) { scanExportService.export(paths, options) }
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        showExportDialog = false,
                        activeTool = null,
                        showExportPreview = true,
                        exportPreviewPaths = result.paths,
                        exportMessage = "Saved ${result.paths.size} file(s) to Documents/AILanguageTutor/",
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun clearExportMessage() {
        _uiState.update { it.copy(exportMessage = null) }
    }

    private fun persistIfApplied(pageId: Long) {
        val state = editStates[pageId] ?: return
        viewModelScope.launch {
            runCatching {
                val rendered = withContext(Dispatchers.Default) { scanEditEngine.renderApplied(state) }
                val docId = _uiState.value.documentId ?: return@runCatching
                val outFile = imageStorage.createWorkingCopy(docId, state.pageId)
                val saved = withContext(Dispatchers.IO) { BitmapUtils.save(rendered, outFile.absolutePath) }
                val finalState = state.copy(workingPath = saved.path)
                editStates[pageId] = finalState
                documentRepository.updatePageImage(pageId, saved.path, saved.width, saved.height, finalState.toJson())
            }
        }
    }

    private fun syncDraftFromState(pageId: Long) {
        val state = editStates[pageId] ?: return
        _uiState.update {
            it.copy(
                draftCrop = state.draftCrop,
                draftTransition = state.draftTransition,
                draftClean = state.draftClean,
                draftGray = state.draftGray,
                canUndo = state.historyIndex >= 0,
                canRedo = state.historyIndex < state.history.lastIndex,
                editHistory = historyChips(state),
                cropSkewDegrees = scanEditEngine.detectSkew(state.draftCrop.corners),
                appliedStages = state.appliedStages().toSet(),
            )
        }
    }

    private fun historyChips(state: PageEditState): List<Pair<Int, String>> =
        state.history.mapIndexed { index, entry -> index to entry.label }

    private fun refreshPreview(pageId: Long, forceOriginal: Boolean = false) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val state = editStates[pageId] ?: return@launch
            _uiState.update { it.copy(isProcessingPreview = true) }
            runCatching {
                if (forceOriginal || _uiState.value.compareOriginal) {
                    state.originalPath
                } else {
                    val tool = _uiState.value.activeTool?.takeUnless { it == ScanTool.SAVE }
                    val useBlend = tool != null
                    val beforeAfter = if (useBlend) _uiState.value.beforeAfterSlider else null
                    val bitmap = withContext(Dispatchers.Default) {
                        scanEditEngine.renderPreview(state, tool, beforeAfter)
                    }
                    val previewFile = File(
                        imageStorage.createWorkingCopy(_uiState.value.documentId ?: 0, pageId).parentFile,
                        "preview_$pageId.jpg",
                    )
                    withContext(Dispatchers.IO) { BitmapUtils.save(bitmap, previewFile.absolutePath) }
                    previewFile.absolutePath
                }
            }.onSuccess { path ->
                _uiState.update { it.copy(previewPath = path, isProcessingPreview = false) }
            }.onFailure {
                _uiState.update { it.copy(isProcessingPreview = false) }
            }
        }
    }
}
