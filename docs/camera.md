# Camera: spec

Package `io.github.michaelmassoni.tessera.camera`. Status: v0.1, first implementation.

## Goals

A dependable point-and-shoot camera on CameraX (Jetpack), with no Google services. It opens
fast, focuses where you tap, and saves to the gallery like the system camera. It also handles
the standard capture intents, so other apps can use it to take a photo or video.

## Permissions

| Permission | Why |
|------------|-----|
| `CAMERA` | The viewfinder and capture |
| `RECORD_AUDIO` | Sound in videos; asked for the first time you switch to Video, and videos record silently without it |

No location permission: photos aren't geotagged in v0.1.

## Features

- **Modes:** Photo and Video, switched with a segmented button above the shutter.
- **Viewfinder:** full screen, edge to edge, with the controls over it.
  - **Focus:** tap to focus and meter, shown with a focus ring.
  - **Zoom:** pinch to zoom, and a zoom chip (1×, 2×, ...) when the lens supports it.
- **Cameras:** switch between front and back.
- **Flash (photo):** off, auto or on. In video the same button turns the torch on or off.
- **Capture:**
  - The shutter takes a photo, with a brief flash animation for feedback.
  - In Video it starts and stops recording, with an elapsed-time pill while recording.
  - Volume keys also take the photo.
- **Saving:** photos and videos go to `DCIM/Camera` through MediaStore, as `IMG_yyyyMMdd_HHmmss.jpg`
  and `VID_yyyyMMdd_HHmmss.mp4`, so they appear in the gallery straight away.
- **Last shot:** a round thumbnail of the latest capture opens it in the gallery app
  (`ACTION_VIEW`).

## Other apps

- **Capture for an app:** `MediaStore.ACTION_IMAGE_CAPTURE` and `ACTION_VIDEO_CAPTURE` take one
  shot and return it.
  - **Destination:** to the caller's `EXTRA_OUTPUT` URI, or for images without one, a small
    bitmap in the `data` extra.
  - **Review:** a review screen offers Retake or OK (✓) first.
- **Open the camera:** `STILL_IMAGE_CAMERA` (for example from a double press of the power
  button) and `VIDEO_CAMERA` open the camera in that mode.

## Engine (pure Kotlin, unit tested)

- `FileNames`: timestamped names that never clash within the same second (a suffix is added).
- `FlashMode` cycling.
- `ZoomSteps`: which zoom chips to show for a lens's zoom range.

## Accessibility

- **Labels:** every control has one. The shutter says "Take photo", "Start recording" or "Stop
  recording".
- **Recording:** the elapsed-time pill is a live region.

## Out of scope for v0.1

- HDR, night and portrait (CameraX extensions).
- Timer.
- Grid.
- Aspect-ratio choices (4:3 photos, 16:9 video).
- Location tags.
- QR scanning.
- Pro controls.
- RAW.
