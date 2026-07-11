package com.cheradip.ailanguagetutor.feature.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cheradip.ailanguagetutor.core.image.CleanAdjustmentKind
import com.cheradip.ailanguagetutor.core.image.CleanParams
import com.cheradip.ailanguagetutor.core.image.CropPreset
import com.cheradip.ailanguagetutor.core.image.DocumentFilterPresets
import com.cheradip.ailanguagetutor.core.image.EditStage
import com.cheradip.ailanguagetutor.core.image.GrayParams
import com.cheradip.ailanguagetutor.core.image.ScanTool
import com.cheradip.ailanguagetutor.core.image.TransitionParams
import kotlin.math.roundToInt

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
    onAddCustomFilter: () -> Unit,
) {
    val selected = uiState.draftFilterSelection
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
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