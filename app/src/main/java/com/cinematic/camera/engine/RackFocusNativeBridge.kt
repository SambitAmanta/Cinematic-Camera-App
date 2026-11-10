package com.cinematic.camera.engine

/**
 * JNI wrapper for the native C++ Cinematic Rack Focus Controller.
 * Executes smooth cubic Hermite (S-curve) focus pulls between calibrated focus distances.
 */
class RackFocusNativeBridge(durationSec: Double = 1.5) : AutoCloseable {

    data class PullResult(
        val commandedDiopters: Double,
        val progress: Double,
        val isComplete: Boolean
    )

    private var nativeHandle: Long = 0
    private val progressBuffer = DoubleArray(1)

    init {
        nativeHandle = nativeCreate(durationSec)
    }

    fun configure(focusADiopters: Double, focusBDiopters: Double, durationSec: Double = 1.5) {
        if (nativeHandle != 0L) {
            nativeConfigure(nativeHandle, focusADiopters, focusBDiopters, durationSec)
        }
    }

    fun start(startTimeSec: Double) {
        if (nativeHandle != 0L) {
            nativeStart(nativeHandle, startTimeSec)
        }
    }

    /**
     * Updates the focus pull trajectory.
     * @return Commanded lens focus distance in diopters (Camera2 LENS_FOCUS_DISTANCE).
     */
    fun update(currentTimeSec: Double): PullResult {
        if (nativeHandle == 0L) {
            return PullResult(0.0, 1.0, true)
        }

        val commandedDiopters = nativeUpdate(nativeHandle, currentTimeSec, progressBuffer)
        val progress = progressBuffer[0]
        return PullResult(
            commandedDiopters = commandedDiopters,
            progress = progress,
            isComplete = progress >= 0.999
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

    private external fun nativeCreate(durationSec: Double): Long
    private external fun nativeDestroy(nativeHandle: Long)
    private external fun nativeConfigure(nativeHandle: Long, focusA: Double, focusB: Double, durationSec: Double)
    private external fun nativeStart(nativeHandle: Long, startTimeSec: Double)
    private external fun nativeUpdate(nativeHandle: Long, currentTimeSec: Double, outProgress: DoubleArray): Double

    companion object {
        init {
            System.loadLibrary("cinematic_engine")
        }
    }
}

