package io.github.michaelmassoni.tessera.contacts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.michaelmassoni.tessera.contacts.R

/**
 * A contact as a QR code for another phone's camera to add (docs/contacts.md, Share as QR code).
 * Always dark squares on a light ground, as most scanners need: in a dark theme the code sits on the
 * inverse surface.
 */
@Composable
internal fun QrCodeDialog(
    name: String,
    vcard: String,
    onDismiss: () -> Unit,
) {
    val matrix = remember(vcard) { encode(vcard) }
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val ground = if (dark) scheme.inverseSurface else scheme.surfaceContainerLowest
    val ink = if (dark) scheme.inverseOnSurface else scheme.onSurface
    val description = stringResource(R.string.qr_code_of, name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (matrix == null) {
                    Text(stringResource(R.string.qr_too_long))
                } else {
                    Text(
                        stringResource(R.string.qr_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    Box(
                        Modifier
                            .widthIn(max = 320.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(ground, MaterialTheme.shapes.large)
                            .padding(16.dp)
                            .semantics { contentDescription = description },
                    ) {
                        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                            val cell = size.width / matrix.width
                            for (y in 0 until matrix.height) {
                                for (x in 0 until matrix.width) {
                                    // A hair wider than the cell so neighbours join without seams.
                                    if (matrix[x, y]) drawRect(ink, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

/** The code's modules (no quiet zone: the padded ground is it), or null if the card is too long. */
private fun encode(text: String): BitMatrix? =
    runCatching {
        QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 0,
            ),
        )
    }.getOrNull()
