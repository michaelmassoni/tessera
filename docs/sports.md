# Sports (draft: waiting on the data-source decision)

Scores, fixtures and standings for the teams and leagues you follow, with live games updating on
their own. Inspired by Apple Sports' focus (your teams first, scores at a glance, no news feed or
video), in Tessera's Material 3 Expressive style with dynamic colour.

## Goals

- Open the app and see your teams' games: live first, then today's, then upcoming and recent.
- Live scores refresh by themselves while a game is on, without opening the app (a Live Update
  notification for a followed team's game).
- No account, no ads, no betting odds, no news, no video.

## Data source (network) — to decide

Sports needs the network; this spec is the justification CLAUDE.md asks for. Options:

| Source | Coverage | Key | Live | Notes |
|---|---|---|---|---|
| **ESPN public JSON** (`site.api.espn.com/apis/site/v2/sports/...`) | NBA, NFL, MLB, NHL, AFL, NRL, NBL, EPL, A-League, cricket, F1 and more | None | Yes | Undocumented and unofficial; could change without notice. F-Droid would mark it NonFreeNet. |
| TheSportsDB | Broad | Free test key; paid for live | Paid only | Official API, community data. |
| League-specific (balldontlie, football-data.org…) | One sport each | Yes | Varies | Several keys to manage; no keyless option. |

**Proposed:** ESPN, behind a small `ScoreSource` interface so another source can replace it.
Only league and team ids are sent; nothing about the person.

## Following

- First run: pick leagues, then teams (searchable; logos come from the source).
- Followed teams and leagues are stored on the device and can be reordered.

## Screens

- **Scores** (home): followed teams' games grouped as Live, Today, Upcoming, Recent. A game card:
  both teams (logo, short name, record), the score or start time, the state ("Q3 4:12",
  "Half time", "Final", "Tomorrow 11:30 AM"), and the broadcaster when known. Live cards have a
  pulsing dot and update every 30 seconds while visible. Pull to refresh.
- **League** pages (tabs per followed league): the day's scoreboard with a date strip, and
  Standings (table or ladder: AFL percentage, NBA conference standings, EPL points/goal
  difference).
- **Game**: header with the score; box score or team stats; scoring summary or key plays when
  the source has them.
- **Team**: next game, recent results, fixture list, ladder position.
- **Settings**: followed leagues and teams, Live Update notifications on/off, spoiler-free mode
  (hide scores until tapped).

## Live Updates

A followed team's live game posts an ongoing Live Update notification (Android 16 progress-centric
style, like Clock's timers): score, period and clock, refreshed by a short-lived foreground
service only while that game is live. Ends with the final score. Needs `POST_NOTIFICATIONS`.

## Engine (pure Kotlin, unit tested)

- Source JSON → games, teams, standings; game state and period wording per sport.
- Grouping (live / today / upcoming / recent) in the device's time zone.
- Refresh schedule: how often while live, when the next game starts.

## Permissions

| Permission | Why |
|------------|-----|
| `INTERNET` | Scores, fixtures and standings. |
| `POST_NOTIFICATIONS` | Optional: Live Updates for followed teams' games. |
| `FOREGROUND_SERVICE` (`dataSync`) | Keeping a live game's notification current while it's on. |

## Out of scope for v0.1

Betting odds, news, video, fantasy, player profiles, widgets (next), Wear OS.
