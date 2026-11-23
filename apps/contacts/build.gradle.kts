plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.contacts"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.contacts"
        versionCode = 7
        versionName = "0.3.1"
    }
}

dependencies {
    implementation(project(":core:dav"))
    implementation(libs.work.runtime)
}
