# Music: spec

Package `io.github.michaelmassoni.tessera.music`. Status: v0.1, first implementation.

## Goals

A music player for the audio files on the device or, since 0.4, a Subsonic-compatible server
(see "Music on a server"). Local music needs no network.
Playback continues in the background with the standard media notification, lock screen
controls, Bluetooth and headset buttons, and Android's output switcher. All of this comes from
a Media3 `MediaSessionService`.

## Permissions

| Permission | Why |
|------------|-----|
| `READ_MEDIA_AUDIO` (`READ_EXTERNAL_STORAGE` with `maxSdkVersion` 32) | Find and play audio files |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playing when the app is in the background |

- **Notifications:** the media notification belongs to a media session, which Android exempts
  from `POST_NOTIFICATIONS`, so that permission isn't requested.

## Library

- **Source:** MediaStore audio with `IS_MUSIC = 1`, which skips ringtones, notification sounds
  and recordings.
- **Songs:** every track, sorted by title. Leading "The " and "A " are ignored when sorting.
- **Albums:** a grid of covers. Album art is the system's album thumbnail
  (`ContentResolver.loadThumbnail`). An album shows its tracks in disc and track order.
- **Artists:** a list with album and song counts. An artist shows their albums, then all their
  songs.
- **Search:** title, artist and album, ignoring case and accents.

## Playback

- **Starting:** tapping a song plays it with the rest of the list it was in (album, artist or all
  songs) as the queue.
- **Shuffle all:** a button above the song list.
- **Mini player:** a bar above the navigation shows the cover, title, artist and play/pause,
  with a thin progress line. Tapping it opens the full player.
- **Full player:**
  - Large artwork, title, artist and album.
  - Seek slider with elapsed and remaining time.
  - Previous, play/pause and next; shuffle and repeat (off, all, one).
  - A queue sheet where you can jump to any track.
- **Artwork:** the player uses artwork embedded in the file (Media3 reads it); the library uses
  album thumbnails. Embedded art is decoded at the size shown (it is often thousands of pixels
  across), and album thumbnails are kept in memory (16 MB), since lists show the same album many
  times.
- **Audio focus:** pauses for calls and ducks for navigation prompts (Media3 handles audio
  focus). Unplugging headphones pauses ("becoming noisy").
- **Resuming:** `PlaybackService` saves the queue (URI, title, artist, album per track), current
  track, position, shuffle and repeat to `queue.json` whenever they change, every 10 seconds
  while playing, and when the app is swiped away. When the service starts again it puts the queue
  back, paused. Media3's `onPlaybackResumption` serves the same file, so Android's media controls
  and Bluetooth "play" resume it too (`MediaButtonReceiver` is declared for that).
- **Progress** is its own flow, ticking twice a second while playing. Only the progress line and
  the seek slider read it, so the rest of the player doesn't recompose with each tick.

## Playlists

- Kept by the app in `playlists.json` (by MediaStore song id), not in MediaStore, whose
  playlists are deprecated and unreliable on Android 12+. Songs that leave the library are
  skipped when shown and stay in the file in case they come back.
- **Playlists tab:** New playlist, then every playlist with its song count.
- **Adding:** long-press a song (Songs tab or search) for a sheet with Add to playlist, which
  lists the playlists and New playlist. A song already in a playlist isn't added twice.
- **A playlist:** song count, Play and Shuffle, the songs (long-press to remove one), and Rename
  and Delete playlist in the overflow. Deleting asks first and leaves the songs in the library.

## Widget

- **Now playing (Glance, 4×1, home and lock screen):** album art, title and artist, with
  previous, play/pause and next. The buttons send media button events to Media3's
  `MediaButtonReceiver`, so they work, and resume the saved queue, even when the service isn't
  running. `PlaybackService` writes what's playing to shared preferences and redraws the widget
  as it changes. Tapping elsewhere opens the app.

## Engine (pure Kotlin, unit tested)

- `Library`:
  - Groups songs into albums and artists.
  - Sort keys ignore articles and accents.
  - Track order uses disc then track number (MediaStore `TRACK` is `disc*1000 + track`).
  - Search.
- `formatDuration`.

## Layout

| Window | Layout |
|--------|--------|
| Compact | Bottom navigation (Songs, Albums, Artists), mini player above it; full player as a full screen |
| Expanded | Navigation rail; the full player opens as a side pane next to the library |

## Accessibility

- **Controls:** every control has a label, and play/pause announces its state.
- **Seek slider:** has a label and supports the standard slider accessibility actions.
- **Song rows:** read "title, artist, duration".

## Music on a server (0.4)

Music can play from a Subsonic-compatible server (Navidrome, Gonic, Airsonic and others) instead
of the files on the phone. Requested by the maintainer, who keeps their music in Navidrome.
Jellyfin has its own API, not Subsonic's, and isn't covered.

- **Choosing:** first-run setup asks where the music is: "On this phone" (as before, asks for
  audio access) or "On a server" (asks for the server's address, username and password, then
  checks them). Settings > Music source changes it later. One source at a time: the library,
  search and playlists show that source's music.
- **Library:** every song is fetched with `search3` (an empty query pages through the whole
  library, 500 songs a request), falling back to `getAlbumList2` + `getAlbum` for servers that
  don't allow an empty query. It's saved on the phone (`files/server-library.json`), shown at
  once when the app opens, and fetched again in the background each time it opens. Albums,
  artists, search and the app's own playlists work exactly as with local files. Server
  playlists are shown since 0.5 (see below).
- **IDs:** Subsonic ids are strings. Each becomes a stable 64-bit number (a hash, separate for
  songs, albums and artists), so playlists, the queue and the widget work unchanged; the string
  id is kept in the song's URI (`tessera-subsonic:song/<id>`).
- **Playback:** streamed with `stream`. The URI is turned into the real address only when
  playback opens it, with a new salt and token each time, so the queue file never holds a
  credential. **Streaming quality:** original (default), 320, 192 or 128 kbps (`maxBitRate`;
  the server transcodes).
- **Cache:** played songs are kept in an on-phone cache (Media3 `SimpleCache`, least recently
  played removed first) so replays and an unreliable connection don't need the network.
  Settings > Offline cache: 500 MB, 1 GB (default), 2 GB or 5 GB (a new size applies the next
  time Music starts), and Clear. Media3 also buffers the next song in the queue, so it's cached
  too. Pinned downloads for offline listening are a later step.
- **Offline and not cached:** the mini player says "Can't play: no connection to the server" in
  the error colour; pressing play tries again.
- **Art:** `getCoverArt` at the size shown, kept in the image cache and on disk
  (`cache/art/`). Media controllers (notification, lock screen, Bluetooth) are other apps that
  load the artwork URI themselves, so server art is served by `ArtProvider`
  (`content://io.github.michaelmassoni.tessera.music.art/<art id>`): exported, read-only, and
  all it returns is cover images. No address or token ever leaves the app.
- **Plays:** the server is told what's playing (`scrobble` with `submission=false`) and, once
  half the song or four minutes has played, that it was played, so Navidrome's play counts and
  "recently played" stay right. Failures are ignored.

### Signing in

- **Auth:** Subsonic token auth: `t = md5(password + salt)` with a new random salt per request.
  The password itself is never sent.
- **Stored:** address and username in `shared_prefs/music_server.xml`; the password encrypted
  with an AES-GCM key in the Android Keystore. That file is left out of Tessera backups and
  Android backups: a restored phone asks for the password again.
- **Address:** `https://` by default. `http://` is accepted only for addresses on a private
  network: 10.x, 172.16–31.x, 192.168.x, 100.64–127.x (Tailscale and other CGNAT), loopback,
  IPv6 private and link-local, `.local`, `.lan`, `.home.arpa`, `.ts.net` and single-word names.
  The manifest allows cleartext so those work; the app refuses it for anything else.
  User-installed certificate authorities are trusted, for servers with a private CA.
- **Errors**, in words: can't reach the server, wrong username or password, not a Subsonic
  server, server too old. Wrong credentials later show a "Sign in again" banner; an unreachable
  server keeps the saved library and cached songs playing.

### Permissions (server)

| Permission | Why |
|------------|-----|
| `INTERNET` | Only to reach the server the person adds. Nothing else is contacted. |

## Player, queue, playlists and lyrics (0.5)

Asked for by the maintainer after using 0.4 with their Navidrome library.

- **Playing from:** the player's top bar says where the queue came from: "Playing from album /
  petal", playlist, artist, search, or "your library / All songs". It travels as the queue's
  playlist metadata (`Player.playlistMetadata`), so it survives the app being closed.
- **Shuffle reorders the queue:** turning shuffle on moves the song playing to the top and
  shuffles the rest, in the queue itself, so the queue shows what plays next. Turning it off puts
  back the order from before, with songs added meanwhile at the end. It works the same whichever
  controller turns it on (the notification, Bluetooth). ExoPlayer is given an unshuffled shuffle
  order, and the service reorders with moves so the song never stops (`QueueOrder`, unit tested).
  Starting a new queue with shuffle on shuffles it around the song tapped.
- **Queue:** drag a song by its handle to move it; swipe it away to remove it; tap to play it.
  TalkBack has Move up, Move down and Remove actions. Songs' long-press menu adds **Play next**
  and **Add to queue**.
- **Save to playlist:** a button beside the song title (ticked when the song is in one of your
  playlists) opens the playlists with checkboxes: tick to add, untick to remove, or make a new one.
- **Server playlists:** with a server, the Playlists tab shows the server's playlists (`getPlaylists`
  then `getPlaylist` for each, saved with the library). Creating, renaming, deleting, adding and
  removing songs happen on the server (`createPlaylist`, `updatePlaylist`, `deletePlaylist`); the
  change shows at once and the server's playlists are read back after. **Smart playlists**
  (Navidrome's `.nsp`, marked `readonly`) and other people's shared playlists can be played but not
  changed: they say "Can't be edited here", have no edit menu, and aren't offered when adding
  songs. Playlists kept on the phone are for music on the phone.
- **Lyrics:**
  - **From the server first:** OpenSubsonic `getLyricsBySongId` (Navidrome reads lyrics from the
    file's tags or a `.lrc` file next to it), then the older `getLyrics`. Synced lyrics are
    preferred.
  - **Online, if turned on:** LRCLIB (lrclib.net), an open lyrics database with no key or account.
    Off until the person turns it on, from the lyrics card ("Look up lyrics online", which says
    what's sent) or Settings > Lyrics. It's sent the song's title, artist, album and length, and
    nothing else, with a User-Agent naming the app. The exact match (`/api/get`) is tried first,
    then `/api/search`, taking a record within 5 seconds of the song's length (synced first).
  - **Cached** in `cache/lyrics/`. Lyrics are kept; "none found" is kept for a week, and is
    forgotten when online lyrics are turned on. A failure to reach anything isn't remembered.
  - **On the player:** a Lyrics card under the controls shows the line being sung (bold) and the
    next, or the first lines of unsynced lyrics. It follows the song without drawing every frame:
    it waits until the next line is due, and corrects itself each time the player reports the
    position (twice a second).
  - **Full lyrics:** tapping the card opens every line. The line being sung is highlighted and
    kept about a third of the way down; after the person scrolls, following resumes 4 seconds
    later. Tapping a synced line plays from there. Says where the lyrics came from.
  - Not yet: lyrics embedded in local files (MediaStore doesn't expose them).

## Out of scope for v0.1

- Lyrics.
- Equalizer.
- Gapless tuning.
- Folders view.
- Tag editing.
- Android Auto.

## First run

See [setup.md](setup.md) for what music asks for the first time it opens.
