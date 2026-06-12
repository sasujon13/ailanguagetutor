package com.cheradip.ailanguagetutor.core.locale

/** English (US) source strings — default app language. All keys are batch-translated on language change. */
object AppStrings {
    const val DEFAULT_LANG = "en"
    const val DEFAULT_REGION = "US"

    val english: Map<String, String> = mapOf(
        // Bottom nav
        "nav_home" to "Home",
        "nav_practice" to "Practice",
        "nav_grammar" to "Grammar",
        "nav_learning" to "Learning",
        "nav_languages" to "Languages",
        "nav_profile" to "Profile",
        "nav_settings" to "Settings",

        // Profile
        "profile_title" to "Profile",
        "profile_dashboard_subtitle" to "Your account dashboard",
        "profile_guest_subtitle" to "Sign in or create an account",
        "profile_dashboard" to "Dashboard",
        "profile_role" to "Role",
        "profile_account_actions" to "Account",
        "profile_logout" to "Log out",
        "profile_sign_in_prompt" to "Sign in to sync referrals, subscriptions, and progress.",
        "profile_login" to "Login",
        "profile_sign_up" to "Sign up",
        "profile_guest_browse" to "Browse",
        "profile_sign_in_for_referrals" to "Sign in to view your referral link",
        "profile_sign_in_for_subscription" to "Sign in to manage your plan",
        "profile_footer" to "Use the menu (☰) on any screen to jump between sections.",

        // App language
        "app_language" to "App language",
        "app_language_active_only_hint" to "Activate up to 3 languages on the Languages tab to choose app language.",
        "onboarding_app_language_hint" to "App language must be one of your selected study languages.",
        "english_us" to "English (US)",
        "updating_language" to "Translating app…",
        "search_languages" to "Search languages…",
        "language_updated" to "App language updated",

        // Home
        "home_title" to "AI Language Tutor",
        "home_subtitle" to "243 languages · Offline-first",
        "home_prompt" to "What would you like to do?",
        "home_tips_header" to "Quick tips",
        "home_tips_body" to "Scan or import a document → tap words to learn → practice with voice or typing. Pick up to 3 languages in the Languages tab.",
        "action_scan" to "Scan",
        "action_camera" to "Camera",
        "action_import" to "Import",
        "action_practice" to "Practice",
        "action_type" to "Type",
        "action_voice" to "Voice",
        "action_listen" to "Listen",
        "action_learning" to "Learning",
        "action_grammar" to "Grammar",
        "settings_title" to "Settings",
        "settings_subtitle" to "Voice, AI, preferences",
        "section_teen_voice" to "Teen tutor voice",
        "voice_gender" to "Voice gender",
        "voice_teen_male" to "Teen male",
        "voice_teen_female" to "Teen female",
        "section_grammar" to "Grammar on tap",
        "grammar_detail_level" to "Grammar detail level",
        "section_learning_ai" to "Learning & AI",
        "settings_ai_mode" to "AI Mode, Languages & Voice Calibration",
        "settings_ai_mode_sub" to "Intent, engine, languages, mic setup",
        "settings_subscription" to "Subscription & Paywall",
        "settings_referrals" to "Referrals & credits",
        "settings_account" to "Account / Login",
        "section_admin" to "Admin",
        "settings_admin_console" to "Admin console",
        "settings_admin_ai" to "AI API status",
        "settings_admin_ai_sub" to "Providers, quotas, routing",
        "settings_footer" to "243 languages · Offline packs · Local AI when connected",

        // Languages tab
        "languages_title" to "Languages",
        "languages_subtitle_active" to "active · max 3",
        "languages_search" to "Search",
        "languages_search_hint" to "Filter 243 languages…",
        "languages_show" to "Show",
        "languages_filter_all" to "All languages",
        "languages_filter_downloaded" to "Downloaded",
        "languages_filter_active" to "Active",
        "languages_filter_not_downloaded" to "Not downloaded",
        "languages_shown" to "shown",
        "languages_loading" to "Loading languages…",
        "languages_no_match" to "No languages match your filters",
        "languages_downloaded_active" to "Downloaded · Active",
        "languages_downloaded" to "Downloaded",
        "languages_pack_downloaded" to "Pack downloaded for %s",
        "languages_now_active" to "%s is now active",
        "languages_deactivated" to "%s deactivated",
        "languages_max_active" to "You can only have 3 languages active at once. Turn off one of your active languages before turning this one on.",

        // Onboarding
        "onboarding_welcome" to "Welcome to AI Language Tutor",
        "onboarding_tagline" to "243 languages · Scan · Read · Learn offline",
        "onboarding_intro" to "Set your app language and download up to 3 study language packs to get started.",
        "continue" to "Continue",
        "next" to "Next",
        "onboarding_choose_langs" to "Choose your languages",
        "onboarding_langs_help" to "App language controls menus and labels (default English). Study packs are offline dictionary packs — pick 1 to 3.",
        "onboarding_study_packs" to "Study language packs",
        "onboarding_study_packs_hint" to "243 language packs · max 3 active at once",
        "onboarding_voice_title" to "Teen tutor voice",
        "onboarding_voice_label" to "Voice",
        "onboarding_finish_title" to "Download & finish",
        "onboarding_finish_app" to "App:",
        "onboarding_download_start" to "Download & Get started",
        "onboarding_downloading" to "Downloading language packs…",
        "onboarding_downloading_lang" to "Downloading %s…",
        "onboarding_download_complete" to "Download complete!",
        "onboarding_download_failed" to "Download failed. Try again.",
    )

    fun text(key: String, translations: Map<String, String>): String =
        translations[key] ?: english[key] ?: key

    fun format(key: String, translations: Map<String, String>, vararg args: Any): String {
        val template = text(key, translations)
        return if (args.isEmpty()) template else String.format(template, *args)
    }
}
