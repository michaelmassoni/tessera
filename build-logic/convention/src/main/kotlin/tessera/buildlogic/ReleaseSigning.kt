package tessera.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Project

/**
 * Release signing from environment variables only; keys are never committed.
 *
 *   TESSERA_KEYSTORE_FILE      path to the .jks/.p12 keystore
 *   TESSERA_KEYSTORE_PASSWORD  keystore password
 *   TESSERA_KEY_ALIAS          key alias
 *   TESSERA_KEY_PASSWORD       key password (defaults to the keystore password)
 *
 * When they are absent, release builds are left unsigned. That is what F-Droid wants (it signs
 * with its own key), and it keeps `./gradlew assembleRelease` working on any machine.
 */
internal fun Project.configureReleaseSigning(android: ApplicationExtension) {
    fun env(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

    val storeFile = env("TESSERA_KEYSTORE_FILE") ?: return
    val storePassword =
        requireNotNull(env("TESSERA_KEYSTORE_PASSWORD")) {
            "TESSERA_KEYSTORE_FILE is set but TESSERA_KEYSTORE_PASSWORD is not"
        }
    val keyAlias =
        requireNotNull(env("TESSERA_KEY_ALIAS")) {
            "TESSERA_KEYSTORE_FILE is set but TESSERA_KEY_ALIAS is not"
        }
    val keyPassword = env("TESSERA_KEY_PASSWORD") ?: storePassword

    android.signingConfigs.create("release") {
        this.storeFile = file(storeFile)
        this.storePassword = storePassword
        this.keyAlias = keyAlias
        this.keyPassword = keyPassword
    }
    android.buildTypes.getByName("release").signingConfig = android.signingConfigs.getByName("release")
}
