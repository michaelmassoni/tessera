plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.phone"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.phone"
        versionCode = 14
        versionName = "0.6.1"
    }
}

dependencies {
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.libphonenumber.geocoder)
}
