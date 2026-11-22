plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.messages"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.messages"
        versionCode = 7
        versionName = "0.3.4"
    }
}

dependencies {
    implementation(libs.coil.compose)
}
