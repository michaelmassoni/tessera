plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.tessera.core.design"
    compileSdk { version = release(37) }
    buildToolsVersion = "37.0.0"
    defaultConfig { minSdk { version = release(31) } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.material3)
    api(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
