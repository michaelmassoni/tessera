plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.calculator"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.calculator"
        versionCode = 1
        versionName = "0.1.0"
    }
}
