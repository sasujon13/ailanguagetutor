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

    /** One equation per line; blank line before math for readability. */
    private fun organizeMathBlocks(text: String): String {
        val lines = text.lines()
        if (lines.size <= 1) return text
        return buildString {
            var prevWasEquation = false
            lines.map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
                val equation = isEquationLine(line)
                if (equation && !prevWasEquation) appendLine()
                when {
                    line.startsWith("##") -> appendLine(line)
                    else -> appendLine(line)
                }
                prevWasEquation = equation
            }
        }.trim()
    }

    fun isEquationLine(line: String): Boolean {
        if (line.startsWith("```") || line.startsWith("#")) return false
        val trimmed = line.trim()
        if (trimmed.length < 3) return false
        val hasOperator = trimmed.any { it in "=+−-*/^" }
        val hasDigitOrVar = trimmed.any { it.isDigit() || it in "xyzαβγθπ" }
        return hasOperator && hasDigitOrVar && trimmed.length < 120
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
