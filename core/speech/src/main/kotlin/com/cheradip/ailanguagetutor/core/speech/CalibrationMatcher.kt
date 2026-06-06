package com.cheradip.ailanguagetutor.core.speech

object CalibrationMatcher {
    private const val WORD_THRESHOLD = 0.70f
    private const val SENTENCE_THRESHOLD = 0.75f
    private const val PARAGRAPH_THRESHOLD = 0.85f

    fun isMatch(reference: String, spoken: String, tier: CalibrationTier): Boolean =
        score(reference, spoken) >= threshold(tier)

    fun score(reference: String, spoken: String): Float {
        val refWords = tokenize(reference)
        if (refWords.isEmpty()) return 0f
        val spokenWords = tokenize(spoken)
        if (spokenWords.isEmpty()) return 0f
        val matched = refWords.count { ref ->
            spokenWords.any { said -> fuzzyMatch(ref, said) }
        }
        return matched.toFloat() / refWords.size.toFloat()
    }

    private fun threshold(tier: CalibrationTier): Float = when (tier) {
        CalibrationTier.WORD -> WORD_THRESHOLD
        CalibrationTier.SENTENCE -> SENTENCE_THRESHOLD
        CalibrationTier.PARAGRAPH -> PARAGRAPH_THRESHOLD
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s']"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    private fun fuzzyMatch(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length >= 3 && b.length >= 3) {
            if (a.startsWith(b) || b.startsWith(a)) return true
            if (levenshtein(a, b) <= 1) return true
        }
        return false
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val costs = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = i - 1
            costs[0] = i
            for (j in 1..b.length) {
                val temp = costs[j]
                costs[j] = minOf(
                    costs[j] + 1,
                    costs[j - 1] + 1,
                    prev + if (a[i - 1] == b[j - 1]) 0 else 1,
                )
                prev = temp
            }
        }
        return costs[b.length]
    }
}
