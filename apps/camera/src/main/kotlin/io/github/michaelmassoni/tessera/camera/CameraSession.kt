package io.github.michaelmassoni.tessera.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.github.michaelmassoni.tessera.camera.engine.FileNames
import io.github.michaelmassoni.tessera.camera.engine.Flash
import io.github.michaelmassoni.tessera.camera.engine.Mode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.LocalDateTime

data class CameraState(
    val mode: Mode = Mode.Photo,
    val front: Boolean = false,
    val hasFront: Boolean = true,
    val flash: Flash = Flash.Off,
    val zoom: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val recording: Boolean = false,
    val recordingStart: Long = 0,
    val capturing: Boolean = false,
    val lastCapture: Uri? = null,
    val error: Boolean = false,
)

/**
 * Binds CameraX use cases to a lifecycle and runs captures. Photos and videos go to
 * DCIM/Camera through MediaStore, or to a caller's file/URI for capture intents.
 */
class CameraSession(
    private val context: Context,
) {
    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state.asStateFlow()

    private val surfaceFlow = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = surfaceFlow.asStateFlow()

    private val preview = Preview.Builder().build().apply { setSurfaceProvider { surfaceFlow.value = it } }
    private val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    private val videoCapture = VideoCapture.withOutput(Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HIGHEST)).build())

    private var camera: Camera? = null
    private var provider: ProcessCameraProvider? = null
    private var owner: LifecycleOwner? = null
    private var recording: Recording? = null
    private val executor = ContextCompat.getMainExecutor(context)

    fun bind(lifecycleOwner: LifecycleOwner) {
        owner = lifecycleOwner
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            provider = runCatching { future.get() }.getOrNull()
            rebind()
        }, executor)
    }

    private fun rebind() {
        val p = provider ?: return
        val o = owner ?: return
        val s = _state.value
        val selector = if (s.front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        p.unbindAll()
        camera =
            runCatching {
                val capture = if (s.mode == Mode.Photo) imageCapture else videoCapture
                p.bindToLifecycle(o, selector, preview, capture)
            }.getOrNull()
        val zoom = camera?.cameraInfo?.zoomState?.value
        _state.update {
            it.copy(
                error = camera == null,
                hasFront = runCatching { p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false),
                zoom = zoom?.zoomRatio ?: 1f,
                minZoom = zoom?.minZoomRatio ?: 1f,
                maxZoom = zoom?.maxZoomRatio ?: 1f,
            )
        }
        applyFlash()
    }

    fun setMode(mode: Mode) {
        if (_state.value.recording || mode == _state.value.mode) return
        _state.update { it.copy(mode = mode, flash = Flash.Off) }
        rebind()
    }

    fun switchCamera() {
        if (_state.value.recording) return
        _state.update { it.copy(front = !it.front) }
        rebind()
    }

    fun cycleFlash() {
        _state.update { it.copy(flash = it.flash.next(it.mode == Mode.Video)) }
        applyFlash()
    }

    private fun applyFlash() {
        val s = _state.value
        if (s.mode == Mode.Photo) {
            imageCapture.flashMode =
                when (s.flash) {
                    Flash.Off -> ImageCapture.FLASH_MODE_OFF
                    Flash.Auto -> ImageCapture.FLASH_MODE_AUTO
                    Flash.On -> ImageCapture.FLASH_MODE_ON
                }
            camera?.cameraControl?.enableTorch(false)
        } else {
            camera?.cameraControl?.enableTorch(s.flash == Flash.On)
        }
    }

    fun setZoom(ratio: Float) {
        val s = _state.value
        val clamped = ratio.coerceIn(s.minZoom, s.maxZoom)
        camera?.cameraControl?.setZoomRatio(clamped)
        _state.update { it.copy(zoom = clamped) }
    }

    fun focus(point: MeteringPoint) {
        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
    }

    /** Takes a photo into the gallery, or to [target] when another app asked for one. */
    fun takePhoto(
        target: Uri? = null,
        onSaved: (Uri?) -> Unit = {},
    ) {
        if (_state.value.capturing) return
        _state.update { it.copy(capturing = true) }
        val options =
            if (target != null) {
                ImageCapture.OutputFileOptions.Builder(context.contentResolver.openOutputStream(target) ?: return).build()
            } else {
                ImageCapture.OutputFileOptions
                    .Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaValues(Mode.Photo, "image/jpeg"))
                    .build()
            }
        imageCapture.takePicture(
            options,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = output.savedUri ?: target
                    _state.update { it.copy(capturing = false, lastCapture = if (target == null) uri else it.lastCapture) }
                    onSaved(uri)
                }

                override fun onError(exception: ImageCaptureException) {
                    _state.update { it.copy(capturing = false) }
                    onSaved(null)
                }
            },
        )
    }

    /** Takes a photo into [file] (for capture intents without an output URI). */
    fun takePhotoToFile(
        file: File,
        onSaved: (Boolean) -> Unit,
    ) {
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = onSaved(true)

                override fun onError(exception: ImageCaptureException) = onSaved(false)
            },
        )
    }

    /** Starts or stops recording. [onFinished] gets the saved video's URI (null on failure). */
    @SuppressLint("MissingPermission") // Audio is only enabled when RECORD_AUDIO is granted.
    fun toggleRecording(
        targetFile: File? = null,
        onFinished: (Uri?) -> Unit = {},
    ) {
        recording?.let {
            it.stop()
            recording = null
            return
        }
        val recorder = videoCapture.output
        val pending =
            if (targetFile != null) {
                recorder.prepareRecording(context, FileOutputOptions.Builder(targetFile).build())
            } else {
                recorder.prepareRecording(
                    context,
                    MediaStoreOutputOptions
                        .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                        .setContentValues(mediaValues(Mode.Video, "video/mp4"))
                        .build(),
                )
            }
        val withAudio =
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                pending.withAudioEnabled()
            } else {
                pending
            }
        recording =
            withAudio.start(executor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        _state.update { it.copy(recording = true, recordingStart = System.currentTimeMillis()) }
                    }

                    is VideoRecordEvent.Finalize -> {
                        val uri =
                            if (event.hasError()) {
                                null
                            } else {
                                event.outputResults.outputUri.takeUnless { it == Uri.EMPTY }
                                    ?: targetFile?.let(Uri::fromFile)
                            }
                        _state.update {
                            it.copy(
                                recording = false,
                                lastCapture =
                                    if (targetFile == null &&
                                        uri != null
                                    ) {
                                        uri
                                    } else {
                                        it.lastCapture
                                    },
                            )
                        }
                        onFinished(uri)
                    }
                }
            }
    }

    private fun mediaValues(
        mode: Mode,
        mime: String,
    ) = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, FileNames.name(mode, LocalDateTime.now()))
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
    }

    fun release() {
        recording?.stop()
        recording = null
        provider?.unbindAll()
    }
}
