package com.cheradip.ailanguagetutor.core.image

import android.graphics.Bitmap
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

class ScanEditEngine {
    fun renderPreview(
        state: PageEditState,
        tool: ScanTool? = null,
        beforeAfter: Float? = null,
        customPresets: List<DocumentFilterPreset> = emptyList(),
    ): Bitmap {
        val source = BitmapUtils.load(state.originalPath)
        val edited = when {
            tool == ScanTool.ORIGINAL -> renderApplied(state, customPresets)
            tool == ScanTool.CLEAN -> renderCleanToolPreview(state, customPresets)
            else -> renderPipeline(
                source = source,
                crop = previewCrop(state, tool),
                transition = previewTransition(state, tool),
                clean = previewClean(state, tool),
                gray = previewGray(state, tool),
                customPresets = customPresets,
            )
        }
        return if (beforeAfter != null) {
            ScanBeforeAfterBlend.blend(source, edited, beforeAfter)
        } else {
            edited
        }
    }

    fun detectSkew(corners: QuadPoints): Float = DocumentEdgeDetector.detectSkewDegrees(corners)

    fun renderApplied(state: PageEditState, customPresets: List<DocumentFilterPreset> = emptyList()): Bitmap {
        val source = BitmapUtils.load(state.originalPath)
        val usesStack = state.appliedFilterSelection?.let {
            it.presetIds.isNotEmpty() || it.adjustments.isNotEmpty()
        } == true
        return renderPipeline(
            source = source,
            crop = state.appliedCrop,
            transition = state.appliedTransition,
            clean = if (usesStack) null else state.appliedClean,
            gray = if (usesStack) null else state.appliedGray,
            filterSelection = state.appliedFilterSelection,
            customPresets = customPresets,
        )
    }

    private fun renderCleanToolPreview(
        state: PageEditState,
        customPresets: List<DocumentFilterPreset>,
    ): Bitmap {
        val base = renderApplied(state, customPresets)
        val draft = state.draftFilterSelection
        if (draft.presetIds.isEmpty() && draft.adjustments.isEmpty()) return base
        return CleanFilterRenderer.applyStack(base, draft, customPresets)
    }

    /** Raw capture — no edits applied. */
    fun renderOriginal(state: PageEditState): Bitmap = BitmapUtils.load(state.originalPath)

    /** Committed pipeline (before current tool draft changes). */
    fun renderBeforeCurrentTool(state: PageEditState): Bitmap = renderApplied(state)

    /** Live preview with the active tool's draft settings. */
    fun renderAfterCurrentTool(state: PageEditState, tool: ScanTool?): Bitmap =
        renderPreview(state, tool, beforeAfter = null)

    fun renderAtHistoryIndex(
        state: PageEditState,
        index: Int,
        customPresets: List<DocumentFilterPreset> = emptyList(),
    ): Bitmap {
        val historical = stateAtHistory(state, index)
        return renderApplied(historical, customPresets)
    }

    fun autoDetectCrop(
        bitmap: Bitmap,
        hints: DocumentDetectionHints = DocumentDetectionHints(),
    ): QuadPoints = DocumentEdgeDetector.detectCorners(bitmap, hints)

    fun presetCrop(preset: CropPreset, imageWidth: Int, imageHeight: Int): QuadPoints {
        val ratio = preset.aspectRatio() ?: return QuadPoints()
        val imageRatio = imageWidth.toFloat() / imageHeight
        return if (ratio > imageRatio) {
            val h = imageRatio / ratio
            val pad = (1f - h) / 2f
            QuadPoints(
                topLeft = PointF(0.05f, pad),
                topRight = PointF(0.95f, pad),
                bottomRight = PointF(0.95f, 1f - pad),
                bottomLeft = PointF(0.05f, 1f - pad),
            )
        } else {
            val w = ratio / imageRatio
            val pad = (1f - w) / 2f
            QuadPoints(
                topLeft = PointF(pad, 0.05f),
                topRight = PointF(1f - pad, 0.05f),
                bottomRight = PointF(1f - pad, 0.95f),
                bottomLeft = PointF(pad, 0.95f),
            )
        }
    }

    fun applyTool(state: PageEditState, tool: ScanTool): PageEditState {
        val next = when (tool) {
            ScanTool.CROP -> {
                val params = state.draftCrop
                state.copy(appliedCrop = params, draftCrop = params)
            }
            ScanTool.TRANSITION -> {
                val params = state.draftTransition
                state.copy(appliedTransition = params, draftTransition = params)
            }
            ScanTool.CLEAN -> {
                val selection = state.draftFilterSelection
                val hasDraftFilters = selection.presetIds.isNotEmpty() || selection.adjustments.isNotEmpty()
                val appliedSelection = if (hasDraftFilters) {
                    mergeFilterSelection(state.appliedFilterSelection, selection)
                } else {
                    state.appliedFilterSelection ?: CleanFilterSelection()
                }
                state.copy(
                    appliedClean = null,
                    appliedGray = null,
                    appliedFilterSelection = appliedSelection,
                    draftClean = CleanParams(),
                    draftGray = GrayParams(),
                    draftFilterSelection = CleanFilterSelection(),
                )
            }
            ScanTool.GRAY -> {
                val params = state.draftGray.takeIf { it.active }
                state.copy(appliedGray = params, draftGray = state.draftGray)
            }
            else -> state
        }
        val stage = when (tool) {
            ScanTool.CROP -> EditStage.CROP
            ScanTool.TRANSITION -> EditStage.TRANSITION
            ScanTool.CLEAN -> EditStage.CLEAN
            ScanTool.GRAY -> EditStage.GRAY
            else -> EditStage.ORIGINAL
        }
        return appendHistory(next, stage, historyLabel(next, tool, state))
    }

    private fun historyLabel(next: PageEditState, tool: ScanTool, previous: PageEditState): String = when (tool) {
        ScanTool.CLEAN -> {
            val added = previous.draftFilterSelection
            val desc = CleanFilterRenderer.describeSelection(added, emptyList())
            if (desc.isBlank()) "Clean applied" else "Clean: $desc"
        }
        else -> "${tool.name.lowercase().replaceFirstChar { it.uppercase() }} applied"
    }

    private fun mergeFilterSelection(
        base: CleanFilterSelection?,
        draft: CleanFilterSelection,
    ): CleanFilterSelection {
        val presets = (base?.presetIds.orEmpty() + draft.presetIds).distinct()
        val adjustments = base?.adjustments.orEmpty() + draft.adjustments
        return CleanFilterSelection(presetIds = presets, adjustments = adjustments)
    }

    fun revertAll(state: PageEditState): PageEditState {
        val cleared = state.copy(
            appliedCrop = null,
            appliedTransition = null,
            appliedClean = null,
            appliedGray = null,
            draftCrop = CropParams(corners = QuadPoints.fullFrame()),
            draftTransition = TransitionParams(),
            draftClean = CleanParams(),
            draftGray = GrayParams(),
        )
        return appendHistory(cleared, EditStage.ORIGINAL, "Reverted to original")
    }

    fun resetCurrentToolDraft(state: PageEditState, tool: ScanTool): PageEditState = when (tool) {
        ScanTool.CROP -> state.copy(draftCrop = state.appliedCrop ?: CropParams(corners = QuadPoints.fullFrame()))
        ScanTool.TRANSITION -> state.copy(draftTransition = state.appliedTransition ?: TransitionParams())
        ScanTool.CLEAN -> state.copy(
            draftClean = CleanParams(),
            draftGray = GrayParams(),
            draftFilterSelection = CleanFilterSelection(),
        )
        ScanTool.GRAY -> state.copy(draftGray = state.appliedGray ?: GrayParams())
        else -> state
    }

    fun revertAppliedEffect(state: PageEditState, tool: ScanTool): PageEditState = when (tool) {
        ScanTool.CROP -> state.copy(
            appliedCrop = null,
            draftCrop = CropParams(corners = QuadPoints.fullFrame()),
        )
        ScanTool.TRANSITION -> state.copy(appliedTransition = null, draftTransition = TransitionParams())
        ScanTool.CLEAN -> state.copy(
            appliedClean = null,
            appliedGray = null,
            appliedFilterSelection = null,
            draftClean = CleanParams(),
            draftGray = GrayParams(),
            draftFilterSelection = CleanFilterSelection(),
            draftTransition = TransitionParams(),
        )
        ScanTool.GRAY -> state.copy(appliedGray = null, draftGray = GrayParams())
        else -> state
    }

    /** Non-destructive snapshot at a history index (does not mutate stored history). */
    fun stateAtHistory(state: PageEditState, index: Int): PageEditState = jumpToHistory(state, index)

    fun restoreCropBoundaries(state: PageEditState): PageEditState =
        state.copy(draftCrop = state.draftCrop.copy(corners = QuadPoints.fullFrame()))

    fun restoreOriginalColors(state: PageEditState): PageEditState =
        state.copy(
            appliedClean = null,
            appliedGray = null,
            draftClean = CleanParams(),
            draftGray = GrayParams(),
        )

    fun jumpToHistory(state: PageEditState, index: Int): PageEditState {
        if (index < 0) return revertAll(state.copy(history = state.history, historyIndex = state.historyIndex))
        val entry = state.history.getOrNull(index) ?: return state
        val snap = entry.snapshot ?: return state.copy(historyIndex = index)
        return state.copy(
            appliedCrop = snap.appliedCrop,
            appliedTransition = snap.appliedTransition,
            appliedClean = snap.appliedClean,
            appliedGray = snap.appliedGray,
            appliedFilterSelection = snap.appliedFilterSelection,
            draftCrop = snap.appliedCrop ?: CropParams(corners = QuadPoints.fullFrame()),
            draftTransition = snap.appliedTransition ?: TransitionParams(),
            draftClean = snap.appliedClean ?: CleanParams(),
            draftGray = snap.appliedGray ?: GrayParams(),
            draftFilterSelection = snap.appliedFilterSelection ?: CleanFilterSelection(),
            historyIndex = index,
        )
    }

    fun undo(state: PageEditState): PageEditState {
        if (state.historyIndex <= 0) return revertAll(state)
        return jumpToHistory(state, state.historyIndex - 1)
    }

    fun redo(state: PageEditState): PageEditState {
        if (state.historyIndex >= state.history.lastIndex) return state
        return jumpToHistory(state, state.historyIndex + 1)
    }

    private fun appendHistory(state: PageEditState, stage: EditStage, label: String): PageEditState {
        val trimmed = if (state.historyIndex >= 0 && state.historyIndex < state.history.lastIndex) {
            state.history.take(state.historyIndex + 1)
        } else {
            state.history
        }
        val entry = EditHistoryEntry(
            stage = stage,
            label = label,
            snapshot = EditHistorySnapshot(
                appliedCrop = state.appliedCrop,
                appliedTransition = state.appliedTransition,
                appliedClean = state.appliedClean,
                appliedGray = state.appliedGray,
                appliedFilterSelection = state.appliedFilterSelection,
            ),
        )
        val newHistory = trimmed + entry
        return state.copy(history = newHistory, historyIndex = newHistory.lastIndex)
    }

    private fun previewCrop(state: PageEditState, tool: ScanTool?) =
        if (tool == ScanTool.CROP) state.draftCrop else state.appliedCrop

    private fun previewTransition(state: PageEditState, tool: ScanTool?) =
        when (tool) {
            ScanTool.TRANSITION, ScanTool.CLEAN -> state.draftTransition
            else -> state.appliedTransition
        }

    private fun previewClean(state: PageEditState, tool: ScanTool?) =
        when (tool) {
            ScanTool.CLEAN -> null
            else -> state.appliedClean
        }

    private fun previewGray(state: PageEditState, tool: ScanTool?) =
        when (tool) {
            ScanTool.CLEAN -> null
            ScanTool.GRAY -> state.draftGray.takeIf { it.active } ?: state.appliedGray
            ScanTool.CROP -> state.appliedGray
            else -> state.appliedGray
        }

    private fun renderPipeline(
        source: Bitmap,
        crop: CropParams?,
        transition: TransitionParams?,
        clean: CleanParams?,
        gray: GrayParams?,
        filterSelection: CleanFilterSelection? = null,
        customPresets: List<DocumentFilterPreset> = emptyList(),
    ): Bitmap {
        var bmp = source
        crop?.let { bmp = applyCrop(bmp, it) }
        transition?.let { bmp = applyTransition(bmp, it) }
        val selection = filterSelection?.takeIf { it.presetIds.isNotEmpty() || it.adjustments.isNotEmpty() }
        if (selection != null) {
            bmp = CleanFilterRenderer.applyStack(bmp, selection, customPresets)
        } else {
            clean?.let { bmp = ImageCleanProcessor.apply(bmp, it) }
            if (gray?.active == true) {
                bmp = ImageGrayProcessor.apply(bmp, gray)
            }
        }
        return bmp
    }

    private fun applyCrop(bitmap: Bitmap, params: CropParams): Bitmap {
        var corners = params.corners
        if (params.keystoneCorrection) {
            corners = PerspectiveTransform.applyKeystoneCorrection(corners, 50, 50)
        }
        if (params.horizontalAlignment || params.verticalAlignment) {
            corners = alignCorners(corners, params.horizontalAlignment, params.verticalAlignment)
        }
        var result = bitmap
        var rotation = params.rotationDegrees
        if (params.autoStraighten) {
            rotation += computeStraightenDegrees(corners)
        }
        if (rotation != 0f) result = BitmapUtils.rotate(result, rotation)
        val pxCorners = PerspectiveTransform.cornersToPixels(corners, result.width, result.height)
        return if (params.perspectiveCorrection) {
            PerspectiveTransform.warp(result, pxCorners, 1f)
        } else {
            cropRect(result, corners)
        }
    }

    private fun applyTransition(bitmap: Bitmap, params: TransitionParams): Bitmap {
        var corners = if (params.autoDetect) {
            DocumentEdgeDetector.detectCorners(
                bitmap,
                DocumentDetectionHints(scanType = params.scanType),
            )
        } else {
            params.corners
        }
        corners = PerspectiveTransform.applyKeystoneCorrection(
            corners,
            params.verticalCorrection,
            params.horizontalCorrection,
        )
        if (params.curvedPageCorrection) {
            val curve = params.pageFlattening / 200f
            corners = corners.copy(
                bottomLeft = corners.bottomLeft.copy(x = corners.bottomLeft.x + curve),
                bottomRight = corners.bottomRight.copy(x = corners.bottomRight.x - curve),
            )
        }
        var result = bitmap
        var rotation = params.rotationDegrees
        if (params.autoStraightenText) {
            rotation += computeStraightenDegrees(corners)
        }
        if (rotation != 0f) result = BitmapUtils.rotate(result, rotation)
        val px = PerspectiveTransform.cornersToPixels(corners, result.width, result.height)
        val strength = params.perspectiveStrength / 100f * (params.pageFlattening / 100f + 0.5f)
        return PerspectiveTransform.warp(result, px, strength.coerceIn(0.1f, 1f))
    }

    private fun alignCorners(quad: QuadPoints, horizontal: Boolean, vertical: Boolean): QuadPoints {
        if (horizontal) {
            val avgTop = (quad.topLeft.y + quad.topRight.y) / 2f
            val avgBottom = (quad.bottomLeft.y + quad.bottomRight.y) / 2f
            return quad.copy(
                topLeft = quad.topLeft.copy(y = avgTop),
                topRight = quad.topRight.copy(y = avgTop),
                bottomLeft = quad.bottomLeft.copy(y = avgBottom),
                bottomRight = quad.bottomRight.copy(y = avgBottom),
            ).let { if (vertical) alignVertical(it) else it }
        }
        return if (vertical) alignVertical(quad) else quad
    }

    private fun alignVertical(quad: QuadPoints): QuadPoints {
        val avgLeft = (quad.topLeft.x + quad.bottomLeft.x) / 2f
        val avgRight = (quad.topRight.x + quad.bottomRight.x) / 2f
        return quad.copy(
            topLeft = quad.topLeft.copy(x = avgLeft),
            bottomLeft = quad.bottomLeft.copy(x = avgLeft),
            topRight = quad.topRight.copy(x = avgRight),
            bottomRight = quad.bottomRight.copy(x = avgRight),
        )
    }

    private fun computeStraightenDegrees(corners: QuadPoints): Float {
        val dx = corners.topRight.x - corners.topLeft.x
        val dy = corners.topRight.y - corners.topLeft.y
        return Math.toDegrees(-atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    private fun cropRect(bitmap: Bitmap, quad: QuadPoints): Bitmap {
        val left = (min(quad.topLeft.x, quad.bottomLeft.x) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = (min(quad.topLeft.y, quad.topRight.y) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = (max(quad.topRight.x, quad.bottomRight.x) * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (max(quad.bottomLeft.y, quad.bottomRight.y) * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }
}
