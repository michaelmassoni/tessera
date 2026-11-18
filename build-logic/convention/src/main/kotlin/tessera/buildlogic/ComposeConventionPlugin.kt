package tessera.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Compose plus screenshot testing.
 *
 * Every `@Preview` in the module's namespace becomes a Roborazzi screenshot test that runs under
 * Robolectric during `./gradlew check`. Golden images live in `<module>/screenshots/` and are
 * committed. To update them after an intended UI change, run `./gradlew recordRoborazziDebug`.
 */
@OptIn(ExperimentalRoborazziApi::class)
class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("io.github.takahirom.roborazzi")

            val android = extensions.getByType(CommonExtension::class.java)
            android.buildFeatures.compose = true

            extensions.configure<RoborazziExtension> {
                outputDir.set(layout.projectDirectory.dir("screenshots"))
                generateComposePreviewRobolectricTests {
                    enable.set(true)
                    packages.set(provider { listOf(requireNotNull(android.namespace)) })
                    includePrivatePreviews.set(true)
                    robolectricConfig.set(
                        mapOf(
                            "sdk" to "[${libs.version("sdk-target")}]",
                            "qualifiers" to "RobolectricDeviceQualifiers.Pixel5",
                        ),
                    )
                }
            }

            dependencies {
                val bom = platform(libs.library("compose-bom"))
                "implementation"(bom)
                "testImplementation"(bom)
                "implementation"(libs.library("compose-ui-tooling-preview"))
                "debugImplementation"(libs.library("compose-ui-tooling"))
                "debugImplementation"(libs.library("compose-ui-test-manifest"))

                "testImplementation"(libs.library("robolectric"))
                "testImplementation"(libs.library("compose-ui-test-junit4"))
                "testImplementation"(libs.library("androidx-test-ext-junit"))
                "testImplementation"(libs.library("androidx-test-espresso-core"))
                "testImplementation"(libs.library("roborazzi"))
                "testImplementation"(libs.library("roborazzi-compose"))
                "testImplementation"(libs.library("roborazzi-junit-rule"))
                "testImplementation"(libs.library("roborazzi-compose-preview-scanner-support"))
                "testImplementation"(libs.library("composable-preview-scanner"))
            }
        }
}
