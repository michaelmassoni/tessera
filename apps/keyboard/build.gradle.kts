plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.keyboard"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.keyboard"
        versionCode = 1
        versionName = "0.1.0"
    }
}
