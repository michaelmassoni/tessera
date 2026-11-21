package io.github.michaelmassoni.tessera.contacts.ui

import android.graphics.ImageDecoder
import android.util.LruCache
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
    val cacheKey = photoUri?.let { "$it@$px" }
    // Rows scrolled back into view take the decoded photo from memory instead of decoding it again.
    val photo by produceState(cacheKey?.let(PhotoCache::get), photoUri, px) {
        value = cacheKey?.let(PhotoCache::get)
        if (value != null || photoUri == null || cacheKey == null) return@produceState
        value =
            withContext(Dispatchers.IO) {
                try {
                    ImageDecoder
                        .decodeBitmap(ImageDecoder.createSource(context.contentResolver, photoUri.toUri())) { decoder, _, _ ->
                            decoder.setTargetSize(px, px)
                        }.asImageBitmap()
                        .also { PhotoCache.put(cacheKey, it) }
                } catch (_: IOException) {
                    null
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

/** Decoded contact photos by URI and size, up to about 8 MB. */
private object PhotoCache : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
    override fun sizeOf(
        key: String,
        value: ImageBitmap,
    ) = value.width * value.height * 4
}

/** Contacts changed: a contact's photo URI stays the same when the photo is replaced. */
fun clearAvatarCache() = PhotoCache.evictAll()
