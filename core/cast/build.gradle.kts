plugins {
    alias(libs.plugins.tessera.android.library)
    alias(libs.plugins.tessera.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.michaelmassoni.tessera.core.cast"
}

// Casting without Google Play Services (docs/decisions.md, 2026-11-26): Music and Podcasts.
dependencies {
    api(project(":core:design"))
    implementation(project(":core:settings"))
    api(libs.media3.datasource)
    implementation(libs.media3.exoplayer)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.serialization.json)
}
