package com.cheradip.ailanguagetutor.core.model

/** Input/output language pairing for Practice and Mode Selection. */
object PracticeLanguageRules {
    fun translationOutputCodes(activeCodes: List<String>, inputCode: String): List<String> =
        activeCodes.map { it.lowercase() }.distinct()
            .filter { !it.equals(inputCode, ignoreCase = true) }

    fun resolveOutputForTranslationInput(
        activeCodes: List<String>,
        inputCode: String,
        preferredOutput: String?,
    ): String {
        val options = translationOutputCodes(activeCodes, inputCode)
        if (options.isEmpty()) return inputCode.lowercase()
        preferredOutput?.takeIf { preferred ->
            options.any { it.equals(preferred, ignoreCase = true) }
        }?.let { return it.lowercase() }
        return options.first().lowercase()
    }

    fun isValidTranslationPair(input: String, output: String): Boolean =
        !input.equals(output, ignoreCase = true)

    fun reconcileOutput(
        intent: ProcessingIntent,
        activeCodes: List<String>,
        input: String,
        output: String,
    ): String = when (intent) {
        ProcessingIntent.TRANSLATION ->
            resolveOutputForTranslationInput(activeCodes, input, output)
        ProcessingIntent.ANSWER -> output.lowercase().takeIf { code ->
            activeCodes.any { it.equals(code, ignoreCase = true) }
        } ?: input.lowercase()
    }
}
