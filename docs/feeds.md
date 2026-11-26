# Feeds: spec

Package `io.github.michaelmassoni.tessera.feeds`. Status: draft for v0.1 (2026-11-26).

## Goals

An RSS and Atom reader that looks and behaves like a first-party app. The maintainer already reads
feeds in [yarr](https://github.com/nkanaev/yarr) on their NAS. Feeds works in one of two modes, picked at first
run and changeable in Settings:

- **yarr server:** the phone mirrors the server's folders, feeds and articles. Read, unread and
  starred sync both ways, and feeds and folders added or removed on the phone change on the server.
  yarr does the fetching, so the phone only talks to one address.
- **On this phone:** the phone fetches each feed itself. Feeds can be imported from an OPML file
  (yarr's Settings > Export, or any other reader's) and exported again.

## Model

- **Folder:** a name. One level, as yarr and OPML readers do.
- **Feed:** title, feed URL, site URL, folder (or none), icon, notify on new articles (off by
  default), last error.
- **Article:** feed, title, link, author, date, content (HTML), state (unread, read, starred).
  Starred is a state of its own, as in yarr: starring a read article keeps it starred, unstarring it
  makes it read.

## Screens

- **Articles (main):** a list of articles, newest first. A navigation drawer chooses what's listed:
  Unread, Starred, All articles, then each folder (with its feeds under it) and feeds without a folder, each with an
  unread count. On expanded widths the drawer is permanent and the article opens beside the list.
  - A row shows the feed's icon and name, how long ago, the title (bold while unread) and the
    first lines of text, with the article's first image as a thumbnail when it has one.
  - Swipe a row right to mark it read or unread, left to star or unstar it (Settings > Reading >
    Swipe actions changes either: mark read or unread, star or unstar, or nothing). Pull to refresh.
  - Top bar: search (titles and text of what's on the phone), **Mark all as read** (with Undo),
    Settings, and the drawer button.
- **Article:** the feed name, title, author and date, then the content shown natively (Compose,
  not a WebView): paragraphs, headings, links, emphasis, lists, quotes, code, images (Coil), and
  figure captions. Opening an article marks it read. Top bar: star, mark unread, share, open in
  browser. **Full article** fetches the page and shows its main text, for feeds that send only a
  summary. A yarr server provides this through its readability endpoint (`/page`). On this phone it
  isn't offered in 0.1, and **Open in browser** is there instead.
- **Manage feeds** (from the drawer): folders and their feeds. Add a feed with **+**: paste a feed or site
  address and choose a folder. A site address is looked up for its feed (`<link rel="alternate">`),
  and when there's more than one the person picks. Per feed: rename, move to folder, notify on new
  articles, unsubscribe. Per folder: rename, delete (its feeds move out of the folder).
- **Settings:** the source (yarr server address and sign-in, or this phone; switching clears
  the articles kept on the phone), the sync settings shared with the DAV apps (how often, on
  local changes, Wi-Fi only, Sync now), keep read articles for (1 week, 1 month, 3 months; starred
  articles are always kept), article text size, import OPML and export OPML (SAF), notifications.

## yarr sync

yarr's own JSON API (`/api/...`), not its Fever API. It works with or without yarr's `-auth`
(Fever needs auth), and it can add and remove feeds and folders, which Fever can't.

- **Sign-in:** address (https, or http on a private network or Tailscale, as Music and Tasks),
  then `GET /api/status`. 200 means no login is needed. 401 means a username and password are
  needed: `POST /login` (form) returns an `auth` cookie, valid 7 days. The app keeps the password
  (Keystore-encrypted, out of backups) and logs in again on any 401.
- **Pull:** `/api/folders`, `/api/feeds` (with icons), `/api/feeds/errors`; then every unread and
  every starred article (`/api/items?status=…`, 20 per page, following `after`); then the newest
  articles of any state until one already on the phone is reached (at most 500), which picks up
  articles read elsewhere since the last sync. An article on the phone that the server no longer lists as
  unread or starred is read (unless a change made on the phone is waiting to go up).
- **Push:** each change of state made on the phone is queued and sent first (`PUT /api/items/{id}`,
  `{"status": …}`), so the pull that follows sees it. Mark all as read sends each article that
  was unread on the phone, not yarr's mark-all endpoint, which would also mark articles the
  person hasn't seen yet.
- **Feeds and folders** are changed on the server straight away (`POST /api/feeds`, `PUT` and
  `DELETE /api/feeds/{id}`, `/api/folders`). They need the server to be reachable, and say so
  when it isn't. Adding a feed may answer `"multiple"`. The choices are shown to pick from.
- **Retention:** the server keeps 50 articles a feed or 90 days. The phone drops read articles
  older than the "keep read articles" setting and keeps unread and starred ones.

## On this phone

- Each feed is fetched with a conditional GET (`ETag`, `Last-Modified`). RSS 2.0, RSS 1.0 (RDF)
  and Atom are parsed with jsoup's XML parser, which is lenient with the broken XML many feeds
  serve. The article id is the entry's guid/id, else its link, else a hash of its title and date.
- New articles are unread. An article that drops out of the feed stays on the phone until the
  retention rule removes it.
- The feed's icon is the site's `/favicon.ico` (fetched once, kept as a file).
- OPML import keeps one level of folders (deeper outlines become folders named after the top
  level). Export writes the same shape yarr does.

## Background refresh and notifications

- WorkManager, at the interval from the sync settings (default 1 hour here; the DAV apps default
  to 4), network required, Wi-Fi only if chosen. Also on opening the app and on pull-to-refresh.
- Feeds marked **notify** post a notification for new unread articles ("Feed name: Title", or "3
  new articles" grouped per feed). Tapping opens the article. Notifications are optional; setup
  asks.

## Widget

**Latest articles:** the newest unread articles (title, feed, age), the unread count in the header,
and refresh. Tapping an article opens it in the app. Tapping the header opens the app. Glance, with the
keep rules from Calendar.

## Storage

- `files/feeds.json`: folders, feeds, and article metadata (no content), plus the queue of state
  changes waiting to sync. Written atomically, as Tasks does.
- `files/articles/<id>.html`: each article's content, read when it's shown.
- `files/icons/<feed id>`: feed icons.
- Tessera backups include `feeds.json` and the settings. Content and icons are fetched again. The
  server's password is left out of all backups.

## First run

Welcome; "Where are your feeds?" (yarr server: address, then username and password if it asks;
or on this phone: import an OPML file now, or start empty); Notifications (optional); restore.

## Accessibility

Rows read as "Unread. Feed name. Title. 2 hours ago". Swipe actions are also in each row's
long-press menu (Mark read/unread, Star). Article text follows the system font scale times the
app's text size. Images have their alt text as a description. Touch targets are at least 48dp.

## Permissions

| Permission | Why |
|------------|-----|
| `INTERNET` | Fetching feeds (on this phone) or syncing with the yarr server the person adds; nothing else is contacted except the sites the feeds' articles link images from |
| `POST_NOTIFICATIONS` | New-article notifications, for feeds the person turns them on for |

## Engine (pure Kotlin, unit tested)

Feed parsing (RSS 2.0, RDF, Atom), feed discovery in HTML, OPML read/write, HTML to article
blocks, text snippets and first image, the yarr client (MockWebServer) and sync merge, retention,
and the article list filters and counts.

## Out of scope for v0.1

The Fever and Google Reader APIs (Miniflux, FreshRSS) behind the same sync interface. Full
article on this phone (a readability port). Podcasts (Tessera Podcasts does that). JSON Feed.
Swiping between articles. Per-widget settings.

## Testing against yarr

`YarrServerTest` runs against a real yarr when `YARR_URL` is set, and is skipped otherwise:

```
mkdir -p /tmp/yarr/www && echo 'tester:sesame-test' > /tmp/yarr/auth
# /tmp/yarr/www: index.html linking feed.xml (3 items) and atom.xml with <link rel="alternate">
nix shell nixpkgs#python3 -c python3 -m http.server 7071 --bind 127.0.0.1 --directory /tmp/yarr/www &
nix run nixpkgs#yarr -- -addr 127.0.0.1:7070 -auth-file /tmp/yarr/auth -db /tmp/yarr/yarr.db &
YARR_URL=http://127.0.0.1:7070 YARR_USER=tester YARR_PASSWORD=sesame-test YARR_FEED_SITE=http://127.0.0.1:7071 \
  ./gradlew :apps:feeds:testDebugUnitTest --tests '*YarrServerTest*'
```

On the emulator, `adb reverse tcp:7070 tcp:7070` (and 7071) and sign in to `http://127.0.0.1:7070`.
yarr's "Full article" refuses internal addresses, so it can only be tried with real feeds.
