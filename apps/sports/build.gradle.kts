plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.sports"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.sports"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.work.runtime)
}
