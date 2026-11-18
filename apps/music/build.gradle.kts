plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.music"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.music"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
}
