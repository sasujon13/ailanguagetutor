package com.cheradip.ailanguagetutor.core.image

/** Named filter preset — combines clean, optional gray, and optional transition tweaks. */
data class DocumentFilterPreset(
    val id: String,
    val name: String,
    val clean: CleanParams,
    val gray: GrayParams? = null,
    val transition: TransitionParams? = null,
    val isCustomSlot: Boolean = false,
    val savedSelection: CleanFilterSelection? = null,
)

object DocumentFilterPresets {
    private fun clean(
        brightness: Int = 50,
        contrast: Int = 50,
        sharpness: Int = 0,
        noise: Int = 0,
        shadow: Int = 0,
        paper: Int = 0,
        ink: Int = 0,
        gamma: Int = 50,
        autoEnhance: Boolean = false,
        adaptive: Boolean = false,
    ) = CleanParams(
        brightness = brightness,
        contrast = contrast,
        sharpness = sharpness,
        noiseReduction = noise,
        shadowRemoval = shadow,
        paperWhitening = paper,
        inkEnhancement = ink,
        gamma = gamma,
        autoEnhance = autoEnhance,
        adaptiveThreshold = adaptive,
    )

    private fun gray(
        mode: GrayMode = GrayMode.STANDARD,
        brightness: Int = 50,
        contrast: Int = 50,
        exposure: Int = 50,
        gamma: Int = 50,
        black: Int = 5,
        white: Int = 95,
        ocr: Boolean = false,
        darkenText: Boolean = false,
        lightenPaper: Boolean = false,
    ) = GrayParams(
        active = true,
        mode = mode,
        brightness = brightness,
        contrast = contrast,
        exposure = exposure,
        gamma = gamma,
        blackPoint = black,
        whitePoint = white,
        improveOcrAccuracy = ocr,
        darkenText = darkenText,
        lightenPaper = lightenPaper,
    )

    private fun straighten(level: Int) = TransitionParams(
        autoDetect = true,
        autoStraightenText = true,
        perspectiveStrength = if (level <= 3) 12 + level * 6 else 35 + level * 7,
        pageFlattening = 12 + level * 9,
        curvedPageCorrection = level >= 7,
    )

    val colorRowIds = listOf(
        "color", "bw", "magic", "gray", "soft_gray", "newspaper", "ocr",
    )

    val documentRowIds = listOf(
        "document", "receipt", "book", "handwritten", "historical",
        "text_dark", "paper_white", "shadow_fix", "sharp", "denoise",
    )

    val adjustmentKinds = CleanAdjustmentKind.entries

    val builtIn: List<DocumentFilterPreset> = listOf(
        DocumentFilterPreset("color", "Color", clean(brightness = 52, contrast = 54, sharpness = 48)),
        DocumentFilterPreset("magic", "Magic color", clean(brightness = 55, contrast = 58, shadow = 55, paper = 50, ink = 52, autoEnhance = true)),
        DocumentFilterPreset("gray", "Gray", clean(contrast = 52), gray(mode = GrayMode.STANDARD)),
        DocumentFilterPreset("soft_gray", "Soft gray", clean(brightness = 54), gray(mode = GrayMode.SOFT, contrast = 45)),
        DocumentFilterPreset("bw", "B&W", clean(contrast = 62, paper = 55), gray(mode = GrayMode.HIGH_CONTRAST, contrast = 68)),
        DocumentFilterPreset("newspaper", "Newspaper", clean(sharpness = 55), gray(mode = GrayMode.NEWSPAPER)),
        DocumentFilterPreset("ocr", "OCR", clean(sharpness = 60, ink = 58), gray(mode = GrayMode.OCR_OPTIMIZED, ocr = true)),

        DocumentFilterPreset("document", "Document", clean(brightness = 54, contrast = 60, paper = 58, ink = 50, shadow = 50)),
        DocumentFilterPreset("receipt", "Receipt", clean(contrast = 64, paper = 62), gray(mode = GrayMode.RECEIPT)),
        DocumentFilterPreset("book", "Book", clean(brightness = 52, paper = 42), gray(mode = GrayMode.BOOK)),
        DocumentFilterPreset("handwritten", "Handwritten", clean(sharpness = 48), gray(mode = GrayMode.HANDWRITTEN, darkenText = true)),
        DocumentFilterPreset("historical", "Historical", clean(contrast = 55), gray(mode = GrayMode.HISTORICAL)),
        DocumentFilterPreset("text_dark", "Text boost", clean(ink = 62, contrast = 58), gray(contrast = 62, black = 12, darkenText = true)),
        DocumentFilterPreset("paper_white", "White paper", clean(paper = 68, shadow = 58, brightness = 58), gray(lightenPaper = true)),
        DocumentFilterPreset("shadow_fix", "Shadow fix", clean(shadow = 72, brightness = 56, paper = 50)),
        DocumentFilterPreset("sharp", "Sharp", clean(sharpness = 72, contrast = 56)),
        DocumentFilterPreset("denoise", "Denoise", clean(noise = 65, sharpness = 42)),
    )

    fun byId(id: String): DocumentFilterPreset? = builtIn.find { it.id == id }

    fun presetName(id: String): String = byId(id)?.name ?: id

    fun adjustmentPreset(kind: CleanAdjustmentKind, level: Int): DocumentFilterPreset {
        val clamped = level.coerceIn(0, 9)
        val t = clamped / 9f
        return when (kind) {
            CleanAdjustmentKind.BRIGHTNESS -> DocumentFilterPreset(
                id = "adj_brightness_$clamped",
                name = "Brightness $clamped",
                clean = clean(brightness = lerp(50, 78, t), contrast = lerp(50, 54, t)),
            )
            CleanAdjustmentKind.DARK -> DocumentFilterPreset(
                id = "adj_dark_$clamped",
                name = "Dark $clamped",
                clean = clean(
                    brightness = lerp(50, 24, t),
                    contrast = lerp(50, 62, t),
                    shadow = lerp(0, 65, t),
                ),
            )
            CleanAdjustmentKind.CONTRAST -> DocumentFilterPreset(
                id = "adj_contrast_$clamped",
                name = "Contrast $clamped",
                clean = clean(contrast = lerp(50, 82, t), brightness = lerp(50, 48, t)),
            )
            CleanAdjustmentKind.EXPOSURE -> DocumentFilterPreset(
                id = "adj_exposure_$clamped",
                name = "Exposure $clamped",
                clean = clean(
                    brightness = lerp(50, 72, t),
                    shadow = lerp(0, 68, t),
                    contrast = lerp(50, 56, t),
                ),
            )
            CleanAdjustmentKind.GAMMA -> DocumentFilterPreset(
                id = "adj_gamma_$clamped",
                name = "Gamma $clamped",
                clean = clean(
                    gamma = lerp(50, 74, t),
                    contrast = lerp(50, 62, t),
                ),
            )
            CleanAdjustmentKind.STRAIGHTEN -> DocumentFilterPreset(
                id = "adj_straighten_$clamped",
                name = "Straighten $clamped",
                clean = clean(sharpness = if (clamped == 0) 0 else lerp(48, 62, t)),
                transition = if (clamped == 0) null else straighten(clamped),
            )
        }
    }

    fun customSlot(id: String, name: String, selection: CleanFilterSelection) = DocumentFilterPreset(
        id = id,
        name = name,
        clean = CleanParams(filterPresetId = id),
        isCustomSlot = true,
        savedSelection = selection,
    )

    private fun lerp(min: Int, max: Int, t: Float): Int = (min + (max - min) * t).toInt()
}
