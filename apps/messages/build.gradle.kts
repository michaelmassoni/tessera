plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.messages"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.messages"
        versionCode = 20
        versionName = "0.10.0"
    }
}

dependencies {
    implementation(libs.coil.compose)
}
