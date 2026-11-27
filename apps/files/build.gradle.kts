plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.files"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.files"
        versionCode = 12
        versionName = "0.8.0"
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    // tar and tar.gz archives (docs/files.md, Archives).
    implementation(libs.commons.compress)
    implementation(libs.pdf.compose)
    implementation(libs.pdf.document.service)
}
