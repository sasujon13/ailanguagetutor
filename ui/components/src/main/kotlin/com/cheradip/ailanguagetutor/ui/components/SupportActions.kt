package com.cheradip.ailanguagetutor.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri

object SupportActions {
    const val WHATSAPP_E164 = "+8801722710298"

    fun openWhatsAppSupport(context: Context, phoneE164: String = WHATSAPP_E164) {
        val digits = phoneE164.removePrefix("+")
        val uri = Uri.parse("https://wa.me/$digits")
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
