plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.files"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.files"
        versionCode = 3
        versionName = "0.2.1"
    }
}
