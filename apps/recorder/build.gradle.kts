plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.recorder"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.recorder"
        versionCode = 2
        versionName = "0.1.1"
    }
}
