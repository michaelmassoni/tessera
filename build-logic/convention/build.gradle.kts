plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

group = "tessera.buildlogic"

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.ktlint.gradle.plugin)
    compileOnly(libs.roborazzi.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "tessera.android.application"
            implementationClass = "tessera.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "tessera.android.library"
            implementationClass = "tessera.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("compose") {
            id = "tessera.compose"
            implementationClass = "tessera.buildlogic.ComposeConventionPlugin"
        }
    }
}
