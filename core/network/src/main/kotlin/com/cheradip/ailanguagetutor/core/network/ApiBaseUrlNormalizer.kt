package com.cheradip.ailanguagetutor.core.network

/** Canonical App API base — must match nginx: cheradip.com/ailt/api/ → FastAPI /api/ailt/ */
object ApiBaseUrlNormalizer {
    const val PRODUCTION = "https://cheradip.com/ailt/api/"

    fun normalize(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return PRODUCTION
        // Legacy / mistyped host — Cloudflare 530; packs and auth live on cheradip.com.
        if (trimmed.contains("ailt.cheradip.com", ignoreCase = true)) return PRODUCTION
        return trimmed.let { if (it.endsWith("/")) it else "$it/" }
    }

    fun isDeprecatedHost(url: String): Boolean =
        url.contains("ailt.cheradip.com", ignoreCase = true)
}
