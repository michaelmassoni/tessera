package io.github.michaelmassoni.tessera.core.design

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * The standard preview set for a Tessera screen: a phone in light and dark mode, a phone in
 * landscape and a tablet. Each preview is also a Roborazzi screenshot test (see build-logic
 * `tessera.compose`), so annotating a screen-level preview with this gives it screenshot
 * coverage for compact, medium and expanded widths.
 *
 * Wrap preview content in `TesseraTheme(dynamicColor = false)` so colors are deterministic.
 */
@Preview(name = "Phone", device = "spec:width=411dp,height=891dp", showBackground = true)
@Preview(
    name = "Phone dark",
    device = "spec:width=411dp,height=891dp",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    showBackground = true,
)
@Preview(
    name = "Phone landscape",
    device = "spec:width=891dp,height=411dp,orientation=landscape",
    showBackground = true,
)
@Preview(name = "Tablet", device = "spec:width=1280dp,height=800dp,dpi=240", showBackground = true)
annotation class TesseraPreviews
