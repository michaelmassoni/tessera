# Sports

Scores, fixtures and standings for the teams and leagues you follow, with your team's live game
in a Live Update. Inspired by Apple Sports' focus (your teams first, scores at a glance; no news,
video or betting), in Tessera's Material 3 Expressive style with dynamic colour.

## Leagues

NBA, WNBA, NBL, AFL, NRL, MLB and NFL. The maintainer also asked for the WNBL, but ESPN doesn't
carry it (checked 2026-11-21: none of ESPN's 15 basketball leagues is the WNBL); it needs another
source, behind the same `ScoreSource` interface.

## Data source (network)

Sports needs the network; this spec is the justification CLAUDE.md asks for.

- **ESPN's public JSON** (`site.api.espn.com`): keyless, live scores for every league above. It's
  undocumented and unofficial, so it may change without notice, and F-Droid would mark the app
  with the NonFreeNet anti-feature. Everything goes through `ScoreSource`, so another source can
  replace it. The maintainer chose it (2026-11-21).
- Only league and team ids and dates are sent. Team logos come from ESPN's image server.
- Requests: a scoreboard per followed league for yesterday, today and tomorrow (ESPN's days are
  US time for US leagues, and it no longer accepts date ranges), and a season schedule per
  followed team. Where both have a game, the scoreboard's is fresher.
- The app credits "Scores from ESPN".

## Following

- First run (and Edit teams): switch leagues on, then pick teams (chips with logos). With no team
  picked, a league shows all its games around today.
- Follows are stored on the device (`follows.json`).

## Screens

- **Scores**: followed games as Live, Today, Upcoming (a week ahead) and Recent (three days
  back). A game card: league and broadcaster; away team left, home right (logo, name, record);
  between them the start time and day, or the score with "Final" (the loser dimmed) or, live, a
  red dot with the period and clock. Followed teams' names are bold. Pull to refresh; while the
  app is open it refreshes every 30 s when something is live or about to start, every 5 min
  otherwise.
- **League tabs** (one per followed league): Games (yesterday to three days ahead) and Standings
  (conferences or one ladder, with the league's columns: NBA/WNBA/NBL W, L, PCT, GB, STRK; AFL P,
  W, L, D, %, PTS; NRL P, W, L, D, PD, PTS; MLB W, L, PCT, GB, STRK; NFL W, L, T, PCT, STRK).
  Followed teams are highlighted.
- **Game** (a sheet): the score card, the line score by period, and each team's leaders.
- **Settings**: Live Updates on or off.

## Live Updates

While a followed team plays, its score is in an ongoing notification (asking to be promoted to a
Live Update on Android 16 QPR1+): "HOU 115 – 117 DEN", "3:20 - 4th". When it ends, a dismissible
final score stays for up to three hours.

A WorkManager job checks about every minute while a followed game is live, and otherwise sleeps
until a minute before the next one starts. It's not a foreground service: Android doesn't let an
app start one from the background for this, so in deep sleep updates can come less often. The
notification permission is asked when teams are picked.

## Engine (pure Kotlin, unit tested)

- ESPN JSON → games, teams, standings and game detail, read loosely (fields can be missing, and
  a score is a string on scoreboards but an object on schedules).
- Sections (live, today, upcoming, recent) in the device's time zone; which games are followed;
  the refresh schedule.

## Permissions

| Permission | Why |
|------------|-----|
| `INTERNET` | Scores, fixtures, standings and logos. |
| `POST_NOTIFICATIONS` | Live Updates for followed teams' games. Asked when teams are picked. |
| `POST_PROMOTED_NOTIFICATIONS` | Lets the live score show as a Live Update. |

## Out of scope for v0.1

WNBL (needs a source), team pages, spoiler-free mode, betting odds, news, video, fantasy, player
profiles, widgets, Wear OS.
