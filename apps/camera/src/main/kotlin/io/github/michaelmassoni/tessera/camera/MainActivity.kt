package io.github.michaelmassoni.tessera.camera

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michaelmassoni.tessera.camera.engine.FileNames
import io.github.michaelmassoni.tessera.camera.engine.Mode
import io.github.michaelmassoni.tessera.camera.ui.CameraActions
import io.github.michaelmassoni.tessera.camera.ui.CameraScreen
import io.github.michaelmassoni.tessera.camera.ui.PermissionScreen
import io.github.michaelmassoni.tessera.camera.ui.ReviewScreen
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.core.settings.TesseraSettings
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import java.io.File
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
    private val session by lazy { CameraSession(this) }
    private var granted by mutableStateOf(false)
    private var denied by mutableStateOf(false)

    /** Set when another app asked for a capture (IMAGE_CAPTURE / VIDEO_CAPTURE). */
    private var captureFor: Mode? = null
    private var output: Uri? = null
    private var review by mutableStateOf<File?>(null)

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            denied = !ok
            if (ok) session.bind(this)
        }
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        granted = has(Manifest.permission.CAMERA)
        val actions = Actions()
        setTesseraContent {
            val settings by TesseraSettings.get(this).shared.collectAsStateWithLifecycle()
            // The camera is always shown on black, whatever the app theme.
            TesseraTheme(darkTheme = true, dynamicColor = settings.theme.dynamicColor, pureBlack = true) {
                val reviewing = review
                when {
                    !granted -> {
                        PermissionScreen(
                            denied = denied,
                            onGrant = { cameraPermission.launch(Manifest.permission.CAMERA) },
                            onSettings = {
                                startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()),
                                )
                            },
                        )
                    }

                    reviewing != null -> {
                        ReviewScreen(reviewing, captureFor == Mode.Video, onRetake = {
                            reviewing.delete()
                            review = null
                        }, onAccept = { deliver(reviewing) })
                    }

                    else -> {
                        val state by session.state.collectAsStateWithLifecycle()
                        val surface by session.surfaceRequest.collectAsStateWithLifecycle()
                        CameraScreen(state, surface, actions, captureIntent = captureFor != null)
                    }
                }
            }
        }
        // Light system bar icons over the always-dark camera UI.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (granted) session.bind(this) else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        if (!granted && has(Manifest.permission.CAMERA)) {
            granted = true
            session.bind(this)
        }
    }

    override fun onDestroy() {
        session.release()
        super.onDestroy()
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        // Volume keys work as a shutter, like the system camera.
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) && granted && review == null) {
            if (event.repeatCount == 0) shutter()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun has(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            MediaStore.ACTION_IMAGE_CAPTURE -> {
                captureFor = Mode.Photo
            }

            MediaStore.ACTION_VIDEO_CAPTURE -> {
                captureFor = Mode.Video
                session.setMode(Mode.Video)
            }

            MediaStore.INTENT_ACTION_VIDEO_CAMERA -> {
                session.setMode(Mode.Video)
            }
        }
        if (captureFor != null) {
            @Suppress("DEPRECATION")
            output = intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT) ?: intent.clipData
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.uri
        }
    }

    private fun shutter() {
        val mode = session.state.value.mode
        val forApp = captureFor
        when {
            forApp != null && mode == Mode.Photo -> {
                val file = File(cacheDir, "capture.jpg")
                session.takePhotoToFile(file) { ok -> if (ok) review = file }
            }

            forApp != null -> {
                val file = File(cacheDir, "capture.mp4")
                session.toggleRecording(file) { uri -> if (uri != null) review = file }
            }

            mode == Mode.Photo -> {
                session.takePhoto()
            }

            else -> {
                session.toggleRecording()
            }
        }
    }

    /** Hands the reviewed capture to the app that asked for it. */
    private fun deliver(file: File) {
        val target = output
        val result = Intent()
        try {
            if (target != null) {
                contentResolver.openOutputStream(target, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } }
                result.data = target
            } else if (captureFor == Mode.Photo) {
                // No output URI: the documented fallback is a small bitmap in "data".
                val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 8 })
                result.putExtra("data", bitmap)
            } else {
                // A video with no output URI is saved to the gallery and its URI returned.
                val values =
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, FileNames.name(Mode.Video, LocalDateTime.now()))
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
                    }
                val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let { contentResolver.openOutputStream(it)?.use { out -> file.inputStream().use { input -> input.copyTo(out) } } }
                result.data = uri
                result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            setResult(Activity.RESULT_OK, result)
        } catch (_: Exception) {
            setResult(Activity.RESULT_CANCELED)
        } finally {
            file.delete()
        }
        finish()
    }

    private inner class Actions : CameraActions {
        override fun shutter() = this@MainActivity.shutter()

        override fun setMode(mode: Mode) {
            if (mode == Mode.Video && !has(Manifest.permission.RECORD_AUDIO)) audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            session.setMode(mode)
        }

        override fun switchCamera() = session.switchCamera()

        override fun cycleFlash() = session.cycleFlash()

        override fun zoom(ratio: Float) = session.setZoom(ratio)

        override fun focus(
            x: Float,
            y: Float,
            surfaceWidth: Int,
            surfaceHeight: Int,
        ) = session.focus(SurfaceOrientedMeteringPointFactory(surfaceWidth.toFloat(), surfaceHeight.toFloat()).createPoint(x, y))

        override fun openLast(uri: Uri) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this@MainActivity, R.string.no_app, Toast.LENGTH_SHORT).show()
            }
        }

        override fun openSettings() = startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
    }
}
