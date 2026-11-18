// Plugins are put on the build classpath here once; modules apply them through the
// tessera.* convention plugins in build-logic/.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.roborazzi) apply false
}

// `./gradlew check` also lints the convention plugins in the included build.
tasks.register("check") {
    dependsOn(gradle.includedBuild("build-logic").task(":convention:check"))
}
