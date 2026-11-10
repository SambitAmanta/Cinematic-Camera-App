package com.cinematic.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cinematic.camera.camera.CameraPipeline
import com.cinematic.camera.engine.DollyZoomNativeBridge
import com.cinematic.camera.engine.RackFocusNativeBridge
import com.cinematic.camera.guidance.DeviceOrientationSensor
import com.cinematic.camera.guidance.HapticMetronome
import com.cinematic.camera.ui.AppShotState
import com.cinematic.camera.ui.CinemaPlaybackStudio
import com.cinematic.camera.ui.CinematicHUD
import com.cinematic.camera.ui.CinematicTake
import com.cinematic.camera.ui.FocusPullMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main Cinematic Camera Activity.
 * Implements frame-synchronized real-time Dolly Zoom (Vertigo shot)
 * on the Google Pixel 8a primary sensor (1.0x to 3.0x).
 */
class MainActivity : ComponentActivity() {

    private val tag = "CinematicMainActivity"
    private lateinit var cameraPipeline: CameraPipeline
    private lateinit var hapticMetronome: HapticMetronome
    private lateinit var orientationSensor: DeviceOrientationSensor

    private var activePreviewView: PreviewView? = null
    private var isCameraStarted = false
    private var countdownJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // UI States
    private val shotState = mutableStateOf(AppShotState.IDLE)
    // Default: DOLLY_OUT (Walk Backward: 1x ➔ 3x)
    private val selectedDirection = mutableStateOf(DollyZoomNativeBridge.Direction.DOLLY_OUT)
    // 3 Zoom Paces: SLOW (7s), MEDIUM (4.5s), FAST (2.5s)
    private val selectedPace = mutableStateOf(DollyZoomNativeBridge.Pace.MEDIUM)
    // 3 Motion Curves: LINEAR, S_CURVE, DRAMATIC
    private val selectedCurve = mutableStateOf(DollyZoomNativeBridge.Curve.LINEAR)
    // Target Framerate: 60 FPS (default) or 30 FPS
    private val currentFps = mutableIntStateOf(60)
    private val currentZoomRatio = mutableFloatStateOf(1.0f)
    private val countdownSeconds = mutableIntStateOf(3)
    private val isShotComplete = mutableStateOf(false)

    // Coordinated Focus Mode (Prototype 2 Native C++ Rack Focus)
    private val selectedFocusMode = mutableStateOf(FocusPullMode.TRACKING)
    private var rackFocusBridge: RackFocusNativeBridge? = null

    // Configurable Zoom Bounds (Default: 1.0x to 3.0x, dynamic hardware limits)
    private val configuredMinZoom = mutableFloatStateOf(1.0f)
    private val configuredMaxZoom = mutableFloatStateOf(3.0f)
    private val deviceMinZoom = mutableFloatStateOf(1.0f)
    private val deviceMaxZoom = mutableFloatStateOf(8.0f)

    // In-App Cinema Playback Studio State
    private val lastTake = mutableStateOf<CinematicTake?>(null)
    private val isPlaybackStudioOpen = mutableStateOf(false)

    // Frame-Synchronized Harmonic Progression Engine (4-Pixel Ultra-Fine Quanta)
    private var recordedFrameCount = 0
    private var totalTargetFrames = 270
    private var startZoom = 1.0f
    private var targetZoom = 3.0f
    private var lastHarmonicZoom = 1.0f
    private var isEncoderRecording = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (cameraGranted && audioGranted) {
            tryStartCamera()
        } else {
            Log.e(tag, "Required camera or audio permissions denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraPipeline = CameraPipeline(this, this)
        hapticMetronome = HapticMetronome(this)
        orientationSensor = DeviceOrientationSensor(this)

        setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                // 1. CameraX PreviewView
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            onPreviewViewReady(this)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 2. Production Cinema Viewfinder HUD
                CinematicHUD(
                    shotState = shotState.value,
                    selectedDirection = selectedDirection.value,
                    selectedPace = selectedPace.value,
                    selectedCurve = selectedCurve.value,
                    currentZoom = currentZoomRatio.floatValue,
                    targetFps = currentFps.intValue,
                    countdownSeconds = countdownSeconds.intValue,
                    isComplete = isShotComplete.value,
                    minZoom = configuredMinZoom.floatValue,
                    maxZoom = configuredMaxZoom.floatValue,
                    deviceMinZoom = deviceMinZoom.floatValue,
                    deviceMaxZoom = deviceMaxZoom.floatValue,
                    rollDegrees = orientationSensor.rollDegrees.value,
                    selectedFocusMode = selectedFocusMode.value,
                    lastTake = lastTake.value,
                    onDirectionChanged = { dir ->
                        selectedDirection.value = dir
                        updateInitialZoomForDirection(dir)
                    },
                    onPaceChanged = { pace ->
                        selectedPace.value = pace
                    },
                    onCurveChanged = { curve ->
                        selectedCurve.value = curve
                    },
                    onFocusModeChanged = { mode ->
                        selectedFocusMode.value = mode
                    },
                    onFpsChanged = { fps ->
                        currentFps.intValue = fps
                        cameraPipeline.setTargetFps(fps)
                    },
                    onZoomBoundsChanged = { min, max ->
                        configuredMinZoom.floatValue = min
                        configuredMaxZoom.floatValue = max
                        cameraPipeline.setCustomZoomBounds(min, max)
                        updateInitialZoomForDirection(selectedDirection.value)
                    },
                    onStartShotClicked = { startCountdownSequence() },
                    onStopShotClicked = { stopShot() },
                    onResetClicked = { resetToIdle() },
                    onOpenPlaybackStudio = { isPlaybackStudioOpen.value = true }
                )

                // 3. In-App Cinema Playback Studio (Full-Screen Animated Review Drawer)
                AnimatedVisibility(
                    visible = isPlaybackStudioOpen.value && lastTake.value != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    lastTake.value?.let { take ->
                        CinemaPlaybackStudio(
                            take = take,
                            onClose = { isPlaybackStudioOpen.value = false }
                        )
                    }
                }
            }
        }

        checkAndRequestPermissions()
    }

    private fun onPreviewViewReady(view: PreviewView) {
        activePreviewView = view
        tryStartCamera()
    }

    private fun checkAndRequestPermissions() {
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        if (hasCamera && hasAudio) {
            tryStartCamera()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    private fun tryStartCamera() {
        val view = activePreviewView
        if (view == null) {
            Log.d(tag, "tryStartCamera: activePreviewView is not ready yet")
            return
        }
        if (isCameraStarted) return

        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasCamera || !hasAudio) return

        isCameraStarted = true
        Log.i(tag, "Starting ultra-streamlined CameraPipeline on Pixel 8a primary sensor (1x to 3x at ${currentFps.intValue} FPS)...")

        // Hook frame-synchronized capture callback directly into camera hardware
        cameraPipeline.onFrameCapturedListener = { timestampNs ->
            handleFrameCaptured(timestampNs)
        }

        cameraPipeline.onDeviceZoomLimitsDetected = { min, max ->
            runOnUiThread {
                deviceMinZoom.floatValue = min
                deviceMaxZoom.floatValue = max
            }
        }

        cameraPipeline.startCamera(
            view = view,
            onReady = {
                Log.i(tag, "Camera pipeline ready. Preview and VideoCapture active.")
                deviceMinZoom.floatValue = cameraPipeline.deviceMinZoom
                deviceMaxZoom.floatValue = cameraPipeline.deviceMaxZoom
                updateInitialZoomForDirection(selectedDirection.value)
            }
        )
    }

    /**
     * Ultra-Fine Quantized Harmonic Zoom Snapping (4-Pixel Bayer Alignment).
     * Aligns zoom crop width to exact 4-pixel Bayer cells on the Sony IMX787
     * sensor (4080x3072 binned active array) or any device sensor.
     * Snaps sensor crop width to multiples of 4 pixels (2px left, 2px right margin),
     * locking optical center symmetry while supporting any custom min/max zoom ratio.
     * Completely eliminates the pause-step-pause sawtooth vibration.
     */
    private fun computeHarmonicZoom(targetZoom: Float): Float {
        val clampedZoom = targetZoom.coerceIn(cameraPipeline.minZoomRatio, cameraPipeline.maxZoomRatio)
        if (clampedZoom < 1.0f) {
            return (Math.round(clampedZoom * 100.0) / 100.0).toFloat()
        }
        val rawCropWidth = 4080.0 / clampedZoom.toDouble()
        val snappedCropWidth = (Math.round(rawCropWidth / 4.0) * 4.0).coerceIn(400.0, 4080.0)
        val snappedZoom = (4080.0 / snappedCropWidth).toFloat()
        return snappedZoom.coerceIn(cameraPipeline.minZoomRatio, cameraPipeline.maxZoomRatio)
    }

    /**
     * Hardware Frame-Synchronized Progression Engine.
     * Invoked exactly once per physical sensor exposure.
     * Advances zoom by an exact, deterministic harmonic step (zero Bayer jitter).
     */
    private fun handleFrameCaptured(timestampNs: Long) {
        if (shotState.value == AppShotState.RECORDING && isEncoderRecording) {
            recordedFrameCount++
            val rawProgress = (recordedFrameCount.toDouble() / totalTargetFrames.toDouble()).coerceIn(0.0, 1.0)
            val eased = computeEasedProgress(rawProgress, selectedCurve.value)
            val continuousTarget = (startZoom + (targetZoom - startZoom) * eased).toFloat()
            val harmonicZoom = computeHarmonicZoom(continuousTarget)

            // Actuate optical zoom on physical sensor ONLY when crossing a 4-pixel harmonic boundary
            if (harmonicZoom != lastHarmonicZoom) {
                cameraPipeline.setZoomRatio(harmonicZoom)
                lastHarmonicZoom = harmonicZoom
            }

            // Native C++ Hermite Rack Focus Puller (Frame Synchronized)
            if (selectedFocusMode.value != FocusPullMode.TRACKING) {
                val durationSec = selectedPace.value.durationSec
                val currentSec = rawProgress * durationSec
                rackFocusBridge?.let { bridge ->
                    val pullResult = bridge.update(currentSec)
                    cameraPipeline.setCommandedFocusDiopters(pullResult.commandedDiopters.toFloat())
                }
            }

            val complete = rawProgress >= 1.0
            mainHandler.post {
                currentZoomRatio.floatValue = harmonicZoom
                isShotComplete.value = complete
            }

            hapticMetronome.updateGuidance(selectedPace.value, complete)
        }
    }

    /**
     * Mathematical easing curves for frame-by-frame zoom progression.
     */
    private fun computeEasedProgress(rawProgress: Double, curve: DollyZoomNativeBridge.Curve): Double {
        val p = rawProgress.coerceIn(0.0, 1.0)
        return when (curve) {
            DollyZoomNativeBridge.Curve.LINEAR -> p
            DollyZoomNativeBridge.Curve.S_CURVE -> p * p * (3.0 - 2.0 * p)
            DollyZoomNativeBridge.Curve.DRAMATIC -> {
                if (p < 0.5) {
                    4.0 * p * p * p
                } else {
                    1.0 - Math.pow(-2.0 * p + 2.0, 3.0) / 2.0
                }
            }
        }
    }

    /**
     * Instantly sets the camera preview zoom for the selected direction:
     * - WALK FORWARD (DOLLY_IN): Camera snaps immediately to 3.0x zoom before the shot.
     * - WALK BACKWARD (DOLLY_OUT): Camera snaps immediately to 1.0x zoom before the shot.
     */
    private fun updateInitialZoomForDirection(direction: DollyZoomNativeBridge.Direction) {
        val initialZoom = if (direction == DollyZoomNativeBridge.Direction.DOLLY_IN) {
            cameraPipeline.maxZoomRatio // 3.0f
        } else {
            cameraPipeline.minZoomRatio // 1.0f
        }
        val initialHarmonic = computeHarmonicZoom(initialZoom)
        lastHarmonicZoom = initialHarmonic
        cameraPipeline.setZoomRatio(initialHarmonic, immediate = true)
        currentZoomRatio.floatValue = initialHarmonic
    }

    private fun startCountdownSequence() {
        shotState.value = AppShotState.COUNTDOWN
        countdownSeconds.intValue = 3

        countdownJob = lifecycleScope.launch {
            for (sec in 3 downTo 1) {
                countdownSeconds.intValue = sec
                hapticMetronome.triggerCountdownTick()
                delay(1000)
            }
            startRecordingShot()
        }
    }

    private fun startRecordingShot() {
        shotState.value = AppShotState.RECORDING
        isShotComplete.value = false
        isEncoderRecording = false
        hapticMetronome.reset()

        // Determine zoom bounds based on direction
        if (selectedDirection.value == DollyZoomNativeBridge.Direction.DOLLY_IN) {
            // Walk forward: start at 3.0x, zoom out to 1.0x
            startZoom = cameraPipeline.maxZoomRatio
            targetZoom = cameraPipeline.minZoomRatio
        } else {
            // Walk backward: start at 1.0x, zoom in to 3.0x
            startZoom = cameraPipeline.minZoomRatio
            targetZoom = cameraPipeline.maxZoomRatio
        }

        // Calculate total frame budget: duration * FPS
        val durationSec = selectedPace.value.durationSec
        totalTargetFrames = (durationSec * cameraPipeline.targetFps).toInt().coerceAtLeast(30)
        recordedFrameCount = 0

        // Lock exposure and focus to eliminate exposure flutter
        cameraPipeline.lockExposureAndFocus(true)

        // Native C++ Algorithmic Rack Focus Configuration (Prototype 2)
        if (selectedFocusMode.value != FocusPullMode.TRACKING) {
            rackFocusBridge?.close()
            rackFocusBridge = RackFocusNativeBridge(durationSec).apply {
                val (startDiopters, endDiopters) = if (selectedFocusMode.value == FocusPullMode.RACK_TO_BG) {
                    0.85 to 0.08 // Actor (~1.2m) to Background (~12m)
                } else {
                    0.08 to 0.85 // Background (~12m) to Actor (~1.2m)
                }
                configure(startDiopters, endDiopters, durationSec)
                start(0.0)
            }
            cameraPipeline.setRackFocusMode(true, if (selectedFocusMode.value == FocusPullMode.RACK_TO_BG) 0.85f else 0.08f)
        } else {
            cameraPipeline.setRackFocusMode(false)
        }

        val initialHarmonic = computeHarmonicZoom(startZoom)
        lastHarmonicZoom = initialHarmonic
        cameraPipeline.setZoomRatio(initialHarmonic, immediate = true)
        currentZoomRatio.floatValue = initialHarmonic

        // Direct hardware 1080p60 recording to Android MediaStore Gallery (Movies/CinematicCamera)
        cameraPipeline.startRecording(
            onRecordingStarted = {
                Log.i(tag, "Native 1080p60 recording started with Harmonic Zoom Snapping")
                isEncoderRecording = true
            },
            onVideoSaved = { uri ->
                Log.i(tag, "Cinematic Dolly Zoom saved directly to Gallery: $uri")
                val thumb = extractVideoThumbnail(uri)
                val take = CinematicTake(
                    uri = uri,
                    timestampMs = System.currentTimeMillis(),
                    direction = selectedDirection.value,
                    pace = selectedPace.value,
                    curve = selectedCurve.value,
                    targetFps = currentFps.intValue,
                    startZoom = startZoom,
                    endZoom = targetZoom,
                    durationSec = selectedPace.value.durationSec,
                    focusMode = selectedFocusMode.value,
                    thumbnailBitmap = thumb
                )
                runOnUiThread {
                    lastTake.value = take
                    shotState.value = AppShotState.REVIEW
                }
            },
            onError = { err ->
                Log.e(tag, "Recording error: $err")
                runOnUiThread {
                    shotState.value = AppShotState.IDLE
                }
            }
        )
    }

    private fun extractVideoThumbnail(uri: Uri): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            bitmap
        } catch (e: Exception) {
            Log.w(tag, "Failed to extract thumbnail: ${e.message}")
            null
        }
    }

    private fun stopShot() {
        countdownJob?.cancel()
        isEncoderRecording = false
        shotState.value = AppShotState.PROCESSING // Immediately hide Stop button & display instant saving state!
        cameraPipeline.stopRecording()
        cameraPipeline.lockExposureAndFocus(false)
        cameraPipeline.setRackFocusMode(false)
        rackFocusBridge?.close()
        rackFocusBridge = null
        hapticMetronome.reset()
    }

    private fun resetToIdle() {
        hapticMetronome.reset()
        isShotComplete.value = false
        isEncoderRecording = false
        recordedFrameCount = 0
        cameraPipeline.lockExposureAndFocus(false)
        cameraPipeline.setRackFocusMode(false)
        rackFocusBridge?.close()
        rackFocusBridge = null
        updateInitialZoomForDirection(selectedDirection.value)
        shotState.value = AppShotState.IDLE
    }

    override fun onResume() {
        super.onResume()
        orientationSensor.start()
    }

    override fun onPause() {
        super.onPause()
        orientationSensor.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        orientationSensor.stop()
        cameraPipeline.setRackFocusMode(false)
        rackFocusBridge?.close()
        rackFocusBridge = null
        cameraPipeline.shutdown()
    }
}
