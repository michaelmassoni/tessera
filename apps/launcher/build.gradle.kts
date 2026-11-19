plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.launcher"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.launcher"
        versionCode = 2
        versionName = "0.1.1"
    }
}
