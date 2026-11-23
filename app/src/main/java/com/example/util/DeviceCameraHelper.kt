package com.example.util

import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.VoidBlack
import java.io.File
import java.util.Locale

enum class CameraPermissionStatus {
    GRANTED,
    NEEDS_REQUEST,
    PERMANENTLY_DENIED
}

fun evaluateCameraPermission(
    isGranted: Boolean,
    shouldShowRationale: Boolean,
    hasRequestedBefore: Boolean
): CameraPermissionStatus = when {
    isGranted -> CameraPermissionStatus.GRANTED
    hasRequestedBefore && !shouldShowRationale -> CameraPermissionStatus.PERMANENTLY_DENIED
    else -> CameraPermissionStatus.NEEDS_REQUEST
}

fun formatDurationMmSs(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
}

fun resolvePreferredLens(hasFront: Boolean, hasBack: Boolean, preferFront: Boolean): Boolean? {
    return when {
        preferFront && hasFront -> true
        !preferFront && hasBack -> false
        hasBack -> false
        hasFront -> true
        else -> null
    }
}

/**
 * Reliable, immediate-playback MP4 video player with play/pause & mute/unmute controls.
 */
@Composable
fun LoopingMp4Player(
    videoPath: String,
    modifier: Modifier = Modifier
) {
    val file = remember(videoPath) { File(videoPath) }
    var isPlaying by remember(videoPath) { mutableStateOf(true) }
    var isMuted by remember(videoPath) { mutableStateOf(false) }
    var playbackError by remember(videoPath) { mutableStateOf<String?>(null) }
    var mediaPlayerRef by remember(videoPath) { mutableStateOf<MediaPlayer?>(null) }
    var videoViewRef by remember(videoPath) { mutableStateOf<VideoView?>(null) }

    LaunchedEffect(isMuted, mediaPlayerRef) {
        val vol = if (isMuted) 0f else 1f
        runCatching { mediaPlayerRef?.setVolume(vol, vol) }
    }

    DisposableEffect(videoPath) {
        onDispose {
            mediaPlayerRef = null
            runCatching { videoViewRef?.stopPlayback() }
        }
    }

    Box(
        modifier = modifier
            .background(VoidBlack)
            .clickable(enabled = playbackError == null) {
                val view = videoViewRef ?: return@clickable
                if (view.isPlaying) {
                    view.pause()
                    isPlaying = false
                } else {
                    view.start()
                    isPlaying = true
                }
            }
    ) {
        if (!file.exists() || file.length() == 0L) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = ErrorCrimson, modifier = Modifier.size(40.dp))
                Text("Recorded video file unavailable", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        } else if (playbackError != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = ErrorCrimson, modifier = Modifier.size(40.dp))
                Text(playbackError!!, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            val uri = remember(videoPath) { Uri.fromFile(file) }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        videoViewRef = this
                        tag = videoPath
                        setZOrderMediaOverlay(true)
                        setVideoURI(uri)
                        setOnPreparedListener { mp ->
                            mediaPlayerRef = mp
                            mp.isLooping = true
                            val vol = if (isMuted) 0f else 1f
                            runCatching { mp.setVolume(vol, vol) }
                            start()
                            isPlaying = true
                            playbackError = null
                        }
                        setOnErrorListener { _, what, extra ->
                            playbackError = "Video playback error ($what/$extra)"
                            isPlaying = false
                            true
                        }
                    }
                },
                update = { view ->
                    videoViewRef = view
                    if (view.tag != videoPath) {
                        view.tag = videoPath
                        view.setVideoURI(uri)
                        view.start()
                        isPlaying = true
                    }
                },
                onRelease = { view ->
                    runCatching { view.stopPlayback() }
                }
            )

            // Play indicator when paused
            if (!isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(VoidBlack.copy(alpha = 0.72f))
                        .border(1.5.dp, NeonPurple, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play video", tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }

            // Bottom-right Play/Pause & Mute controls
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = VoidBlack.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.border(1.dp, NeonPurple.copy(alpha = 0.6f), RoundedCornerShape(50))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val view = videoViewRef ?: return@IconButton
                                if (view.isPlaying) {
                                    view.pause()
                                    isPlaying = false
                                } else {
                                    view.start()
                                    isPlaying = true
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause video" else "Play video",
                                tint = Color(0xFFE0AAFF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        IconButton(
                            onClick = { isMuted = !isMuted },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isMuted) "Unmute video" else "Mute video",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
