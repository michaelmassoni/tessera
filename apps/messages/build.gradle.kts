plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.messages"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.messages"
        versionCode = 2
        versionName = "0.2.0"
    }
}

dependencies {
    implementation(libs.coil.compose)
}
