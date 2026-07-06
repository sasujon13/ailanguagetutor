package com.cheradip.ailanguagetutor.core.ai

/** Home AI did not respond to /health within the reachability window. */
class HomeAiUnreachableException(
    message: String = "Home AI server unreachable",
    cause: Throwable? = null,
) : Exception(message, cause)

/** Home AI accepted the request but inference exceeded the response budget. */
class HomeAiResponseTimeoutException(
    val timeoutMs: Long,
    cause: Throwable? = null,
) : Exception("Home AI response timed out after ${timeoutMs}ms", cause)
