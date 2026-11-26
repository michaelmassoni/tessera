plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.files"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.files"
        versionCode = 11
        versionName = "0.7.0"
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.pdf.compose)
    implementation(libs.pdf.document.service)
}
