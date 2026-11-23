package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.HotPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatBrandGradient
import com.example.ui.theme.PChatTheme
import com.example.ui.theme.VoidBlack
import com.example.ui.viewmodel.PChatViewModel
import com.example.util.MediaUtils

private val PurpleSurfaceCard = Color(0xFF0E0818)
private val PurpleBorderTone = Color(0xFF5A189A)
private val NeonLavender = Color(0xFFE0AAFF)

@Composable
fun PlusCameraScreen(
    viewModel: PChatViewModel,
    onClose: () -> Unit,
    onPostedMoment: () -> Unit,
    onEditInAiStudio: (Bitmap) -> Unit
) {
    val colors = PChatTheme.colors
    val context = LocalContext.current

    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var snapCaption by remember { mutableStateOf("") }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val raw = MediaUtils.uriToBitmap(context, uri, 960)
            if (raw != null) {
                selectedBitmap = raw
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (colors.isDark) {
                    Brush.verticalGradient(listOf(VoidBlack, Color(0xFF100626), VoidBlack))
                } else {
                    Brush.verticalGradient(listOf(colors.background, colors.surface, colors.background))
                }
            )
    ) {
        if (selectedBitmap != null) {
            // Post-selection Gallery Photo Composer (Story, Moment, Save, Share, AI Studio)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VoidBlack)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { selectedBitmap = null },
                        modifier = Modifier
                            .testTag("retake_capture_button")
                            .clip(CircleShape)
                            .background(PurpleSurfaceCard)
                            .border(1.dp, PurpleBorderTone, CircleShape)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Discard selection", tint = Color.White)
                    }
                    Text(
                        text = "Share Media",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                val res = selectedBitmap?.let {
                                    MediaUtils.saveBitmapToGallery(context, it)
                                } ?: Result.failure(IllegalStateException("No photo selected"))
                                Toast.makeText(
                                    context,
                                    res.getOrElse { it.localizedMessage ?: "Could not save photo" },
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .testTag("save_capture_button")
                                .clip(CircleShape)
                                .background(PurpleSurfaceCard)
                                .border(1.dp, ElectricBlue.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = "Save to gallery", tint = ElectricBlue)
                        }

                        IconButton(
                            onClick = {
                                val res = selectedBitmap?.let {
                                    MediaUtils.shareBitmapViaChooser(
                                        context = context,
                                        bitmap = it,
                                        caption = snapCaption
                                    )
                                } ?: Result.failure(IllegalStateException("No photo selected"))
                                res.onFailure { err ->
                                    Toast.makeText(
                                        context,
                                        err.localizedMessage ?: "Could not open share sheet",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier
                                .testTag("share_capture_external_button")
                                .clip(CircleShape)
                                .background(PurpleSurfaceCard)
                                .border(1.dp, NeonPurple, CircleShape)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Share media", tint = NeonLavender)
                        }

                        IconButton(
                            onClick = { selectedBitmap?.let(onEditInAiStudio) },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(PurpleSurfaceCard)
                                .border(1.dp, HotPink.copy(alpha = 0.85f), CircleShape)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "Edit in AI Studio", tint = HotPink)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(VoidBlack)
                        .border(1.5.dp, PChatBrandGradient, RoundedCornerShape(24.dp))
                ) {
                    Image(
                        bitmap = selectedBitmap!!.asImageBitmap(),
                        contentDescription = "Selected photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = snapCaption,
                    onValueChange = { snapCaption = it },
                    placeholder = { Text("Add a caption to share...", color = Color(0xFF9C89B8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonPurple,
                        unfocusedBorderColor = PurpleBorderTone,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = PurpleSurfaceCard,
                        unfocusedContainerColor = PurpleSurfaceCard
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { selectedBitmap = null },
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PurpleBorderTone)
                    ) {
                        Text("Back", color = Color.White)
                    }
                    Button(
                        onClick = {
                            val b64 = selectedBitmap?.let { MediaUtils.bitmapToBase64(it, 640, 72) }.orEmpty()
                            viewModel.createMoment(
                                caption = snapCaption.ifBlank { "Daily Story" },
                                mediaType = "photo",
                                filterTag = "",
                                imageBase64 = b64,
                                isStory = true,
                                localVideoPath = "",
                                onDone = onPostedMoment
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Story", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val b64 = selectedBitmap?.let { MediaUtils.bitmapToBase64(it, 640, 72) }.orEmpty()
                            viewModel.createMoment(
                                caption = snapCaption.ifBlank { "Shared on P-CHAT" },
                                mediaType = "photo",
                                filterTag = "",
                                imageBase64 = b64,
                                isStory = false,
                                localVideoPath = "",
                                onDone = onPostedMoment
                            )
                        },
                        modifier = Modifier
                            .weight(1.1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = VoidBlack),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Post", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Camera • Coming Soon View with Gallery Photo Sharing
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .testTag("camera_coming_soon_close")
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.card)
                            .border(1.dp, colors.border, CircleShape)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.textPrimary)
                    }

                    Surface(
                        color = NeonPurple.copy(alpha = 0.18f),
                        shape = CircleShape,
                        modifier = Modifier.border(1.dp, PChatBrandGradient, CircleShape)
                    ) {
                        Text(
                            text = "CAMERA • COMING SOON",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.accentBlue,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }

                    Spacer(modifier = Modifier.size(48.dp))
                }

                Surface(
                    color = colors.card,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                        .border(1.5.dp, PChatBrandGradient, RoundedCornerShape(28.dp))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(26.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(NeonPurple.copy(alpha = 0.28f), ElectricBlue.copy(alpha = 0.24f))
                                    )
                                )
                                .border(1.5.dp, PChatBrandGradient, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Camera coming soon",
                                tint = ElectricBlue,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Text(
                            text = "Camera • Coming Soon",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Live camera capture and video recording are temporarily paused while we upgrade our hardware pipeline. You can still pick photos from your Gallery to post Moments, Stories, or edit in MALAK AI Studio.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                galleryPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .testTag("camera_pick_gallery_button")
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonPurple,
                                contentColor = colors.onGradientText
                            )
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pick Photo from Gallery", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
