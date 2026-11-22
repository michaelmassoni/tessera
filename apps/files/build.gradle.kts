plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.files"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.files"
        versionCode = 6
        versionName = "0.4.0"
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
