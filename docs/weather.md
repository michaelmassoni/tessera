# Weather

A forecast app in the style of current Android: the conditions now, the next 24 hours, the next
10 days and the details (UV, wind, humidity, sunrise…), for your location and the places you
save. Material 3 Expressive with dynamic colour, like the rest of Tessera.

## Goals

- Answer "what's it like now, later today and this week" at a glance.
- Work without Google Play Services and without an account or API key.
- Keep working offline with the last forecast, saying how old it is.

## Data source (network)

Weather needs the network; this spec is the justification CLAUDE.md asks for.

- **[Open-Meteo](https://open-meteo.com)**: free, open-source forecast API (national weather
  services' models, including the Bureau of Meteorology's ACCESS-G), no key, no account, no
  tracking. Data is CC BY 4.0, so the app credits "Weather data by Open-Meteo.com".
  - Forecast: `api.open-meteo.com/v1/forecast` with `current`, `hourly`, `daily`,
    `timezone=auto`, `timeformat=unixtime`, `forecast_days=10`, always in metric (converted on
    the device, so a cached forecast survives a change of units).
  - Place search: `geocoding-api.open-meteo.com/v1/search`.
- Only coordinates rounded to 2 decimals (about 1 km) are sent. Nothing else leaves the phone.
- F-Droid: no anti-features (Open-Meteo is open source and self-hostable).

## Places

- **Current location** (optional): `ACCESS_COARSE_LOCATION` only, read with `LocationManager`
  (the platform's fused provider, else network, else the last known fix). No Play Services. Its
  name comes from the platform `Geocoder` when there is one, else "Current location".
- **Saved places**: search by name (Open-Meteo geocoding), shown as "Melbourne, Victoria,
  Australia". Add, delete, and move up or down (from the row's menu).
- Pages: swipe between places (current location first). The last page viewed is reopened.

## Screens

- **Forecast page** (one per place), a scrolling column:
  - Hero: place name, a large temperature, condition in words with its icon, "H 24° L 13°",
    "Feels like 21°".
  - Next 24 hours: a horizontal row of hour chips: time ("Now" first), icon, temperature,
    chance of rain when 20% or more.
  - 10 days: one row per day: day name ("Today", "Tue"), icon, rain chance, low, a range bar
    placed on the 10 days' scale with today's current temperature marked, high.
  - Details: a grid of tiles: UV index (with its band: Low… Extreme), wind (speed, gusts,
    direction), humidity (and dew point), feels like, precipitation today, pressure,
    visibility, sunrise and sunset.
  - "Updated 10 min ago" and the Open-Meteo credit at the bottom. Pull down to refresh.
- **Places**: a list with each place's current temperature and condition; search field to add;
  each row's menu moves it up or down, or deletes it.
- **Settings** (shared Tessera settings screen plus): units for temperature (°C/°F), wind
  (km/h, m/s, mph, knots) and precipitation (mm, in), defaulting from the locale; use current
  location on/off.

## Behaviour

- A forecast is cached per place (JSON in app storage). Opening the app shows the cache at once
  and refreshes it if it's more than 15 minutes old.
- Offline or failing: the cached forecast with "Updated 3 h ago", and a snackbar with Retry.
- Night: condition icons switch to night variants after sunset (`is_day`).
- Conditions come from WMO weather codes: clear, mostly clear, partly cloudy, overcast, fog,
  drizzle, rain (light, moderate, heavy), freezing rain, snow, showers, snow showers,
  thunderstorm, thunderstorm with hail.

## Engine (pure Kotlin, unit tested)

- WMO code → condition (and day/night icon), condition words.
- Open-Meteo JSON → `Forecast` (current, hourly from now, daily).
- Unit conversions and rounding; UV bands; wind direction to compass point.
- The 10-day range bar: each day's low/high as fractions of the period's span.
- Staleness ("Updated n min ago", when to refresh).
- Place search results and the saved places list.

## Accessibility

- The hero reads as one sentence: "Melbourne, 18 degrees, partly cloudy, high 24, low 13".
- Hour chips and day rows read their time, condition and temperatures.
- Icons that carry meaning have descriptions; the range bar is decorative (its numbers are
  read).
- Font scaling up to 200%: chips and tiles grow; the hero wraps.

## Permissions

| Permission | Why |
|------------|-----|
| `INTERNET` | The forecast and place search (Open-Meteo). |
| `ACCESS_COARSE_LOCATION` | Optional: the forecast for where you are. Asked only when you turn it on. |

## Out of scope for v0.1

- Widgets (next), notifications and severe-weather warnings, radar maps, air quality and
  pollen, hourly detail per day, Wear OS.
