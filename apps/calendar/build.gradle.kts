plugins {
    alias(libs.plugins.tessera.android.application)
}

android {
    namespace = "io.github.michaelmassoni.tessera.calendar"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.calendar"
        versionCode = 12
        versionName = "0.5.2"
    }
}

dependencies {
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.work.runtime)
}
