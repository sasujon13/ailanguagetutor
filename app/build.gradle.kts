import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val releaseApkUsesUploadKeystore =
    project.findProperty("alt.releaseApkUsesUploadKeystore")?.toString()?.equals("true", ignoreCase = true) == true

val keystorePropertiesFile = rootProject.file("keystore.properties")
val localEnvFile = rootProject.file("local.env.properties")

fun loadLocalEnv(key: String, default: String = ""): String {
    if (localEnvFile.exists()) {
        val p = Properties()
        localEnvFile.inputStream().use { p.load(it) }
        p.getProperty(key)?.let { return it }
    }
    return System.getenv(key) ?: default
}

android {
    namespace = "com.cheradip.ailanguagetutor"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.cheradip.ailanguagetutor"
        // minSdk 26 (Android 8.0): CameraX, ML Kit OCR, Play Billing 7.x, scoped storage baseline
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 2
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("long", "HOME_AI_TIMEOUT_MS", "30000L")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
        if (releaseApkUsesUploadKeystore) {
            create("release") {
                check(keystorePropertiesFile.exists()) {
                    "Create keystore.properties (see keystore.properties.example) or set alt.releaseApkUsesUploadKeystore=false."
                }
                val props = Properties()
                keystorePropertiesFile.inputStream().use { props.load(it) }
                val storeFileProp = props.getProperty("storeFile")
                    ?: error("keystore.properties: missing storeFile")
                val keyStore = rootProject.file(storeFileProp)
                check(keyStore.isFile) { "Keystore not found: ${keyStore.absolutePath}" }
                storeFile = keyStore
                storePassword = props.getProperty("storePassword")
                    ?: error("keystore.properties: missing storePassword")
                keyAlias = props.getProperty("keyAlias") ?: error("keystore.properties: missing keyAlias")
                keyPassword = props.getProperty("keyPassword")
                    ?: error("keystore.properties: missing keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Debug: local.env.properties or LAN/tunnel dev URLs (server/ never packaged in APK)
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${loadLocalEnv("API_BASE_URL", "https://ailt.cheradip.com/api/ailt/")}\"",
            )
            buildConfigField(
                "String",
                "HOME_AI_BASE_URL",
                "\"${loadLocalEnv("HOME_AI_BASE_URL", "https://ai.cheradip.com")}\"",
            )
            buildConfigField(
                "String",
                "ADMIN_SEED_PASSWORD",
                "\"${loadLocalEnv("ADMIN_SEED_PASSWORD")}\"",
            )
        }
        release {
            // Release: Cloudflare tunnel endpoints (server/cloud-api + server/v2 on your PC)
            buildConfigField("String", "API_BASE_URL", "\"https://ailt.cheradip.com/api/ailt/\"")
            buildConfigField("String", "HOME_AI_BASE_URL", "\"https://ai.cheradip.com\"")
            buildConfigField("String", "ADMIN_SEED_PASSWORD", "\"\"")
            signingConfig = if (releaseApkUsesUploadKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val linkReleaseAfterDebug =
    project.findProperty("alt.assembleReleaseWithDebug")?.toString()?.equals("false", ignoreCase = true) != true
afterEvaluate {
    if (linkReleaseAfterDebug) {
        tasks.named("assembleDebug").configure { finalizedBy("assembleRelease") }
        tasks.named("installDebug").configure { finalizedBy("assembleRelease") }
    }
    tasks.named("assembleRelease").configure {
        doLast {
            val apk = layout.buildDirectory.get().asFile.resolve("outputs/apk/release/app-release.apk")
            if (apk.isFile) {
                logger.lifecycle("")
                logger.lifecycle("AI Language Tutor: app-release.apk → ${apk.absolutePath}")
                logger.lifecycle("")
            }
        }
    }
}

dependencies {
    implementation(project(":ui:theme"))
    implementation(project(":ui:components"))
    implementation(project(":core:locale"))
    implementation(project(":ui:navigation"))
    implementation(project(":feature:home"))
    implementation(project(":feature:onboarding"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:translation"))
    implementation(project(":core:device"))
    implementation(project(":core:ai"))
    implementation(project(":core:ocr"))
    implementation(project(":core:audio"))
    implementation(project(":core:auth"))
    implementation(project(":core:billing"))
    implementation(project(":core:speech"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.google.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
}
