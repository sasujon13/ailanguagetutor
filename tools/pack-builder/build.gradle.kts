plugins {
    application
    alias(libs.plugins.kotlin.jvm)
}

application {
    mainClass.set("com.cheradip.packbuilder.MainKt")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.sqlite.jdbc)
    implementation(libs.moshi.kotlin)
    implementation(libs.kotlinx.coroutines.core)
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    workingDir = rootProject.projectDir
}
