plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.notes"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.notes"
        versionCode = 3
        versionName = "0.1.2"
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
