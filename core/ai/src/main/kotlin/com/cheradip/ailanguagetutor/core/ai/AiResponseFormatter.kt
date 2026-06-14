package com.cheradip.ailanguagetutor.core.ai

/**
 * Normalizes AI / OCR output for readable mobile display (fixes broken LaTeX, organizes math).
 */
object AiResponseFormatter {

    fun format(raw: String): String {
        if (raw.isBlank()) return raw
        var text = raw.trim()
        text = fixBrokenLatexDelimiters(text)
        if (text.contains('\\') || text.contains("frac") || text.contains("$$")) {
            text = simplifyLatex(text)
        }
        text = organizeMathBlocks(text)
        text = stripMarkdownEmphasis(text)
        text = normalizeWhitespace(text)
        return text.trim()
    }

    /** Strip orphan $$ and convert display-math blocks to plain structured lines. */
    private fun fixBrokenLatexDelimiters(text: String): String {
        if (!text.contains('$')) return text
        val paired = Regex("""(?<!\$)\$\$(.+?)\$\$""", RegexOption.DOT_MATCHES_ALL)
        if (paired.containsMatchIn(text)) {
            return paired.replace(text) { match ->
                val inner = match.groupValues[1].trim()
                "\n${simplifyLatex(inner)}\n"
            }
        }
        // Unclosed or single $$ — remove delimiters then simplify the whole fragment
        var cleaned = text.replace("$$", "")
        cleaned = cleaned.replace(Regex("(?<!\\$)\\$(?!\\$)(.+?)(?<!\\$)\\$(?!\\$)")) { m ->
            simplifyLatex(m.groupValues[1].trim())
        }
        return simplifyLatex(cleaned)
    }

    private fun simplifyLatex(text: String): String {
        var s = text
        s = s.replace(Regex("""\\frac\{([^}]+)\}\{([^}]+)\}""")) { m ->
            "${m.groupValues[1].trim()}/${m.groupValues[2].trim()}"
        }
        // OCR often truncates closing braces: \frac{y}{\tan \alpha....
        s = s.replace(Regex("""\\frac\{([^}]+)\}\{([^}\n]*)""")) { m ->
            "${m.groupValues[1].trim()}/${m.groupValues[2].trim()}"
        }
        s = s.replace(Regex("""\\sqrt\{([^}]+)\}""")) { m -> "√(${m.groupValues[1].trim()})" }
        s = s.replace(Regex("""\\text\{([^}]+)\}""")) { m -> m.groupValues[1].trim() }
        val replacements = mapOf(
            "\\times" to "×",
            "\\cdot" to "·",
            "\\div" to "÷",
            "\\leq" to "≤",
            "\\geq" to "≥",
            "\\neq" to "≠",
            "\\approx" to "≈",
            "\\infty" to "∞",
            "\\alpha" to "α",
            "\\beta" to "β",
            "\\gamma" to "γ",
            "\\theta" to "θ",
            "\\pi" to "π",
            "\\sigma" to "σ",
            "\\Delta" to "Δ",
            "\\tan" to "tan",
            "\\sin" to "sin",
            "\\cos" to "cos",
            "\\log" to "log",
            "\\ln" to "ln",
            "\\left" to "",
            "\\right" to "",
            "\\," to " ",
            "\\;" to " ",
            "\\!" to "",
            "{" to "",
            "}" to "",
        )
        replacements.forEach { (k, v) -> s = s.replace(k, v) }
        s = s.replace(Regex("""\\[a-zA-Z]+""")) { "" }
        s = s.replace(Regex("""\s+"""), " ").trim()
        return s
    }

    /** One equation per line; preserve paragraph breaks. */
    private fun organizeMathBlocks(text: String): String {
        val lines = text.lines()
        if (lines.size <= 1) return text
        return buildString {
            var prevWasEquation = false
            lines.forEach { rawLine ->
                if (rawLine.isBlank()) {
                    appendLine()
                    prevWasEquation = false
                    return@forEach
                }
                val line = rawLine.trimEnd()
                val trimmed = line.trim()
                val equation = isEquationLine(trimmed)
                if (equation && !prevWasEquation) appendLine()
                appendLine(trimmed)
                prevWasEquation = equation
            }
        }.trim()
    }

    fun isEquationLine(line: String): Boolean {
        if (line.startsWith("```") || line.startsWith("#")) return false
        val trimmed = line.trim()
        if (trimmed.length < 3 || trimmed.length >= 120) return false
        if (containsNonLatinScript(trimmed)) return false
        val hasEquals = trimmed.contains('=')
        val hasLatex = trimmed.contains('\\') || trimmed.contains("frac")
        val hasStrongMath = Regex("""\\frac|√|∫|∑|∏|≤|≥|≠|≈|∞""").containsMatchIn(trimmed)
        if (!hasEquals && !hasLatex && !hasStrongMath) return false
        val hasDigitOrVar = trimmed.any { it.isDigit() || it in "xyzαβγθπ" }
        val hasMathOperator = trimmed.any { it in "=+−-*/^" }
        return hasMathOperator && hasDigitOrVar && (hasEquals || hasLatex || hasStrongMath)
    }

    private fun containsNonLatinScript(text: String): Boolean {
        var nonLatinLetters = 0
        var latinLetters = 0
        text.forEach { ch ->
            when {
                ch in 'a'..'z' || ch in 'A'..'Z' -> latinLetters++
                ch.isLetter() -> nonLatinLetters++
            }
        }
        return nonLatinLetters >= 4 && nonLatinLetters >= latinLetters
    }

    private fun stripMarkdownEmphasis(text: String): String =
        text.replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
            .replace(Regex("""(?<!\*)\*(?!\*)([^*]+)(?<!\*)\*(?!\*)"""), "$1")

    private fun normalizeWhitespace(text: String): String =
        text.replace(Regex("\n{3,}"), "\n\n")
            .replace(Regex("[ \t]+"), " ")
            .lines()
            .joinToString("\n") { it.trimEnd() }
}
