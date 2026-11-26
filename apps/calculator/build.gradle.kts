plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "io.github.michaelmassoni.tessera.calculator"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.calculator"
        versionCode = 7
        versionName = "0.4.0"
    }
}

dependencies {
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":profiles:calculator"))
}
