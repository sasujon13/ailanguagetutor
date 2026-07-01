import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// OpenCV + heavy image math triggers intermittent K2 ICE under incremental compile.
tasks.withType<KotlinCompile>().configureEach {
    incremental = false
}

android {
    namespace = "com.cheradip.ailanguagetutor.core.image"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.opencv)
    implementation(libs.pdfboxAndroid)
}