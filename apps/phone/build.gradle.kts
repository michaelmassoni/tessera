plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.phone"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.phone"
        versionCode = 20
        versionName = "0.9.1"
    }
}

dependencies {
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.libphonenumber.geocoder)
}
