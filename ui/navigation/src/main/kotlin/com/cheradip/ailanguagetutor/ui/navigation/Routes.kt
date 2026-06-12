package com.cheradip.ailanguagetutor.ui.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PRACTICE_HUB = "practice/{activityId}?startVoice={startVoice}"
    const val GRAMMAR = "grammar"
    const val LIBRARY = "library"
    const val LANGUAGES = "languages"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val SCANNER = "scanner?mode={mode}"
    const val SCANNER_DOC = "scanner/{documentId}"
    const val OCR_PROCESSING = "ocr/{documentId}"
    const val READER = "reader/{documentId}"
    const val STUDY_LIST = "study"
    const val LOGIN = "login?returnTo={returnTo}"
    const val REGISTER = "register?returnTo={returnTo}"
    const val PAYWALL = "paywall"
    const val REFERRAL = "referral"
    const val ADMIN = "admin"
    const val ADMIN_AI = "admin/ai"
    const val MODE_SELECTION = "mode_selection"

    fun practiceHub(startVoice: Boolean = false, activityId: Long = -1L) =
        "practice/$activityId?startVoice=$startVoice"
    fun scanner(mode: String = "camera") = "scanner?mode=$mode"
    fun login(returnTo: String = "") = "login?returnTo=$returnTo"
    fun register(returnTo: String = "") = "register?returnTo=$returnTo"
    fun scannerDoc(documentId: Long) = "scanner/$documentId"
    fun ocrProcessing(documentId: Long) = "ocr/$documentId"
    fun reader(documentId: Long) = "reader/$documentId"
}
