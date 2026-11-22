# Recorder: spec

Package `io.github.michaelmassoni.tessera.recorder`. Status: v0.1 (2026-11-23).

## Goals

A voice recorder in the spirit of Pixel Recorder: one big button to start, a live waveform while
it records, and a list of recordings to play back, rename, share and delete. Recording carries on
with the screen off or in another app. Fully offline: no network permission.

Out of scope for v0.1: transcription (it would need a large on-device speech model, such as
whisper.cpp; possible later), editing (trim, cut), noise reduction, recording calls (Android
doesn't allow it), widgets.

## Where recordings live

In the shared `Recordings/Tessera` folder (MediaStore `Audio`, `RELATIVE_PATH`), so they show up in
Files, other players, and whatever backs up or syncs the phone (Syncthing to a NAS, say). The app
needs no storage permission to create, rename and delete its own recordings.

- Format: AAC in an `.m4a` file, which every phone, computer and player opens.
- Quality (Settings): **Standard** (mono, 44.1 kHz, 128 kbit/s, about 1 MB a minute) or **High**
  (stereo, 48 kHz, 256 kbit/s).
- Name: "Recording 23 Nov 10.42" (the locale's date and time; the colon a file name can't hold
  becomes a dot), renamable. The file name is the title; a clash gets " (2)".
- While recording, the file is pending (`IS_PENDING`), so other apps don't see a half-written
  file. If the app dies mid-recording, Android's media server still closes the
  file properly, and the next start publishes it (without a waveform); one that won't open is
  deleted and the person told.
- Delete moves a recording to the system trash (`IS_TRASHED`, 30 days) with Undo.
- Recordings made before a reinstall belong to the old install as far as Android is concerned;
  v0.1 doesn't list them (they're still in the folder, playable from Files).
- Tessera backups don't include recordings (they're ordinary files in a shared folder already),
  only settings.

The waveform of each recording is kept beside it in the app's own storage (`waves/<id>.bin`, one
byte per 50 ms, about 70 KB an hour), so the player draws it without decoding the audio. A
recording without one (deleted app data) shows a plain progress bar.

## Screens

- **Recordings (home):** the list, newest first, in a large title layout: title, date and time,
  duration. Search by title. Tap to play; long-press to select (share, delete). Empty state: "Tap
  the button to record". A large **Record** FAB.
- **Recording:** full screen while recording: elapsed time (large), scrolling live waveform,
  buttons **Pause/Resume**, **Stop** (saves) and **Discard** (asks first). Back leaves it recording
  (the notification brings it back); the home list shows a "Recording 3:12" bar to return.
- **Player:** title (tap to rename), date, the recording's waveform with the played part
  coloured, drag or tap the waveform to seek, play/pause, back 10 s / forward 10 s, speed (0.5×,
  0.75×, 1×, 1.25×, 1.5×, 2×), share, delete. Playback stops when leaving the player.
- **Settings:** quality; the shared Tessera settings (theme, icon, backup).

## Recording in the background

A foreground service of type `microphone` holds the recorder. It's started only from the app's
own activity (Android 14+ doesn't allow starting a microphone service from the background).

- Notification (ongoing, no sound or vibration, in the main section so its buttons show):
  "Recording · 3:12", actions **Pause/Resume** and **Stop**. Tap to open the Recording screen. On
  the lock screen too.
- **Quick Settings tile** "Record": opens the app recording (a tile may not start a microphone
  service itself), or stops the recording that's running.
- **App shortcut** (long-press the icon): "New recording".
- Phone calls: Android silences the microphone for other apps during a call. The recording carries
  on (that stretch is silent) and the notification says a call has the microphone; pausing
  itself could leave it paused if Android never says the microphone is back.
- Low storage: stops and saves when less than 50 MB is free, and says why.

## First run

Welcome; Microphone (required, the app can't work without it); Notifications (for the
recording notification; optional); restore.

## Permissions

| Permission | Why |
|------------|-----|
| `RECORD_AUDIO` | Recording |
| `POST_NOTIFICATIONS` | The notification while recording (Android requires one for the service) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` | Keep recording with the screen off or in another app |

No storage permission (MediaStore lets the app manage its own files), no network.

## Engine (pure Kotlin, unit tested)

Default titles and file names (with clash numbering), duration formatting, the waveform's level
scale (MediaRecorder amplitude to 0..255, logarithmic so speech fills the height) and its
downsampling to the width shown, the sidecar file format, and the recording's timing across
pauses.

## Accessibility

The record button announces "Record" / "Stop recording"; elapsed time is a live region every 10 s,
not every second. The waveform has a description ("Waveform, 3 minutes 12 seconds, at 1:05") and
seeking works with the play controls for those who can't drag. 48 dp targets; text to 200%.
