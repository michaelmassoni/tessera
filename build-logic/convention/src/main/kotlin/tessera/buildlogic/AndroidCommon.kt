package tessera.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/** Settings shared by every Android module (apps and core libraries). */
internal fun Project.configureAndroidCommon(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk { version = release(libs.version("sdk-compile").toInt()) }
        buildToolsVersion = libs.version("build-tools")
        defaultConfig.minSdk { version = release(libs.version("sdk-min").toInt()) }

        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        lint.apply {
            abortOnError = true
            warningsAsErrors = true
            checkReleaseBuilds = false
            // Dependency freshness is tracked by Dependabot, not by failing the build.
            disable +=
                setOf(
                    "GradleDependency",
                    "NewerVersionAvailable",
                    "AndroidGradlePluginVersion",
                    "OldTargetApi",
                )
        }

        testOptions.apply {
            unitTests.isIncludeAndroidResources = true
        }
    }

    // Host tests (unit + screenshot) run once, against the debug variant.
    extensions.getByType(AndroidComponentsExtension::class.java).apply {
        beforeVariants(selector().withBuildType("release")) { variant ->
            (variant as? HasHostTestsBuilder)?.hostTests?.get(HostTestBuilder.UNIT_TEST_TYPE)?.enable = false
        }
    }

    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(true)
        }
    }

    pluginManager.apply("org.jlleitschuh.gradle.ktlint")
    extensions.configure<KtlintExtension> {
        version.set(libs.version("ktlint"))
        android.set(true)
    }

    dependencies {
        "lintChecks"(libs.library("compose-lint-checks"))
        "testImplementation"(libs.library("junit"))
    }
}
