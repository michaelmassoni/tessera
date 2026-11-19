plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.music"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.music"
        versionCode = 2
        versionName = "0.1.1"
    }
}

dependencies {
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
}
