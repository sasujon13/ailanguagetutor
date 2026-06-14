package com.cheradip.ailanguagetutor.core.ocr

import com.cheradip.ailanguagetutor.core.model.ScannedContentType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScannedContentClassifier @Inject constructor() {

    fun classify(rawOcrText: String): ScannedContentType {
        val text = rawOcrText.trim()
        if (text.isBlank()) return ScannedContentType.DIAGRAM

        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.size <= 2 && text.length < 40) {
            return ScannedContentType.DIAGRAM
        }

        val scriptProfile = scriptProfile(text)
        val scores = mutableMapOf(
            ScannedContentType.CODE to scoreCode(text, lines),
            ScannedContentType.MATH to scoreMath(text, lines, scriptProfile),
            ScannedContentType.FLOWCHART to scoreFlowchart(text, lines),
        )

        val top = scores.maxByOrNull { it.value }?.takeIf { it.value >= 3 }
        if (top != null && top.key != ScannedContentType.PROSE) {
            val second = scores.filterKeys { it != top.key }.maxOfOrNull { it.value } ?: 0
            if (second >= 2 && top.value - second <= 1) {
                return ScannedContentType.MIXED
            }
            if (scriptProfile.hasSubstantialNonLatin && top.key == ScannedContentType.MATH) {
                return ScannedContentType.MIXED
            }
            return top.key
        }
        return ScannedContentType.PROSE
    }

    private data class ScriptProfile(
        val bengali: Int,
        val arabic: Int,
        val devanagari: Int,
        val latin: Int,
    ) {
        val hasSubstantialNonLatin: Boolean =
            (bengali + arabic + devanagari) >= 12 && (bengali + arabic + devanagari) >= latin
    }

    private fun scriptProfile(text: String): ScriptProfile {
        var bengali = 0
        var arabic = 0
        var devanagari = 0
        var latin = 0
        text.forEach { ch ->
            when (ch.code) {
                in 0x0980..0x09FF -> bengali++
                in 0x0600..0x06FF, in 0x0750..0x077F -> arabic++
                in 0x0900..0x097F -> devanagari++
                in 0x0000..0x024F -> if (ch.isLetter()) latin++
            }
        }
        return ScriptProfile(bengali, arabic, devanagari, latin)
    }

    private fun scoreCode(text: String, lines: List<String>): Int {
        var score = 0
        val lower = text.lowercase()
        val codeKeywords = listOf(
            "function ", "def ", "class ", "import ", "public ", "private ", "return ",
            "#include", "console.log", "println", "package ", "void ", "int ", "const ",
            "let ", "var ", "fun ", "struct ", "interface ", "enum ",
        )
        codeKeywords.forEach { if (lower.contains(it)) score += 2 }

        val symbolHits = text.count { it in "{}[]();<>=" }
        if (symbolHits > text.length / 8) score += 3
        if (lines.any { it.trimStart().startsWith("//") || it.trimStart().startsWith("#") || it.trimStart().startsWith("*") }) {
            score += 2
        }
        if (lines.any { INDENTED_CODE.matches(it) }) score += 2
        if (text.contains("=>") || text.contains("->") || text.contains("::")) score += 1
        return score
    }

    private fun scoreMath(text: String, lines: List<String>, script: ScriptProfile): Int {
        var score = 0
        if (MATH_SYMBOLS.containsMatchIn(text)) score += 2
        if (text.contains('$') || text.contains('\\') || text.contains("∫") || text.contains("∑")) score += 3
        val equationLines = lines.count { EQUATION_LINE.matches(it.trim()) }
        if (equationLines >= 2) score += 3
        else if (equationLines == 1) score += 1
        if (text.contains("x^") || text.contains("y^") || text.contains("^2") || text.contains("^3")) score += 1
        if (GREEK_LETTERS.containsMatchIn(text)) score += 1
        if (script.hasSubstantialNonLatin) score -= 3
        return score.coerceAtLeast(0)
    }

    private fun scoreFlowchart(text: String, lines: List<String>): Int {
        var score = 0
        val lower = text.lowercase()
        if (lower.contains("flowchart") || lower.contains("decision") || lower.contains("process")) score += 2
        if (lines.count { ARROW_LINE.matches(it.trim()) } >= 2) score += 3
        if (lines.count { it.contains("->") || it.contains("-->") || it.contains("→") } >= 2) score += 2
        if (lower.contains("yes") && lower.contains("no")) score += 1
        if (lines.any { BOX_LINE.matches(it.trim()) }) score += 1
        return score
    }

    companion object {
        private val MATH_SYMBOLS = Regex("[+*/=^√∫∑∏≤≥≠≈∞]")
        private val GREEK_LETTERS = Regex("[αβγδεζηθικλμνξοπρστυφχψω]", RegexOption.IGNORE_CASE)
        private val EQUATION_LINE = Regex(""".*=.*[0-9xXyYzZαβγθπ].*""")
        private val ARROW_LINE = Regex(".*(->|-->|→|=>).*")
        private val BOX_LINE = Regex("^[|\\[\\(+].+[|\\]\\)]+$")
        private val INDENTED_CODE = Regex("^\\s{2,}\\S+.*")
    }
}
