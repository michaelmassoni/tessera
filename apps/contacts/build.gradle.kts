plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.contacts"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.contacts"
        versionCode = 20
        versionName = "0.7.0"
    }
}

dependencies {
    implementation(project(":core:dav"))
    implementation(libs.work.runtime)
    implementation(libs.zxing.core)
}
