plugins {
    alias(libs.plugins.tessera.android.library)
    alias(libs.plugins.tessera.compose)
}

android {
    namespace = "io.github.michaelmassoni.tessera.core.settings"
}

dependencies {
    api(project(":core:design"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
}
