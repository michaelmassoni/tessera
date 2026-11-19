package io.github.michaelmassoni.tessera.clock.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.michaelmassoni.tessera.core.design.TesseraTheme

/**
 * Previews of a tab: the theme plus the surface `ClockRoot`'s Scaffold gives it in the app.
 * Without the surface, the dark screenshots show black text on white and can't catch a real
 * dark-theme regression.
 */
@Composable
internal fun ClockPreview(content: @Composable () -> Unit) {
    TesseraTheme(dynamicColor = false) {
        Surface(Modifier.fillMaxSize()) { content() }
    }
}
