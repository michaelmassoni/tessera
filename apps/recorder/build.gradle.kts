plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.recorder"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.recorder"
        versionCode = 7
        versionName = "0.2.4"
    }
}
