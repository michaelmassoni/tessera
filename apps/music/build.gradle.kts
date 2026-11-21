plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.music"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.music"
        versionCode = 4
        versionName = "0.2.0"
    }
}

dependencies {
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
}
