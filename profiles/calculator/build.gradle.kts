plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "io.github.michaelmassoni.tessera.profiles.calculator"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }
    buildToolsVersion = libs.versions.build.tools.get()
    defaultConfig {
        minSdk = 31
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["app"] = "calculator"
    }
    targetProjectPath = ":apps:calculator"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
}
