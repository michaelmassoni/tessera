plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.weather"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.weather"
        versionCode = 10
        versionName = "0.7.0"
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.work.runtime)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
}
