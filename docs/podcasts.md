# Podcasts: spec

Package `io.github.michaelmassoni.tessera.podcasts`. Status: v0.1 (2026-11-25).

## Goals

A simple podcast player in the spirit of Apple Podcasts and the old Google Podcasts: follow
shows, see their new episodes, play them with an Up Next queue, and download them for offline
listening. Not a power-user app (no filters, playlists, statistics or per-show rules in v0.1).

Out of scope for v0.1: sync with a server (gPodder, Nextcloud), chapters, transcripts, video
episodes (they play as audio), new-episode notifications, a widget, Android Auto, per-show
settings, charts or a "Browse" page.

## Finding shows

- **Search** (the Search tab) asks two directories at once and merges the results, Apple's first
  (the best coverage), then fyyd's that Apple didn't have (same feed URL):
  - Apple's iTunes Search API, `https://itunes.apple.com/search?media=podcast&entity=podcast&term=…`.
    Keyless, public. AntennaPod uses it too.
  - fyyd, `https://api.fyyd.de/0.2/search/podcast?title=…`. Keyless, an open directory.
  A directory that fails is skipped; both failing says "Couldn't search. Check your connection."
- **Add by URL** (Search tab overflow): an RSS/Atom feed URL, or an Apple Podcasts link
  (`podcasts.apple.com/…/id123`), which is turned into its feed with Apple's lookup API.
- **OPML** import and export in Settings (the format every podcast app reads and writes).
- Only the search words go to Apple and fyyd; nothing about the person or their shows.

## Screens

Navigation bar (rail on wider screens): **Home**, **Library**, **Search**. Settings from the top
bar. A mini player sits above the navigation whenever something is loaded.

- **Home:**
  - **Up Next:** the queue, in order (drag to reorder, swipe to remove).
  - **New episodes:** unplayed episodes of followed shows from the last 14 days, newest first.
  - Pull to refresh all feeds. Empty state: "Follow shows to see their new episodes here", with a
    button to Search.
- **Library:** followed shows as a grid of covers (title under each), sorted by latest episode.
  A **Downloaded** row at the top opens the downloaded episodes.
- **Search:** search field; results as rows (cover, title, author). Tapping opens the show
  (a show isn't followed until **Follow** is tapped).
- **Show:** cover, title, author, description (collapsed to 3 lines, tap for more), **Follow** /
  **Following** button, then the episodes newest first. Unfollowing asks first and deletes the
  show's downloads.
- **Episode row** (everywhere): date · duration or time left, title, two-line summary, and
  buttons: play/pause (with a ring showing progress), download (progress while downloading; tap
  to cancel), and ⋮ (Play next, Add to Up Next / Remove from Up Next, Mark as played /
  unplayed, Delete download, Go to show). Played episodes are dimmed.
- **Episode:** cover, show, title, date and duration, the play and download buttons, and the
  show notes (HTML rendered as text with links; images are left out).
- **Player** (a full-screen sheet from the mini player): cover, title, show (tap to open it),
  seek slider with elapsed and remaining time (and the chapter playing, with a break in the
  track where each chapter begins), then, as Spotify lays it out (0.3): speed (0.5× to 3×,
  remembered for all episodes), back 15 s, play/pause, forward 15 s and the sleep timer (5, 15,
  30, 45, 60 min, end of episode; the icon turns the accent colour while it's set). Under them,
  Cast on the left and Up Next on the right (a sheet: tap an episode to play it, or take it
  out). Up Next is also on Home.

## Playback

- A Media3 `MediaSessionService` with ExoPlayer: media notification, lock screen, Bluetooth and
  headset buttons, output switcher, audio focus (pauses for calls, ducks for navigation),
  "becoming noisy" pauses. Content type speech.
- The media notification shows back 15 s and forward 15 s (Media3 media button preferences) in
  place of previous/next, as podcast apps do. Headset and Bluetooth next/previous still move
  through Up Next.
- **The queue is the player's playlist:** the episode playing first, then Up Next. Playing an
  episode puts it at the front (moving it if it was already queued); the one it interrupts stays
  right after it. When an episode ends it is marked played, leaves the queue, and the next one
  starts. Marking the episode playing as played does the same.
- **Position:** saved per episode every 10 s while playing, on pause and on every change of
  episode; an episode starts where it was left. An episode within 30 s of its end when stopped
  counts as played (outros, ads).
- Downloaded episodes play from the file; others stream (with a wake lock so streaming carries on
  with the screen off). Feeds and enclosures often redirect between http and https (tracking
  prefixes), so redirects across protocols are followed.
- **Resuming:** the queue and position are restored, paused, when the service starts again
  (swiped away, process died, Android's "resume" media controls).

## Feeds

- RSS 2.0 with the iTunes and Podcasting 2.0 tags we use (`itunes:image`, `itunes:duration`,
  `itunes:author`, `itunes:summary`, `content:encoded`, `podcast:guid` is ignored). Atom is
  read too. An item without an audio enclosure is skipped.
- Episode identity is the item's `guid`, else the enclosure URL, so re-published feeds don't
  duplicate episodes.
- **Refreshing:** when the app opens (if the last refresh was more than an hour ago), on pull to
  refresh, and every 6 hours in the background (WorkManager, needs a network). Conditional
  requests (`ETag`, `Last-Modified`) so unchanged feeds cost almost nothing.
- A feed that moves (HTTP 301/308, or `itunes:new-feed-url`) is followed and its URL updated.

## Downloads

- Stored in the app's own external folder (`Android/data/…/files/Podcasts`), so no storage
  permission; uninstalling removes them. Not in Tessera backups.
- Manual: the download button on any episode. Downloads run in a WorkManager job with a
  foreground notification showing progress (type `dataSync`) and a Cancel action, retried when
  the network comes back.
- **Settings:**
  - **Download new episodes** (default on): episodes published after the last refresh of a
    followed show download automatically, on unmetered networks (Wi-Fi) only, when storage isn't
    low.
  - **Delete played downloads** (default on): a download is deleted once its episode is played.
  - **Mobile data downloads** (default off): lets manual downloads use mobile data. Streaming
    always works on any network.
- Downloads whose file has gone (cleared storage) fall back to streaming and are marked not
  downloaded.

## Data

- `files/library.json`: followed shows (feed URL, title, author, cover URL, description,
  ETag/Last-Modified, last refresh) and the person's state per episode (position, played,
  downloaded file), the Up Next order, and the speed. Small; in Tessera backups.
- `files/feeds/<show>.json`: the parsed episodes of each show, without show notes;
  `files/notes/<show>.json`: show notes, read only when an episode is opened. Both are a cache,
  fetched again, so left out of backups.
- Shows looked at in Search but not followed are kept in memory only.

## Settings

Download new episodes; Delete played downloads; Mobile data downloads; Import OPML; Export
OPML; then the shared Tessera settings (theme, icon, backup).

## First run

Welcome; Notifications (optional: download progress; the media notification doesn't need the
permission); restore.

## Permissions

| Permission | Why |
|------------|-----|
| `INTERNET` | Feeds, search, streaming and downloading |
| `ACCESS_NETWORK_STATE` | Download on Wi-Fi only (WorkManager constraints) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playing in the background |
| `FOREGROUND_SERVICE_DATA_SYNC` | Finish a download with the app closed |
| `WAKE_LOCK` | Keep streaming with the screen off |
| `POST_NOTIFICATIONS` | Download progress (optional) |
| `ACCESS_LOCAL_NETWORK` | Casting (0.3): finding the cast device and serving it the episode over the Wi-Fi. Asked for when Cast is first tapped; nothing else needs it. |
| `ACCESS_WIFI_STATE` | Casting: a Wi-Fi lock while casting, so the phone keeps serving with the screen off. |

- **Cleartext HTTP** is allowed: many podcast feeds and audio files are still served over plain
  HTTP, and refusing them would make those shows unplayable.

## Casting (0.3)

Asked for by the maintainer once casting worked in Music. It uses the same Cast client,
now in `core:cast` (docs/music.md, Casting, for how it works; docs/decisions.md, 2026-11-26).

- **The Cast button** is under the controls on the left, with "Casting to <device>" beside it
  while casting.
- **The episode is served from the phone**, download or stream alike: the phone follows the
  publisher's http/https redirects, which the Default Media Receiver may not, and reads the
  stream as it does to play it. The cover is served the same way.
- **Up Next, speed and skips carry over.** Back and forward 15 seek on the device. Speed is
  sent to the device, which plays 0.5× to 2×: faster settings play at 2× while casting, and
  the setting itself is kept.
- **Episodes finish as on the phone:** one that plays to the end on the device is marked played
  and leaves Up Next (the remote player reports it as an automatic transition), and positions
  are saved as usual.
- **The sleep timer** pauses the cast device.
- Stopping casting, or losing the device, brings playback back to the phone, paused where it
  was.

## Chapters (0.3)

Asked for by the maintainer, with Spotify's player as the reference.

- **Where they come from:** the feed's Podcasting 2.0 `<podcast:chapters>` file (JSON: start,
  title, image, link; ones marked `"toc": false` are left out) or, failing that, ID3 `CHAP`
  frames in the audio, which ExoPlayer reads as it opens the file (so they appear once the
  episode has started on the phone). The feed's file wins. Kept in memory for the session
  (`EpisodeChapters`); the file is fetched when the episode starts.
- **In the player:** a Chapters button above the titles opens the list (each chapter's start, the
  one playing highlighted; tap to go there); the seek bar breaks where each chapter begins and
  the chapter playing is named between the times. Untitled chapters are "Chapter n".
- **Engine (unit tested):** `Chapters` (the JSON file, sorting, the chapter at a position, the
  marks) and the feed parser's chapters link.
- Not yet: chapter images in place of the cover as they come round, and skipping by chapter.

## Accessibility

Every icon button has a content description; the progress ring on the play button is described
as "12 minutes left"; the seek slider announces elapsed time; list rows have 48dp targets and
text wraps at 200% font scale.

## Edge cases

- A feed that isn't a podcast (HTML page, no audio items): "This doesn't look like a podcast
  feed."
- A feed that fails to refresh keeps its episodes; the show page says when it last updated.
- An episode whose audio fails to stream: the player says it couldn't play, and play tries
  again.
- Episodes removed from a feed stay if downloaded or in Up Next, otherwise they go.
- Very large feeds (thousands of episodes) are parsed streaming (SAX), not into a DOM.
