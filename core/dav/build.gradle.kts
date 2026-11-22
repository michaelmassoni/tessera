plugins {
    alias(libs.plugins.tessera.android.library)
    alias(libs.plugins.tessera.compose)
}

android {
    namespace = "io.github.michaelmassoni.tessera.core.dav"
}

dependencies {
    api(project(":core:design"))
    implementation(project(":core:settings"))
    implementation(libs.androidx.core.ktx)
    // WebDAV needs PROPFIND, REPORT, MKCALENDAR and PROPPATCH, which HttpURLConnection refuses.
    implementation(libs.okhttp)
    implementation(libs.work.runtime)
    testImplementation(libs.okhttp.mockwebserver)
}
