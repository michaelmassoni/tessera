plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.contacts"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.contacts"
        versionCode = 9
        versionName = "0.4.1"
    }
}

dependencies {
    implementation(project(":core:dav"))
    implementation(libs.work.runtime)
}
