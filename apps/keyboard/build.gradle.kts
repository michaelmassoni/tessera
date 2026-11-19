plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.keyboard"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.keyboard"
        versionCode = 2
        versionName = "0.2.0"
    }
}
