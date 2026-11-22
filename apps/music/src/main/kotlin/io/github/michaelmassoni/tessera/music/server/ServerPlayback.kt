package io.github.michaelmassoni.tessera.music.server

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import io.github.michaelmassoni.tessera.music.engine.Subsonic
import java.io.File
import java.io.IOException

/**
 * Playing server songs: `tessera-subsonic:song/<id>` becomes a `stream` address only when
 * playback opens it (a fresh token each time, so nothing saved holds a credential), through an
 * on-phone cache keyed by song and quality.
 */
@OptIn(UnstableApi::class)
object ServerPlayback {
    @Volatile private var cache: SimpleCache? = null

    /** The one cache for this process (SimpleCache allows a single instance per folder). */
    fun cache(context: Context): SimpleCache =
        cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(context.cacheDir, "media"),
                LeastRecentlyUsedCacheEvictor(ServerStore(context).cacheSize.bytes),
                StandaloneDatabaseProvider(context.applicationContext),
            ).also { cache = it }
        }

    /** Bytes held by the cache. */
    fun cacheBytes(context: Context): Long = cache(context).cacheSpace

    /** Empties the cache (Settings > Clear). */
    fun clearCache(context: Context) {
        val c = cache(context)
        c.keys.toList().forEach { runCatching { c.removeResource(it) } }
    }

    /** What ExoPlayer reads through: local files as before; server songs resolved and cached. */
    fun dataSourceFactory(context: Context): DataSource.Factory {
        val app = context.applicationContext
        val http = DefaultHttpDataSource.Factory().setUserAgent(Subsonic.CLIENT).setAllowCrossProtocolRedirects(false)
        val cached =
            CacheDataSource
                .Factory()
                .setCache(cache(app))
                .setUpstreamDataSourceFactory(http)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val base = DefaultDataSource.Factory(app, cached)
        return ResolvingDataSource.Factory(base) { spec ->
            val id = Subsonic.songId(spec.uri.toString()) ?: return@Factory spec
            val store = ServerStore(app)
            val account = store.account ?: throw IOException("Not signed in")
            val password = store.password() ?: throw IOException("Not signed in")
            val kbps = store.quality.kbps
            spec
                .buildUpon()
                .setUri(SubsonicClient(account, password).streamUrl(id, kbps).toUri())
                // The address changes every time (new token); the cache key doesn't.
                .setKey("${account.address}|$id|$kbps")
                .build()
        }
    }
}
