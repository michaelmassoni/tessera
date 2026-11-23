plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.messages"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.messages"
        versionCode = 11
        versionName = "0.5.1"
    }
}

dependencies {
    implementation(libs.coil.compose)
}
