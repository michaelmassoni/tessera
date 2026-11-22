plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.calculator"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.calculator"
        versionCode = 4
        versionName = "0.2.2"
    }
}
