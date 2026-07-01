import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.android") {
        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }
    afterEvaluate {
        if (pluginManager.hasPlugin("org.jetbrains.kotlin.plugin.compose")) {
            val libs = rootProject.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
            dependencies.add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
            dependencies.add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
        }
    }
}
