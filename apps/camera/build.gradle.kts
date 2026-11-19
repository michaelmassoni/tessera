plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.camera"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.camera"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.video)
    implementation(libs.camerax.compose)
}
