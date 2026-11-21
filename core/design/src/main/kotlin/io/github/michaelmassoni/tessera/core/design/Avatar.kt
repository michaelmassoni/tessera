package io.github.michaelmassoni.tessera.core.design

import android.graphics.ImageDecoder
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * A contact's photo, or their initials on a container colour picked from the theme by a hash of
 * [key], so a contact keeps its colour and every colour follows dynamic colour and dark mode.
 * With no [name] (a number that isn't a contact) it shows a person icon.
 */
@Composable
fun Avatar(
    name: String?,
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
                } catch (_: SecurityException) {
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
        when {
            image != null -> {
                Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
            }

            name.isNullOrBlank() -> {
                Icon(
                    painterResource(R.drawable.ic_person),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(size * 0.55f),
                )
            }

            else -> {
                Text(
                    Initials.of(name),
                    color = content,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = (size.value * 0.4f).sp),
                )
            }
        }
    }
}

/** Avatar initials and a stable colour slot. */
object Initials {
    /** Up to two initials: first and last word ("Ada Lovelace" → "AL"); "#" if there are no letters. */
    fun of(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { w -> w.firstOrNull()?.isLetter() == true }
        return when {
            words.isEmpty() -> "#"
            words.size == 1 -> words[0].first().uppercase()
            else -> (words.first().first().toString() + words.last().first()).uppercase()
        }
    }

    /** 0 until [slots]: the same contact always gets the same colour. */
    fun slot(
        key: String,
        slots: Int,
    ): Int = Math.floorMod(key.hashCode(), slots)
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
