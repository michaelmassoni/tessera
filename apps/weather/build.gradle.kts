plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.weather"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.weather"
        versionCode = 2
        versionName = "0.2.0"
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
