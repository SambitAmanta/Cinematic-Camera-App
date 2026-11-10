package com.cinematic.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pure Kotlin mathematical unit test verifying the optical equations,
 * bounds clamping, and scale constancy invariant.
 */
class DollyZoomMathTest {

    @Test
    fun testDollyInOpticalMath() {
        // Initial setup for Dolly-In:
        // Start distance d_0 = 3.0m, start zoom z_0 = 2.5x
        val d0 = 3.0
        val z0 = 2.5
        val kRatio = z0 / d0 // 0.8333 zoom per meter

        // Operator walks forward to 1.5m
        val dCurrent = 1.5
        val targetZoom = kRatio * dCurrent // Expected: 1.25x

        assertEquals(1.25, targetZoom, 0.001)

        // Verify Subject Scale is constant:
        // Scale ~ Zoom / Distance
        val initialScale = z0 / d0
        val currentScale = targetZoom / dCurrent
        val scaleError = abs(currentScale - initialScale) / initialScale

        assertEquals(0.0, scaleError, 0.0001)
    }

    @Test
    fun testDollyOutOpticalMath() {
        // Initial setup for Dolly-Out:
        // Start distance d_0 = 1.2m, start zoom z_0 = 1.0x
        val d0 = 1.2
        val z0 = 1.0
        val kRatio = z0 / d0

        // Operator walks backward to 2.4m
        val dCurrent = 2.4
        val targetZoom = kRatio * dCurrent // Expected: 2.0x

        assertEquals(2.0, targetZoom, 0.001)

        val initialScale = z0 / d0
        val currentScale = targetZoom / dCurrent
        val scaleError = abs(currentScale - initialScale) / initialScale

        assertEquals(0.0, scaleError, 0.0001)
    }

    @Test
    fun testSensorCropClamping() {
        val minZoom = 1.0
        val maxZoom = 3.0

        // Walking too close (e.g. 0.4m on Dolly In) would require zoom < 1.0x (e.g. 0.33x)
        val calculatedZoom = 0.33
        val clamped = calculatedZoom.coerceIn(minZoom, maxZoom)

        assertEquals(minZoom, clamped, 0.001)

        val excessiveZoom = 4.5
        val clampedMax = excessiveZoom.coerceIn(minZoom, maxZoom)
        assertEquals(maxZoom, clampedMax, 0.001)
    }
}

