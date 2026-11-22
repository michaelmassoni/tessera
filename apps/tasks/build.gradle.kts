plugins {
    alias(libs.plugins.tessera.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.tasks"

    defaultConfig {
        applicationId = "io.github.michaelmassoni.tessera.tasks"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    // CalDAV needs PROPFIND, REPORT and MKCALENDAR, which HttpURLConnection refuses.
    implementation(libs.okhttp)
    implementation(libs.reorderable)
    // Glance brings WorkManager 2.7 with Room 2.2, whose database R8 strips (see Notes).
    implementation(libs.work.runtime)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    testImplementation(libs.okhttp.mockwebserver)
}
