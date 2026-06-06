package com.cheradip.ailanguagetutor.core.common

import java.security.MessageDigest

object Sha256Helper {
    fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
