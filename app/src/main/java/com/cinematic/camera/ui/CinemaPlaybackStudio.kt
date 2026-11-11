package com.cinematic.camera.ui

import android.content.Context
import android.content.Intent
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cinematic.camera.engine.DollyZoomNativeBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cinema Playback Studio.
 * Everyday consumer-friendly review player supporting both 16:9 landscape and 9:16 portrait
 * videos with zero squishing or distortion.
 */
@Composable
fun CinemaPlaybackStudio(
    take: CinematicTake,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(true) }
    var durationMs by remember { mutableIntStateOf((take.durationSec * 1000).toInt()) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }

    var videoWidth by remember { mutableIntStateOf(1920) }
    var videoHeight by remember { mutableIntStateOf(1080) }

    // Read natural video dimensions & orientation from MediaMetadataRetriever
    LaunchedEffect(take.uri) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, take.uri)
            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val rotation = rStr?.toIntOrNull() ?: 0
            val rawW = wStr?.toIntOrNull() ?: 1920
            val rawH = hStr?.toIntOrNull() ?: 1080
            if (rotation == 90 || rotation == 270) {
                videoWidth = rawH
                videoHeight = rawW
            } else {
                videoWidth = rawW
                videoHeight = rawH
            }
            retriever.release()
        } catch (ignored: Exception) {}
    }

    // Live position polling loop
    LaunchedEffect(mediaPlayer, isPlaying, isScrubbing) {
        while (isActive) {
            val mp = mediaPlayer
            if (mp != null && isPlaying && !isScrubbing) {
                try {
                    currentPositionMs = mp.currentPosition
                } catch (ignored: Exception) {}
            }
            delay(40)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (ignored: Exception) {}
            mediaPlayer = null
        }
    }

    val naturalAspectRatio = if (videoWidth > 0 && videoHeight > 0) {
        videoWidth.toFloat() / videoHeight.toFloat()
    } else {
        16f / 9f
    }
    val isVerticalVideo = naturalAspectRatio < 1.0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0D0F))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Navigation Bar
            StudioTopBar(
                take = take,
                onClose = onClose,
                onShare = { shareTake(context, take.uri) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Video Viewport (Adapts dynamically to both 16:9 and 9:16 with zero squishing/distortion)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                    .clickable {
                        mediaPlayer?.let { mp ->
                            if (mp.isPlaying) {
                                mp.pause()
                                isPlaying = false
                            } else {
                                mp.start()
                                isPlaying = true
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val videoModifier = if (isVerticalVideo) {
                    Modifier
                        .height(460.dp)
                        .aspectRatio(naturalAspectRatio)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(naturalAspectRatio)
                }

                Box(
                    modifier = videoModifier,
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                        val surface = Surface(st)
                                        val mp = MediaPlayer().apply {
                                            setDataSource(ctx, take.uri)
                                            setSurface(surface)
                                            this.isLooping = isLooping
                                            setOnVideoSizeChangedListener { _, width, height ->
                                                if (width > 0 && height > 0) {
                                                    videoWidth = width
                                                    videoHeight = height
                                                }
                                            }
                                            setOnPreparedListener { preparedMp ->
                                                durationMs = preparedMp.duration.coerceAtLeast(1)
                                                preparedMp.start()
                                                isPlaying = true
                                            }
                                            prepareAsync()
                                        }
                                        mediaPlayer = mp
                                    }
                                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                        try {
                                            mediaPlayer?.stop()
                                            mediaPlayer?.release()
                                        } catch (ignored: Exception) {}
                                        mediaPlayer = null
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Big play icon indicator when paused
                if (!isPlaying) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.60f),
                        shape = CircleShape,
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Timeline Scrubber Track
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                val progress = if (durationMs > 0) {
                    if (isScrubbing) scrubValue else currentPositionMs.toFloat() / durationMs.toFloat()
                } else 0f

                Slider(
                    value = progress.coerceIn(0f, 1f),
                    onValueChange = { newVal ->
                        isScrubbing = true
                        scrubValue = newVal
                    },
                    onValueChangeFinished = {
                        val seekTargetMs = (scrubValue * durationMs).toInt()
                        mediaPlayer?.seekTo(seekTargetMs)
                        currentPositionMs = seekTargetMs
                        isScrubbing = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E5FF),
                        activeTrackColor = Color(0xFF00E5FF),
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Timecode Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimecode(if (isScrubbing) (scrubValue * durationMs).toInt() else currentPositionMs),
                        color = Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = formatTimecode(durationMs),
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Transport Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rewind 1s
                IconButton(
                    onClick = {
                        mediaPlayer?.let { mp ->
                            val target = (mp.currentPosition - 1000).coerceAtLeast(0)
                            mp.seekTo(target)
                            currentPositionMs = target
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Back 1s",
                        tint = Color.LightGray
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Play / Pause Circle Button
                Surface(
                    color = Color(0xFF1B2026),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E5FF)),
                    modifier = Modifier
                        .size(54.dp)
                        .clickable {
                            mediaPlayer?.let { mp ->
                                if (mp.isPlaying) {
                                    mp.pause()
                                    isPlaying = false
                                } else {
                                    mp.start()
                                    isPlaying = true
                                }
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Loop Toggle Button
                Surface(
                    color = if (isLooping) Color(0xFF00E676).copy(alpha = 0.2f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        if (isLooping) Color(0xFF00E676) else Color.Gray.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.clickable {
                        val newLoop = !isLooping
                        isLooping = newLoop
                        mediaPlayer?.isLooping = newLoop
                    }
                ) {
                    Text(
                        text = if (isLooping) "⟳ Repeat On" else "⟳ Repeat Off",
                        color = if (isLooping) Color(0xFF00E676) else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 5. Shot Details Card (Consumer friendly)
            ShotDetailsCard(take = take)

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

/**
 * Top Studio Navigation Bar.
 */
@Composable
private fun StudioTopBar(
    take: CinematicTake,
    onClose: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back to Camera button
        Surface(
            color = Color(0xFF14171A),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier.clickable(onClick = onClose)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Camera",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        // Title Tag
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Shot Review",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )
            Text(
                text = SimpleDateFormat("h:mm a • MMM d", Locale.getDefault()).format(Date(take.timestampMs)),
                color = Color.Gray,
                fontSize = 9.5.sp
            )
        }

        // Share Action Button
        Surface(
            color = Color(0xFF00E5FF),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.clickable(onClick = onShare)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Share",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Clean Shot Details Card.
 * Everyday human-friendly labels explaining camera movement, zoom range, and quality.
 */
@Composable
private fun ShotDetailsCard(take: CinematicTake) {
    val moveText = if (take.direction == DollyZoomNativeBridge.Direction.DOLLY_OUT) {
        "Walk Backward (Zooming In)"
    } else {
        "Walk Forward (Zooming Out)"
    }

    val styleText = when (take.curve) {
        DollyZoomNativeBridge.Curve.LINEAR -> "Smooth Constant Pace"
        DollyZoomNativeBridge.Curve.S_CURVE -> "Gentle Start & Stop"
        DollyZoomNativeBridge.Curve.DRAMATIC -> "Dramatic Crash Zoom"
    }

    Surface(
        color = Color(0xFF14171A).copy(alpha = 0.90f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Shot Information",
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Surface(
                    color = Color(0xFF00E676).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Saved to Gallery",
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailItem(label = "Camera Move", value = moveText)
                DetailItem(label = "Zoom Range", value = String.format(Locale.US, "%.1fx ➔ %.1fx", take.startZoom, take.endZoom))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailItem(label = "Duration", value = "${take.pace.label} (${take.durationSec}s)")
                DetailItem(label = "Zoom Style", value = styleText)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailItem(label = "Video Quality", value = "1080p Full HD (${take.targetFps} FPS)")
                DetailItem(label = "Focus Actuation", value = take.focusMode.label)
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatTimecode(ms: Int): String {
    val totalSec = ms / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    val tenths = (ms % 1000) / 100
    return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
}

private fun shareTake(context: Context, uri: Uri) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Cinematic Video"))
    } catch (e: Exception) {
        android.util.Log.e("CinemaPlaybackStudio", "Failed to share video", e)
    }
}
