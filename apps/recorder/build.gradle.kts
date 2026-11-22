plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.recorder"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.recorder"
        versionCode = 1
        versionName = "0.1.0"
    }
}
