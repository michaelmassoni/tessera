plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.files"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.files"
        versionCode = 10
        versionName = "0.6.1"
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.pdf.compose)
    implementation(libs.pdf.document.service)
}
