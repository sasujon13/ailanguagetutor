pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ailanguagetutor"

include(":app")
include(":ui:theme")
include(":ui:components")
include(":ui:navigation")
include(":core:common")
include(":core:model")
include(":core:domain")
include(":core:database")
include(":core:network")
include(":core:ocr")
include(":core:image")
include(":core:audio")
include(":core:ai")
include(":core:auth")
include(":core:billing")
include(":core:device")
include(":core:translation")
include(":core:pack")
include(":core:locale")
include(":core:speech")
include(":feature:scanner")
include(":feature:reader")
include(":feature:dictionary")
include(":feature:languages")
include(":feature:journal")
include(":feature:auth")
include(":feature:profile")
include(":feature:billing")
include(":feature:practice")
include(":feature:settings")
include(":feature:onboarding")
include(":feature:grammar")
include(":feature:home")
include(":tools:pack-builder")
