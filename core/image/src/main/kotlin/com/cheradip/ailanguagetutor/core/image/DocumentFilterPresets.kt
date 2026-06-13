package com.cheradip.ailanguagetutor.core.image

/** Named filter preset — combines clean, optional gray, and optional transition tweaks. */
data class DocumentFilterPreset(
    val id: String,
    val name: String,
    val clean: CleanParams,
    val gray: GrayParams? = null,
    val transition: TransitionParams? = null,
    val isCustomSlot: Boolean = false,
)

object DocumentFilterPresets {
    private fun clean(
        brightness: Int = 50,
        contrast: Int = 50,
        sharpness: Int = 50,
        noise: Int = 30,
        shadow: Int = 40,
        paper: Int = 35,
        ink: Int = 45,
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
        autoStraightenText = true,
        perspectiveStrength = 60 + level * 10,
        pageFlattening = 40 + level * 15,
        curvedPageCorrection = level >= 2,
    )

    val builtIn: List<DocumentFilterPreset> = listOf(
        DocumentFilterPreset("auto", "Auto", clean(autoEnhance = true, adaptive = true)),
        DocumentFilterPreset("color", "Color", clean(brightness = 52, contrast = 54, sharpness = 48)),
        DocumentFilterPreset("magic", "Magic color", clean(brightness = 55, contrast = 58, shadow = 55, paper = 50, ink = 52, autoEnhance = true)),

        DocumentFilterPreset("gray", "Gray", clean(contrast = 52), gray(mode = GrayMode.STANDARD)),
        DocumentFilterPreset("soft_gray", "Soft gray", clean(brightness = 54), gray(mode = GrayMode.SOFT, contrast = 45)),
        DocumentFilterPreset("bw", "B&W", clean(contrast = 62, paper = 55), gray(mode = GrayMode.HIGH_CONTRAST, contrast = 68)),
        DocumentFilterPreset("bw_strong", "B&W strong", clean(contrast = 70, paper = 60, ink = 55), gray(mode = GrayMode.HIGH_CONTRAST, contrast = 78, black = 8)),
        DocumentFilterPreset("newspaper", "Newspaper", clean(sharpness = 55), gray(mode = GrayMode.NEWSPAPER)),
        DocumentFilterPreset("ocr", "OCR", clean(sharpness = 60, ink = 58), gray(mode = GrayMode.OCR_OPTIMIZED, ocr = true)),

        DocumentFilterPreset("brightness_1", "Brightness 1", clean(brightness = 58, contrast = 50)),
        DocumentFilterPreset("brightness_2", "Brightness 2", clean(brightness = 65, contrast = 48, paper = 45)),
        DocumentFilterPreset("brightness_3", "Brightness 3", clean(brightness = 72, contrast = 46, paper = 52, shadow = 50)),

        DocumentFilterPreset("contrast_1", "Contrast 1", clean(contrast = 58)),
        DocumentFilterPreset("contrast_2", "Contrast 2", clean(contrast = 66, brightness = 52)),
        DocumentFilterPreset("contrast_3", "Contrast 3", clean(contrast = 74, brightness = 50, ink = 52)),

        DocumentFilterPreset("exposure_1", "Exposure 1", clean(brightness = 56, shadow = 48), gray(exposure = 55)),
        DocumentFilterPreset("exposure_2", "Exposure 2", clean(brightness = 62, shadow = 55), gray(exposure = 62, gamma = 52)),
        DocumentFilterPreset("exposure_3", "Exposure 3", clean(brightness = 68, shadow = 62, paper = 48), gray(exposure = 70, gamma = 48)),

        DocumentFilterPreset("straighten_1", "Straighten 1", clean(sharpness = 52), transition = straighten(0)),
        DocumentFilterPreset("straighten_2", "Straighten 2", clean(sharpness = 55, shadow = 48), transition = straighten(1)),
        DocumentFilterPreset("straighten_3", "Straighten 3", clean(sharpness = 58, contrast = 54), transition = straighten(2)),

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

        DocumentFilterPreset("gamma_1", "Gamma 1", clean(brightness = 52), gray(gamma = 55)),
        DocumentFilterPreset("gamma_2", "Gamma 2", clean(contrast = 54), gray(gamma = 62, contrast = 56)),
        DocumentFilterPreset("gamma_3", "Gamma 3", clean(contrast = 58, shadow = 50), gray(gamma = 70, black = 10)),

        DocumentFilterPreset("black_1", "Black point 1", clean(contrast = 56), gray(black = 12)),
        DocumentFilterPreset("black_2", "Black point 2", clean(contrast = 62, ink = 52), gray(black = 18, contrast = 60)),
        DocumentFilterPreset("black_3", "Black point 3", clean(contrast = 68), gray(black = 24, contrast = 66, gamma = 54)),
    )

    fun byId(id: String): DocumentFilterPreset? = builtIn.find { it.id == id }

    fun customSlot(id: String, name: String, clean: CleanParams, gray: GrayParams?, transition: TransitionParams?) =
        DocumentFilterPreset(id, name, clean.copy(filterPresetId = id), gray, transition, isCustomSlot = true)
}
