plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.clock"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.clock"
        versionCode = 3
        versionName = "0.2.1"
    }
}

dependencies {
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.kotlinx.serialization.json)
}
