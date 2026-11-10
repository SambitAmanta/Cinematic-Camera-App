package com.cinematic.camera.guidance

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.cinematic.camera.engine.DollyZoomNativeBridge

/**
 * Emits robust, high-amplitude tactile metronome cues to guide the camera operator's walking pace.
 */
class HapticMetronome(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var lastPulseTime: Long = 0
    private var hasTriggeredEndAlert = false

    fun reset() {
        lastPulseTime = 0
        hasTriggeredEndAlert = false
    }

    /**
     * Emits energetic step pulses based on selected pace so the operator feels walking cadence.
     */
    fun updateGuidance(pace: DollyZoomNativeBridge.Pace, isComplete: Boolean) {
        val now = System.currentTimeMillis()

        if (isComplete) {
            if (!hasTriggeredEndAlert) {
                hasTriggeredEndAlert = true
                triggerTargetReachedAlert()
            }
            return
        }

        // Metronome cadence tuned to human walking stride for each pace
        val intervalMs = when (pace) {
            DollyZoomNativeBridge.Pace.FAST -> 400L    // Brisk stride cadence
            DollyZoomNativeBridge.Pace.MEDIUM -> 600L  // Steady cinematic walk
            DollyZoomNativeBridge.Pace.SLOW -> 850L    // Slow, deliberate footsteps
        }

        if (now - lastPulseTime >= intervalMs) {
            triggerMetronomeStep()
            lastPulseTime = now
        }
    }

    fun triggerCountdownTick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(55, 255))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(50)
        }
    }

    private fun triggerMetronomeStep() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Crisp, energetic tactile footstep pulse (full amplitude for unmistakable hand feel)
            vibrator?.vibrate(VibrationEffect.createOneShot(45, 240))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(40)
        }
    }

    private fun triggerTargetReachedAlert() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Distinct double buzz signalling that target distance has been reached
            val timings = longArrayOf(0, 80, 80, 140)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(220)
        }
    }
}
