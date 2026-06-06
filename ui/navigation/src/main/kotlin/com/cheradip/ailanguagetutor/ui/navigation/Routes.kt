package com.cheradip.ailanguagetutor.ui.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PRACTICE_HUB = "practice"
    const val LIBRARY = "library"
    const val LANGUAGES = "languages"
    const val SETTINGS = "settings"
    const val SCANNER = "scanner"
    const val SCANNER_DOC = "scanner/{documentId}"
    const val OCR_PROCESSING = "ocr/{documentId}"
    const val READER = "reader/{documentId}"
    const val STUDY_LIST = "study"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val PAYWALL = "paywall"
    const val REFERRAL = "referral"
    const val ADMIN = "admin"
    const val ADMIN_AI = "admin/ai"
    const val MODE_SELECTION = "mode_selection"

    fun scannerDoc(documentId: Long) = "scanner/$documentId"
    fun ocrProcessing(documentId: Long) = "ocr/$documentId"
    fun reader(documentId: Long) = "reader/$documentId"
}
