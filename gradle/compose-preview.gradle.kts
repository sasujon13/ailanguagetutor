/**
 * Compose @Preview support — apply from any Compose module's build.gradle.kts:
 *   apply(from = rootProject.file("gradle/compose-preview.gradle.kts"))
 *
 * Explicit deps help Android Studio resolve androidx.compose.ui.tooling.preview.Preview
 * (dynamic afterEvaluate-only deps often stay red until a full sync/rebuild).
 */
val catalog = rootProject.extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java).named("libs")
dependencies {
    add("implementation", catalog.findLibrary("compose-ui-tooling-preview").get())
    add("debugImplementation", catalog.findLibrary("compose-ui-tooling").get())
}
