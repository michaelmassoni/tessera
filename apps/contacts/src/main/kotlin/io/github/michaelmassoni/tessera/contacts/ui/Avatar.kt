package io.github.michaelmassoni.tessera.contacts.ui

import android.graphics.ImageDecoder
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import io.github.michaelmassoni.tessera.contacts.engine.Initials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * A contact's photo, or their initials on a container colour picked from the theme by a hash of
 * [key], so a contact keeps its colour and every colour follows dynamic colour and dark mode.
 */
@Composable
fun Avatar(
    name: String,
    key: String,
    photoUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val photo by produceState<ImageBitmap?>(null, photoUri, px) {
        value =
            photoUri?.let { uri ->
                withContext(Dispatchers.IO) {
                    try {
                        ImageDecoder
                            .decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri.toUri())) { decoder, _, _ ->
                                decoder.setTargetSize(px, px)
                            }.asImageBitmap()
                    } catch (_: IOException) {
                        null
                    }
                }
            }
    }
    val scheme = MaterialTheme.colorScheme
    val slots =
        listOf(
            scheme.primaryContainer to scheme.onPrimaryContainer,
            scheme.secondaryContainer to scheme.onSecondaryContainer,
            scheme.tertiaryContainer to scheme.onTertiaryContainer,
            scheme.surfaceContainerHighest to scheme.onSurfaceVariant,
        )
    val (container, content) = slots[Initials.slot(key, slots.size)]
    Box(modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        val image = photo
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Text(
                Initials.of(name),
                color = content,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = (size.value * 0.4f).sp),
            )
        }
    }
}
