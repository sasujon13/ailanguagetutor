package com.cheradip.ailanguagetutor.core.pack

/**
 * Resolves regional language tags (e.g. bn-IN, pt-BR) to pack codes and ordered fallback chains.
 * Works for all 243 catalog languages plus common BCP-47 regional tags used in the UI.
 */
object LanguageCodeResolver {
    private val regionalToBase = mapOf(
        "bn-in" to "bn",
        "bn-bd" to "bn",
        "hi-in" to "hi",
        "pt-br" to "pt",
        "pt-pt" to "pt",
        "zh-cn" to "zh",
        "zh-tw" to "zh",
        "zh-hk" to "zh",
        "zh-sg" to "zh",
        "en-us" to "en",
        "en-gb" to "en",
        "en-au" to "en",
        "en-in" to "en",
        "fr-fr" to "fr",
        "fr-ca" to "fr",
        "es-es" to "es",
        "es-mx" to "es",
        "de-de" to "de",
        "de-at" to "de",
        "ar-sa" to "ar",
        "ar-eg" to "ar",
    )

    /** Regional variants that share a base ISO 639-1 pack (same SQLite / JSON pack on server). */
    private val baseToRegionalSiblings = mapOf(
        "bn" to listOf("bn-bd", "bn-in"),
        "hi" to listOf("hi-in"),
        "pt" to listOf("pt-br", "pt-pt"),
        "zh" to listOf("zh-cn", "zh-tw", "zh-hk"),
        "en" to listOf("en-us", "en-gb", "en-au", "en-in"),
        "fr" to listOf("fr-fr", "fr-ca"),
        "es" to listOf("es-es", "es-mx"),
        "de" to listOf("de-de", "de-at"),
        "ar" to listOf("ar-sa", "ar-eg"),
    )

    fun normalizePackCode(code: String): String {
        val normalized = code.lowercase().trim().replace('_', '-')
        regionalToBase[normalized]?.let { return it }
        val base = normalized.substringBefore('-')
        return regionalToBase[base] ?: base.ifBlank { normalized }
    }

    /** Ordered pack codes to try for lookups (requested tag → base → regional siblings → English hub). */
    fun packFallbackChain(code: String): List<String> {
        val raw = code.lowercase().trim().replace('_', '-')
        val base = normalizePackCode(raw)
        val chain = linkedSetOf<String>()
        fun add(c: String) {
            val key = c.lowercase().trim()
            if (key.isNotBlank()) chain.add(key)
        }
        add(raw)
        if (base != raw) add(base)
        baseToRegionalSiblings[base]?.forEach { add(it) }
        add("en")
        return chain.toList()
    }

    fun isEnglish(code: String): Boolean = normalizePackCode(code) == "en"
}
