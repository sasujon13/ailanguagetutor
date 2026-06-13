package com.cheradip.ailanguagetutor.core.ai

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiResponseFormatterTest {

    @Test
    fun brokenLatexFractionBecomesPlainEquation() {
        val raw = "${'$'}${'$'}d=\\frac{y}{\\tan \\alpha........"
        val out = AiResponseFormatter.format(raw)
        assertFalse(out.contains("$$"), "Should not contain LaTeX delimiters")
        assertFalse(out.contains("\\frac"), "Should not contain \\frac")
        assertTrue(out.contains("d=y/tan α") || out.contains("d = y/tan α"))
    }

    @Test
    fun pairedDisplayMathIsSimplified() {
        val raw = "Given ${'$'}${'$'}d=\\frac{y}{\\tan \\alpha}${'$'}${'$'} find d."
        val out = AiResponseFormatter.format(raw)
        assertFalse(out.contains("$$"))
        assertTrue(out.contains("d=y/tan α") || out.contains("d = y/tan α"))
    }

    @Test
    fun mixedBengaliEnglishDetected() {
        val text = "এই সমীকরণটি solve করুন: x + 2 = 5 এবং find the value."
        val profile = MixedLanguageAnalyzer.analyze(text, "bn", "bn")
        assertTrue(profile.isMixed)
    }
}
