plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.weather"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.weather"
        versionCode = 4
        versionName = "0.3.1"
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
