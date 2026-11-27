plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.messages"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.messages"
        versionCode = 21
        versionName = "0.10.1"
    }
}

dependencies {
    implementation(libs.coil.compose)
}
