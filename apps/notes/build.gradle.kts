plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.notes"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.notes"
        versionCode = 5
        versionName = "0.2.0"
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
}
