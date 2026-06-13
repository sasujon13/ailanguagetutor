package com.cheradip.ailanguagetutor.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.database.repository.DocumentRepository
import com.cheradip.ailanguagetutor.core.device.ScanWorkflowRepository
import com.cheradip.ailanguagetutor.core.device.ScanWorkflowSession
import com.cheradip.ailanguagetutor.core.device.ScanWorkflowStage
import com.cheradip.ailanguagetutor.core.image.BitmapUtils
import com.cheradip.ailanguagetutor.core.image.CleanParams
import com.cheradip.ailanguagetutor.core.image.CropParams
import com.cheradip.ailanguagetutor.core.image.CropPreset
import com.cheradip.ailanguagetutor.core.image.DocumentDetectionHints
import com.cheradip.ailanguagetutor.core.image.EditHistoryEntry
import com.cheradip.ailanguagetutor.core.image.EditHistorySnapshot
import com.cheradip.ailanguagetutor.core.image.EditStage
import com.cheradip.ailanguagetutor.core.image.ExportConflictResolution
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class PreviewCompareMode {
    AFTER,
    ORIGINAL,
    BEFORE,
}

data class ScannerPageItem(
    val id: Long,
    /** Last applied / working image shown in thumbnail after Apply. */
    val imagePath: String,
    val originalPath: String,
    val pageIndex: Int,
) {
    /** Thumbnail source: original until first apply, then working image. */
    fun thumbnailPath(applied: Boolean): String = if (applied) imagePath else originalPath
}

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
    val previewRevision: Long = 0L,
    val previewCompareMode: PreviewCompareMode = PreviewCompareMode.AFTER,
    val showExportDialog: Boolean = false,
    val showExportConflictDialog: Boolean = false,
    val exportConflictExisting: List<String> = emptyList(),
    val exportConflictRenameHint: String = "",
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
    private val scanWorkflowRepository: ScanWorkflowRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val editStates = mutableMapOf<Long, PageEditState>()
    private var previewJob: Job? = null
    private var previewGeneration = 0L
    private val captureMutex = Mutex()
    private var documentSourceType: String = "scan"
    private var launchMode: String = "camera"
    private var scanOnly: Boolean = false

    fun initDocument(
        existingId: Long?,
        sourceType: String = "scan",
        mode: String = "camera",
        scanOnlyMode: Boolean = false,
    ) {
        documentSourceType = sourceType
        launchMode = mode
        scanOnly = scanOnlyMode
        viewModelScope.launch {
            val saved = scanWorkflowRepository.currentSession()
            val resolvedId = existingId ?: resolveResumeDocumentId(saved)
            if (resolvedId != null) {
                applySessionMeta(saved?.takeIf { it.documentId == resolvedId })
                loadDocument(resolvedId)
                restoreWorkflowUi(saved?.takeIf { it.documentId == resolvedId })
            } else if (_uiState.value.documentId == null) {
                val activeLang = languagePackRepository.observeActive().first()
                    .firstOrNull()?.languageCode ?: "en"
                val titlePrefix = if (documentSourceType == "import") "Upload" else "Scan"
                val id = documentRepository.createDocument(
                    title = "$titlePrefix ${System.currentTimeMillis()}",
                    languageCode = activeLang,
                    sourceType = documentSourceType,
                )
                _uiState.update { it.copy(documentId = id) }
                persistWorkflow(stage = ScanWorkflowStage.SCANNER)
            }
        }
    }

    fun prepareForOcr() {
        viewModelScope.launch {
            persistWorkflow(stage = ScanWorkflowStage.OCR)
        }
    }

    fun markScanOnlyComplete() {
        viewModelScope.launch { scanWorkflowRepository.clear() }
    }

    private suspend fun resolveResumeDocumentId(saved: ScanWorkflowSession?): Long? {
        if (saved == null || saved.stage != ScanWorkflowStage.SCANNER) return null
        if (documentRepository.getDocument(saved.documentId) == null) {
            scanWorkflowRepository.clear()
            return null
        }
        if (documentRepository.isOcrComplete(saved.documentId)) {
            scanWorkflowRepository.clear()
            return null
        }
        return saved.documentId
    }

    private fun applySessionMeta(session: ScanWorkflowSession?) {
        if (session == null) return
        launchMode = session.mode
        scanOnly = session.scanOnly
        documentSourceType = if (session.mode == "import") "import" else "scan"
    }

    private suspend fun restoreWorkflowUi(session: ScanWorkflowSession?) {
        if (session == null || session.documentId != _uiState.value.documentId) return
        val tool = session.activeTool?.let { runCatching { ScanTool.valueOf(it) }.getOrNull() }
        _uiState.update {
            it.copy(
                selectedPageId = session.selectedPageId ?: it.selectedPageId,
                activeTool = tool ?: it.activeTool,
            )
        }
        (session.selectedPageId ?: _uiState.value.selectedPageId)?.let { refreshPreview(it) }
    }

    private fun persistWorkflow(stage: ScanWorkflowStage = ScanWorkflowStage.SCANNER) {
        val docId = _uiState.value.documentId ?: return
        viewModelScope.launch {
            scanWorkflowRepository.save(
                ScanWorkflowSession(
                    documentId = docId,
                    stage = stage,
                    mode = launchMode,
                    scanOnly = scanOnly,
                    selectedPageId = _uiState.value.selectedPageId,
                    activeTool = _uiState.value.activeTool?.name,
                ),
            )
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
        persistWorkflow(stage = ScanWorkflowStage.SCANNER)
    }

    fun onPhotoCaptured(bytes: ByteArray) {
        viewModelScope.launch {
            captureMutex.withLock {
                addCapturedPage(bytes, enhance = true, openCropTool = true)
            }
        }
    }

    fun onPhotosCaptured(bytesList: List<ByteArray>) {
        if (bytesList.isEmpty()) return
        viewModelScope.launch {
            captureMutex.withLock {
                _uiState.update { it.copy(isSaving = true, error = null) }
                runCatching {
                    val addedIds = mutableListOf<Long>()
                    bytesList.forEach { bytes ->
                        addCapturedPage(
                            bytes = bytes,
                            enhance = false,
                            openCropTool = false,
                            batchMode = true,
                        )?.let { addedIds.add(it) }
                    }
                    addedIds.lastOrNull()?.let { refreshPreview(it) }
                }.onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun onGalleryImage(bytes: ByteArray) = onPhotoCaptured(bytes)

    fun pageThumbnailPath(page: ScannerPageItem): String {
        val state = editStates[page.id]
        val hasApplied = state?.let {
            it.appliedCrop != null || it.appliedTransition != null ||
                it.appliedClean != null || it.appliedGray != null
        } ?: false
        return page.thumbnailPath(hasApplied)
    }

    private suspend fun addCapturedPage(
        bytes: ByteArray,
        enhance: Boolean,
        openCropTool: Boolean,
        batchMode: Boolean = false,
    ): Long? {
        val docId = _uiState.value.documentId ?: return null
        if (!batchMode) {
            _uiState.update { it.copy(isSaving = true, error = null) }
        }
        return try {
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
            val item = ScannerPageItem(pageId, saved.path, saved.originalPath, pageIndex)
            _uiState.update {
                it.copy(
                    isSaving = if (batchMode) it.isSaving else false,
                    pageCount = it.pageCount + 1,
                    pages = it.pages + item,
                    selectedPageId = item.id,
                    activeTool = if (openCropTool) ScanTool.CROP else it.activeTool,
                )
            }
            syncDraftFromState(item.id)
            if (enhance) {
                autoEnhanceCapturedPage(item.id, openCropTool)
            } else if (!batchMode) {
                refreshPreview(item.id)
            }
            persistWorkflow()
            item.id
        } catch (e: Exception) {
            if (!batchMode) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            } else {
                throw e
            }
            null
        }
    }

    fun openSaveExportDialog() {
        _uiState.update { it.copy(showExportDialog = true, activeTool = ScanTool.SAVE) }
    }

    fun selectPage(pageId: Long) {
        _uiState.update { it.copy(selectedPageId = pageId, activeTool = null) }
        syncDraftFromState(pageId)
        refreshPreview(pageId)
        persistWorkflow()
    }

    fun openTool(tool: ScanTool) {
        if (tool == ScanTool.SAVE) {
            _uiState.update { it.copy(showExportDialog = true, activeTool = ScanTool.SAVE) }
            persistWorkflow()
            return
        }
        val pageId = _uiState.value.selectedPageId
        _uiState.update { it.copy(activeTool = tool, previewCompareMode = PreviewCompareMode.AFTER) }
        persistWorkflow()
        pageId ?: return
        if (tool == ScanTool.ORIGINAL) {
            refreshPreview(pageId, forceOriginal = true)
        } else {
            refreshPreview(pageId)
        }
    }

    fun closeToolPanel() {
        _uiState.update { it.copy(activeTool = null, previewCompareMode = PreviewCompareMode.AFTER) }
        _uiState.value.selectedPageId?.let { refreshPreview(it) }
        persistWorkflow()
    }

    fun setPreviewCompareMode(mode: PreviewCompareMode) {
        _uiState.update { it.copy(previewCompareMode = mode) }
        _uiState.value.selectedPageId?.let { refreshPreview(it) }
    }

    fun deleteSelectedPage() {
        val pageId = _uiState.value.selectedPageId ?: return
        val docId = _uiState.value.documentId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                editStates.remove(pageId)
                documentRepository.deletePage(pageId)
                val pages = documentRepository.getPages(docId)
                val items = pages.map { page ->
                    val original = page.originalImagePath ?: page.imagePath
                    editStates.getOrPut(page.id) {
                        parsePageEditStateJson(
                            pageId = page.id,
                            json = page.editStateJson,
                            originalPath = original,
                            workingPath = page.imagePath,
                        )
                    }
                    ScannerPageItem(page.id, page.imagePath, original, page.pageIndex)
                }
                val nextSelected = items.firstOrNull()?.id
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        pageCount = items.size,
                        pages = items,
                        selectedPageId = nextSelected,
                        activeTool = if (items.isEmpty()) null else it.activeTool,
                        previewCompareMode = PreviewCompareMode.AFTER,
                    )
                }
                nextSelected?.let {
                    syncDraftFromState(it)
                    refreshPreview(it)
                } ?: _uiState.update { it.copy(previewPath = null) }
                persistWorkflow()
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
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
        refreshPreview(pageId, debounceMs = 75)
    }

    fun updateDraftTransition(block: (TransitionParams) -> TransitionParams) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        val updated = state.copy(draftTransition = block(state.draftTransition))
        editStates[pageId] = updated
        _uiState.update { it.copy(draftTransition = updated.draftTransition) }
        refreshPreview(pageId, debounceMs = 75)
    }

    fun updateDraftClean(block: (CleanParams) -> CleanParams) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        val updated = state.copy(draftClean = block(state.draftClean))
        editStates[pageId] = updated
        _uiState.update { it.copy(draftClean = updated.draftClean) }
        refreshPreview(pageId, debounceMs = 75)
    }

    fun updateDraftGray(block: (GrayParams) -> GrayParams) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        val updated = state.copy(draftGray = block(state.draftGray))
        editStates[pageId] = updated
        _uiState.update { it.copy(draftGray = updated.draftGray) }
        refreshPreview(pageId, debounceMs = 75)
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
            autoEnhanceCapturedPage(targetId, openCropTool = _uiState.value.activeTool == ScanTool.CROP)
        }
    }

    /** CamScanner-style auto crop, perspective, and background clean right after capture. */
    private suspend fun autoEnhanceCapturedPage(pageId: Long, openCropTool: Boolean) {
        val state = editStates[pageId] ?: return
        val hints = DocumentDetectionHints(
            scanType = _uiState.value.draftTransition.scanType,
            cropPreset = CropPreset.RECTANGLE,
        )
        val bitmap = withContext(Dispatchers.IO) { BitmapUtils.load(state.originalPath) }
        val corners = scanEditEngine.autoDetectCrop(bitmap, hints)
        val cropDraft = CropParams(
            corners = corners,
            preset = CropPreset.RECTANGLE,
            perspectiveCorrection = true,
            autoStraighten = true,
        )
        val transitionDraft = TransitionParams(
            corners = corners,
            autoDetect = true,
            perspectiveStrength = 85,
        )
        val cleanDraft = CleanParams(
            autoEnhance = true,
            paperWhitening = 55,
            shadowRemoval = 55,
            noiseReduction = 45,
            brightness = 52,
            contrast = 58,
            sharpness = 48,
        )
        var updated = state.copy(
            draftCrop = cropDraft,
            draftTransition = transitionDraft,
            draftClean = cleanDraft,
        )
        updated = scanEditEngine.applyTool(updated, ScanTool.CROP)
        updated = scanEditEngine.applyTool(updated, ScanTool.TRANSITION)
        updated = scanEditEngine.applyTool(updated, ScanTool.CLEAN)
        val rendered = withContext(Dispatchers.Default) { scanEditEngine.renderApplied(updated) }
        val docId = _uiState.value.documentId ?: return
        val outFile = imageStorage.createWorkingCopy(docId, updated.pageId)
        val saved = withContext(Dispatchers.IO) { BitmapUtils.save(rendered, outFile.absolutePath) }
        val finalState = updated.copy(workingPath = saved.path)
        editStates[pageId] = finalState
        documentRepository.updatePageImage(
            pageId = pageId,
            imagePath = saved.path,
            width = saved.width,
            height = saved.height,
            editStateJson = finalState.toJson(),
        )
        _uiState.update { ui ->
            ui.copy(
                pages = ui.pages.map { page ->
                    if (page.id == pageId) page.copy(imagePath = saved.path) else page
                },
                selectedPageId = pageId,
                activeTool = if (openCropTool) ScanTool.CROP else ui.activeTool,
                cropSkewDegrees = scanEditEngine.detectSkew(cropDraft.corners),
            )
        }
        syncDraftFromState(pageId)
        refreshPreview(pageId)
    }

    fun applyCropPreset(preset: CropPreset) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { BitmapUtils.load(state.originalPath) }
            val corners = when (preset) {
                CropPreset.FREEFORM -> state.draftCrop.corners
                CropPreset.RECTANGLE -> state.draftCrop.corners.toAxisAlignedRectangle()
                CropPreset.ID_CARD,
                CropPreset.BUSINESS_CARD,
                CropPreset.PASSPORT,
                -> {
                    val hints = DocumentDetectionHints(
                        scanType = _uiState.value.draftTransition.scanType,
                        cropPreset = preset,
                    )
                    scanEditEngine.autoDetectCrop(bitmap, hints)
                }
                else -> scanEditEngine.presetCrop(preset, bitmap.width, bitmap.height)
            }
            updateDraftCrop {
                it.copy(
                    preset = preset,
                    corners = corners,
                    perspectiveCorrection = preset != CropPreset.FREEFORM,
                )
            }
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
                val plan = withContext(Dispatchers.IO) { scanExportService.planExport(paths.size, options) }
                if (plan.hasConflict) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            showExportConflictDialog = true,
                            exportConflictExisting = plan.conflictingFiles.map { f -> f.name },
                            exportConflictRenameHint = plan.renameTargets.joinToString(", ") { f -> f.name },
                        )
                    }
                    return@launch
                }
                performExport(paths, options, ExportConflictResolution.RENAME)
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun confirmExportReplace() {
        val options = _uiState.value.exportOptions
        val paths = _uiState.value.pages.map { it.imagePath }
        viewModelScope.launch {
            _uiState.update { it.copy(showExportConflictDialog = false, isSaving = true) }
            runCatching {
                performExport(paths, options, ExportConflictResolution.REPLACE)
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun confirmExportRename() {
        val options = _uiState.value.exportOptions
        val paths = _uiState.value.pages.map { it.imagePath }
        viewModelScope.launch {
            _uiState.update { it.copy(showExportConflictDialog = false, isSaving = true) }
            runCatching {
                performExport(paths, options, ExportConflictResolution.RENAME)
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun dismissExportConflictDialog() {
        _uiState.update {
            it.copy(
                showExportConflictDialog = false,
                exportConflictExisting = emptyList(),
                exportConflictRenameHint = "",
            )
        }
    }

    private suspend fun performExport(
        paths: List<String>,
        options: ExportOptions,
        resolution: ExportConflictResolution,
    ) {
        val result = withContext(Dispatchers.IO) {
            scanExportService.export(paths, options, resolution)
        }
        _uiState.update {
            it.copy(
                isSaving = false,
                showExportDialog = false,
                showExportConflictDialog = false,
                exportConflictExisting = emptyList(),
                exportConflictRenameHint = "",
                activeTool = null,
                showExportPreview = true,
                exportPreviewPaths = result.paths,
                exportMessage = "Saved ${result.paths.size} file(s) to Documents/AILanguageTutor/",
            )
        }
        if (scanOnly) {
            viewModelScope.launch { scanWorkflowRepository.clear() }
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

    private fun refreshPreview(
        pageId: Long,
        forceOriginal: Boolean = false,
        debounceMs: Long = 0,
    ) {
        previewJob?.cancel()
        val generation = ++previewGeneration
        previewJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            if (generation != previewGeneration) return@launch
            val state = editStates[pageId] ?: return@launch
            _uiState.update { it.copy(isProcessingPreview = true) }
            runCatching {
                val tool = _uiState.value.activeTool?.takeUnless { it == ScanTool.SAVE || it == ScanTool.ORIGINAL }
                val compareMode = _uiState.value.previewCompareMode
                val bitmap = withContext(Dispatchers.Default) {
                    when {
                        compareMode == PreviewCompareMode.ORIGINAL -> scanEditEngine.renderOriginal(state)
                        compareMode == PreviewCompareMode.BEFORE -> scanEditEngine.renderBeforeCurrentTool(state)
                        tool != null -> scanEditEngine.renderAfterCurrentTool(state, tool)
                        else -> scanEditEngine.renderApplied(state)
                    }
                }
                if (generation != previewGeneration) return@runCatching null
                val revision = _uiState.value.previewRevision + 1
                val previewFile = File(
                    imageStorage.createWorkingCopy(_uiState.value.documentId ?: 0, pageId).parentFile,
                    "preview_${pageId}_$revision.jpg",
                )
                withContext(Dispatchers.IO) { BitmapUtils.save(bitmap, previewFile.absolutePath) }
                previewFile.absolutePath
            }.onSuccess { path ->
                if (generation != previewGeneration || path == null) {
                    _uiState.update { it.copy(isProcessingPreview = false) }
                    return@onSuccess
                }
                _uiState.update {
                    it.copy(
                        previewPath = path,
                        previewRevision = it.previewRevision + 1,
                        isProcessingPreview = false,
                    )
                }
            }.onFailure {
                if (generation == previewGeneration) {
                    _uiState.update { it.copy(isProcessingPreview = false) }
                }
            }
        }
    }
}
