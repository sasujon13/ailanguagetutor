#!/usr/bin/env python3
"""Generate Phase 0 multi-module Android scaffold."""
import os
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

ANDROID_CORE = [
    "database", "network", "ocr", "image", "audio", "ai", "auth", "billing",
    "device", "translation", "pack", "speech",
]
FEATURES = [
    "scanner", "reader", "dictionary", "languages", "journal", "auth", "profile",
    "billing", "practice", "settings", "onboarding", "home",
]
UI = ["theme", "components", "navigation"]
JVM_CORE = ["common", "model", "domain"]


def write(path: str, content: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)


def android_manifest(namespace: str) -> str:
    return f"""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
"""


def jvm_build(module: str) -> str:
    pkg = module.replace(":", ".")
    return f"""plugins {{
    alias(libs.plugins.kotlin.jvm)
}}

kotlin {{
    jvmToolchain(17)
}}

dependencies {{
    implementation(libs.kotlinx.coroutines.core)
}}
"""


def android_lib_build(namespace: str, extra_plugins: str = "", deps: str = "") -> str:
    plugins = """plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)"""
    if extra_plugins:
        plugins += f"\n    {extra_plugins}"
    plugins += "\n}"
    return f"""{plugins}

android {{
    namespace = "{namespace}"
    compileSdk = 36

    defaultConfig {{
        minSdk = 26
    }}

    compileOptions {{
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }}

    buildFeatures {{
        compose = true
    }}
}}

dependencies {{
    implementation(libs.androidx.core.ktx)
{deps}
}}
"""


def stub_kt(path: str, pkg: str, name: str = "Placeholder") -> None:
    write(path, f"package {pkg}\n\n/** Phase 0 stub — implement in later phases. */\ninternal object {name}\n")


# --- settings.gradle.kts ---
modules = ["app"] + [f"ui:{u}" for u in UI]
modules += [f"core:{c}" for c in JVM_CORE + ANDROID_CORE]
modules += [f"feature:{f}" for f in FEATURES]

settings = """pluginManagement {
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

"""
for m in modules:
    settings += f'include(":{m}")\n'
write(os.path.join(ROOT, "settings.gradle.kts"), settings)

write(os.path.join(ROOT, "gradle.properties"), """org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
""")

write(os.path.join(ROOT, "gradle/wrapper/gradle-wrapper.properties"), """distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-8.13-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
""")

write(os.path.join(ROOT, "gradle/libs.versions.toml"), """[versions]
agp = "8.7.2"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
compose-bom = "2024.10.01"
hilt = "2.52"
room = "2.6.1"
retrofit = "2.11.0"
okhttp = "4.12.0"
camerax = "1.4.0"
mlkit-text-latin = "19.0.1"
coil = "2.7.0"
work = "2.9.1"
navigation = "2.8.4"
lifecycle = "2.8.7"
billing = "7.1.1"
datastore = "1.1.1"
serialization = "1.7.3"
coroutines = "1.9.0"
core-ktx = "1.15.0"
activity-compose = "1.9.3"
hilt-navigation-compose = "1.2.0"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "core-ktx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity-compose" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hilt-navigation-compose" }
billing-ktx = { group = "com.android.billingclient", name = "billing-ktx", version.ref = "billing" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
mlkit-text-latin = { group = "com.google.android.gms", name = "play-services-mlkit-text-recognition", version.ref = "mlkit-text-latin" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
""")

write(os.path.join(ROOT, "build.gradle.kts"), """plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
""")

# JVM core modules
for m in JVM_CORE:
    mod_path = f"core/{m}"
    pkg = f"com.cheradip.ailanguagetutor.core.{m}"
    write(os.path.join(ROOT, mod_path, "build.gradle.kts"), jvm_build(m))
    stub_kt(os.path.join(ROOT, mod_path, f"src/main/kotlin/{pkg.replace('.', '/')}/Placeholder.kt"), pkg)

# core:common - Sha256Helper
write(os.path.join(ROOT, "core/common/src/main/kotlin/com/cheradip/ailanguagetutor/core/common/Sha256Helper.kt"), """package com.cheradip.ailanguagetutor.core.common

import java.security.MessageDigest

object Sha256Helper {
    fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
""")

# Android core stubs
for m in ANDROID_CORE:
    ns = f"com.cheradip.ailanguagetutor.core.{m}"
    mod_path = f"core/{m}"
    write(os.path.join(ROOT, mod_path, "build.gradle.kts"), android_lib_build(ns))
    write(os.path.join(ROOT, mod_path, "src/main/AndroidManifest.xml"), android_manifest(ns))
    stub_kt(os.path.join(ROOT, mod_path, f"src/main/kotlin/{ns.replace('.', '/')}/Placeholder.kt"), ns)

# UI theme
write(os.path.join(ROOT, "ui/theme/build.gradle.kts"), android_lib_build(
    "com.cheradip.ailanguagetutor.ui.theme",
    'alias(libs.plugins.kotlin.compose)',
    """    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling.preview)
"""))

write(os.path.join(ROOT, "ui/theme/src/main/AndroidManifest.xml"), android_manifest("com.cheradip.ailanguagetutor.ui.theme"))

write(os.path.join(ROOT, "ui/theme/src/main/kotlin/com/cheradip/ailanguagetutor/ui/theme/Color.kt"), """package com.cheradip.ailanguagetutor.ui.theme

import androidx.compose.ui.graphics.Color

val CheradipPrimary = Color(0xFF1B5E96)
val CheradipSecondary = Color(0xFF2E7D52)
val CheradipTertiary = Color(0xFFF57C00)
val CheradipSurface = Color(0xFFFAFAFA)
val CheradipSurfaceVariant = Color(0xFFE8EDF2)

val CheradipPrimaryDark = Color(0xFF90CAF9)
val CheradipSecondaryDark = Color(0xFF81C784)
val CheradipSurfaceDark = Color(0xFF121212)
""")

write(os.path.join(ROOT, "ui/theme/src/main/kotlin/com/cheradip/ailanguagetutor/ui/theme/Theme.kt"), """package com.cheradip.ailanguagetutor.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = CheradipPrimary,
    secondary = CheradipSecondary,
    tertiary = CheradipTertiary,
    surface = CheradipSurface,
    surfaceVariant = CheradipSurfaceVariant,
)

private val DarkColors = darkColorScheme(
    primary = CheradipPrimaryDark,
    secondary = CheradipSecondaryDark,
    surface = CheradipSurfaceDark,
)

@Composable
fun CheradipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
""")

# UI navigation - routes only
write(os.path.join(ROOT, "ui/navigation/build.gradle.kts"), """plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.cheradip.ailanguagetutor.ui.navigation"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.navigation.compose)
}
""")

write(os.path.join(ROOT, "ui/navigation/src/main/AndroidManifest.xml"), android_manifest("com.cheradip.ailanguagetutor.ui.navigation"))

write(os.path.join(ROOT, "ui/navigation/src/main/kotlin/com/cheradip/ailanguagetutor/ui/navigation/Routes.kt"), """package com.cheradip.ailanguagetutor.ui.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PRACTICE_HUB = "practice"
    const val LIBRARY = "library"
    const val LANGUAGES = "languages"
    const val SETTINGS = "settings"
    const val SCANNER = "scanner"
    const val LOGIN = "login"
}
""")

# UI components - LanguageFlagBadge stub
write(os.path.join(ROOT, "ui/components/build.gradle.kts"), android_lib_build(
    "com.cheradip.ailanguagetutor.ui.components",
    'alias(libs.plugins.kotlin.compose)',
    """    implementation(project(":ui:theme"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
"""))

write(os.path.join(ROOT, "ui/components/src/main/AndroidManifest.xml"), android_manifest("com.cheradip.ailanguagetutor.ui.components"))

write(os.path.join(ROOT, "ui/components/src/main/kotlin/com/cheradip/ailanguagetutor/ui/components/LanguageFlagBadge.kt"), """package com.cheradip.ailanguagetutor.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LanguageFlagBadge(
    flagEmoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = flagEmoji, fontSize = (size.value * 0.55f).sp)
        }
    }
}
""")

# Feature home
write(os.path.join(ROOT, "feature/home/build.gradle.kts"), android_lib_build(
    "com.cheradip.ailanguagetutor.feature.home",
    'alias(libs.plugins.kotlin.compose)',
    """    implementation(project(":ui:theme"))
    implementation(project(":ui:components"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.lifecycle.runtime.compose)
"""))

write(os.path.join(ROOT, "feature/home/src/main/AndroidManifest.xml"), android_manifest("com.cheradip.ailanguagetutor.feature.home"))

write(os.path.join(ROOT, "feature/home/src/main/kotlin/com/cheradip/ailanguagetutor/feature/home/HomeScreen.kt"), """package com.cheradip.ailanguagetutor.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onScanClick: () -> Unit = {},
    onPracticeClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "AI Language Tutor",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "243 languages · Offline-first · Cheradip",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onPracticeClick, modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Practice")
        }
        Button(onClick = onScanClick, modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Scan New Document")
        }
    }
}
""")

# Other feature stubs
for f in FEATURES:
    if f == "home":
        continue
    ns = f"com.cheradip.ailanguagetutor.feature.{f}"
    mod_path = f"feature/{f}"
    write(os.path.join(ROOT, mod_path, "build.gradle.kts"), android_lib_build(ns))
    write(os.path.join(ROOT, mod_path, "src/main/AndroidManifest.xml"), android_manifest(ns))
    stub_kt(os.path.join(ROOT, mod_path, f"src/main/kotlin/{ns.replace('.', '/')}/Placeholder.kt"), ns)

# App module
write(os.path.join(ROOT, "app/build.gradle.kts"), """plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.cheradip.ailanguagetutor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cheradip.ailanguagetutor"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
    }
}

dependencies {
    implementation(project(":ui:theme"))
    implementation(project(":ui:components"))
    implementation(project(":ui:navigation"))
    implementation(project(":feature:home"))
    implementation(project(":core:common"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
}
""")

write(os.path.join(ROOT, "app/proguard-rules.pro"), """# AI Language Tutor — add rules as modules grow
""")

write(os.path.join(ROOT, "app/src/main/AndroidManifest.xml"), """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:name=".AiLanguageTutorApp"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@android:style/Theme.Material.Light.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""")

write(os.path.join(ROOT, "app/src/main/res/values/strings.xml"), """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">AI Language Tutor</string>
</resources>
""")

write(os.path.join(ROOT, "app/src/main/kotlin/com/cheradip/ailanguagetutor/AiLanguageTutorApp.kt"), """package com.cheradip.ailanguagetutor

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AiLanguageTutorApp : Application()
""")

write(os.path.join(ROOT, "app/src/main/kotlin/com/cheradip/ailanguagetutor/MainActivity.kt"), """package com.cheradip.ailanguagetutor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cheradip.ailanguagetutor.feature.home.HomeScreen
import com.cheradip.ailanguagetutor.ui.navigation.Routes
import com.cheradip.ailanguagetutor.ui.theme.CheradipTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CheradipTheme {
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }
                val tabs = listOf(
                    Triple(Routes.HOME, "Home", Icons.Default.Home),
                    Triple(Routes.PRACTICE_HUB, "Practice", Icons.Default.Translate),
                    Triple(Routes.LIBRARY, "Learning", Icons.Default.MenuBook),
                    Triple(Routes.LANGUAGES, "Languages", Icons.Default.Translate),
                    Triple(Routes.SETTINGS, "Settings", Icons.Default.Settings),
                )
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            tabs.forEachIndexed { index, (_, label, icon) ->
                                NavigationBarItem(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    when (selectedTab) {
                        0 -> HomeScreen(
                            modifier = Modifier.padding(innerPadding),
                            onScanClick = { /* Phase 1 */ },
                            onPracticeClick = { selectedTab = 1 },
                        )
                        else -> Text(
                            text = "Coming in Phase ${selectedTab + 1}",
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}
""")

# Fix HomeScreen to accept modifier - update feature home
write(os.path.join(ROOT, "feature/home/src/main/kotlin/com/cheradip/ailanguagetutor/feature/home/HomeScreen.kt"), """package com.cheradip.ailanguagetutor.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onScanClick: () -> Unit = {},
    onPracticeClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "AI Language Tutor",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "243 languages · Offline-first · Cheradip",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onPracticeClick, modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Practice")
        }
        Button(onClick = onScanClick, modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Scan New Document")
        }
    }
}
""")

# Copy catalog to assets
src_catalog = os.path.join(ROOT, "catalog", "world_languages.json")
dst_catalog = os.path.join(ROOT, "app", "src", "main", "assets", "catalog", "world_languages.json")
if os.path.exists(src_catalog):
    os.makedirs(os.path.dirname(dst_catalog), exist_ok=True)
    shutil.copy2(src_catalog, dst_catalog)

# README
write(os.path.join(ROOT, "README.md"), """# AI Language Tutor by Cheradip

Offline-first Android language learning app — **243 languages**, OCR, practice modes, teen tutor voice.

## Docs

- **Spec:** [`ailanguagetutor.md`](ailanguagetutor.md)
- **Build progress:** [`BUILD_STATUS.md`](BUILD_STATUS.md)
- **Agent guide:** [`AGENTS.md`](AGENTS.md)

## Quick start

1. Open this folder in **Android Studio** (Ladybug / Koala or newer).
2. **Sync Gradle** — JDK 17 required.
3. Run **`app`** on emulator API 26+.

```bash
./gradlew assembleDebug
```

## Project structure

```
app/                 Application + MainActivity + Nav shell
ui/theme/            Cheradip Material 3
ui/navigation/       Route constants
ui/components/       LanguageFlagBadge, shared Compose
feature/home/        Home screen (Phase 0)
core/common/         Sha256Helper, shared utilities
catalog/             world_languages.json (243 langs)
```

## Build phases

Follow **`ailanguagetutor.md` → Build Order** (Phase 0–14). Do not skip phases.

## Version

App **1.0.0** · Package `com.cheradip.ailanguagetutor`
""")

print("Phase 0 scaffold generated at", ROOT)
