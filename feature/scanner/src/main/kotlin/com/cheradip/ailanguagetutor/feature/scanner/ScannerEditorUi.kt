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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Transform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
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
import com.cheradip.ailanguagetutor.core.image.CleanAdjustmentKind
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
fun ScannerPageThumbnailStrip(
    pages: List<ScannerPageItem>,
    selectedPageId: Long?,
    thumbnailPathFor: (ScannerPageItem) -> String,
    onSelectPage: (Long) -> Unit,
    onAddPage: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
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
        if (onAddPage != null) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .width(48.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                    .clickable(onClick = onAddPage),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add page",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
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
        val onOriginalView = uiState.previewCompareMode == PreviewCompareMode.ORIGINAL
        val showInteractiveCropOverlay = isCropMode && onOriginalView
        val showCaptureRegionGuide = !isCropMode && onOriginalView
        val curveBoundary = effectiveCurveBoundary(uiState.draftCrop)
        val preview = when {
            showInteractiveCropOverlay || showCaptureRegionGuide ->
                uiState.selectedPageOriginalPath ?: uiState.previewPath
            else -> uiState.previewPath ?: uiState.selectedPageOriginalPath
        }
        if (preview != null) {
            val previewModel = scanPreviewImageModel(
                path = preview,
                cacheKey = if (showInteractiveCropOverlay || showCaptureRegionGuide) {
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
            if (showInteractiveCropOverlay) {
                PenToolCropOverlay(
                    boundary = curveBoundary,
                    previewPath = preview,
                    previewRevision = uiState.previewRevision,
                    selectedPageId = uiState.selectedPageId,
                    imageWidth = uiState.cropSourceWidth,
                    imageHeight = uiState.cropSourceHeight,
                    interactive = true,
                    onBoundaryChanged = { curve ->
                        onUpdateCrop {
                            it.copy(
                                curveBoundary = curve,
                                useCurvedBoundary = true,
                                corners = curve.boundingQuad(),
                            )
                        }
                    },
                )
            } else if (showCaptureRegionGuide && curveBoundary.isValid) {
                CurveGuideOverlay(
                    boundary = curveBoundary,
                    imageWidth = uiState.cropSourceWidth,
                    imageHeight = uiState.cropSourceHeight,
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
    onToggleCleanAdjustment: (CleanAdjustmentKind, Int) -> Unit,
    onSetExpandedCleanAdjustment: (CleanAdjustmentKind?) -> Unit,
    onSetCleanIntensityLevel: (Int) -> Unit,
    onAddCustomFilter: () -> Unit,
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

        ScannerEditorIconToolbar(
            activeTool = uiState.activeTool,
            onOpenTool = onOpenTool,
        )

        if (uiState.activeTool == ScanTool.CLEAN) {
            CleanAdjustmentSection(
                uiState = uiState,
                onToggleAdjustment = onToggleCleanAdjustment,
                onSetExpandedAdjustment = onSetExpandedCleanAdjustment,
            )
        }

        ScannerFilterPresetsSection(
            uiState = uiState,
            onTogglePreset = onSelectFilterPreset,
            onSetCleanIntensityLevel = onSetCleanIntensityLevel,
            onAddCustomFilter = onAddCustomFilter,
        )

        when (uiState.activeTool) {
            ScanTool.CROP -> CropToolPanel(
                uiState = uiState,
                onClose = onCloseTool,
                onApply = onApply,
                onRevertCurrent = onRevertCurrent,
                onRestoreCrop = onRestoreCrop,
                onAutoDetect = onAutoDetect,
                onCropPreset = onCropPreset,
                onUpdateCrop = onUpdateCrop,
            )
            ScanTool.TRANSITION -> TransitionToolPanel(
                uiState = uiState,
                onClose = onCloseTool,
                onApply = onApply,
                onRevertCurrent = onRevertCurrent,
                onUpdateTransition = onUpdateTransition,
            )
            else -> Unit
        }

        if (uiState.editHistory.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                uiState.editHistory.takeLast(10).forEach { (index, label) ->
                    FilterChip(
                        selected = uiState.selectedHistoryIndex == index,
                        onClick = { onJumpToHistory(index) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerCircleIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val tint = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun ScannerCompareCircleRow(
    selected: PreviewCompareMode,
    activeTool: ScanTool?,
    onSelect: (PreviewCompareMode) -> Unit,
    onOpenCrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScannerCircleIconButton(
            selected = selected == PreviewCompareMode.ORIGINAL && activeTool != ScanTool.CROP,
            onClick = { onSelect(PreviewCompareMode.ORIGINAL) },
            icon = Icons.Default.Restore,
            contentDescription = "Original",
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScannerCircleIconButton(
                selected = selected == PreviewCompareMode.AFTER && activeTool != ScanTool.CROP,
                onClick = { onSelect(PreviewCompareMode.AFTER) },
                icon = Icons.Default.AutoFixHigh,
                contentDescription = "Enhance",
            )
            ScannerCircleIconButton(
                selected = activeTool == ScanTool.CROP,
                onClick = onOpenCrop,
                icon = Icons.Default.CropRotate,
                contentDescription = "Crop and rotate",
            )
        }
    }
}

@Composable
private fun ScannerEditorIconToolbar(
    activeTool: ScanTool?,
    onOpenTool: (ScanTool) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScannerCircleIconButton(
            selected = activeTool == ScanTool.TRANSITION,
            onClick = { onOpenTool(ScanTool.TRANSITION) },
            icon = Icons.Default.Transform,
            contentDescription = "Transform",
        )
        ScannerCircleIconButton(
            selected = activeTool == ScanTool.CLEAN,
            onClick = { onOpenTool(ScanTool.CLEAN) },
            icon = Icons.Default.CleaningServices,
            contentDescription = "Clean",
        )
        ScannerCircleIconButton(
            selected = activeTool == ScanTool.SAVE,
            onClick = { onOpenTool(ScanTool.SAVE) },
            icon = Icons.Default.Save,
            contentDescription = "Save",
        )
    }
}

@Composable
private fun CropToolPanel(
    uiState: ScannerUiState,
    onClose: () -> Unit,
    onApply: () -> Unit,
    onRevertCurrent: () -> Unit,
    onRestoreCrop: () -> Unit,
    onAutoDetect: () -> Unit,
    onCropPreset: (CropPreset) -> Unit,
    onUpdateCrop: ((com.cheradip.ailanguagetutor.core.image.CropParams) -> com.cheradip.ailanguagetutor.core.image.CropParams) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            "Drag anchors and handles like a pen tool. Tap an edge to add a point.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Drag the green curve on the image above to set the capture area.",
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
        RotationDegreeSlider(
            degrees = uiState.draftCrop.rotationDegrees,
            onDegreesChange = { deg -> onUpdateCrop { it.copy(rotationDegrees = deg) } },
        )
        if (kotlin.math.abs(uiState.cropSkewDegrees) > 1f) {
            Text(
                "Skew detected: ${uiState.cropSkewDegrees.toInt()}° — use Auto straighten",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            ActionChip("Auto detect edges", onAutoDetect)
            ActionChip("Restore boundaries", onRestoreCrop)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text("Cancel") }
            TextButton(onClick = onRevertCurrent) { Text("Revert") }
            Button(onClick = onApply, enabled = !uiState.isSaving) { Text("Apply") }
        }
    }
}

@Composable
private fun TransitionToolPanel(
    uiState: ScannerUiState,
    onClose: () -> Unit,
    onApply: () -> Unit,
    onRevertCurrent: () -> Unit,
    onUpdateTransition: ((TransitionParams) -> TransitionParams) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        ToggleChip("Auto straighten text", uiState.draftTransition.autoStraightenText) {
            onUpdateTransition { p -> p.copy(autoStraightenText = !p.autoStraightenText) }
        }
        ToggleChip("Curved page correction", uiState.draftTransition.curvedPageCorrection) {
            onUpdateTransition { p -> p.copy(curvedPageCorrection = !p.curvedPageCorrection) }
        }
        Text("Perspective strength", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = uiState.draftTransition.perspectiveStrength.toFloat(),
            onValueChange = { value ->
                onUpdateTransition { p -> p.copy(perspectiveStrength = value.roundToInt()) }
            },
            valueRange = 0f..100f,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text("Cancel") }
            TextButton(onClick = onRevertCurrent) { Text("Revert") }
            Button(onClick = onApply, enabled = !uiState.isSaving) { Text("Apply") }
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onToggle: () -> Unit) {
    FilterChip(selected = selected, onClick = onToggle, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
    )
}

@Composable
private fun CleanAdjustmentSection(
    uiState: ScannerUiState,
    onToggleAdjustment: (CleanAdjustmentKind, Int) -> Unit,
    onSetExpandedAdjustment: (CleanAdjustmentKind?) -> Unit,
) {
    val selected = uiState.draftFilterSelection
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        CleanAdjustmentCircleRow(
            uiState = uiState,
            selected = selected,
            onToggleAdjustment = onToggleAdjustment,
            onSetExpandedAdjustment = onSetExpandedAdjustment,
        )
        uiState.expandedCleanAdjustment?.let { kind ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                (0..9).forEach { level ->
                    FilterChip(
                        selected = selected.adjustments[kind] == level,
                        onClick = { onToggleAdjustment(kind, level) },
                        label = { Text(level.toString(), style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerFilterPresetsSection(
    uiState: ScannerUiState,
    onTogglePreset: (String) -> Unit,
    onSetCleanIntensityLevel: (Int) -> Unit,
    onAddCustomFilter: () -> Unit,
) {
    val selected = uiState.draftFilterSelection
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        CleanIntensitySizeRow(
            selectedLevel = uiState.selectedCleanIntensityLevel,
            onSelectLevel = onSetCleanIntensityLevel,
        )
        FilterPresetRow(
            presetIds = DocumentFilterPresets.colorRowIds,
            selectedIds = selected.presetIds,
            onToggle = onTogglePreset,
        )
        FilterPresetRow(
            presetIds = DocumentFilterPresets.documentRowIds,
            selectedIds = selected.presetIds,
            onToggle = onTogglePreset,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            uiState.customFilters.filter { it.hasSavedSettings }.forEach { slot ->
                FilterChip(
                    selected = slot.slotId in selected.presetIds,
                    onClick = { onTogglePreset(slot.slotId) },
                    label = { Text(slot.displayName, style = MaterialTheme.typography.labelSmall) },
                )
            }
            IconButton(onClick = onAddCustomFilter) {
                Icon(Icons.Default.Add, contentDescription = "Save current filters")
            }
        }
    }
}

@Composable
private fun CleanIntensitySizeRow(
    selectedLevel: Int?,
    onSelectLevel: (Int) -> Unit,
) {
    val sizes = listOf(10.dp, 12.dp, 14.dp, 16.dp, 18.dp, 20.dp, 22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sizes.forEachIndexed { index, diameter ->
            val level = index + 1
            val isSelected = selectedLevel == level
            val fillColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
            Box(
                modifier = Modifier
                    .size(diameter + 8.dp)
                    .clickable { onSelectLevel(level) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(diameter)
                        .clip(CircleShape)
                        .background(fillColor),
                )
            }
        }
    }
}

@Composable
private fun CleanAdjustmentCircleRow(
    uiState: ScannerUiState,
    selected: com.cheradip.ailanguagetutor.core.image.CleanFilterSelection,
    onToggleAdjustment: (CleanAdjustmentKind, Int) -> Unit,
    onSetExpandedAdjustment: (CleanAdjustmentKind?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DocumentFilterPresets.adjustmentKinds.forEach { kind ->
            val isActive = uiState.expandedCleanAdjustment == kind || kind in selected.adjustments
            val background = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            val label = kind.label.first().toString()
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(background)
                    .clickable {
                        onSetExpandedAdjustment(if (uiState.expandedCleanAdjustment == kind) null else kind)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun FilterPresetRow(
    presetIds: List<String>,
    selectedIds: List<String>,
    onToggle: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        presetIds.forEach { id ->
            val name = DocumentFilterPresets.presetName(id)
            FilterChip(
                selected = id in selectedIds,
                onClick = { onToggle(id) },
                label = { Text(name, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Visibility,
            contentDescription = "Preview",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilterChip(
            selected = selected == PreviewCompareMode.ORIGINAL,
            onClick = { onSelect(PreviewCompareMode.ORIGINAL) },
            label = { Text("Original", style = MaterialTheme.typography.labelSmall) },
        )
        if (showBeforeAfter) {
            FilterChip(
                selected = selected == PreviewCompareMode.BEFORE,
                onClick = { onSelect(PreviewCompareMode.BEFORE) },
                label = { Text("Before", style = MaterialTheme.typography.labelSmall) },
            )
            FilterChip(
                selected = selected == PreviewCompareMode.AFTER,
                onClick = { onSelect(PreviewCompareMode.AFTER) },
                label = { Text("After", style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

@Composable
private fun RotationDegreeSlider(
    degrees: Float,
    onDegreesChange: (Float) -> Unit,
) {
    val normalized = ((degrees % 360f) + 360f) % 360f
    val displayDegrees = normalized.roundToInt()
    Text(
        "Rotation: $displayDegrees°",
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onDegreesChange(0f) }) {
            Icon(Icons.Default.RestartAlt, contentDescription = "Reset rotation")
        }
        Slider(
            value = normalized,
            onValueChange = { onDegreesChange(it.roundToInt().toFloat()) },
            valueRange = 0f..360f,
            steps = 359,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onDegreesChange(((normalized - 45f) % 360f + 360f) % 360f) }) {
            Icon(Icons.Default.RotateLeft, contentDescription = "Rotate -45°")
        }
        IconButton(onClick = { onDegreesChange(((normalized + 45f) % 360f + 360f) % 360f) }) {
            Icon(Icons.Default.RotateRight, contentDescription = "Rotate +45°")
        }
    }
}

@Composable
private fun CaptureRegionGuideOverlay(
    corners: QuadPoints,
    imageWidth: Int,
    imageHeight: Int,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val fitRect = imageContentFitRect(size, imageWidth, imageHeight)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it },
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
            drawPath(shadePath, Color.Black.copy(alpha = 0.35f))
            drawPath(path, Color(0xFF00897B), style = Stroke(width = 2.5f))
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
        val d = hypot(offset.x - center.x, offset.y - center.y).toFloat()
        if (d < bestDist) {
            bestDist = d
            best = i
        }
    }
    edgeMidpoints(corners, fitRect).forEachIndexed { i, e ->
        val d = hypot(offset.x - e.x, offset.y - e.y).toFloat()
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
