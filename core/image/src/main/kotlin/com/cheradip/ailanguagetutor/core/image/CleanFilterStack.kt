package com.cheradip.ailanguagetutor.core.image

import android.graphics.Bitmap

enum class CleanAdjustmentKind(val label: String) {
    BRIGHTNESS("Brightness"),
    DARK("Dark"),
    CONTRAST("Contrast"),
    EXPOSURE("Exposure"),
    GAMMA("Gamma"),
    STRAIGHTEN("Straighten"),
}

data class CleanFilterSelection(
    val presetIds: List<String> = emptyList(),
    val adjustments: Map<CleanAdjustmentKind, Int> = emptyMap(),
)

object CleanFilterRenderer {

    fun applyStack(
        bitmap: Bitmap,
        selection: CleanFilterSelection,
        customPresets: List<DocumentFilterPreset> = emptyList(),
    ): Bitmap {
        if (selection.presetIds.isEmpty() && selection.adjustments.isEmpty()) return bitmap
        var result = bitmap
        selection.presetIds.forEach { id ->
            resolvePreset(id, customPresets)?.let { preset ->
                result = applyPreset(result, preset, customPresets)
            }
        }
        CleanAdjustmentKind.entries.forEach { kind ->
            val level = selection.adjustments[kind] ?: return@forEach
            val preset = DocumentFilterPresets.adjustmentPreset(kind, level)
            result = applyPreset(result, preset, customPresets)
        }
        return result
    }

    fun describeSelection(selection: CleanFilterSelection, customPresets: List<DocumentFilterPreset>): String {
        val names = buildList {
            selection.presetIds.forEach { id ->
                resolvePreset(id, customPresets)?.let { add(it.name) }
            }
            selection.adjustments.forEach { (kind, level) ->
                add("${kind.label} $level")
            }
        }
        return names.joinToString(", ")
    }

    private fun resolvePreset(id: String, customPresets: List<DocumentFilterPreset>): DocumentFilterPreset? {
        customPresets.find { it.id == id }?.let { custom ->
            return custom
        }
        return DocumentFilterPresets.byId(id)
    }

    private fun applyPreset(bitmap: Bitmap, preset: DocumentFilterPreset, customPresets: List<DocumentFilterPreset>): Bitmap {
        preset.savedSelection?.let { nested ->
            return applyStack(bitmap, nested, customPresets)
        }
        var result = ImageCleanProcessor.apply(bitmap, preset.clean)
        preset.gray?.takeIf { it.active }?.let { gray ->
            result = ImageGrayProcessor.apply(result, gray)
        }
        return result
    }
}
