plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.feeds"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.feeds"
        versionCode = 4
        versionName = "0.3.0"
    }
}

dependencies {
    // The sync settings, schedule and screen shared with the DAV apps (docs/sync.md).
    implementation(project(":core:dav"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // Glance brings WorkManager 2.7 with Room 2.2, whose database R8 strips (see Notes).
    implementation(libs.work.runtime)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    testImplementation(libs.okhttp.mockwebserver)
}
