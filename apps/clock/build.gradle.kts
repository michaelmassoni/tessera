plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.clock"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.clock"
        versionCode = 4
        versionName = "0.3.0"
    }
}

dependencies {
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.kotlinx.serialization.json)
}
