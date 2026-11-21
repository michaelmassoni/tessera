plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.calendar"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.calendar"
        versionCode = 2
        versionName = "0.2.0"
    }
}

dependencies {
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.work.runtime)
}
