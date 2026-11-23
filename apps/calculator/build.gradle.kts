plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.calculator"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.calculator"
        versionCode = 5
        versionName = "0.2.3"
    }
}
