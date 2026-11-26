plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.contacts"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.contacts"
        versionCode = 16
        versionName = "0.6.0"
    }
}

dependencies {
    implementation(project(":core:dav"))
    implementation(libs.work.runtime)
}
