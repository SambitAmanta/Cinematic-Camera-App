package com.cinematic.camera.ui

import android.graphics.Bitmap
import android.net.Uri
import com.cinematic.camera.engine.DollyZoomNativeBridge

/**
 * Metadata record for a completed Cinematic take.
 * Stores hardware telemetry, motion curves, and video URI for immediate in-app review.
 */
data class CinematicTake(
    val uri: Uri,
    val timestampMs: Long = System.currentTimeMillis(),
    val direction: DollyZoomNativeBridge.Direction,
    val pace: DollyZoomNativeBridge.Pace,
    val curve: DollyZoomNativeBridge.Curve,
    val targetFps: Int,
    val startZoom: Float,
    val endZoom: Float,
    val durationSec: Double,
    val focusMode: FocusPullMode = FocusPullMode.TRACKING,
    val thumbnailBitmap: Bitmap? = null
)

