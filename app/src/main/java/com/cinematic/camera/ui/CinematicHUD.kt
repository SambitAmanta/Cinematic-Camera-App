package com.cinematic.camera.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cinematic.camera.engine.DollyZoomNativeBridge
import java.util.Locale

enum class AppShotState {
    IDLE,
    LOCKED,
    COUNTDOWN,
    RECORDING,
    PROCESSING,
    REVIEW
}

/**
 * Coordinated Optical Focus Modes (Prototype 2 Native C++ Rack Focus Puller).
 */
enum class FocusPullMode(val label: String, val description: String) {
    TRACKING("Subject Lock", "Continuous Center PDAF Tracking"),
    RACK_TO_BG("Pull to Back", "Foreground Actor ➔ Deep Background"),
    RACK_TO_FG("Pull to Actor", "Deep Background ➔ Foreground Actor")
}

/**
 * Production-Grade Cinematic Viewfinder HUD.
 * Designed for normal users with simple, intuitive everyday language:
 * - Wide-open, uncluttered active sensor viewfinder
 * - Subtle Rule of Thirds & Precision Center Focus Crosshair
 * - Interactive Top Telemetry Bar with Clickable Zoom Range Button
 * - Unified, Cohesive Cinema Dock with Symmetrical Shutter Bar
 * - Configurable Zoom Range Dialog supporting any Android device camera limits
 */
@Composable
fun CinematicHUD(
    shotState: AppShotState,
    selectedDirection: DollyZoomNativeBridge.Direction,
    selectedPace: DollyZoomNativeBridge.Pace,
    selectedCurve: DollyZoomNativeBridge.Curve,
    currentZoom: Float,
    targetFps: Int,
    countdownSeconds: Int,
    isComplete: Boolean,
    minZoom: Float = 1.0f,
    maxZoom: Float = 3.0f,
    deviceMinZoom: Float = 1.0f,
    deviceMaxZoom: Float = 8.0f,
    rollDegrees: Float = 0f,
    selectedFocusMode: FocusPullMode = FocusPullMode.TRACKING,
    lastTake: CinematicTake? = null,
    onDirectionChanged: (DollyZoomNativeBridge.Direction) -> Unit,
    onPaceChanged: (DollyZoomNativeBridge.Pace) -> Unit,
    onCurveChanged: (DollyZoomNativeBridge.Curve) -> Unit,
    onFocusModeChanged: (FocusPullMode) -> Unit = {},
    onFpsChanged: (Int) -> Unit,
    onZoomBoundsChanged: (min: Float, max: Float) -> Unit = { _, _ -> },
    onStartShotClicked: () -> Unit,
    onStopShotClicked: () -> Unit,
    onResetClicked: () -> Unit,
    onOpenPlaybackStudio: () -> Unit = {}
) {
    var showZoomRangeDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Cinema Viewfinder Reticle & Aeronautical Horizon Guide (Prototype 2)
        CinemaViewfinderReticle(
            isRecording = (shotState == AppShotState.RECORDING),
            rollDegrees = rollDegrees
        )

        // Real-Time Tilt Guidance Badge (warns operator if camera rolls > 1.0 degree)
        if (kotlin.math.abs(rollDegrees) > 1.0f && shotState != AppShotState.COUNTDOWN) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF14171A).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFFFB300).copy(alpha = 0.7f))
                ) {
                    Text(
                        text = String.format(Locale.US, "TILT %+.1f°", rollDegrees),
                        color = Color(0xFFFFB300),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                    )
                }
            }
        }

        // 2. Studio Telemetry Top Bar (Resolution, 35mm Focal Length, Shutter, Interactive Zoom Badge)
        StudioTelemetryBar(
            shotState = shotState,
            currentZoom = currentZoom,
            minZoom = minZoom,
            maxZoom = maxZoom,
            targetFps = targetFps,
            onFpsChanged = onFpsChanged,
            onZoomClicked = {
                if (shotState != AppShotState.RECORDING && shotState != AppShotState.COUNTDOWN) {
                    showZoomRangeDialog = true
                }
            }
        )

        // 3. Dynamic Shot Status Guidance Pill (Top Guidance)
        if (shotState == AppShotState.RECORDING) {
            RecordingStatusPill(
                selectedDirection = selectedDirection,
                isComplete = isComplete
            )
        }

        // 4. Center Countdown Overlay (3.. 2.. 1..)
        if (shotState == AppShotState.COUNTDOWN) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape,
                    modifier = Modifier.size(140.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$countdownSeconds",
                            fontSize = 80.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00E5FF),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 5. Finalizing Shot Loader (Clean everyday words)
        if (shotState == AppShotState.PROCESSING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF14171A).copy(alpha = 0.95f),
                    shape = RoundedCornerShape(22.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 34.dp, vertical = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF00E5FF),
                            strokeWidth = 3.5.dp,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "Saving Your Video...",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1080p HD • Optical Stabilization",
                            color = Color(0xFF00E5FF),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 6. Unified Cinema Control Dock (Direction, Speed, Style, Focus Mode, Symmetrical Shutter Bar)
        CinemaControlDeck(
            shotState = shotState,
            selectedDirection = selectedDirection,
            selectedPace = selectedPace,
            selectedCurve = selectedCurve,
            selectedFocusMode = selectedFocusMode,
            minZoom = minZoom,
            maxZoom = maxZoom,
            lastTake = lastTake,
            onDirectionChanged = onDirectionChanged,
            onPaceChanged = onPaceChanged,
            onCurveChanged = onCurveChanged,
            onFocusModeChanged = onFocusModeChanged,
            onOpenZoomSettings = { showZoomRangeDialog = true },
            onStartShotClicked = onStartShotClicked,
            onStopShotClicked = onStopShotClicked,
            onResetClicked = onResetClicked,
            onOpenPlaybackStudio = onOpenPlaybackStudio,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp, start = 14.dp, end = 14.dp)
        )

        // 7. Configurable Zoom Range Dialog
        if (showZoomRangeDialog) {
            ZoomRangeDialog(
                currentMinZoom = minZoom,
                currentMaxZoom = maxZoom,
                deviceMinZoom = deviceMinZoom,
                deviceMaxZoom = deviceMaxZoom,
                onDismiss = { showZoomRangeDialog = false },
                onApply = { newMin, newMax ->
                    onZoomBoundsChanged(newMin, newMax)
                    showZoomRangeDialog = false
                }
            )
        }
    }
}

/**
 * Professional Viewfinder Reticle:
 * - Subtle Rule of Thirds grid
 * - Safe Area Corner Brackets
 * - Precision Center Crosshair with Concentric Focus Target Ring
 * - Turns tally red during active recording
 */
@Composable
private fun CinemaViewfinderReticle(
    isRecording: Boolean,
    rollDegrees: Float = 0f
) {
    val tallyColor = if (isRecording) Color(0xFFFF3B30) else Color.White
    val guideAlpha = if (isRecording) 0.65f else 0.30f

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Rule of Thirds Grid (Subtle lines)
        val gridColor = Color.White.copy(alpha = 0.05f)
        val gridStroke = 0.75.dp.toPx()
        drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), gridStroke)
        drawLine(gridColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), gridStroke)
        drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), gridStroke)
        drawLine(gridColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), gridStroke)

        // 2. Safe Area Corner Brackets
        val safeMarginX = w * 0.05f
        val safeMarginY = h * 0.08f
        val armLen = 20.dp.toPx()
        val cornerStroke = 1.3.dp.toPx()
        val cornerColor = tallyColor.copy(alpha = guideAlpha)

        val left = safeMarginX
        val right = w - safeMarginX
        val top = safeMarginY
        val bottom = h - safeMarginY

        // Top-Left Corner
        drawLine(cornerColor, Offset(left, top), Offset(left + armLen, top), cornerStroke)
        drawLine(cornerColor, Offset(left, top), Offset(left, top + armLen), cornerStroke)

        // Top-Right Corner
        drawLine(cornerColor, Offset(right, top), Offset(right - armLen, top), cornerStroke)
        drawLine(cornerColor, Offset(right, top), Offset(right, top + armLen), cornerStroke)

        // Bottom-Left Corner
        drawLine(cornerColor, Offset(left, bottom), Offset(left + armLen, bottom), cornerStroke)
        drawLine(cornerColor, Offset(left, bottom), Offset(left, bottom - armLen), cornerStroke)

        // Bottom-Right Corner
        drawLine(cornerColor, Offset(right, bottom), Offset(right - armLen, bottom), cornerStroke)
        drawLine(cornerColor, Offset(right, bottom), Offset(right, bottom - armLen), cornerStroke)

        // 3. Center Precision Focus Crosshair & Ring
        val cx = w / 2f
        val cy = h / 2f
        val crossArm = 11.dp.toPx()
        val crossGap = 5.dp.toPx()
        val crossStroke = 1.0.dp.toPx()

        drawLine(cornerColor, Offset(cx - crossArm, cy), Offset(cx - crossGap, cy), crossStroke)
        drawLine(cornerColor, Offset(cx + crossGap, cy), Offset(cx + crossArm, cy), crossStroke)
        drawLine(cornerColor, Offset(cx, cy - crossArm), Offset(cx, cy - crossGap), crossStroke)
        drawLine(cornerColor, Offset(cx, cy + crossGap), Offset(cx, cy + crossArm), crossStroke)

        // Concentric Center Target Ring
        val ringRadius = 22.dp.toPx()
        drawCircle(
            color = cornerColor.copy(alpha = guideAlpha * 0.70f),
            radius = ringRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 0.8.dp.toPx())
        )
        // Center micro-dot
        drawCircle(cornerColor, radius = 1.5.dp.toPx(), center = Offset(cx, cy))

        // 4. Aeronautical Cinema Horizon Leveling Line (Prototype 2 Gyroscope Fusion)
        val isLevel = kotlin.math.abs(rollDegrees) <= 1.0f
        val horizonColor = if (isLevel) Color(0xFF00E676) else Color(0xFFFFB300)
        val horizonAlpha = if (isLevel) 0.85f else 0.65f
        val horizonArm = 48.dp.toPx()
        val horizonGap = 26.dp.toPx()
        val horizonStroke = 1.3.dp.toPx()

        rotate(degrees = -rollDegrees, pivot = Offset(cx, cy)) {
            // Left horizon wing
            drawLine(
                color = horizonColor.copy(alpha = horizonAlpha),
                start = Offset(cx - horizonArm, cy),
                end = Offset(cx - horizonGap, cy),
                strokeWidth = horizonStroke
            )
            // Right horizon wing
            drawLine(
                color = horizonColor.copy(alpha = horizonAlpha),
                start = Offset(cx + horizonGap, cy),
                end = Offset(cx + horizonArm, cy),
                strokeWidth = horizonStroke
            )
        }
    }
}

/**
 * Studio Telemetry Top Bar:
 * - Format & Framerate Selector: 1080P • [30] [60] fps
 * - 35mm Equivalent Focal Length & Shutter Angle
 * - Clickable Zoom Range Button with edit icon (Opens Zoom Range dialog)
 */
@Composable
private fun StudioTelemetryBar(
    shotState: AppShotState,
    currentZoom: Float,
    minZoom: Float,
    maxZoom: Float,
    targetFps: Int,
    onFpsChanged: (Int) -> Unit,
    onZoomClicked: () -> Unit
) {
    val focalLengthMm = Math.round(24.0 * currentZoom).toInt()
    val isRecording = shotState == AppShotState.RECORDING

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Format & Framerate Pill
        Surface(
            color = Color(0xFF14171A).copy(alpha = 0.85f),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(0.7.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) Color(0xFFFF3B30) else Color(0xFF00E676))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "1080P",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (!isRecording) {
                    Row {
                        FpsBadge(fps = 30, isSelected = targetFps == 30, onClick = { onFpsChanged(30) })
                        Spacer(modifier = Modifier.width(2.dp))
                        FpsBadge(fps = 60, isSelected = targetFps == 60, onClick = { onFpsChanged(60) })
                    }
                } else {
                    Text(
                        text = "${targetFps}P",
                        color = Color(0xFFFFD600),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Center: 35mm Equivalent Focal Length & Shutter Angle
        Surface(
            color = Color(0xFF14171A).copy(alpha = 0.85f),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(0.7.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${focalLengthMm}mm",
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "•",
                    color = Color.Gray,
                    fontSize = 9.sp
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (targetFps >= 60) "180° (1/120s)" else "180° (1/60s)",
                    color = Color.LightGray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Right: Interactive Zoom Range Button (Tapping opens settings dialog)
        Surface(
            color = Color(0xFF14171A).copy(alpha = 0.85f),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                0.8.dp,
                if (isRecording) Color(0xFFFFD600).copy(alpha = 0.6f) else Color(0xFF00E5FF).copy(alpha = 0.45f)
            ),
            modifier = Modifier.clickable(enabled = !isRecording, onClick = onZoomClicked)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRecording) {
                    Text(
                        text = String.format(Locale.US, "%.2fx", currentZoom),
                        color = Color(0xFFFFD600),
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Text(
                        text = String.format(Locale.US, "%.1fx-%.1fx", minZoom, maxZoom),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Zoom Range",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FpsBadge(fps: Int, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = "$fps",
            color = if (isSelected) Color.Black else Color.Gray,
            fontWeight = FontWeight.Black,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

/**
 * Slim guidance pill shown during active recording.
 */
@Composable
private fun RecordingStatusPill(
    selectedDirection: DollyZoomNativeBridge.Direction,
    isComplete: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        val text = when {
            isComplete -> "✓ Target Reached • Tap Stop"
            selectedDirection == DollyZoomNativeBridge.Direction.DOLLY_OUT -> "🚶‍♂️ Walk Backward • Steady steps"
            else -> "🚶‍♂️ Walk Forward • Steady steps"
        }
        val textColor = if (isComplete) Color(0xFF00E676) else Color(0xFFFFD600)

        Surface(
            color = Color(0xFF14171A).copy(alpha = 0.90f),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(0.8.dp, textColor.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isComplete) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = text,
                    color = textColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Unified Cinema Control Dock:
 * Cleanly aligned single panel with intuitive everyday language:
 * - Direction Segmented Tabs: [ Walk Backward ] [ Walk Forward ]
 * - Shot Speed: Slow (7s) | Normal (4.5s) | Fast (2.5s)
 * - Shot Style: Smooth | Movie | Dramatic
 * - Symmetrical Shutter Bar: [Recent Take] - [Cinema Shutter] - [Zoom Range]
 */
@Composable
private fun CinemaControlDeck(
    shotState: AppShotState,
    selectedDirection: DollyZoomNativeBridge.Direction,
    selectedPace: DollyZoomNativeBridge.Pace,
    selectedCurve: DollyZoomNativeBridge.Curve,
    selectedFocusMode: FocusPullMode,
    minZoom: Float,
    maxZoom: Float,
    lastTake: CinematicTake? = null,
    onDirectionChanged: (DollyZoomNativeBridge.Direction) -> Unit,
    onPaceChanged: (DollyZoomNativeBridge.Pace) -> Unit,
    onCurveChanged: (DollyZoomNativeBridge.Curve) -> Unit,
    onFocusModeChanged: (FocusPullMode) -> Unit,
    onOpenZoomSettings: () -> Unit,
    onStartShotClicked: () -> Unit,
    onStopShotClicked: () -> Unit,
    onResetClicked: () -> Unit,
    onOpenPlaybackStudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (shotState == AppShotState.IDLE || shotState == AppShotState.LOCKED) {
            // Unified Cinema Dock Card
            Surface(
                color = Color(0xFF121518).copy(alpha = 0.92f),
                shape = RoundedCornerShape(26.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth(0.96f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Level 1: Direction Segmented Switcher (Walk Backward vs Walk Forward)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1A1F24))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DirectionTab(
                            title = "Walk Backward",
                            subtitle = "Zooms In (1x ➔ 3x)",
                            isSelected = selectedDirection == DollyZoomNativeBridge.Direction.DOLLY_OUT,
                            onClick = { onDirectionChanged(DollyZoomNativeBridge.Direction.DOLLY_OUT) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        DirectionTab(
                            title = "Walk Forward",
                            subtitle = "Zooms Out (3x ➔ 1x)",
                            isSelected = selectedDirection == DollyZoomNativeBridge.Direction.DOLLY_IN,
                            onClick = { onDirectionChanged(DollyZoomNativeBridge.Direction.DOLLY_IN) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Level 2: Speed and Style Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shot Speed (Pace)
                        Column {
                            Text(
                                text = "SPEED",
                                color = Color.Gray,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row {
                                PaceOptionPill(
                                    label = "Slow (7s)",
                                    isSelected = selectedPace == DollyZoomNativeBridge.Pace.SLOW,
                                    onClick = { onPaceChanged(DollyZoomNativeBridge.Pace.SLOW) }
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                PaceOptionPill(
                                    label = "Normal (4.5s)",
                                    isSelected = selectedPace == DollyZoomNativeBridge.Pace.MEDIUM,
                                    onClick = { onPaceChanged(DollyZoomNativeBridge.Pace.MEDIUM) }
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                PaceOptionPill(
                                    label = "Fast (2.5s)",
                                    isSelected = selectedPace == DollyZoomNativeBridge.Pace.FAST,
                                    onClick = { onPaceChanged(DollyZoomNativeBridge.Pace.FAST) }
                                )
                            }
                        }

                        // Movement Style (Curve)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "STYLE",
                                color = Color.Gray,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row {
                                CurveOptionPill(
                                    label = "Smooth",
                                    isSelected = selectedCurve == DollyZoomNativeBridge.Curve.LINEAR,
                                    onClick = { onCurveChanged(DollyZoomNativeBridge.Curve.LINEAR) }
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                CurveOptionPill(
                                    label = "Movie",
                                    isSelected = selectedCurve == DollyZoomNativeBridge.Curve.S_CURVE,
                                    onClick = { onCurveChanged(DollyZoomNativeBridge.Curve.S_CURVE) }
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                CurveOptionPill(
                                    label = "Dramatic",
                                    isSelected = selectedCurve == DollyZoomNativeBridge.Curve.DRAMATIC,
                                    onClick = { onCurveChanged(DollyZoomNativeBridge.Curve.DRAMATIC) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Level 2.5: Focus Mode (Native C++ Coordinated Rack Focus)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FOCUS",
                            color = Color.Gray,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Row {
                            FocusPullMode.values().forEach { mode ->
                                val isSelected = selectedFocusMode == mode
                                Surface(
                                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E2328),
                                    shape = RoundedCornerShape(8.dp),
                                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(0.6.dp, Color.White.copy(alpha = 0.08f)),
                                    modifier = Modifier.clickable { onFocusModeChanged(mode) }
                                ) {
                                    Text(
                                        text = mode.label,
                                        color = if (isSelected) Color.Black else Color.LightGray,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(3.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Level 3: Symmetrical Shutter Bar
                    // [Left Thumbnail: 52dp] ---- [Center Shutter: 72dp] ---- [Right Zoom Range: 52dp]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Recent Take Thumbnail Button
                        if (lastTake != null) {
                            Surface(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(14.dp))
                                    .clickable(onClick = onOpenPlaybackStudio),
                                color = Color(0xFF14171A)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (lastTake.thumbnailBitmap != null) {
                                        Image(
                                            bitmap = lastTake.thumbnailBitmap.asImageBitmap(),
                                            contentDescription = "Review Shot",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play",
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Review",
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp)),
                                color = Color(0xFF1A1F24).copy(alpha = 0.6f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "SHOTS",
                                        color = Color.DarkGray,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // Center: Professional Cinema Shutter Trigger
                        CinemaShutterButton(
                            isRecording = false,
                            onClick = onStartShotClicked
                        )

                        // Right: Zoom Range Quick Settings Button (Balances the Left Thumbnail)
                        Surface(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.2.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .clickable(onClick = onOpenZoomSettings),
                            color = Color(0xFF1A1F24)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Zoom Range",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = String.format(Locale.US, "%.1f-%.1f", minZoom, maxZoom),
                                    color = Color.White,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        } else if (shotState == AppShotState.RECORDING) {
            // Prominent Red Cinema Stop Trigger Dock
            Surface(
                color = Color(0xFF121518).copy(alpha = 0.94f),
                shape = RoundedCornerShape(26.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF3B30).copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(0.96f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 18.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CinemaShutterButton(
                        isRecording = true,
                        onClick = onStopShotClicked
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "TAP STOP TO FINISH SHOT",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        } else if (shotState == AppShotState.REVIEW) {
            // Take Completed Card: Watch Video or Next Shot
            Surface(
                color = Color(0xFF121518).copy(alpha = 0.95f),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth(0.96f)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Shot Saved to Gallery!",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = onOpenPlaybackStudio,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Watch Video",
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedButton(
                            onClick = onResetClicked,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Next Shot",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Direction Segmented Tab: [ Walk Backward ] [ Walk Forward ]
 */
@Composable
private fun DirectionTab(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
        shape = RoundedCornerShape(11.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = if (isSelected) Color.Black else Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 11.5.sp
            )
            Text(
                text = subtitle,
                color = if (isSelected) Color.Black.copy(alpha = 0.75f) else Color.Gray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PaceOptionPill(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (isSelected) Color(0xFFFFD600) else Color(0xFF1E2328),
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(0.6.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else Color.LightGray,
            fontWeight = FontWeight.Bold,
            fontSize = 9.5.sp,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.5.dp)
        )
    }
}

@Composable
private fun CurveOptionPill(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E2328),
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(0.6.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else Color.LightGray,
            fontWeight = FontWeight.Bold,
            fontSize = 9.5.sp,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.5.dp)
        )
    }
}

/**
 * Cinema Shutter Button:
 * Milled tactile rim, glowing red record core with smooth pulse.
 */
@Composable
private fun CinemaShutterButton(
    isRecording: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .size(72.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Outer milled rim
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(
                width = 3.dp,
                color = if (isRecording) Color(0xFFFF3B30).copy(alpha = pulseAlpha) else Color.White.copy(alpha = 0.85f)
            )
        ) {}

        // Inner tactile core
        if (isRecording) {
            Surface(
                modifier = Modifier.size(30.dp),
                shape = RoundedCornerShape(7.dp),
                color = Color(0xFFFF3B30)
            ) {}
        } else {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = Color(0xFFFF3B30)
            ) {}
        }
    }
}

/**
 * Configurable Zoom Range Dialog:
 * Allows user to choose min and max zoom thresholds for the Vertigo shot.
 * Supports quick presets and continuous sliders, constrained to device hardware limits.
 */
@Composable
fun ZoomRangeDialog(
    currentMinZoom: Float,
    currentMaxZoom: Float,
    deviceMinZoom: Float,
    deviceMaxZoom: Float,
    onDismiss: () -> Unit,
    onApply: (min: Float, max: Float) -> Unit
) {
    var minVal by remember { mutableFloatStateOf(currentMinZoom) }
    var maxVal by remember { mutableFloatStateOf(currentMaxZoom) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF14171A),
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "Shot Zoom Range",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Configure start and end zoom levels for your Vertigo shot.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Hardware phone limits chip
                Surface(
                    color = Color(0xFF1E2328),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "Phone camera limits: %.1fx to %.1fx", deviceMinZoom, deviceMaxZoom),
                        color = Color(0xFF00E5FF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Presets Title
                Text(
                    text = "QUICK PRESETS",
                    color = Color.LightGray,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Presets Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ZoomPresetChip(label = "1x - 3x (Default)", min = 1.0f, max = 3.0f, isSelected = (minVal == 1.0f && maxVal == 3.0f)) {
                        minVal = 1.0f.coerceIn(deviceMinZoom, deviceMaxZoom - 0.5f)
                        maxVal = 3.0f.coerceIn(minVal + 0.5f, deviceMaxZoom)
                    }
                    ZoomPresetChip(label = "1x - 2x (Subtle)", min = 1.0f, max = 2.0f, isSelected = (minVal == 1.0f && maxVal == 2.0f)) {
                        minVal = 1.0f.coerceIn(deviceMinZoom, deviceMaxZoom - 0.5f)
                        maxVal = 2.0f.coerceIn(minVal + 0.5f, deviceMaxZoom)
                    }
                    ZoomPresetChip(label = "1x - 5x (Deep)", min = 1.0f, max = 5.0f, isSelected = (minVal == 1.0f && maxVal == 5.0f)) {
                        minVal = 1.0f.coerceIn(deviceMinZoom, deviceMaxZoom - 0.5f)
                        maxVal = 5.0f.coerceIn(minVal + 0.5f, deviceMaxZoom)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Start Zoom (Min) Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Start Zoom (Min)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(text = String.format(Locale.US, "%.1fx", minVal), color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
                Slider(
                    value = minVal,
                    onValueChange = { newVal ->
                        val rounded = Math.round(newVal * 10f) / 10f
                        minVal = rounded.coerceIn(deviceMinZoom, maxVal - 0.5f)
                    },
                    valueRange = deviceMinZoom..(maxVal - 0.5f).coerceAtLeast(deviceMinZoom + 0.1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E5FF),
                        activeTrackColor = Color(0xFF00E5FF)
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // End Zoom (Max) Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "End Zoom (Max)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(text = String.format(Locale.US, "%.1fx", maxVal), color = Color(0xFFFFD600), fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
                Slider(
                    value = maxVal,
                    onValueChange = { newVal ->
                        val rounded = Math.round(newVal * 10f) / 10f
                        maxVal = rounded.coerceIn(minVal + 0.5f, deviceMaxZoom)
                    },
                    valueRange = (minVal + 0.5f).coerceAtMost(deviceMaxZoom - 0.1f)..deviceMaxZoom,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFFD600),
                        activeTrackColor = Color(0xFFFFD600)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = "Cancel", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onApply(minVal, maxVal) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "Apply Range", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomPresetChip(
    label: String,
    min: Float,
    max: Float,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E2328),
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(0.6.dp, Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else Color.LightGray,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
        )
    }
}
