package com.cheradip.ailanguagetutor.core.model

/** Thrown when a guest user has used all free AI requests and must sign in. */
class GuestAiLimitReachedException(
    val requestCount: Int,
    val limit: Int = GUEST_AI_REQUEST_LIMIT,
) : Exception("Guest AI limit reached ($requestCount/$limit). Sign in to continue.")

const val GUEST_AI_REQUEST_LIMIT = 99
