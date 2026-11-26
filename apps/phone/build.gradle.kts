plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.phone"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.phone"
        versionCode = 19
        versionName = "0.9.0"
    }
}

dependencies {
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.libphonenumber.geocoder)
}
