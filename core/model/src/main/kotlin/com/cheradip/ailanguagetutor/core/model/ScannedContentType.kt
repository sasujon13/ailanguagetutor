package com.cheradip.ailanguagetutor.core.model

/** Detected layout of scanned page content after OCR. */
enum class ScannedContentType {
    /** Normal paragraphs, letters, articles. */
    PROSE,
    /** Equations, formulas, math homework. */
    MATH,
    /** Source code, scripts, terminal output. */
    CODE,
    /** Flowcharts, decision trees, process diagrams with labels. */
    FLOWCHART,
    /** Mostly image/diagram with little readable text. */
    DIAGRAM,
    /** Multiple distinct blocks (e.g. code + prose). */
    MIXED,
    ;

    /** Home/local AI is weak for these — prefer cloud LLM APIs. */
    fun prefersCloudStructure(): Boolean = when (this) {
        MATH, CODE, FLOWCHART, DIAGRAM, MIXED -> true
        PROSE -> false
    }

    fun displayLabel(): String = when (this) {
        PROSE -> "Document text"
        MATH -> "Mathematics"
        CODE -> "Code"
        FLOWCHART -> "Flowchart"
        DIAGRAM -> "Diagram / image"
        MIXED -> "Mixed content"
    }

    companion object {
        fun fromStored(value: String?): ScannedContentType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PROSE
    }
}
