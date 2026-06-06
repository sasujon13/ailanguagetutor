package com.cheradip.ailanguagetutor.core.billing

/** Validates and normalizes promo codes from API / config (guards against translated "%s" placeholders). */
object PromoCodes {
    private val VALID = Regex("^[A-Z0-9_-]{2,32}$", RegexOption.IGNORE_CASE)

    fun sanitize(raw: String?): String {
        val code = raw.orEmpty().trim()
        if (code.isBlank() || code.contains('%') || !VALID.matches(code)) return ""
        return code.uppercase()
    }

    /** Auto-apply slot 1 code from server paywall config (promo_codes table only). */
    fun resolveAutoApplyCode(slot: PaywallSlot): String {
        if (!slot.visible || slot.discountPercent <= 0) return ""
        return sanitize(slot.code)
    }
}
