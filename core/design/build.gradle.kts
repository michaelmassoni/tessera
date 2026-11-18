plugins {
    alias(libs.plugins.tessera.android.library)
    alias(libs.plugins.tessera.compose)
}

android {
    namespace = "io.github.michaelmassoni.tessera.core.design"
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.material3)
    api(libs.compose.ui.tooling.preview)
}
