plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.contacts"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.contacts"
        versionCode = 8
        versionName = "0.4.0"
    }
}

dependencies {
    implementation(project(":core:dav"))
    implementation(libs.work.runtime)
}
