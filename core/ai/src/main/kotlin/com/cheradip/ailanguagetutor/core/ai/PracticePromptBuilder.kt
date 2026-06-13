package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.model.ProcessingIntent

/** High-quality tutor / translation prompts for Practice hub (home AI + cloud fallback). */
object PracticePromptBuilder {
    fun build(
        text: String,
        sourceLang: String,
        targetLang: String,
        intent: ProcessingIntent,
    ): String = when (intent) {
        ProcessingIntent.ANSWER -> buildAnswerPrompt(text, sourceLang, targetLang)
        ProcessingIntent.TRANSLATION -> buildTranslationPrompt(text, sourceLang, targetLang)
    }

    fun buildAnswerPrompt(text: String, sourceLang: String, targetLang: String): String {
        val trimmed = text.trim()
        val sameLang = sourceLang.equals(targetLang, ignoreCase = true)
        return if (sameLang) {
            """
            You are an expert language tutor. The learner submitted text in $sourceLang.

            Explain the following in clear, detailed $targetLang. Cover:
            - Overall meaning and context
            - Important grammar or structure
            - Key vocabulary (brief glosses where helpful)

            Write naturally in well-formed paragraphs — like Gemini or ChatGPT tutoring.
            Reply with the explanation only. Do not repeat the original text verbatim.

            Text:
            $trimmed
            """.trimIndent()
        } else {
            """
            You are an expert language tutor.

            Source text ($sourceLang):
            $trimmed

            Write a clear, detailed explanation entirely in $targetLang for a language learner.
            Cover meaning, grammar patterns, and useful vocabulary notes.
            Use natural paragraphs at the quality of Google Gemini or ChatGPT tutoring.
            Reply with the explanation only — no preamble or labels.
            """.trimIndent()
        }
    }

    fun buildTranslationPrompt(text: String, sourceLang: String, targetLang: String): String {
        val trimmed = text.trim()
        val unitHint = when {
            trimmed.lines().size > 1 || trimmed.length > 280 -> "paragraph"
            trimmed.contains(' ') -> "sentence"
            else -> "word or short phrase"
        }
        return """
        You are a professional translator. Translate from $sourceLang to $targetLang.

        Rules:
        - Output ONLY the translation in $targetLang — no notes, labels, or quotes around the whole text.
        - Preserve line breaks when the source has multiple lines.
        - Match natural fluency of Google Translate / Gemini for a $unitHint.
        - Keep names, numbers, and formatting faithful to the source.

        Source:
        $trimmed
        """.trimIndent()
    }
}
