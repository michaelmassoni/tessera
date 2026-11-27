plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.calendar"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.calendar"
        versionCode = 32
        versionName = "0.11.4"
    }
}

dependencies {
    implementation(project(":core:dav"))
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.work.runtime)
}
