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

- **Scores** (0.2, laid out like Apple Sports): tabs Yesterday (results from the last three days,
  older days under their date), Today (live games, then results, then "Later today") and Upcoming
  (a week ahead, by day). A game card: the league centred on top; away team left, home right
  (logo with the name under it, a star on the outer edge for followed teams); beside each logo the
  score, or before the game the team's record; between them the clock with a red dot, "Final"
  (the loser dimmed) or the start time. The broadcaster is only in the game sheet. Logos use
  ESPN's dark-background versions on a dark theme. Pull to refresh; while the
  app is open it refreshes every 30 s when something is live or about to start, every 5 min
  otherwise.
- **Leagues** (a screen from the top bar, a tab per followed league): Games (yesterday to three days ahead) and Standings
  (conferences or one ladder, with the league's columns: NBA/WNBA/NBL W, L, PCT, GB, STRK; AFL P,
  W, L, D, %, PTS; NRL P, W, L, D, PD, PTS; MLB W, L, PCT, GB, STRK; NFL W, L, T, PCT, STRK).
  Followed teams are highlighted and starred.
- **Game** (a sheet): the score card, the line score by period, and each team's leaders. The
  sheet opens once the details are in (a loading indicator shows over the list meanwhile; Back
  cancels), so it comes up at its final height instead of growing and jumping.
- **Settings**: Live Updates on or off.

## Live Updates

While a followed team plays, its score is in an ongoing notification, promoted to a Live Update on
Android 16 QPR1+ (0.2.1):

- Title: each team beside its own score, away first as in the app: "Rockets 115 · Nuggets 117".
- Text: the clock and who leads: "3:20 - 4th · Nuggets by 2" ("Tied" when level).
- Status bar chip: your team and its margin, "DEN +2" / "DEN −3" / "DEN =".
- Android 16: a progress bar of the game, a segment per quarter, half or inning, between the
  away and home logos (the dark-background logos in dark mode). Older Android: your team's logo.
- When it ends, a dismissible "Final · Nuggets win" stays for up to three hours.

Refreshing: a WorkManager job finds what's live (the full followed-games fetch), then keeps running
while anything is: every 15 s it asks ESPN's current scoreboard for that league only (one small
request; ESPN caches scoreboards for up to about 10 s, so faster wouldn't be fresher), and right
away when the screen comes on. Each run watches for 9 minutes and hands over to the next
(WorkManager stops a run at 10). Between games it sleeps until a minute before the next one.
It's not a foreground service: Android doesn't let an app start one from the background for
this, so with the screen off and the phone idle Android may hold checks back; turning the screen
on lets them run within a few seconds. While the app is open, live games refresh every 15 s too. The
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
