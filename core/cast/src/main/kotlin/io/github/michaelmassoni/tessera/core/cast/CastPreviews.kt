package io.github.michaelmassoni.tessera.core.cast

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import java.net.InetAddress

private val livingRoom =
    CastDevice("1", "Living Room TV", "Chromecast with Google TV", InetAddress.getLoopbackAddress(), 8009, video = true)
private val kitchen = CastDevice("2", "Kitchen speaker", "Nest Audio", InetAddress.getLoopbackAddress(), 8009, video = false)

private object NoCast : CastActions {
    override fun pick(device: CastDevice) = Unit

    override fun stop() = Unit

    override fun volume(level: Float) = Unit

    override fun allow() = Unit

    override fun openSettings() = Unit
}

// "Cast to": devices found, one connecting, one that failed; then connected, with volume; then the permission ask.
@Preview(showBackground = true, widthDp = 411)
@Preview(showBackground = true, widthDp = 411, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CastSheetPreview() {
    TesseraTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                CastSheetContent(CastState.Connecting(livingRoom), listOf(kitchen, livingRoom), false, NoCast, searchMs = 0)
                CastSheetContent(CastState.Failed(kitchen), listOf(kitchen), false, NoCast, searchMs = 0)
                CastSheetContent(CastState.Off, emptyList(), false, NoCast, searchMs = 0)
                CastSheetContent(CastState.Connected(livingRoom, 0.4f, false), listOf(kitchen, livingRoom), false, NoCast)
                CastSheetContent(CastState.Off, emptyList(), true, NoCast)
            }
        }
    }
}
