package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.UiState
import com.example.ui.components.UserAvatar
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatBrandGradient
import com.example.ui.theme.PChatTheme
import com.example.ui.theme.VoidBlack
import com.example.ui.viewmodel.PChatViewModel
import kotlinx.coroutines.delay

@Composable
fun CallScreen(
    callId: String,
    viewModel: PChatViewModel,
    onEndCall: () -> Unit
) {
    val colors = PChatTheme.colors
    val callsState by viewModel.calls.collectAsStateWithLifecycle()
    val call = (callsState as? UiState.Success)?.data?.firstOrNull { it.id == callId }

    val isVideoCall = call?.callType?.contains("video") != false
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(true) }
    var isCameraEnabled by remember(isVideoCall) { mutableStateOf(isVideoCall) }
    var elapsedSec by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            elapsedSec += 1
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "call_ring")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.surface,
                        NeonPurple.copy(alpha = if (colors.isDark) 0.25f else 0.08f),
                        colors.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = colors.card,
                        shape = CircleShape,
                        modifier = Modifier.border(1.dp, colors.border, CircleShape)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(colors.accentEmerald)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (call?.callType) {
                                    "group_video" -> "GROUP CALL"
                                    "group_voice" -> "GROUP VOICE"
                                    "video" -> "P-CHAT CALL"
                                    else -> "VOICE CALL"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accentBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = call?.title ?: "P-CHAT Call",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val mm = "%02d".format(elapsedSec / 60)
                    val ss = "%02d".format(elapsedSec % 60)
                    Text(
                        text = "${call?.status?.uppercase() ?: "CONNECTED"} • $mm:$ss",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.accentEmerald,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Center Call Avatar + Video Coming Soon notice when camera is toggled on
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(ringScale)
                        .clip(CircleShape)
                        .border(3.dp, PChatBrandGradient, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    UserAvatar(
                        name = call?.title ?: "P-CHAT",
                        isOnline = true,
                        isLive = true,
                        size = 136.dp
                    )
                }

                if (isCameraEnabled) {
                    Surface(
                        color = colors.card,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .testTag("call_camera_coming_soon_banner")
                            .border(1.dp, NeonPurple.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    ) {
                        Text(
                            text = "Video Camera • Coming Soon\nAudio call active cleanly without camera access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.accentBlue,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Call Controls Dock
            Surface(
                color = colors.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border, RoundedCornerShape(32.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) colors.accentBlue else colors.card)
                    ) {
                        Icon(
                            if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = "Mute microphone",
                            tint = if (isMuted) colors.primaryButtonContent else colors.textPrimary
                        )
                    }

                    IconButton(
                        onClick = { isCameraEnabled = !isCameraEnabled },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isCameraEnabled) ElectricBlue else colors.card)
                    ) {
                        Icon(
                            if (isCameraEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                            contentDescription = "Video camera coming soon",
                            tint = if (isCameraEnabled) VoidBlack else colors.textPrimary
                        )
                    }

                    IconButton(
                        onClick = { isSpeaker = !isSpeaker },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isSpeaker) NeonPurple else colors.card)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Toggle speaker",
                            tint = if (isSpeaker) colors.onGradientText else colors.textPrimary
                        )
                    }

                    IconButton(
                        onClick = {
                            viewModel.updateCallStatus(callId, "ended")
                            onEndCall()
                        },
                        modifier = Modifier
                            .testTag("end_call_button")
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(ErrorCrimson)
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = "End call", tint = Color.White)
                    }
                }
            }
        }
    }
}
