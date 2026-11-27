package io.github.michaelmassoni.tessera.music

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.github.michaelmassoni.tessera.core.cast.Cast
import io.github.michaelmassoni.tessera.core.cast.CastContent
import io.github.michaelmassoni.tessera.core.cast.CastMedia
import io.github.michaelmassoni.tessera.core.cast.RemotePlayer
import io.github.michaelmassoni.tessera.music.data.LibraryLoader
import io.github.michaelmassoni.tessera.music.data.LikeStore
import io.github.michaelmassoni.tessera.music.data.MusicSettings
import io.github.michaelmassoni.tessera.music.data.PlayHistory
import io.github.michaelmassoni.tessera.music.engine.BrowseTree
import io.github.michaelmassoni.tessera.music.engine.PlayingFrom
import io.github.michaelmassoni.tessera.music.engine.QueueOrder
import io.github.michaelmassoni.tessera.music.engine.SavedItem
import io.github.michaelmassoni.tessera.music.engine.SavedQueue
import io.github.michaelmassoni.tessera.music.engine.Song
import io.github.michaelmassoni.tessera.music.server.ArtProvider
import io.github.michaelmassoni.tessera.music.server.MusicSource
import io.github.michaelmassoni.tessera.music.server.Scrobbler
import io.github.michaelmassoni.tessera.music.server.ServerLibrary
import io.github.michaelmassoni.tessera.music.server.ServerPlayback
import io.github.michaelmassoni.tessera.music.server.ServerStore
import io.github.michaelmassoni.tessera.music.widget.NowPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Background playback. Media3 gives the media notification, lock-screen and Bluetooth controls,
 * audio focus and "becoming noisy" handling. Controllers can't pass file URIs directly (Media3
 * strips them), so the app sends each URI in the item's request metadata and [Callback]
 * restores it.
 *
 * The queue, track, position, shuffle and repeat are saved to `queue.json` whenever they change
 * (and the position on pause), and put back, paused, when the service starts again: after the
 * app was swiped away, the process died, or from Android's "resume playback" controls.
 */
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queueFile by lazy { File(filesDir, "queue.json") }
    private val scrobbler by lazy { Scrobbler(applicationContext, scope) }

    // The phone's player, and the cast device's while casting (the session plays through one or the other).
    private lateinit var exo: ExoPlayer
    private var remote: RemotePlayer? = null

    // The media source factory and bitmap loader are unstable in Media3 1.x, but the documented way to stream through a cache.
    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player =
            ExoPlayer
                .Builder(this)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                ).setHandleAudioBecomingNoisy(true)
                // Server songs stream through the cache; local files play as before.
                .setMediaSourceFactory(DefaultMediaSourceFactory(ServerPlayback.dataSourceFactory(this)))
                .build()
        // Shuffle reorders the queue itself (see [reorderForShuffle]), so the player keeps to the queue order.
        player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(0))
        session =
            MediaLibrarySession
                .Builder(this, player, Callback())
                // Tapping the player (Quick Settings, notification, lock screen) opens the app on the player.
                .setSessionActivity(openPlayerIntent())
                .build()
        loadQueue()?.let { saved ->
            player.setMediaItems(saved.items.map(::mediaItem), saved.index, saved.positionMs)
            shuffled = saved.shuffle
            unshuffled = saved.unshuffled
            player.shuffleModeEnabled = saved.shuffle
            player.playlistMetadata = playlistMetadata(saved.from)
            player.repeatMode = saved.repeatMode
            player.prepare()
        }
        player.addListener(listener)
        exo = player
        updateButtons(player)
        // A like made in the app shows on the heart too.
        scope.launch { LikeStore.flow.collect { session?.player?.let(::updateButtons) } }
        // What the cast device gets for a song: the song and its cover, served from the phone through
        // the same reading as playing here (files, and server songs through the cache).
        val sources = ServerPlayback.dataSourceFactory(this)
        Cast.content =
            CastContent { item, phone ->
                val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri ?: return@CastContent null
                val song = phone.serve(uri, sources, "audio/mpeg") ?: return@CastContent null
                val m = item.mediaMetadata
                val albumId = m.extras?.getLong(EXTRA_ALBUM_ID) ?: 0
                val cover = m.artworkUri ?: albumId.takeIf { it > 0 }?.let(ArtProvider::localUri)
                CastMedia(
                    url = song.url,
                    contentType = song.type,
                    title = m.title?.toString().orEmpty(),
                    artist = m.artist?.toString().orEmpty(),
                    album = m.albumTitle?.toString().orEmpty(),
                    imageUrl = cover?.let { phone.serve(it, sources, "image/jpeg") }?.url,
                )
            }
        scope.launch { Cast.link.collect(::castTo) }
    }

    private val listener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                if (events.containsAny(
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED,
                        Player.EVENT_PLAYLIST_METADATA_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_POSITION_DISCONTINUITY,
                    )
                ) {
                    saveQueue(player)
                }
                if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) reorderForShuffle(player)
                if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) keepSavingPosition(player)
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) || events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                    recordPlay(player)
                }
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) scrobbler.started(player)
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED,
                    )
                ) {
                    updateButtons(player)
                }
                if (events.containsAny(
                        Player.EVENT_MEDIA_METADATA_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_TIMELINE_CHANGED,
                    )
                ) {
                    NowPlaying.publish(this@PlaybackService, player)
                }
            }
        }

    /**
     * Casting started or ended (docs/music.md, Casting): the session switches to the cast device's
     * player, which takes the queue, song, position and play state, or back to ExoPlayer, paused
     * where the device was.
     */
    @OptIn(UnstableApi::class)
    private fun castTo(link: Cast.Link?) {
        val session = session ?: return
        remote?.takeIf { it.link !== link }?.let { r ->
            r.handBack(exo)
            r.removeListener(listener)
            session.player = exo
            r.release()
            remote = null
            saveQueue(exo)
        }
        if (link != null && remote == null) {
            val r = RemotePlayer(link, mainLooper)
            r.start(exo, link.startVolume)
            exo.pause()
            r.addListener(listener)
            session.player = r
            remote = r
        }
        updateButtons(session.player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /** Stop when the user swipes the app away while nothing is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        player?.let(::saveQueue)
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    // RemotePlayer is a SimpleBasePlayer, unstable in Media3 1.x.
    @OptIn(UnstableApi::class)
    override fun onDestroy() {
        session?.run {
            saveQueue(player)
            remote?.release()
            exo.release()
            release()
        }
        // The phone serves what the cast device plays, so casting can't outlive the service.
        Cast.disconnect()
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private var recorded: String? = null

    /** Notes the song playing in this phone's play history, once each time it starts. */
    private fun recordPlay(player: Player) {
        val item = player.currentMediaItem ?: return
        if (!player.isPlaying) return
        val key = "${player.currentMediaItemIndex}:${item.mediaId}"
        if (key == recorded) return
        recorded = key
        val id = item.mediaId.toLongOrNull() ?: return
        scope.launch(Dispatchers.IO) { PlayHistory.played(applicationContext, id) }
    }

    /** Whether the queue is in shuffled order now, and the order it had before. */
    private var shuffled = false
    private var unshuffled: List<String>? = null

    /**
     * Shuffle turned on (here, the notification, Bluetooth, anywhere): the queue itself is reordered,
     * the song playing first, so what's shown is what plays. Turned off: the order before comes back.
     * Moves, not a new list, so the song keeps playing.
     */
    private fun reorderForShuffle(player: Player) {
        if (player.shuffleModeEnabled == shuffled) return
        shuffled = player.shuffleModeEnabled
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val order =
            if (shuffled) {
                unshuffled = ids
                QueueOrder.shuffled(ids, player.currentMediaItemIndex)
            } else {
                QueueOrder.restored(ids, unshuffled.orEmpty()).also { unshuffled = null }
            }
        // Everything but the song playing is taken out and put back in the new order: four changes
        // however long the queue, where one move per song sent the whole queue to the app each time.
        val items = (0 until player.mediaItemCount).map(player::getMediaItemAt)
        val current = player.currentMediaItemIndex
        val at = order.indexOf(current)
        if (current < 0 || at < 0) return
        player.removeMediaItems(current + 1, items.size)
        player.removeMediaItems(0, current)
        player.addMediaItems(0, order.subList(0, at).map(items::get))
        player.addMediaItems(at + 1, order.subList(at + 1, order.size).map(items::get))
    }

    /** The song playing, as the library has it (for its like), or null before the library loads. */
    private suspend fun currentSong(player: Player): Song? {
        val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return null
        return tree().song(id)
    }

    private var buttonsJob: Job? = null

    /**
     * The now-playing buttons beyond play and skip: like, shuffle and repeat, for Android Auto and
     * the media notification. Their icons follow the state, so they're rebuilt when it changes.
     */
    @OptIn(UnstableApi::class)
    private fun updateButtons(player: Player) {
        buttonsJob?.cancel()
        buttonsJob =
            scope.launch {
                val song = currentSong(player)
                val liked = song != null && LikeStore.isLiked(applicationContext, song)
                val like =
                    CommandButton
                        .Builder(if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
                        .setDisplayName(getString(if (liked) R.string.unlike else R.string.like))
                        .setSessionCommand(LIKE)
                        .setEnabled(song != null)
                        .setSlots(CommandButton.SLOT_OVERFLOW)
                        .build()
                val shuffle =
                    CommandButton
                        .Builder(if (player.shuffleModeEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
                        .setDisplayName(getString(if (player.shuffleModeEnabled) R.string.shuffle_on else R.string.shuffle_off))
                        .setSessionCommand(SHUFFLE)
                        .setSlots(CommandButton.SLOT_OVERFLOW)
                        .build()
                val (repeatIcon, repeatName) =
                    when (player.repeatMode) {
                        Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL to R.string.repeat_all
                        Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE to R.string.repeat_one
                        else -> CommandButton.ICON_REPEAT_OFF to R.string.repeat_off
                    }
                val repeat =
                    CommandButton
                        .Builder(repeatIcon)
                        .setDisplayName(getString(repeatName))
                        .setSessionCommand(REPEAT)
                        .setSlots(CommandButton.SLOT_OVERFLOW)
                        .build()
                session?.setMediaButtonPreferences(listOf(like, shuffle, repeat))
            }
    }

    /** A tap on one of [updateButtons]' buttons. */
    private suspend fun onButton(
        command: String,
        player: Player,
    ) {
        when (command) {
            SHUFFLE.customAction -> {
                player.shuffleModeEnabled = !player.shuffleModeEnabled
            }

            REPEAT.customAction -> {
                player.repeatMode =
                    when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
            }

            LIKE.customAction -> {
                val song = currentSong(player) ?: return
                val liked = !LikeStore.isLiked(applicationContext, song)
                withContext(Dispatchers.IO) { LikeStore.set(applicationContext, song, liked) }
                // Liked songs in the car changes too.
                reloadTree()
                session?.notifyChildrenChanged(BrowseTree.LIKED, Int.MAX_VALUE, null)
            }
        }
    }

    private var positionSaver: Job? = null

    /** While playing, saves the position every few seconds, so a killed process resumes close to where it was. */
    private fun keepSavingPosition(player: Player) {
        positionSaver?.cancel()
        if (!player.isPlaying) return
        positionSaver =
            scope.launch {
                while (isActive) {
                    delay(POSITION_SAVE_MS)
                    saveQueue(player)
                    scrobbler.progress(player)
                }
            }
    }

    private fun snapshot(player: Player): SavedQueue? {
        if (player.mediaItemCount == 0) return null
        val items =
            (0 until player.mediaItemCount).mapNotNull { i ->
                val item = player.getMediaItemAt(i)
                val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri ?: return@mapNotNull null
                val m = item.mediaMetadata
                SavedItem(
                    id = item.mediaId,
                    uri = uri.toString(),
                    title = m.title?.toString().orEmpty(),
                    artist = m.artist?.toString().orEmpty(),
                    album = m.albumTitle?.toString().orEmpty(),
                    albumId = m.extras?.getLong(EXTRA_ALBUM_ID) ?: 0,
                    art = m.extras?.getString(EXTRA_ART),
                )
            }
        if (items.isEmpty()) return null
        return SavedQueue(
            items,
            player.currentMediaItemIndex,
            player.currentPosition,
            player.shuffleModeEnabled,
            player.repeatMode,
            unshuffled,
            playingFrom(player.playlistMetadata),
        )
    }

    /** Writes the queue off the main thread; an empty queue removes the file. */
    private fun saveQueue(player: Player) {
        val text = snapshot(player)?.encode()
        scope.launch(Dispatchers.IO) {
            try {
                if (text == null) {
                    queueFile.delete()
                } else {
                    val temp = File(queueFile.path + ".tmp")
                    temp.writeText(text)
                    temp.renameTo(queueFile)
                }
            } catch (_: IOException) {
                // Not worth interrupting playback for; the previous save stays.
            }
        }
    }

    private fun loadQueue(): SavedQueue? =
        try {
            if (queueFile.exists()) SavedQueue.decode(queueFile.readText()) else null
        } catch (_: IOException) {
            null
        }

    /**
     * The library as browsers see it, built off the main thread and rebuilt when Android Auto
     * reconnects. Touched only on the main thread (the session's callbacks and [scope]).
     */
    private var treeLoad: Deferred<BrowseTree>? = null

    @Volatile private var serverSource = false

    /** Starts building the tree again; browsing waits for it. */
    private fun reloadTree(): Deferred<BrowseTree> =
        scope
            .async(Dispatchers.IO) {
                serverSource = ServerStore(applicationContext).source == MusicSource.Server
                val (songs, playlists) = LibraryLoader.load(applicationContext)
                BrowseTree(
                    songs,
                    playlists,
                    MusicSettings(applicationContext).albumArtists,
                    history = PlayHistory.read(applicationContext),
                    likes = LikeStore.read(applicationContext),
                )
            }.also { treeLoad = it }

    private suspend fun tree(): BrowseTree = (treeLoad ?: reloadTree()).await()

    /** Runs [block] on the service's scope and hands its result to Media3 as a future. */
    private fun <T : Any> later(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        scope.launch {
            try {
                future.set(block())
            } catch (e: CancellationException) {
                future.cancel(false)
                throw e
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    private fun sectionTitle(
        id: String,
        fallback: String,
    ): String =
        when (id) {
            BrowseTree.PLAYLISTS -> getString(R.string.tab_playlists)
            BrowseTree.ALBUMS -> getString(R.string.tab_albums)
            BrowseTree.ARTISTS -> getString(R.string.tab_artists)
            BrowseTree.SONGS -> getString(R.string.all_songs)
            BrowseTree.RECENT -> getString(R.string.browse_recent)
            BrowseTree.LIKED -> getString(R.string.liked_songs)
            else -> fallback
        }

    private fun groupTitle(group: String): String? =
        when (group) {
            BrowseTree.RECENTLY_PLAYED -> getString(R.string.sort_played)
            BrowseTree.RECENTLY_ADDED -> getString(R.string.sort_added)
            else -> null
        }

    /**
     * A browse entry as a MediaItem: title, subtitle, cover, and whether it opens or plays. The grid
     * hint is unstable in Media3 1.x, but it's how Android Auto is told to show covers.
     */
    @OptIn(UnstableApi::class)
    private fun browseItem(node: BrowseTree.Node): MediaItem {
        val art =
            if (serverSource) {
                (node.song?.art ?: node.albumId.takeIf { it > 0 }?.let { ServerLibrary.artFor(this, it) })?.let(ArtProvider::uri)
            } else {
                node.albumId.takeIf { it > 0 }?.let(ArtProvider::localUri)
            }
        val title = if (node.id.startsWith(BrowseTree.SHUFFLE)) getString(R.string.shuffle) else sectionTitle(node.id, node.title)
        return MediaItem
            .Builder()
            .setMediaId(node.id)
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(title)
                    .apply { if (node.subtitle.isNotBlank()) setSubtitle(node.subtitle).setArtist(node.subtitle) }
                    .setArtworkUri(art)
                    .setIsBrowsable(!node.playable)
                    .setIsPlayable(node.playable)
                    .setMediaType(if (node.playable) MediaMetadata.MEDIA_TYPE_MUSIC else MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .apply {
                        val extras = Bundle()
                        // Albums show as a grid of covers; everything else as lists.
                        if (node.grid && !node.playable) {
                            extras.putInt(
                                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
                            )
                        }
                        // Recent's headings: Auto lists items with the same group title under it.
                        groupTitle(node.group)?.let { extras.putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, it) }
                        if (!extras.isEmpty) setExtras(extras)
                    }.build(),
            ).build()
    }

    /**
     * A queue picked in a browser, ready for Media3. Shuffled ones are built shuffled (a random song
     * first) and recorded as such, so turning shuffle on doesn't reorder anything; whatever queue
     * was playing before isn't touched.
     */
    @OptIn(UnstableApi::class)
    private fun queueItems(queue: BrowseTree.Queue): MediaItemsWithStartPosition {
        val items = queue.songs.map { s -> mediaItem(SavedItem(s.id.toString(), s.uri, s.title, s.artist, s.album, s.albumId, s.art)) }
        val player = session?.player
        player?.playlistMetadata = playlistMetadata(queue.from)
        if (queue.shuffle) {
            val order = QueueOrder.shuffled(items.map { it.mediaId }, items.indices.random())
            unshuffled = items.map { it.mediaId }
            shuffled = true
            player?.shuffleModeEnabled = true
            return MediaItemsWithStartPosition(order.map(items::get), 0, 0)
        }
        unshuffled = null
        shuffled = false
        player?.shuffleModeEnabled = false
        return MediaItemsWithStartPosition(items, queue.start, 0)
    }

    // Media3 1.x marks parts of the library API unstable (params extras, start positions); they're
    // the documented way to serve Android Auto.
    @OptIn(UnstableApi::class)
    private inner class Callback : MediaLibrarySession.Callback {
        // The now-playing buttons' commands (like, shuffle, repeat) on top of Media3's defaults.
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val defaults =
                if (controller.isTrusted) {
                    MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                } else {
                    MediaSession.ConnectionResult.DEFAULT_UNTRUSTED_SESSION_AND_LIBRARY_COMMANDS
                }
            return MediaSession.ConnectionResult
                .AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(
                    defaults
                        .buildUpon()
                        .add(LIKE)
                        .add(SHUFFLE)
                        .add(REPEAT)
                        .build(),
                ).build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> =
            later {
                onButton(customCommand.customAction, session.player)
                SessionResult(SessionResult.RESULT_SUCCESS)
            }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // A browser connecting (the car starting) sees the library as it is now. The root itself
            // is answered at once: Android Auto connects as a legacy browser, and Media3 then holds
            // the main thread until the root arrives, so a root that waited for the main thread
            // (as the tree's loading does) never came and the car showed a spinner for good.
            reloadTree()
            val root =
                MediaItem
                    .Builder()
                    .setMediaId(BrowseTree.ROOT)
                    .setMediaMetadata(
                        MediaMetadata
                            .Builder()
                            .setTitle(getString(R.string.app_name))
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            .build(),
                    ).build()
            val extras = Bundle().apply { putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, true) }
            return Futures.immediateFuture(LibraryResult.ofItem(root, LibraryParams.Builder().setExtras(extras).build()))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            later {
                val nodes = tree().children(parentId)
                if (nodes == null) {
                    LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                } else {
                    val from = (page.coerceAtLeast(0).toLong() * pageSize).coerceAtMost(nodes.size.toLong()).toInt()
                    LibraryResult.ofItemList(ImmutableList.copyOf(nodes.drop(from).take(pageSize).map(::browseItem)), params)
                }
            }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> =
            later {
                session.notifySearchResultChanged(browser, query, tree().searchNodes(query).size, params)
                LibraryResult.ofVoid()
            }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            later {
                val nodes = tree().searchNodes(query)
                val from = (page.coerceAtLeast(0).toLong() * pageSize).coerceAtMost(nodes.size.toLong()).toInt()
                LibraryResult.ofItemList(ImmutableList.copyOf(nodes.drop(from).take(pageSize).map(::browseItem)), params)
            }

        /**
         * Something picked in a browser (a song, Shuffle) or asked for by voice ("play Juniper") becomes
         * a whole queue: the album, playlist or artist it's in, starting there. The app's own queues
         * already carry their files and go through unchanged.
         */
        @OptIn(UnstableApi::class)
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaItemsWithStartPosition> {
            val single = mediaItems.singleOrNull()
            val query = single?.requestMetadata?.searchQuery
            val picked = single?.mediaId
            val fromBrowser = picked != null && (picked.startsWith(BrowseTree.SONG) || picked.startsWith(BrowseTree.SHUFFLE))
            if (query == null &&
                !fromBrowser
            ) {
                return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
            }
            return later {
                val t = tree()
                val queue = if (query != null) t.search(query) else t.queueFor(picked!!)
                queue?.let(::queueItems) ?: throw UnsupportedOperationException("Nothing matches")
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(
                mediaItems.map { item -> item.buildUpon().setUri(item.requestMetadata.mediaUri).build() }.toMutableList(),
            )

        /**
         * Android's media controls ("resume playback" after the app was closed). Unstable in Media3
         * 1.x, but the documented way to support playback resumption.
         */
        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaItemsWithStartPosition> {
            val saved = loadQueue() ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
            return Futures.immediateFuture(MediaItemsWithStartPosition(saved.items.map(::mediaItem), saved.index, saved.positionMs))
        }
    }

    private fun openPlayerIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val EXTRA_ALBUM_ID = "album_id"

        private val LIKE = SessionCommand("io.github.michaelmassoni.tessera.music.LIKE", Bundle.EMPTY)
        private val SHUFFLE = SessionCommand("io.github.michaelmassoni.tessera.music.SHUFFLE", Bundle.EMPTY)
        private val REPEAT = SessionCommand("io.github.michaelmassoni.tessera.music.REPEAT", Bundle.EMPTY)
        const val EXTRA_ART = "art"
        private const val POSITION_SAVE_MS = 10_000L

        private const val EXTRA_FROM_KIND = "from_kind"
        private const val EXTRA_FROM_ID = "from_id"

        /** "Playing from" travels as the queue's (playlist) metadata, which every controller sees. */
        fun playlistMetadata(from: PlayingFrom?): MediaMetadata =
            if (from == null) {
                MediaMetadata.EMPTY
            } else {
                MediaMetadata
                    .Builder()
                    .setTitle(from.name)
                    .setExtras(
                        Bundle().apply {
                            putString(EXTRA_FROM_KIND, from.kind.name)
                            putLong(EXTRA_FROM_ID, from.id)
                        },
                    ).build()
            }

        fun playingFrom(metadata: MediaMetadata): PlayingFrom? {
            val kind = metadata.extras?.getString(EXTRA_FROM_KIND) ?: return null
            return PlayingFrom.Kind.entries
                .firstOrNull { it.name == kind }
                ?.let { PlayingFrom(it, metadata.title?.toString().orEmpty(), metadata.extras?.getLong(EXTRA_FROM_ID) ?: 0) }
        }

        fun mediaItem(item: SavedItem): MediaItem =
            MediaItem
                .Builder()
                .setMediaId(item.id)
                .setUri(item.uri.toUri())
                .setRequestMetadata(
                    MediaItem.RequestMetadata
                        .Builder()
                        .setMediaUri(item.uri.toUri())
                        .build(),
                ).setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setAlbumTitle(item.album)
                        .setExtras(
                            Bundle().apply {
                                putLong(EXTRA_ALBUM_ID, item.albumId)
                                item.art?.let { putString(EXTRA_ART, it) }
                            },
                        ).apply { item.art?.let { setArtworkUri(ArtProvider.uri(it)) } }
                        .build(),
                ).build()
    }
}
