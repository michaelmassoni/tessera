package tessera.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * A Tessera app under `apps/`. The module sets only namespace, applicationId and version;
 * everything else (SDK levels, Compose, theme, lint, signing, F-Droid settings) comes from here.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("tessera.compose")

            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)

                defaultConfig {
                    targetSdk { version = release(libs.version("sdk-target").toInt()) }
                }

                configureReleaseSigning(this)

                buildTypes {
                    release {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
                    }
                }

                // F-Droid: don't embed the Google-encrypted dependency metadata blob.
                dependenciesInfo {
                    includeInApk = false
                    includeInBundle = false
                }
            }

            dependencies {
                "implementation"(project(":core:design"))
                "implementation"(libs.library("androidx-core-ktx"))
                "implementation"(libs.library("androidx-activity-compose"))
                "implementation"(libs.library("androidx-lifecycle-runtime-compose"))
                "implementation"(libs.library("androidx-lifecycle-viewmodel-compose"))
                "testImplementation"(libs.library("kotlinx-coroutines-test"))
            }
        }
}
