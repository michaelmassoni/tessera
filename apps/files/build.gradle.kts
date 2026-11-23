plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.files"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.files"
        versionCode = 7
        versionName = "0.4.1"
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
