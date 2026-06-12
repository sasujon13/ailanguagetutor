package com.cheradip.ailanguagetutor.core.image

import org.json.JSONArray
import org.json.JSONObject

private fun QuadPoints.toJson(): JSONObject = JSONObject().apply {
    put("tlx", topLeft.x); put("tly", topLeft.y)
    put("trx", topRight.x); put("try", topRight.y)
    put("brx", bottomRight.x); put("bry", bottomRight.y)
    put("blx", bottomLeft.x); put("bly", bottomLeft.y)
}

private fun JSONObject.toQuadPoints(): QuadPoints = QuadPoints(
    topLeft = PointF(optDouble("tlx", 0.05).toFloat(), optDouble("tly", 0.05).toFloat()),
    topRight = PointF(optDouble("trx", 0.95).toFloat(), optDouble("try", 0.05).toFloat()),
    bottomRight = PointF(optDouble("brx", 0.95).toFloat(), optDouble("bry", 0.95).toFloat()),
    bottomLeft = PointF(optDouble("blx", 0.05).toFloat(), optDouble("bly", 0.95).toFloat()),
)

private fun cropParamsFromJson(obj: JSONObject) = CropParams(
    corners = obj.optJSONObject("corners")?.toQuadPoints() ?: QuadPoints.fullFrame(),
    preset = runCatching { CropPreset.valueOf(obj.optString("preset")) }.getOrDefault(CropPreset.RECTANGLE),
    rotationDegrees = obj.optDouble("rotationDegrees", 0.0).toFloat(),
    autoStraighten = obj.optBoolean("autoStraighten"),
    perspectiveCorrection = obj.optBoolean("perspectiveCorrection"),
    keystoneCorrection = obj.optBoolean("keystoneCorrection"),
    horizontalAlignment = obj.optBoolean("horizontalAlignment"),
    verticalAlignment = obj.optBoolean("verticalAlignment"),
)

private fun CropParams.toJson(): JSONObject = JSONObject().apply {
    put("corners", corners.toJson())
    put("preset", preset.name)
    put("rotationDegrees", rotationDegrees)
    put("autoStraighten", autoStraighten)
    put("perspectiveCorrection", perspectiveCorrection)
    put("keystoneCorrection", keystoneCorrection)
    put("horizontalAlignment", horizontalAlignment)
    put("verticalAlignment", verticalAlignment)
}

private fun transitionParamsFromJson(obj: JSONObject) = TransitionParams(
    corners = obj.optJSONObject("corners")?.toQuadPoints() ?: QuadPoints(),
    perspectiveStrength = obj.optInt("perspectiveStrength", 80),
    rotationDegrees = obj.optDouble("rotationDegrees", 0.0).toFloat(),
    verticalCorrection = obj.optInt("verticalCorrection", 50),
    horizontalCorrection = obj.optInt("horizontalCorrection", 50),
    pageFlattening = obj.optInt("pageFlattening", 50),
    autoDetect = obj.optBoolean("autoDetect", true),
    autoStraightenText = obj.optBoolean("autoStraightenText"),
    curvedPageCorrection = obj.optBoolean("curvedPageCorrection"),
    scanType = runCatching { DocumentScanType.valueOf(obj.optString("scanType")) }.getOrDefault(DocumentScanType.AUTO),
)

private fun TransitionParams.toJson(): JSONObject = JSONObject().apply {
    put("corners", corners.toJson())
    put("perspectiveStrength", perspectiveStrength)
    put("rotationDegrees", rotationDegrees)
    put("verticalCorrection", verticalCorrection)
    put("horizontalCorrection", horizontalCorrection)
    put("pageFlattening", pageFlattening)
    put("autoDetect", autoDetect)
    put("autoStraightenText", autoStraightenText)
    put("curvedPageCorrection", curvedPageCorrection)
    put("scanType", scanType.name)
}

private fun cleanParamsFromJson(obj: JSONObject) = CleanParams(
    brightness = obj.optInt("brightness", 50),
    contrast = obj.optInt("contrast", 50),
    sharpness = obj.optInt("sharpness", 50),
    noiseReduction = obj.optInt("noiseReduction", 30),
    shadowRemoval = obj.optInt("shadowRemoval", 40),
    paperWhitening = obj.optInt("paperWhitening", 35),
    inkEnhancement = obj.optInt("inkEnhancement", 45),
    autoEnhance = obj.optBoolean("autoEnhance"),
    adaptiveThreshold = obj.optBoolean("adaptiveThreshold"),
    preserveSignatures = obj.optBoolean("preserveSignatures", true),
    preserveStamps = obj.optBoolean("preserveStamps", true),
    preserveLogos = obj.optBoolean("preserveLogos", true),
)

private fun CleanParams.toJson(): JSONObject = JSONObject().apply {
    put("brightness", brightness); put("contrast", contrast); put("sharpness", sharpness)
    put("noiseReduction", noiseReduction); put("shadowRemoval", shadowRemoval)
    put("paperWhitening", paperWhitening); put("inkEnhancement", inkEnhancement)
    put("autoEnhance", autoEnhance); put("adaptiveThreshold", adaptiveThreshold)
    put("preserveSignatures", preserveSignatures)
    put("preserveStamps", preserveStamps)
    put("preserveLogos", preserveLogos)
}

private fun grayParamsFromJson(obj: JSONObject) = GrayParams(
    mode = runCatching { GrayMode.valueOf(obj.optString("mode")) }.getOrDefault(GrayMode.STANDARD),
    brightness = obj.optInt("brightness", 50),
    contrast = obj.optInt("contrast", 50),
    exposure = obj.optInt("exposure", 50),
    gamma = obj.optInt("gamma", 50),
    blackPoint = obj.optInt("blackPoint", 5),
    whitePoint = obj.optInt("whitePoint", 95),
    darkenText = obj.optBoolean("darkenText"),
    lightenPaper = obj.optBoolean("lightenPaper"),
    improveOcrAccuracy = obj.optBoolean("improveOcrAccuracy"),
)

private fun GrayParams.toJson(): JSONObject = JSONObject().apply {
    put("mode", mode.name)
    put("brightness", brightness); put("contrast", contrast); put("exposure", exposure)
    put("gamma", gamma); put("blackPoint", blackPoint); put("whitePoint", whitePoint)
    put("darkenText", darkenText); put("lightenPaper", lightenPaper)
    put("improveOcrAccuracy", improveOcrAccuracy)
}

private fun snapshotFromJson(obj: JSONObject?) = obj?.let {
    EditHistorySnapshot(
        appliedCrop = it.optJSONObject("appliedCrop")?.let { c -> cropParamsFromJson(c) },
        appliedTransition = it.optJSONObject("appliedTransition")?.let { t -> transitionParamsFromJson(t) },
        appliedClean = it.optJSONObject("appliedClean")?.let { c -> cleanParamsFromJson(c) },
        appliedGray = it.optJSONObject("appliedGray")?.let { g -> grayParamsFromJson(g) },
    )
}

private fun EditHistorySnapshot.toJson(): JSONObject = JSONObject().apply {
    appliedCrop?.let { put("appliedCrop", it.toJson()) }
    appliedTransition?.let { put("appliedTransition", it.toJson()) }
    appliedClean?.let { put("appliedClean", it.toJson()) }
    appliedGray?.let { put("appliedGray", it.toJson()) }
}

private fun editHistoryFromJson(obj: JSONObject) = EditHistoryEntry(
    stage = EditStage.valueOf(obj.getString("stage")),
    label = obj.getString("label"),
    timestampMs = obj.optLong("timestampMs"),
    snapshot = snapshotFromJson(obj.optJSONObject("snapshot")),
)

private fun EditHistoryEntry.toJson(): JSONObject = JSONObject().apply {
    put("stage", stage.name); put("label", label); put("timestampMs", timestampMs)
    snapshot?.let { put("snapshot", it.toJson()) }
}

fun PageEditState.toJson(): String = JSONObject().apply {
    put("originalPath", originalPath)
    put("workingPath", workingPath)
    appliedCrop?.let { put("appliedCrop", it.toJson()) }
    appliedTransition?.let { put("appliedTransition", it.toJson()) }
    appliedClean?.let { put("appliedClean", it.toJson()) }
    appliedGray?.let { put("appliedGray", it.toJson()) }
    put("historyIndex", historyIndex)
    put("history", JSONArray().apply { history.forEach { put(it.toJson()) } })
}.toString()

fun parsePageEditStateJson(pageId: Long, json: String?, originalPath: String, workingPath: String): PageEditState {
    if (json.isNullOrBlank()) {
        return PageEditState(pageId = pageId, originalPath = originalPath, workingPath = workingPath)
    }
    return runCatching {
        val obj = JSONObject(json)
        PageEditState(
            pageId = pageId,
            originalPath = obj.optString("originalPath", originalPath),
            workingPath = obj.optString("workingPath", workingPath),
            appliedCrop = obj.optJSONObject("appliedCrop")?.let { cropParamsFromJson(it) },
            appliedTransition = obj.optJSONObject("appliedTransition")?.let { transitionParamsFromJson(it) },
            appliedClean = obj.optJSONObject("appliedClean")?.let { cleanParamsFromJson(it) },
            appliedGray = obj.optJSONObject("appliedGray")?.let { grayParamsFromJson(it) },
            historyIndex = obj.optInt("historyIndex", -1),
            history = obj.optJSONArray("history")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { editHistoryFromJson(it) }
                }
            } ?: emptyList(),
        )
    }.getOrElse {
        PageEditState(pageId = pageId, originalPath = originalPath, workingPath = workingPath)
    }
}
