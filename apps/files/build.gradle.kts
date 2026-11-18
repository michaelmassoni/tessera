plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.files"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.files"
        versionCode = 1
        versionName = "0.1.0"
    }
}
