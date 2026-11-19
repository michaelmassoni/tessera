plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.gallery"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.gallery"
        versionCode = 6
        versionName = "0.2.0"
    }
}

dependencies {
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.telephoto.zoomable.image.coil3)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui.compose)
    implementation(libs.androidx.exifinterface)
}
