package com.cheradip.ailanguagetutor.feature.help

enum class ManualType(
    val id: String,
    val assetFileName: String,
    val titleKey: String,
    val subtitleKey: String,
) {
    USER(
        id = "user",
        assetFileName = "USER_MANUAL.md",
        titleKey = "manual_user_title",
        subtitleKey = "manual_user_subtitle",
    ),
    ADMIN(
        id = "admin",
        assetFileName = "ADMIN_MANUAL.md",
        titleKey = "manual_admin_title",
        subtitleKey = "manual_admin_subtitle",
    ),
    DEVELOPER(
        id = "developer",
        assetFileName = "DEVELOPER_MANUAL.md",
        titleKey = "manual_developer_title",
        subtitleKey = "manual_developer_subtitle",
    ),
    ;

    companion object {
        fun fromId(id: String?): ManualType? = entries.find { it.id == id }

        fun visibleToUser(isAdmin: Boolean): List<ManualType> =
            if (isAdmin) listOf(USER, ADMIN, DEVELOPER) else listOf(USER)
    }
}
