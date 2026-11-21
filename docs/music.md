# Music: spec

Package `io.github.michaelmassoni.tessera.music`. Status: v0.1, first implementation.

## Goals

A local music player for the audio files on the device, with no streaming, accounts or network.
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

## Out of scope for v0.1

- Lyrics.
- Equalizer.
- Gapless tuning.
- Folders view.
- Tag editing.
- Android Auto.
