package com.cinematic.camera.guidance

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.atan2

/**
 * 60 Hz Gyroscope/Gravity Sensor for Real-Time Cinema Horizon Leveling (Prototype 2).
 * Employs a low-pass exponential filter to absorb walking footstep vibrations
 * while tracking camera roll angle with sub-degree precision.
 */
class DeviceOrientationSensor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _rollDegrees = mutableFloatStateOf(0f)
    val rollDegrees: State<Float> = _rollDegrees

    private var smoothedAx = 0f
    private var smoothedAy = 9.8f
    private val alpha = 0.22f // Exponential smoothing filter absorbing walking footstep shocks

    fun start() {
        sensor?.let { s ->
            sensorManager?.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val rawAx = event.values[0]
        val rawAy = event.values[1]

        // Low-pass exponential smoothing
        smoothedAx += alpha * (rawAx - smoothedAx)
        smoothedAy += alpha * (rawAy - smoothedAy)

        // Calculate roll angle: 0 degrees when phone is perfectly upright in portrait
        val rollRad = atan2(smoothedAx.toDouble(), smoothedAy.toDouble())
        val rollDeg = Math.toDegrees(rollRad).toFloat()

        _rollDegrees.floatValue = rollDeg
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

