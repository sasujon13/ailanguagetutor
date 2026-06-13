package com.cheradip.ailanguagetutor.core.ai

/**
 * Detects mixed-script input (e.g. Bengali + English, Arabic + English) for smarter prompts.
 */
object MixedLanguageAnalyzer {

    data class Profile(
        val configuredInput: String,
        val configuredOutput: String,
        val detectedPrimary: String?,
        val detectedSecondary: String?,
        val isMixed: Boolean,
        val hasMath: Boolean,
        val hasCode: Boolean,
    ) {
        fun effectiveSourceLang(): String =
            detectedPrimary?.takeIf { isMixed } ?: configuredInput

        fun promptSection(): String {
            val primary = detectedPrimary ?: configuredInput
            val secondary = detectedSecondary
            val mixedNote = if (isMixed && secondary != null) {
                """
                - Input mixes $primary with $secondary (often English for technical terms).
                - Translate descriptive / explanatory prose into $configuredOutput.
                - When English appears alongside $primary, prefer $primary for explanations in $configuredOutput.
                """.trimIndent()
            } else {
                "- Respond in $configuredOutput for all descriptive explanation."
            }
            val preserveNote = buildString {
                if (hasMath) appendLine("- Keep equations, formulas, and math symbols exactly as in the source (do not translate math).")
                if (hasCode) appendLine("- Keep code, identifiers, and syntax exactly as in the source.")
                if (hasMath || hasCode) {
                    appendLine("- Use Unicode math (α, β, ×, ÷, √, ≤, ≥) or plain notation — not raw LaTeX $$ delimiters.")
                    appendLine("- Put each equation on its own line; add blank lines between derivation steps.")
                }
            }.trim()
            return buildString {
                appendLine("Language & formatting rules:")
                appendLine(mixedNote)
                if (preserveNote.isNotBlank()) append(preserveNote)
            }.trim()
        }
    }

    fun analyze(text: String, configuredInput: String, configuredOutput: String): Profile {
        val counts = scriptCounts(text)
        val ranked = counts.filter { it.key != "other" && it.value > 0 }.entries.sortedByDescending { it.value }
        val top = ranked.firstOrNull()
        val second = ranked.getOrNull(1)

        val detectedPrimary = top?.let { scriptToLang(it.key, configuredInput) }
        val detectedSecondary = second?.let { scriptToLang(it.key, configuredInput) }
            ?.takeIf { it != detectedPrimary }

        val isMixed = top != null && second != null &&
            top.value >= 8 && second.value >= 8 &&
            top.key != second.key

        val hasMath = MATH_HINT.containsMatchIn(text) || text.contains("$$")
        val hasCode = CODE_HINT.containsMatchIn(text) ||
            text.contains("```") ||
            text.lines().any { it.trimStart().startsWith("//") }

        return Profile(
            configuredInput = configuredInput.lowercase(),
            configuredOutput = configuredOutput.lowercase(),
            detectedPrimary = detectedPrimary,
            detectedSecondary = if (isMixed) detectedSecondary ?: "en" else null,
            isMixed = isMixed,
            hasMath = hasMath,
            hasCode = hasCode,
        )
    }

    private fun scriptCounts(text: String): Map<String, Int> {
        var latin = 0
        var bengali = 0
        var arabic = 0
        var devanagari = 0
        var cjk = 0
        var other = 0
        text.forEach { ch ->
            when (ch.code) {
                in 0x0000..0x007F, in 0x0080..0x024F -> if (ch.isLetter()) latin++
                in 0x0980..0x09FF -> bengali++
                in 0x0600..0x06FF, in 0x0750..0x077F -> arabic++
                in 0x0900..0x097F -> devanagari++
                in 0x4E00..0x9FFF, in 0x3040..0x30FF -> cjk++
                else -> if (!ch.isWhitespace()) other++
            }
        }
        return mapOf(
            "latin" to latin,
            "bn" to bengali,
            "ar" to arabic,
            "hi" to devanagari,
            "cjk" to cjk,
            "other" to other,
        )
    }

    private fun scriptToLang(script: String, fallback: String): String = when (script) {
        "bn" -> "bn"
        "ar" -> "ar"
        "hi" -> "hi"
        "cjk" -> if (fallback.startsWith("ja") || fallback.startsWith("ko")) fallback else "zh"
        "latin" -> "en"
        else -> fallback.lowercase()
    }

    private val MATH_HINT = Regex("""[=^+\-*/\\]|\\frac|\\tan|\\alpha|\$\$|∫|∑|√|≤|≥""")
    private val CODE_HINT = Regex("""(?m)^\s*(fun |def |class |import |#include|public |private |val |var |const )""")
}
