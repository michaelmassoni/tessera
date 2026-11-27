plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.messages"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.messages"
        versionCode = 17
        versionName = "0.9.0"
    }
}

dependencies {
    implementation(libs.coil.compose)
}
