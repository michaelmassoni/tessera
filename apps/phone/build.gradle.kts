plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.phone"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.phone"
        versionCode = 5
        versionName = "0.2.2"
    }
}

dependencies {
    implementation(libs.compose.material3.navigation.suite)
}
