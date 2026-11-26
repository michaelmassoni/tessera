plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.notes"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.notes"
        versionCode = 12
        versionName = "0.4.2"
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    // Glance brings WorkManager 2.7 with Room 2.2, whose database R8 strips: release builds crashed
    // on start (0.2.0, 0.2.1). The current WorkManager has the keep rules.
    implementation(libs.work.runtime)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
}
