plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.music"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.music"
        versionCode = 33
        versionName = "0.14.1"
    }
}

dependencies {
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource)
    implementation(libs.media3.database)
    implementation(project(":core:cast"))
    implementation(libs.reorderable)
    implementation(libs.kotlinx.serialization.json)
    // Glance brings WorkManager 2.7 with Room 2.2, whose database R8 strips: release builds crashed
    // on start (0.2.0, 0.2.1). The current WorkManager has the keep rules.
    implementation(libs.work.runtime)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
}
