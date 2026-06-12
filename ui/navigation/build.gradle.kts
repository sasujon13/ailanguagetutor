plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.cheradip.ailanguagetutor.ui.navigation"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":ui:theme"))
    implementation(project(":ui:components"))
    implementation(project(":core:locale"))
    implementation(project(":core:model"))
    implementation(project(":core:auth"))
    implementation(project(":core:billing"))
    implementation(project(":core:device"))
    implementation(project(":core:database"))
    implementation(project(":core:audio"))
    implementation(project(":feature:home"))
    implementation(project(":feature:scanner"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:languages"))
    implementation(project(":feature:journal"))
    implementation(project(":feature:practice"))
    implementation(project(":feature:grammar"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:billing"))
    implementation(project(":core:locale"))
    implementation(project(":feature:onboarding"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
