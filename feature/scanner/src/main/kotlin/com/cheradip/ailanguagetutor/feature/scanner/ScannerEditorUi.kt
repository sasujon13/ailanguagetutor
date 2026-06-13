package com.cheradip.ailanguagetutor.feature.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cheradip.ailanguagetutor.core.image.DocumentFilterPresets
import com.cheradip.ailanguagetutor.core.image.EditStage
import java.io.File
import com.cheradip.ailanguagetutor.core.image.WatermarkMode
import com.cheradip.ailanguagetutor.core.image.CleanParams
import com.cheradip.ailanguagetutor.core.image.CropPreset
import com.cheradip.ailanguagetutor.core.image.ExportCompression
import com.cheradip.ailanguagetutor.core.image.ExportFormat
import com.cheradip.ailanguagetutor.core.image.ExportMargins
import com.cheradip.ailanguagetutor.core.image.ExportOptions
import com.cheradip.ailanguagetutor.core.image.ExportOrientation
import com.cheradip.ailanguagetutor.core.image.ExportPageSize
import com.cheradip.ailanguagetutor.core.image.ExportQuality
import com.cheradip.ailanguagetutor.core.image.GrayMode
import com.cheradip.ailanguagetutor.core.image.GrayParams
import com.cheradip.ailanguagetutor.core.image.PointF
import com.cheradip.ailanguagetutor.core.image.QuadPoints
import com.cheradip.ailanguagetutor.core.image.ScanTool
import com.cheradip.ailanguagetutor.core.image.TransitionParams
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
private fun scanPreviewImageModel(path: String, cacheKey: String): Any {
    val context = LocalContext.current
    return remember(path, cacheKey) {
        ImageRequest.Builder(context)
            .data(File(path))
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .build()
    }
}

@Composable
fun ScannerPageThumbnailStrip(
    pages: List<ScannerPageItem>,
    selectedPageId: Long?,
    thumbnailPathFor: (ScannerPageItem) -> String,
    onSelectPage: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pages.forEach { page ->
            val selected = page.id == selectedPageId
            val borderColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
            AsyncImage(
                model = thumbnailPathFor(page),
                contentDescription = "Page ${page.pageIndex + 1}",
                modifier = Modifier
                    .width(48.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(2.dp, borderColor, RoundedCornerShape(6.dp))
                    .clickable { onSelectPage(page.id) },
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
fun ScannerPreviewArea(
    uiState: ScannerUiState,
    onUpdateCrop: ((com.cheradip.ailanguagetutor.core.image.CropParams) -> com.cheradip.ailanguagetutor.core.image.CropParams) -> Unit,
    onDeletePage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val isCropMode = uiState.activeTool == ScanTool.CROP
    LaunchedEffect(isCropMode) {
        if (isCropMode) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }
    val cropScrollBlocker = remember(isCropMode) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (isCropMode && source == NestedScrollSource.UserInput) {
                    available
                } else {
                    Offset.Zero
                }
            }
        }
    }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        if (!isCropMode) {
            scale = (scale * zoomChange).coerceIn(1f, 4f)
            offsetX += panChange.x
            offsetY += panChange.y
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (isCropMode) {
                    Modifier.nestedScroll(cropScrollBlocker)
                } else {
                    Modifier.transformable(state = transformState)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val preview = when {
            isCropMode -> uiState.selectedPageOriginalPath ?: uiState.previewPath
            else -> uiState.previewPath
        }
        if (preview != null) {
            val previewModel = scanPreviewImageModel(
                path = preview,
                cacheKey = if (isCropMode) {
                    "scan-crop-source-${uiState.selectedPageId}"
                } else {
                    "scan-preview-${uiState.selectedPageId}-${uiState.previewRevision}"
                },
            )
            AsyncImage(
                model = previewModel,
                contentDescription = "Page preview",
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (!isCropMode) {
                            Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            }
                        } else {
                            Modifier
                        },
                    ),
                contentScale = ContentScale.Fit,
            )
            if (isCropMode) {
                val cropPreset = uiState.draftCrop.preset
                CropCornerOverlay(
                    corners = uiState.draftCrop.corners,
                    previewPath = preview,
                    previewRevision = uiState.previewRevision,
                    selectedPageId = uiState.selectedPageId,
                    imageWidth = uiState.cropSourceWidth,
                    imageHeight = uiState.cropSourceHeight,
                    lockRectangle = cropPreset != CropPreset.FREEFORM,
                    onCornersChanged = { quad ->
                        onUpdateCrop { it.copy(corners = quad) }
                    },
                )
            }
        }
        if (uiState.isProcessingPreview) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
        IconButton(
            onClick = onDeletePage,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                    CircleShape,
                ),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete page",
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
fun ScannerEditingControls(
    uiState: ScannerUiState,
    onOpenTool: (ScanTool) -> Unit,
    onCloseTool: () -> Unit,
    onApply: () -> Unit,
    onPreviewCompareMode: (PreviewCompareMode) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRevertAll: () -> Unit,
    onRevertCurrent: () -> Unit,
    onRevertCurrentEffect: () -> Unit,
    onRevertToOriginal: () -> Unit,
    onCompareHistory: (Int) -> Unit,
    onRestoreCrop: () -> Unit,
    onRestoreColors: () -> Unit,
    onJumpToHistory: (Int) -> Unit,
    onJumpToStage: (EditStage) -> Unit,
    onAutoDetect: () -> Unit,
    onCropPreset: (CropPreset) -> Unit,
    onUpdateCrop: ((com.cheradip.ailanguagetutor.core.image.CropParams) -> com.cheradip.ailanguagetutor.core.image.CropParams) -> Unit,
    onUpdateTransition: ((TransitionParams) -> TransitionParams) -> Unit,
    onUpdateClean: ((CleanParams) -> CleanParams) -> Unit,
    onUpdateGray: ((GrayParams) -> GrayParams) -> Unit,
    onSelectFilterPreset: (String) -> Unit,
    onSaveCustomFilter: (String) -> Unit,
    onRenameCustomFilter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(onClick = onUndo, enabled = uiState.canUndo) {
                Icon(Icons.Default.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedo, enabled = uiState.canRedo) {
                Icon(Icons.Default.Redo, contentDescription = "Redo")
            }
            TextButton(onClick = onRevertAll) { Text("Revert all") }
            TextButton(onClick = onRevertToOriginal) { Text("To original") }
        }

        ScannerToolBar(activeTool = uiState.activeTool, onOpenTool = onOpenTool)

        uiState.activeTool?.let { tool ->
            ScannerToolPanel(
                tool = tool,
                uiState = uiState,
                onClose = onCloseTool,
                onApply = onApply,
                onPreviewCompareMode = onPreviewCompareMode,
                onRevertCurrent = onRevertCurrent,
                onRevertCurrentEffect = onRevertCurrentEffect,
                onRevertAll = onRevertAll,
                onRestoreCrop = onRestoreCrop,
                onRestoreColors = onRestoreColors,
                onAutoDetect = onAutoDetect,
                onCropPreset = onCropPreset,
                onUpdateCrop = onUpdateCrop,
                onUpdateTransition = onUpdateTransition,
                onUpdateClean = onUpdateClean,
                onUpdateGray = onUpdateGray,
                onSelectFilterPreset = onSelectFilterPreset,
                onSaveCustomFilter = onSaveCustomFilter,
                onRenameCustomFilter = onRenameCustomFilter,
            )
        }

        if (uiState.editHistory.isNotEmpty() || uiState.appliedStages.isNotEmpty()) {
            Text("Version history", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                EditStage.entries.filter { it != EditStage.TRANSITION }.forEach { stage ->
                    FilterChip(
                        selected = uiState.appliedStages.contains(stage) || (stage == EditStage.ORIGINAL && uiState.appliedStages.isEmpty()),
                        onClick = { onJumpToStage(stage) },
                        label = { Text(stage.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                uiState.editHistory.takeLast(8).forEach { (index, label) ->
                    Box(
                        modifier = Modifier.pointerInput(index) {
                            detectTapGestures(
                                onTap = { onJumpToHistory(index) },
                                onLongPress = { onCompareHistory(index) },
                            )
                        },
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = { onJumpToHistory(index) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                Text("Long-press history to compare", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ScannerToolBar(activeTool: ScanTool?, onOpenTool: (ScanTool) -> Unit) {
    val tools = listOf(
        ScanTool.ORIGINAL to ("Original" to Icons.Default.Restore),
        ScanTool.CROP to ("Crop & Rotate" to Icons.Default.CropRotate),
        ScanTool.CLEAN to ("Clean" to Icons.Default.AutoFixHigh),
        ScanTool.SAVE to ("Save" to Icons.Default.Save),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tools.forEach { (tool, labelIcon) ->
            val (label, icon) = labelIcon
            FilterChip(
                selected = activeTool == tool,
                onClick = { onOpenTool(tool) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun ScannerToolPanel(
    tool: ScanTool,
    uiState: ScannerUiState,
    onClose: () -> Unit,
    onApply: () -> Unit,
    onPreviewCompareMode: (PreviewCompareMode) -> Unit,
    onRevertCurrent: () -> Unit,
    onRevertCurrentEffect: () -> Unit,
    onRevertAll: () -> Unit,
    onRestoreCrop: () -> Unit,
    onRestoreColors: () -> Unit,
    onAutoDetect: () -> Unit,
    onCropPreset: (CropPreset) -> Unit,
    onUpdateCrop: ((com.cheradip.ailanguagetutor.core.image.CropParams) -> com.cheradip.ailanguagetutor.core.image.CropParams) -> Unit,
    onUpdateTransition: ((TransitionParams) -> TransitionParams) -> Unit,
    onUpdateClean: ((CleanParams) -> CleanParams) -> Unit,
    onUpdateGray: ((GrayParams) -> GrayParams) -> Unit,
    onSelectFilterPreset: (String) -> Unit,
    onSaveCustomFilter: (String) -> Unit,
    onRenameCustomFilter: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        when (tool) {
            ScanTool.ORIGINAL -> {
                Text("Tap Original anytime to reload the untouched capture.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRevertCurrentEffect) { Text("Revert effect") }
                    TextButton(onClick = onRevertAll) { Text("Revert all") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRevertCurrent) { Text("Reset tool") }
                    TextButton(onClick = onRestoreCrop) { Text("Restore crop") }
                    TextButton(onClick = onRestoreColors) { Text("Restore colors") }
                }
                EditPreviewActions(
                    selected = uiState.previewCompareMode,
                    showBeforeAfter = false,
                    onSelect = onPreviewCompareMode,
                )
                Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) { Text("Apply reset all") }
            }
            ScanTool.CROP -> {
                Text(
                    "Drag the green handles on the image above to set the crop area.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CropPreset.entries.forEach { preset ->
                        AssistChip(onClick = { onCropPreset(preset) }, label = { Text(preset.name.replace('_', ' ')) })
                    }
                }
                FloatSlider("Rotation °", uiState.draftCrop.rotationDegrees, -180f, 180f) { v ->
                    onUpdateCrop { it.copy(rotationDegrees = v) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        onUpdateCrop { it.copy(rotationDegrees = ((it.rotationDegrees + 90f) % 360f + 360f) % 360f) }
                    }) { Text("Rotate +90°") }
                    TextButton(onClick = {
                        onUpdateCrop { it.copy(rotationDegrees = ((it.rotationDegrees - 90f) % 360f + 360f) % 360f) }
                    }) { Text("Rotate -90°") }
                    TextButton(onClick = { onUpdateCrop { it.copy(rotationDegrees = 0f) } }) { Text("Reset") }
                }
                if (kotlin.math.abs(uiState.cropSkewDegrees) > 1f) {
                    Text(
                        "Skew detected: ${uiState.cropSkewDegrees.toInt()}° — use Auto straighten",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToggleChip("Auto straighten", uiState.draftCrop.autoStraighten) {
                        onUpdateCrop { p -> p.copy(autoStraighten = !p.autoStraighten) }
                    }
                    ToggleChip("Perspective", uiState.draftCrop.perspectiveCorrection) {
                        onUpdateCrop { p -> p.copy(perspectiveCorrection = !p.perspectiveCorrection) }
                    }
                    ToggleChip("Keystone", uiState.draftCrop.keystoneCorrection) {
                        onUpdateCrop { p -> p.copy(keystoneCorrection = !p.keystoneCorrection) }
                    }
                    ToggleChip("H align", uiState.draftCrop.horizontalAlignment) {
                        onUpdateCrop { p -> p.copy(horizontalAlignment = !p.horizontalAlignment) }
                    }
                    ToggleChip("V align", uiState.draftCrop.verticalAlignment) {
                        onUpdateCrop { p -> p.copy(verticalAlignment = !p.verticalAlignment) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onAutoDetect) { Text("Auto detect edges") }
                    TextButton(onClick = onRestoreCrop) { Text("Restore boundaries") }
                }
                if (uiState.draftCrop.preset == CropPreset.ID_CARD ||
                    uiState.draftCrop.preset == CropPreset.BUSINESS_CARD ||
                    uiState.draftCrop.preset == CropPreset.PASSPORT
                ) {
                    Text(
                        "Tip: OpenCV finds the card even on top of other documents — tap Auto detect or select the preset again.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                EditPreviewActions(
                    selected = uiState.previewCompareMode,
                    showBeforeAfter = true,
                    onSelect = onPreviewCompareMode,
                )
            }
            ScanTool.CLEAN, ScanTool.GRAY -> {
                Text("Filters", style = MaterialTheme.typography.labelMedium)
                FilterPresetChipGrid(
                    presets = DocumentFilterPresets.builtIn,
                    selectedId = uiState.selectedFilterPresetId,
                    onSelect = onSelectFilterPreset,
                )
                Text("Custom", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    uiState.customFilters.forEach { slot ->
                        FilterChip(
                            selected = uiState.selectedFilterPresetId == slot.slotId,
                            onClick = { onSelectFilterPreset(slot.slotId) },
                            label = { Text(slot.displayName, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                if (uiState.editingCustomFilter) {
                    val activeSlot = uiState.customFilters.find { it.slotId == uiState.selectedFilterPresetId }
                    if (activeSlot != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(onClick = { onSaveCustomFilter(activeSlot.slotId) }) {
                                Text("Save ${activeSlot.displayName}")
                            }
                            TextButton(onClick = { onRenameCustomFilter(activeSlot.slotId) }) {
                                Text("Rename")
                            }
                        }
                    }
                    CustomManualAdjustments(
                        uiState = uiState,
                        onUpdateTransition = onUpdateTransition,
                        onUpdateClean = onUpdateClean,
                        onUpdateGray = onUpdateGray,
                    )
                }
                EditPreviewActions(
                    selected = uiState.previewCompareMode,
                    showBeforeAfter = true,
                    onSelect = onPreviewCompareMode,
                )
            }
            ScanTool.SAVE -> {
                Text("Export to PDF, separate images, or one long image.", style = MaterialTheme.typography.bodyMedium)
                Text("Files save to Documents/AILanguageTutor/", style = MaterialTheme.typography.bodySmall)
            }
            ScanTool.TRANSITION -> Unit
        }
        if (tool != ScanTool.ORIGINAL && tool != ScanTool.SAVE && tool != ScanTool.GRAY) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text("Cancel") }
                TextButton(onClick = onRevertCurrent) { Text("Revert") }
                Button(onClick = onApply, enabled = !uiState.isSaving) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onToggle: () -> Unit) {
    FilterChip(selected = selected, onClick = onToggle, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
}

@Composable
private fun FilterPresetChipGrid(
    presets: List<com.cheradip.ailanguagetutor.core.image.DocumentFilterPreset>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    val chunkSize = 4
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        presets.chunked(chunkSize).forEach { rowPresets ->
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                rowPresets.forEach { preset ->
                    FilterChip(
                        selected = selectedId == preset.id,
                        onClick = { onSelect(preset.id) },
                        label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomManualAdjustments(
    uiState: ScannerUiState,
    onUpdateTransition: ((TransitionParams) -> TransitionParams) -> Unit,
    onUpdateClean: ((CleanParams) -> CleanParams) -> Unit,
    onUpdateGray: ((GrayParams) -> GrayParams) -> Unit,
) {
    Text("Manual adjustments", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    ToggleChip("Grayscale", uiState.draftGray.active) {
        onUpdateGray { p -> p.copy(active = !p.active) }
    }
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        GrayMode.entries.forEach { mode ->
            FilterChip(
                selected = uiState.draftGray.mode == mode,
                onClick = { onUpdateGray { it.copy(active = true, mode = mode) } },
                label = { Text(mode.name.replace('_', ' '), style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
    ToggleChip("Auto straighten text", uiState.draftTransition.autoStraightenText) {
        onUpdateTransition { p -> p.copy(autoStraightenText = !p.autoStraightenText) }
    }
    ToggleChip("Curved page", uiState.draftTransition.curvedPageCorrection) {
        onUpdateTransition { p -> p.copy(curvedPageCorrection = !p.curvedPageCorrection) }
    }
    ToggleChip("Auto enhance", uiState.draftClean.autoEnhance) {
        onUpdateClean { p -> p.copy(autoEnhance = !p.autoEnhance) }
    }
    ToggleChip("Adaptive threshold", uiState.draftClean.adaptiveThreshold) {
        onUpdateClean { p -> p.copy(adaptiveThreshold = !p.adaptiveThreshold) }
    }
    CleanIntSlider("Brightness", uiState.draftClean.brightness, onUpdateClean) { p, v -> p.copy(brightness = v) }
    CleanIntSlider("Contrast", uiState.draftClean.contrast, onUpdateClean) { p, v -> p.copy(contrast = v) }
    CleanIntSlider("Sharpness", uiState.draftClean.sharpness, onUpdateClean) { p, v -> p.copy(sharpness = v) }
    CleanIntSlider("Noise reduction", uiState.draftClean.noiseReduction, onUpdateClean) { p, v -> p.copy(noiseReduction = v) }
    CleanIntSlider("Shadow removal", uiState.draftClean.shadowRemoval, onUpdateClean) { p, v -> p.copy(shadowRemoval = v) }
    CleanIntSlider("Paper whitening", uiState.draftClean.paperWhitening, onUpdateClean) { p, v -> p.copy(paperWhitening = v) }
    CleanIntSlider("Ink enhancement", uiState.draftClean.inkEnhancement, onUpdateClean) { p, v -> p.copy(inkEnhancement = v) }
    GrayIntSlider("Gray brightness", uiState.draftGray.brightness, onUpdateGray) { p, v -> p.copy(active = true, brightness = v) }
    GrayIntSlider("Gray contrast", uiState.draftGray.contrast, onUpdateGray) { p, v -> p.copy(active = true, contrast = v) }
    GrayIntSlider("Exposure", uiState.draftGray.exposure, onUpdateGray) { p, v -> p.copy(active = true, exposure = v) }
    GrayIntSlider("Gamma", uiState.draftGray.gamma, onUpdateGray) { p, v -> p.copy(active = true, gamma = v) }
    GrayIntSlider("Black point", uiState.draftGray.blackPoint, onUpdateGray) { p, v -> p.copy(active = true, blackPoint = v) }
    GrayIntSlider("White point", uiState.draftGray.whitePoint, onUpdateGray) { p, v -> p.copy(active = true, whitePoint = v) }
    ToggleChip("Darken text", uiState.draftGray.darkenText) {
        onUpdateGray { p -> p.copy(active = true, darkenText = !p.darkenText) }
    }
    ToggleChip("Lighten paper", uiState.draftGray.lightenPaper) {
        onUpdateGray { p -> p.copy(active = true, lightenPaper = !p.lightenPaper) }
    }
    ToggleChip("Improve OCR", uiState.draftGray.improveOcrAccuracy) {
        onUpdateGray { p -> p.copy(active = true, improveOcrAccuracy = !p.improveOcrAccuracy) }
    }
}

@Composable
fun CustomFilterRenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename custom filter") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Filter name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = { Button(onClick = { onConfirm(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ScanExportDialog(
    options: ExportOptions,
    previewPath: String?,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onUpdate: ((ExportOptions) -> ExportOptions) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export document") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (previewPath != null) {
                    AsyncImage(
                        model = previewPath,
                        contentDescription = "Export preview",
                        modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
                OutlinedTextField(
                    value = options.documentName,
                    onValueChange = { name -> onUpdate { it.copy(documentName = name) } },
                    label = { Text("Document name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                EnumChips("Format", ExportFormat.entries, options.format) { f -> onUpdate { it.copy(format = f) } }
                EnumChips("Quality", ExportQuality.entries, options.quality) { q -> onUpdate { it.copy(quality = q) } }
                EnumChips("Compression", ExportCompression.entries, options.compression) { c -> onUpdate { it.copy(compression = c) } }
                EnumChips("Page size", ExportPageSize.entries, options.pageSize) { s -> onUpdate { it.copy(pageSize = s) } }
                EnumChips("Margins", ExportMargins.entries, options.margins) { m -> onUpdate { it.copy(margins = m) } }
                EnumChips("Orientation", ExportOrientation.entries, options.orientation) { o -> onUpdate { it.copy(orientation = o) } }
                EnumChips("Watermark", WatermarkMode.entries, options.watermarkMode) { mode ->
                    onUpdate { it.copy(watermarkMode = mode, useTimestampWatermark = mode == WatermarkMode.TIMESTAMP) }
                }
                if (options.watermarkMode == WatermarkMode.CUSTOM) {
                    OutlinedTextField(
                        value = options.watermark,
                        onValueChange = { w -> onUpdate { it.copy(watermark = w) } },
                        label = { Text("Custom watermark text") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = options.passwordEnabled, onCheckedChange = { enabled ->
                        onUpdate { it.copy(passwordEnabled = enabled) }
                    })
                    Text("Password protect PDF", modifier = Modifier.padding(start = 8.dp))
                }
                if (options.passwordEnabled) {
                    OutlinedTextField(
                        value = options.password,
                        onValueChange = { p -> onUpdate { it.copy(password = p) } },
                        label = { Text("PDF password") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(value = options.author, onValueChange = { v -> onUpdate { it.copy(author = v) } }, label = { Text("Author") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = options.title, onValueChange = { v -> onUpdate { it.copy(title = v) } }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = options.subject, onValueChange = { v -> onUpdate { it.copy(subject = v) } }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = options.keywords, onValueChange = { v -> onUpdate { it.copy(keywords = v) } }, label = { Text("Keywords") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = onExport) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun <T : Enum<T>> EnumChips(label: String, values: List<T>, selected: T, onSelect: (T) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(value.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) },
            )
        }
    }
}

@Composable
fun ScanExportConflictDialog(
    existingNames: List<String>,
    renameHint: String,
    onReplace: () -> Unit,
    onRename: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File already exists") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("These files would be overwritten:")
                existingNames.forEach { name ->
                    Text("• $name", style = MaterialTheme.typography.bodyMedium)
                }
                if (renameHint.isNotBlank()) {
                    Text(
                        "Save with a new name instead:",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(renameHint, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onRename) { Text("Save as new name") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = onReplace) { Text("Replace") }
            }
        },
    )
}

@Composable
fun ScanExportPreviewDialog(
    paths: List<String>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export complete") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Saved to Documents/AILanguageTutor/")
                paths.take(3).forEach { path ->
                    AsyncImage(
                        model = path,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                    Text(path.substringAfterLast('/'), style = MaterialTheme.typography.labelSmall)
                }
                if (paths.size > 3) Text("+ ${paths.size - 3} more file(s)")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
fun ScanVersionCompareDialog(
    label: String,
    beforePath: String?,
    afterPath: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compare: $label") },
        text = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Before", style = MaterialTheme.typography.labelSmall)
                    if (beforePath != null) {
                        AsyncImage(
                            model = beforePath,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("After", style = MaterialTheme.typography.labelSmall)
                    if (afterPath != null) {
                        AsyncImage(
                            model = afterPath,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun EditPreviewActions(
    selected: PreviewCompareMode,
    showBeforeAfter: Boolean,
    onSelect: (PreviewCompareMode) -> Unit,
) {
    Text("Preview", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = selected == PreviewCompareMode.ORIGINAL,
            onClick = { onSelect(PreviewCompareMode.ORIGINAL) },
            label = { Text("See Original") },
        )
        if (showBeforeAfter) {
            FilterChip(
                selected = selected == PreviewCompareMode.BEFORE,
                onClick = { onSelect(PreviewCompareMode.BEFORE) },
                label = { Text("Before") },
            )
            FilterChip(
                selected = selected == PreviewCompareMode.AFTER,
                onClick = { onSelect(PreviewCompareMode.AFTER) },
                label = { Text("After") },
            )
        }
    }
}

@Composable
private fun CropCornerOverlay(
    corners: QuadPoints,
    previewPath: String,
    previewRevision: Long,
    selectedPageId: Long?,
    imageWidth: Int,
    imageHeight: Int,
    lockRectangle: Boolean,
    onCornersChanged: (QuadPoints) -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var dragIndex by remember { mutableStateOf(-1) }
    var magnifierCenter by remember { mutableStateOf<Offset?>(null) }
    val cornersState = rememberUpdatedState(corners)
    val handleHitRadiusPx = 56f
    val fitRect = imageContentFitRect(size, imageWidth, imageHeight)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(lockRectangle, size.width, size.height, imageWidth, imageHeight) {
                val fit = imageContentFitRect(size, imageWidth, imageHeight)
                detectDragGestures(
                    onDragStart = { offset ->
                        dragIndex = nearestHandleIndex(offset, cornersState.value, fit, handleHitRadiusPx)
                        magnifierCenter = offset
                    },
                    onDragEnd = { dragIndex = -1; magnifierCenter = null },
                    onDragCancel = { dragIndex = -1; magnifierCenter = null },
                    onDrag = { change, _ ->
                        if (dragIndex < 0 || fit.width <= 0f || fit.height <= 0f) return@detectDragGestures
                        val nx = ((change.position.x - fit.left) / fit.width).coerceIn(0f, 1f)
                        val ny = ((change.position.y - fit.top) / fit.height).coerceIn(0f, 1f)
                        magnifierCenter = change.position
                        onCornersChanged(
                            updateHandle(cornersState.value, dragIndex, nx, ny, lockRectangle),
                        )
                        change.consume()
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.width == 0 || fitRect.width <= 0f) return@Canvas
            val pts = listOf(
                corners.topLeft,
                corners.topRight,
                corners.bottomRight,
                corners.bottomLeft,
            )
            val path = Path().apply {
                pts.forEachIndexed { i, p ->
                    val point = cornerToOffset(p, fitRect)
                    if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
                close()
            }
            val shadePath = Path.combine(
                PathOperation.Difference,
                Path().apply {
                    addRect(Rect(0f, 0f, size.width.toFloat(), size.height.toFloat()))
                },
                path,
            )
            drawPath(shadePath, Color.Black.copy(alpha = 0.4f))
            drawPath(path, Color(0xFF00897B), style = Stroke(width = 3f))
            pts.forEach { p ->
                val center = cornerToOffset(p, fitRect)
                drawCircle(Color(0xFF00897B), radius = 18f, center = center)
                drawCircle(Color.White, radius = 10f, center = center)
            }
            edgeMidpoints(corners, fitRect).forEach { e ->
                drawCircle(Color(0xFF00695C), radius = 12f, center = e)
                drawCircle(Color.White, radius = 6f, center = e)
            }
        }
        magnifierCenter?.let { center ->
            Box(
                modifier = Modifier
                    .offset { IntOffset((center.x - 40).roundToInt(), (center.y - 90).roundToInt()) }
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            ) {
                AsyncImage(
                    model = scanPreviewImageModel(
                        path = previewPath,
                        cacheKey = "scan-magnifier-$selectedPageId-$previewRevision",
                    ),
                    contentDescription = "Magnifier",
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 2.5f; scaleY = 2.5f },
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun CleanIntSlider(
    label: String,
    value: Int,
    onUpdate: ((CleanParams) -> CleanParams) -> Unit,
    transform: (CleanParams, Int) -> CleanParams,
) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Slider(value = value.toFloat(), onValueChange = { v -> onUpdate { transform(it, v.toInt()) } }, valueRange = 0f..100f)
}

@Composable
private fun GrayIntSlider(
    label: String,
    value: Int,
    onUpdate: ((GrayParams) -> GrayParams) -> Unit,
    transform: (GrayParams, Int) -> GrayParams,
) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Slider(value = value.toFloat(), onValueChange = { v -> onUpdate { transform(it, v.toInt()) } }, valueRange = 0f..100f)
}

@Composable
private fun FloatSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Text("$label: ${value.toInt()}", style = MaterialTheme.typography.labelMedium)
    Slider(value = value, onValueChange = onChange, valueRange = min..max)
}

private fun imageContentFitRect(container: IntSize, imageWidth: Int, imageHeight: Int): Rect {
    if (container.width <= 0 || container.height <= 0 || imageWidth <= 0 || imageHeight <= 0) {
        return Rect(
            0f,
            0f,
            max(container.width, 1).toFloat(),
            max(container.height, 1).toFloat(),
        )
    }
    val scale = min(
        container.width.toFloat() / imageWidth,
        container.height.toFloat() / imageHeight,
    )
    val w = imageWidth * scale
    val h = imageHeight * scale
    val left = (container.width - w) / 2f
    val top = (container.height - h) / 2f
    return Rect(left, top, left + w, top + h)
}

private fun cornerToOffset(point: PointF, fitRect: Rect): Offset =
    Offset(fitRect.left + point.x * fitRect.width, fitRect.top + point.y * fitRect.height)

private fun edgeMidpoints(corners: QuadPoints, fitRect: Rect): List<Offset> {
    val tl = cornerToOffset(corners.topLeft, fitRect)
    val tr = cornerToOffset(corners.topRight, fitRect)
    val br = cornerToOffset(corners.bottomRight, fitRect)
    val bl = cornerToOffset(corners.bottomLeft, fitRect)
    return listOf(
        Offset((tl.x + tr.x) / 2f, (tl.y + tr.y) / 2f),
        Offset((tr.x + br.x) / 2f, (tr.y + br.y) / 2f),
        Offset((bl.x + br.x) / 2f, (bl.y + br.y) / 2f),
        Offset((tl.x + bl.x) / 2f, (tl.y + bl.y) / 2f),
    )
}

private fun nearestHandleIndex(offset: Offset, corners: QuadPoints, fitRect: Rect, hitRadius: Float): Int {
    val cornerPts = listOf(corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft)
    var best = -1
    var bestDist = hitRadius
    cornerPts.forEachIndexed { i, p ->
        val center = cornerToOffset(p, fitRect)
        val d = hypot(offset.x - center.x, offset.y - center.y)
        if (d < bestDist) {
            bestDist = d
            best = i
        }
    }
    edgeMidpoints(corners, fitRect).forEachIndexed { i, e ->
        val d = hypot(offset.x - e.x, offset.y - e.y)
        if (d < bestDist) {
            bestDist = d
            best = 4 + i
        }
    }
    return best
}

private fun updateHandle(quad: QuadPoints, index: Int, x: Float, y: Float, lockRectangle: Boolean): QuadPoints {
    if (lockRectangle) return updateRectangleHandle(quad.toAxisAlignedRectangle(), index, x, y)
    return when (index) {
        in 0..3 -> updateCorner(quad, index, x, y)
        4 -> quad.copy(topLeft = quad.topLeft.copy(y = y), topRight = quad.topRight.copy(y = y))
        5 -> quad.copy(topRight = quad.topRight.copy(x = x), bottomRight = quad.bottomRight.copy(x = x))
        6 -> quad.copy(bottomLeft = quad.bottomLeft.copy(y = y), bottomRight = quad.bottomRight.copy(y = y))
        7 -> quad.copy(topLeft = quad.topLeft.copy(x = x), bottomLeft = quad.bottomLeft.copy(x = x))
        else -> quad
    }
}

private fun updateRectangleHandle(quad: QuadPoints, index: Int, x: Float, y: Float): QuadPoints {
    val minSize = 0.05f
    var left = quad.topLeft.x
    var top = quad.topLeft.y
    var right = quad.bottomRight.x
    var bottom = quad.bottomRight.y
    when (index) {
        0 -> {
            left = x.coerceIn(0f, right - minSize)
            top = y.coerceIn(0f, bottom - minSize)
        }
        1 -> {
            right = x.coerceIn(left + minSize, 1f)
            top = y.coerceIn(0f, bottom - minSize)
        }
        2 -> {
            right = x.coerceIn(left + minSize, 1f)
            bottom = y.coerceIn(top + minSize, 1f)
        }
        3 -> {
            left = x.coerceIn(0f, right - minSize)
            bottom = y.coerceIn(top + minSize, 1f)
        }
        4 -> top = y.coerceIn(0f, bottom - minSize)
        5 -> right = x.coerceIn(left + minSize, 1f)
        6 -> bottom = y.coerceIn(top + minSize, 1f)
        7 -> left = x.coerceIn(0f, right - minSize)
    }
    return QuadPoints(
        topLeft = PointF(left, top),
        topRight = PointF(right, top),
        bottomRight = PointF(right, bottom),
        bottomLeft = PointF(left, bottom),
    )
}

private fun updateCorner(quad: QuadPoints, index: Int, x: Float, y: Float): QuadPoints = when (index) {
    0 -> quad.copy(topLeft = PointF(x, y))
    1 -> quad.copy(topRight = PointF(x, y))
    2 -> quad.copy(bottomRight = PointF(x, y))
    else -> quad.copy(bottomLeft = PointF(x, y))
}
