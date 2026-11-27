plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.messages"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.messages"
        versionCode = 22
        versionName = "0.11.0"
    }
}

dependencies {
    implementation(libs.coil.compose)
}
