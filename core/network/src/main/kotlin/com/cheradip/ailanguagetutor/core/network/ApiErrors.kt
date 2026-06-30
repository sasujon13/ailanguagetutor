package com.cheradip.ailanguagetutor.core.network

import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

const val CHECK_INTERNET_CONNECTION = "You are Offline! Connect to Internet."

const val AUTH_NOT_REGISTERED_CODE = "NOT_REGISTERED"
const val AUTH_PASSWORD_MISMATCH_CODE = "PASSWORD_MISMATCH"

const val AUTH_NOT_REGISTERED_MESSAGE =
    "Not registered with your inputted Email/Mobile Number."
const val AUTH_PASSWORD_MISMATCH_MESSAGE = "Password Not Matched!"
const val AUTH_PASSWORD_CONFIRM_MISMATCH_MESSAGE = "Password Not Matched!"

/**
 * For features that require internet: offline → [CHECK_INTERNET_CONNECTION];
 * online → map technical failures to plain language while keeping server/custom text.
 */
fun resolveInternetRequiredError(
    isOnline: Boolean,
    error: Throwable?,
    fallback: String,
): String {
    if (!isOnline) return CHECK_INTERNET_CONNECTION
    return error?.userFacingMessage(fallback) ?: fallback
}

fun mapAuthApiDetail(detail: String): String? = when (detail.trim()) {
    AUTH_NOT_REGISTERED_CODE -> AUTH_NOT_REGISTERED_MESSAGE
    AUTH_PASSWORD_MISMATCH_CODE -> AUTH_PASSWORD_MISMATCH_MESSAGE
    else -> detail.takeIf { it.isLikelyUserCustomMessage() }
}

fun loginFailureMessage(error: Throwable, fallback: String = "Login failed"): String {
    val http = error as? HttpException
    return http?.loginErrorMessage(fallback) ?: error.userFacingMessage(fallback)
}

fun HttpException.loginErrorMessage(fallback: String = "Login failed"): String {
    parseHttpDetail(this)?.let { mapAuthApiDetail(it) }?.let { return it }
    return userFacingMessage(fallback)
}

fun Throwable.userFacingMessage(fallback: String = "Something went wrong. Please try again."): String {
    if (this is HttpException) {
        parseHttpDetail(this)?.let { mapAuthApiDetail(it) }?.let { return it }
        parseHttpDetail(this)?.takeIf { it.isLikelyUserCustomMessage() }?.let { return it }
        return httpStatusFallback(code(), fallback)
    }

    val raw = message?.trim().orEmpty()
    if (raw.isLikelyUserCustomMessage()) return raw

    if (isLikelyConnectivityFailure()) {
        return onlineConnectivityFailureMessage()
    }

    return fallback
}

private fun Throwable.isLikelyConnectivityFailure(): Boolean = when (this) {
    is UnknownHostException, is ConnectException, is SocketTimeoutException, is SSLException -> true
    is IOException -> message.orEmpty().lowercase().let { msg ->
        msg.contains("unable to resolve host") ||
            msg.contains("failed to connect") ||
            msg.contains("connection reset") ||
            msg.contains("network is unreachable") ||
            msg.contains("timeout") ||
            msg.contains("etimedout") ||
            msg.contains("econnrefused") ||
            msg.contains("socket") ||
            msg.contains("hostname")
    }
    else -> {
        val msg = message.orEmpty().lowercase()
        msg.contains("unable to resolve host") ||
            msg.contains("failed to connect") ||
            msg.contains("connection reset") ||
            msg.contains("network is unreachable") ||
            msg.contains("timeout")
    }
}

private fun onlineConnectivityFailureMessage(): String =
    "Could not reach the server. Please try again in a moment."

private fun parseHttpDetail(error: HttpException): String? =
    error.response()?.errorBody()?.use { body ->
        runCatching {
            val text = body.string()
            if (text.isBlank()) return@runCatching null
            val json = JSONObject(text)
            when (val detail = json.opt("detail")) {
                is String -> detail
                null -> text.takeIf { it.isLikelyUserCustomMessage() }
                else -> detail.toString().takeIf { it.isLikelyUserCustomMessage() }
            }
        }.getOrNull()
    }

private fun httpStatusFallback(code: Int, fallback: String): String = when (code) {
    400 -> fallback.ifBlank { "Invalid request. Please check your input and try again." }
    401 -> "Please sign in again."
    403 -> "You don't have permission to do that."
    404 -> "That item was not found on the server."
    408 -> "The request timed out. Please try again."
    429 -> "Too many requests. Please wait a moment and try again."
    in 500..599 -> "The server is temporarily unavailable. Please try again later."
    else -> fallback
}

private fun String.isLikelyUserCustomMessage(): Boolean {
    if (isBlank()) return false
    if (startsWith("HTTP ", ignoreCase = true)) return false
    if (contains("Exception", ignoreCase = true)) return false
    if (contains("java.", ignoreCase = true)) return false
    if (contains("kotlin.", ignoreCase = true)) return false
    if (startsWith("home_ai_", ignoreCase = true)) return false
    if (startsWith("cloud_structure:", ignoreCase = true)) return false
    if (matches(Regex("""^[a-zA-Z0-9_.]+Exception:?.*"""))) return false
    if (length > 240) return false
    return true
}
