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
  checks them). Settings > Source changes it later. One source at a time: the library,
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
  Settings > Source > Offline cache: 500 MB, 1 GB (default), 2 GB or 5 GB (a new size applies the next
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
- **Go to album and artist (0.8):** the full player's overflow menu has **Go to album** and **Go to
  artist**, and the artist and album names under the title are links to the same pages (the menu is
  the dependable target; the names are small). Either closes the player and opens the page over
  the current tab. The page is found from the library song (`Library.albumOf` / `artistOf`), else
  by the album id and artist name the player has, so a restored queue still links; with no match
  the entry isn't shown. Artist means the track artist, as shown. A song's long-press menu (Songs,
  search, playlists, an album, an artist) has the same two entries, less the page you're on.
- **Playing from (0.9):** the "Playing from album Fold" title at the top of the full player is a
  button: it closes the player and opens that album, artist or playlist (all songs: the Songs
  tab; search: not a button). The queue keeps the source's id (`PlayingFrom.id`, also in the
  session's playlist metadata and `queue.json`); a queue saved before 0.9 is matched by name, only
  when one album, artist or playlist has it (`sourceId`, unit tested).
- **Swipe up (0.9):** dragging the mini player up brings the full player up with the finger; let go
  a quarter of the way up (or flick) and it opens, less and it drops back. Dragging the open player
  down still needs to go a quarter of the way (or a flick) to close it.
- **Explicit (0.8):** an "E" badge after the title of a song the server marks explicit (OpenSubsonic
  `explicitStatus`), in song lists and the full player; TalkBack reads "Explicit". Music on the
  phone has no badge: MediaStore has no explicit field, and reading every file's tags isn't worth it.
- **Landscape (0.8):** when the window is wider than tall (at least 560dp), the full player puts
  the art on the left at the full height and the title, seek bar, controls and lyrics card beside
  it, centred, scrolling if they don't fit. Content is kept clear of a display cutout. In a short
  window (under 480dp tall) the library uses a navigation rail instead of the bottom bar. The open
  album, artist or playlist page survives rotation.
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

## Browsing and motion (0.6)

- **Opening albums and artists:** the page slides in a little and fades in; the album cover (or
  artist picture) moves from where it was tapped to the page header (a shared element). Back
  reverses it, and a predictive back gesture shrinks the page as the finger moves. Switching tabs
  cross-fades. While the bigger cover loads, the one already shown stands in, so it never flashes
  the placeholder.
- **Sorting:** a chip above each page. Songs: A to Z, artist, album, recently added, recently
  played. Albums: A to Z, artist, year, recently added, recently played. Artists: A to Z,
  recently added, recently played. Playlists: A to Z, recently played. Remembered per page.
  - Recently added: MediaStore `DATE_ADDED`, or the server's `created`.
  - Recently played: the server's `played`, or this phone's own history (`files/plays.json`,
    written by the playback service as each song starts), whichever is later.
- **Grid or list:** Albums, Artists and Playlists each switch between a grid of covers and a list,
  remembered per page. Playlists show their first song's cover.
- **Album artists:** on by default, the Artists page lists album artists (each album once, under
  the artist it's credited to: the album artist tag, else its most common track artist), so
  featured artists and compilations don't each become an artist. A chip switches back to every
  track artist. Album artists come from MediaStore `ALBUM_ARTIST` or the server's
  `displayAlbumArtist`.
- **Player sheet:** tapping the mini player slides the player up from the bottom. Dragging down
  (from its top bar, or anywhere once its content is scrolled to the top) moves it with the
  finger; let go past a quarter of the way or with a downward flick and it slides away, otherwise
  it springs back. A predictive back gesture lowers it as the finger moves.
- **Shuffle and repeat** show "on" as a filled circle, not just a tint, so it reads in dark theme.
- **Shuffle is immediate:** the service reorders the queue with at most four changes (everything
  but the song playing taken out and put back in the new order). One move per song had sent the
  whole queue to the app for every move, which took seconds for a long queue.

## Android Auto (0.7)

Music is an Android Auto media app (`automotive_app_desc.xml`, `<uses name="media"/>`); no Google
library is involved. `PlaybackService` is a Media3 `MediaLibraryService`, so the car browses it:

- **Browse:** Playlists, Albums (a grid of covers), Artists (album artists unless turned off) and
  Songs, down to songs; playlists, artists and Songs start with Shuffle. The tree is `BrowseTree`
  (pure, unit tested), built from the server's saved library or the phone's music, and rebuilt
  when the car connects.
- **Picking a song** plays its album, playlist or artist from there, with "Playing from" set.
  Shuffle entries queue the list already shuffled.
- **Voice:** "play <album>" plays the album; "play <artist>" shuffles the artist; anything else
  plays the matching songs; just "play music" shuffles everything. The car's search screen lists
  matching songs.
- **Covers** reach the car through `ArtProvider`, which serves server covers and, for music on
  the phone, the system's album thumbnails (`…/local/<album id>`), since the car can't read
  MediaStore itself.
- **Installing outside the Play Store:** Android Auto hides such apps until you allow them: in the
  Android Auto app, tap the version number in Settings ten times to unlock developer settings,
  then turn on "Unknown sources".
- **The root is answered at once (0.10.2):** Android Auto connects as a legacy
  (`android.media.browse`) browser, and Media3 holds the main thread until `onGetLibraryRoot`
  returns. The root used to wait for the library to load, which resumes on the main thread, so
  it never came and the car showed a spinner for good. The library now loads in the background
  and `onGetChildren` waits for it.
- Checked on the emulator by browsing the service with a Media3 `MediaBrowser` (sections, grid
  hint, covers readable by another app, picking a song, voice search, shuffle) and, since 0.10.2,
  with the framework `MediaBrowser` from another app, the path Android Auto takes. A Media3
  browser alone missed the spinner. Not run in the Desktop Head Unit: Android Auto isn't on the
  AOSP emulator.

## Now playing in lists, album options, smooth progress (0.11)

Asked for by the maintainer alongside casting.

- **The song playing is marked** in every song list (Songs, search, album, artist, playlist): the
  row is highlighted, its title is in the primary colour, and a level-bars icon sits beside the
  length. TalkBack adds "playing now".
- **Album and artist pages have More options** in the top bar: Play next, Add to queue, Shuffle
  (albums), Add to playlist (or a new one) and, for an album, Go to artist. Albums also get a
  Shuffle button beside Play. The sheet is the song one (`SongsOptions`), for many songs at once.
- **The seek bar and the mini player's progress move smoothly.** The player is still sampled
  twice a second; between samples `Progress.at` carries the position on from the last sample
  every frame while playing (`rememberSmoothProgress`), so they glide instead of jumping.

## Casting (0.11)

Asked for by the maintainer: cast to Chromecasts, Google TVs, Nest speakers and TVs with Cast
built in. Google's Cast SDK (and Media3's cast module) needs Google Play Services, which Tessera
doesn't use (docs/decisions.md, 2026-11-26), so Music speaks the Cast protocol itself, like
other open-source senders do.

- **The Cast button** is under the playback controls, on the left (since 0.11.2). It opens "Cast to": the devices found on the
  Wi-Fi (TV or speaker icon, by what the device says it can do), "Searching…" while looking, and
  a hint about the same Wi-Fi when nothing turns up. Picking one moves playback there: the queue,
  the song, the position and whether it was playing. While casting the sheet shows the device,
  its volume and "Stop casting", the button shows as connected, and the player says "Casting to
  <device>".
- **What plays where:** the TV runs Google's Default Media Receiver (the stock player with art,
  title and progress). Music on the phone keeps the queue, so shuffle, repeat, the queue sheet,
  Next and Previous work as always, and the next song is sent when one ends.
- **The audio comes from the phone.** Music runs a small web server on the Wi-Fi while casting
  and the cast device fetches each song from it. That serves files on the phone and server songs
  (through the same cache as playing on the phone), so the cast device needs no Subsonic
  password and needn't reach the server itself (a Tailscale-only server still works). Each song
  and cover gets a random, unguessable address, and only the cast device's address may connect.
  The server stops with the cast session.
- **Controls everywhere:** the notification, lock screen, Bluetooth and Android Auto control
  what's casting (the session switches to a remote Media3 player). The phone's volume keys set
  the cast device's volume.
- **Stopping:** "Stop casting", or the cast device going away (switched off, another phone
  taking over, Wi-Fi lost), brings playback back to the phone, paused where it was.
- **Staying awake:** while a cast session is connected, Music holds a partial wake lock and a
  Wi-Fi lock so the phone keeps serving audio and answering the device's heartbeat with the
  screen off.
- **Security:** cast devices use self-signed TLS certificates, so the connection is encrypted
  but the device isn't authenticated (as with other open-source senders). It only ever receives
  song addresses on the phone, never a password.
- **Engine (unit tested):** `CastMessage` (the protobuf frame, hand-encoded: no protobuf
  library), `CastProtocol` (the JSON messages and status parsing), `MediaServer` (HTTP with
  byte ranges, tokens and the allowed address) and `RemoteQueue` (what plays next, with repeat).

- **Checked:** unit tests cover the frame, the messages, the media server (ranges, HEAD, unknown
  lengths, the allowed address) and `RemotePlayer` (advancing, repeat, queue edits, errors,
  volume); `CastSession` runs against a fake receiver with a self-signed certificate (launch,
  load, pause, heartbeat, another phone taking over, the device going away). On the API 37
  emulator: the permission prompt, "Allow" from the sheet after a refusal, mDNS queries going out
  while the sheet is open and stopping when it closes, and "No devices found". Not checked on a
  real Chromecast or TV from here: the emulator can't see devices on the Wi-Fi.

### Permissions (casting)

| Permission | Why |
|------------|-----|
| `ACCESS_LOCAL_NETWORK` | Android 17 asks before an app finds or talks to devices on the Wi-Fi: finding cast devices, controlling them and serving them audio. Asked for when the Cast button is first tapped; Music works without it, only casting doesn't. |
| `WAKE_LOCK` | Keeps the phone serving audio to the cast device with the screen off. Held only while casting. |
| `ACCESS_WIFI_STATE` | The Wi-Fi lock (the same). |

### Not yet

- Cast devices in Android's own output switcher (needs a `MediaRoute2ProviderService`).
- Speaker groups (they appear like any device, but untested).
- DLNA / UPnP renderers.

## Player layout (0.11.2)

Asked for by the maintainer, after the way other players lay it out:

- **Top bar:** only Close on the left, "Playing from" in the middle and More options (go to album,
  go to artist) on the right.
- **Under the controls:** the Cast button on the left, with "Casting to <device>" beside it while
  casting, and the queue on the right. No share button.

## Out of scope for v0.1

- Lyrics.
- Equalizer.
- Gapless tuning.
- Folders view.
- Tag editing.

## First run

See [setup.md](setup.md) for what music asks for the first time it opens.
