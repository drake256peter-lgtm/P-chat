package com.example.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatBrandGradient
import com.example.ui.theme.PChatTheme
import com.example.util.MediaUtils
import com.example.util.SnapFilter
import java.io.File

/**
 * In-context Camera Coming Soon modal usable from Chat, MALAK AI, and Moments,
 * preserving Gallery photo selection via Android Photo Picker.
 */
@Composable
fun DeviceCameraCaptureDialog(
    title: String = "P-CHAT Camera",
    @Suppress("UNUSED_PARAMETER") allowVideo: Boolean = false,
    @Suppress("UNUSED_PARAMETER") allowFilters: Boolean = false,
    @Suppress("UNUSED_PARAMETER") defaultFrontCamera: Boolean = false,
    onDismiss: () -> Unit,
    onPhotoCaptured: (Bitmap, SnapFilter) -> Unit,
    @Suppress("UNUSED_PARAMETER") onVideoCaptured: (File, Bitmap?, Int, SnapFilter) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val colors = PChatTheme.colors

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = MediaUtils.uriToBitmap(context, uri, 960)
            if (bmp != null) {
                onPhotoCaptured(bmp, SnapFilter.NONE)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .testTag("camera_coming_soon_dialog")
                .fillMaxWidth(0.90f)
                .border(1.5.dp, PChatBrandGradient, RoundedCornerShape(26.dp))
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.textSecondary)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(NeonPurple.copy(alpha = 0.25f), ElectricBlue.copy(alpha = 0.22f))
                            )
                        )
                        .border(1.5.dp, PChatBrandGradient, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Camera coming soon",
                        tint = ElectricBlue,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Text(
                    text = "Camera • Coming Soon",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.accentBlue,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Live camera capture is coming soon to P-CHAT. Pick an existing photo from your Gallery to continue immediately.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = {
                        galleryPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .testTag("dialog_pick_gallery_button")
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple,
                        contentColor = colors.onGradientText
                    )
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pick Photo from Gallery", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Close", color = colors.textPrimary)
                }
            }
        }
    }
}
