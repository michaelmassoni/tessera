package io.github.michaelmassoni.tessera.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * The single theme for every Tessera app.
 *
 * Material 3 Expressive with dynamic color (Material You). minSdk is 31, so the wallpaper-based
 * scheme is always available; [dynamicColor] = false falls back to the Material baseline
 * schemes (used by previews and screenshot tests for deterministic output). [pureBlack] makes
 * dark backgrounds black for OLED screens. Apps use `TesseraAppTheme` from `core:settings`,
 * which passes the user's choices.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TesseraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val base =
        when {
            dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
            dynamicColor -> dynamicLightColorScheme(context)
            darkTheme -> darkColorScheme()
            else -> expressiveLightColorScheme()
        }
    // Pure black: backgrounds and the lowest surfaces go black for OLED screens; containers
    // shift down one step so cards stay distinguishable from the background.
    val colorScheme =
        if (pureBlack && darkTheme) {
            base.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceDim = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = base.surfaceContainerLowest,
                surfaceContainer = base.surfaceContainerLow,
            )
        } else {
            base
        }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
