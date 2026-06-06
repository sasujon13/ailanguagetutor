package com.cheradip.ailanguagetutor.core.model

enum class GrammarDepth(val id: Int, val label: String, val subtitle: String) {
    WORD(1, "Word grammar", "Tap a word — role & form in its sentence"),
    SENTENCE(2, "Sentence grammar", "Tap a word — full sentence structure"),
    PARAGRAPH(3, "Paragraph grammar", "Tap anywhere — whole paragraph analysis"),
    ;

    companion object {
        fun fromId(id: Int): GrammarDepth = entries.firstOrNull { it.id == id } ?: WORD
    }
}
