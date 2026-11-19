plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.phone"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.phone"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(libs.compose.material3.navigation.suite)
}
