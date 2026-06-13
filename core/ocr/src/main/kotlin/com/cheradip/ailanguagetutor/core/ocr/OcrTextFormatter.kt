package com.cheradip.ailanguagetutor.core.ocr

import com.cheradip.ailanguagetutor.core.model.ScannedContentType

/** Offline fallback when cloud/home AI is unavailable. */
object OcrTextFormatter {

    fun format(raw: String, type: ScannedContentType): String {
        val normalized = fixCommonOcrErrors(raw.trim())
        return when (type) {
            ScannedContentType.CODE -> formatCode(normalized)
            ScannedContentType.MATH -> formatMath(normalized)
            ScannedContentType.FLOWCHART -> formatFlowchart(normalized)
            ScannedContentType.DIAGRAM -> formatDiagram(normalized)
            ScannedContentType.MIXED -> formatMixed(normalized)
            ScannedContentType.PROSE -> formatProse(normalized)
        }
    }

    private fun fixCommonOcrErrors(text: String): String = text
        .replace(Regex("(?<=\\w)l(?=\\d)"), "1")
        .replace(Regex("(?<=\\s)O(?=\\d)"), "0")
        .replace("—", "-")
        .replace("–", "-")
        .replace(Regex("[ \t]+"), " ")
        .replace(Regex("\n{3,}"), "\n\n")

    private fun formatProse(text: String): String =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")

    private fun formatMath(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        return buildString {
            appendLine("## Mathematics")
            appendLine()
            lines.forEach { line ->
                if (EQUATION_HINT.matches(line)) {
                    appendLine("**$line**")
                } else {
                    appendLine(line)
                }
                appendLine()
            }
        }.trim()
    }

    private fun formatCode(text: String): String {
        val lines = text.lines()
        val lang = guessLanguage(lines.joinToString("\n"))
        return buildString {
            appendLine("```$lang")
            append(lines.joinToString("\n") { it.trimEnd() })
            append("\n```")
        }
    }

    private fun formatFlowchart(text: String): String = buildString {
        appendLine("## Flowchart")
        appendLine()
        text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                when {
                    line.contains("->") || line.contains("-->") || line.contains("→") ->
                        appendLine("- $line")
                    else -> appendLine("**$line**")
                }
            }
    }.trim()

    private fun formatDiagram(text: String): String =
        if (text.isBlank()) {
            "This page appears to be mostly visual content. OCR found little text — view the original scan image for diagrams or photos."
        } else {
            "## Diagram notes\n\n$text"
        }

    private fun formatMixed(text: String): String {
        val blocks = text.split(Regex("\n{2,}"))
        return blocks.joinToString("\n\n") { block ->
            val trimmed = block.trim()
            when {
                scoreCodeBlock(trimmed) >= 3 -> formatCode(trimmed)
                scoreMathBlock(trimmed) >= 2 -> formatMath(trimmed)
                else -> formatProse(trimmed)
            }
        }
    }

    private fun guessLanguage(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("fun ") || lower.contains("val ") -> "kotlin"
            lower.contains("def ") || "import " in lower -> "python"
            lower.contains("function ") || lower.contains("const ") -> "javascript"
            lower.contains("#include") -> "cpp"
            lower.contains("public class") -> "java"
            else -> ""
        }
    }

    private fun scoreCodeBlock(text: String): Int {
        var s = 0
        if (text.count { it in "{}[]();" } > text.length / 10) s += 2
        if (text.lines().any { it.trimStart().startsWith("//") }) s += 2
        return s
    }

    private fun scoreMathBlock(text: String): Int =
        if (EQUATION_HINT.matches(text)) 2 else 0

    private val EQUATION_HINT = Regex(".*[=^+\\-*/].*[0-9x].*", RegexOption.IGNORE_CASE)
}
