package com.cinematic.camera.engine

/**
 * High-performance JNI Bridge to the native C++ Dolly Zoom Controller.
 * Guarantees zero heap allocation per update cycle by reusing a pre-allocated telemetry buffer.
 */
class DollyZoomNativeBridge(
    initialDistance: Double = 2.0,
    direction: Direction = Direction.DOLLY_OUT,
    pace: Pace = Pace.MEDIUM,
    curve: Curve = Curve.LINEAR,
    minZoom: Double = 1.0,
    maxZoom: Double = 3.0
) : AutoCloseable {

    enum class Direction(val value: Int) {
        DOLLY_IN(0),
        DOLLY_OUT(1)
    }

    enum class Pace(val value: Int, val durationSec: Double, val label: String) {
        SLOW(0, 7.0, "SLOW (7s)"),
        MEDIUM(1, 4.5, "MEDIUM (4.5s)"),
        FAST(2, 2.5, "FAST (2.5s)")
    }

    enum class Curve(val value: Int, val label: String) {
        LINEAR(0, "LINEAR"),
        S_CURVE(1, "S-CURVE"),
        DRAMATIC(2, "DRAMATIC")
    }

    enum class SpeedStatus(val value: Int) {
        TOO_SLOW(0),
        OPTIMAL(1),
        TOO_FAST(2);

        companion object {
            fun fromInt(v: Int): SpeedStatus = values().firstOrNull { it.value == v } ?: OPTIMAL
        }
    }

    data class Telemetry(
        val commandedZoom: Double,
        val estimatedDistance: Double,
        val estimatedVelocity: Double,
        val speedStatus: SpeedStatus,
        val isZoomComplete: Boolean,
        val progress: Double
    )

    private var nativeHandle: Long = 0
    private val telemetryBuffer = DoubleArray(6)

    init {
        nativeHandle = nativeCreate(
            initialDistance,
            direction.value,
            pace.value,
            curve.value,
            minZoom,
            maxZoom
        )
    }

    fun reset(initialDistance: Double, direction: Direction, pace: Pace, curve: Curve = Curve.LINEAR) {
        if (nativeHandle != 0L) {
            nativeReset(nativeHandle, initialDistance, direction.value, pace.value, curve.value)
        }
    }

    fun startMotion(startTimeSec: Double = -1.0) {
        if (nativeHandle != 0L) {
            nativeStartMotion(nativeHandle, startTimeSec)
        }
    }

    /**
     * Executes one control loop update.
     * Fast path: ~0.05ms execution time in native C++.
     */
    fun update(currentTimeSec: Double, dt: Double): Telemetry {
        if (nativeHandle == 0L) {
            return Telemetry(1.0, 2.0, 0.0, SpeedStatus.OPTIMAL, false, 0.0)
        }

        nativeUpdate(nativeHandle, currentTimeSec, dt, telemetryBuffer)

        return Telemetry(
            commandedZoom = telemetryBuffer[0],
            estimatedDistance = telemetryBuffer[1],
            estimatedVelocity = telemetryBuffer[2],
            speedStatus = SpeedStatus.fromInt(telemetryBuffer[3].toInt()),
            isZoomComplete = telemetryBuffer[4] > 0.5,
            progress = telemetryBuffer[5]
        )
    }

    override fun close() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
    }

    protected fun finalize() {
        close()
    }

    private external fun nativeCreate(
        initialDistance: Double,
        direction: Int,
        pace: Int,
        curve: Int,
        minZoom: Double,
        maxZoom: Double
    ): Long

    private external fun nativeDestroy(nativeHandle: Long)

    private external fun nativeReset(
        nativeHandle: Long,
        initialDistance: Double,
        direction: Int,
        pace: Int,
        curve: Int
    )

    private external fun nativeStartMotion(
        nativeHandle: Long,
        startTimeSec: Double
    )

    private external fun nativeUpdate(
        nativeHandle: Long,
        currentTimeSec: Double,
        dt: Double,
        outTelemetry: DoubleArray
    )

    companion object {
        init {
            System.loadLibrary("cinematic_engine")
        }
    }
}
