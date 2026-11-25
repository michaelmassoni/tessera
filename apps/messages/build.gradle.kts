plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.messages"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.messages"
        versionCode = 12
        versionName = "0.6.0"
    }
}

dependencies {
    implementation(libs.coil.compose)
}
