plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.podcasts"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.podcasts"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.work.runtime)
    implementation(libs.reorderable)
    implementation(libs.kotlinx.serialization.json)
}
