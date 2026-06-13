package com.cheradip.ailanguagetutor.core.network

import org.json.JSONObject
import retrofit2.HttpException

fun Throwable.userFacingMessage(fallback: String = "Request failed"): String {
    if (this is HttpException) {
        val parsed = response()?.errorBody()?.use { body ->
            runCatching {
                val text = body.string()
                if (text.isBlank()) return@runCatching null
                val json = JSONObject(text)
                when (val detail = json.opt("detail")) {
                    is String -> detail
                    null -> text
                    else -> detail.toString()
                }
            }.getOrNull()
        }
        if (!parsed.isNullOrBlank()) return parsed
    }
    val raw = message.orEmpty()
    return if (raw.startsWith("HTTP ")) fallback else raw.ifBlank { fallback }
}
