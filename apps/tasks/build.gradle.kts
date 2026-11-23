plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.tasks"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.tasks"
        versionCode = 4
        versionName = "0.3.0"
    }
}

dependencies {
    implementation(project(":core:dav"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.reorderable)
    // Glance brings WorkManager 2.7 with Room 2.2, whose database R8 strips (see Notes).
    implementation(libs.work.runtime)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
}
