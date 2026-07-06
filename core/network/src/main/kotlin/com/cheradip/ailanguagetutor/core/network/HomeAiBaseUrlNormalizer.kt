package com.cheradip.ailanguagetutor.core.network

/** Canonical Home AI base — inference only at ai.cheradip.com (not ailt.* or cheradip.com/ailt). */
object HomeAiBaseUrlNormalizer {
    const val PRODUCTION = "https://ai.cheradip.com/"

    fun normalize(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return PRODUCTION
        if (isDeprecatedHost(trimmed)) return PRODUCTION
        return trimmed.let { if (it.endsWith("/")) it else "$it/" }
    }

    fun isDeprecatedHost(url: String): Boolean =
        url.contains("ailt.cheradip.com", ignoreCase = true)
}
