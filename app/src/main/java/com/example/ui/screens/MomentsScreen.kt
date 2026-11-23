package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Moment
import com.example.model.UiState
import com.example.ui.components.DeviceCameraCaptureDialog
import com.example.ui.components.UserAvatar
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.HotPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatBrandGradient
import com.example.ui.theme.PChatTheme
import com.example.ui.theme.VoidBlack
import com.example.ui.viewmodel.PChatViewModel
import com.example.util.LoopingMp4Player
import com.example.util.MediaUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsScreen(
    viewModel: PChatViewModel,
    onOpenCamera: () -> Unit
) {
    val context = LocalContext.current
    val colors = PChatTheme.colors
    val momentsState by viewModel.moments.collectAsStateWithLifecycle()
    val localMomentVideos by viewModel.localMomentVideos.collectAsStateWithLifecycle()
    val moments = (momentsState as? UiState.Success)?.data.orEmpty()
    val stories = moments.filter { it.isStory }

    var showComposeDialog by remember { mutableStateOf(false) }
    var composeCaption by remember { mutableStateOf("") }
    var composeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var composeVideoPath by remember { mutableStateOf<String?>(null) }
    var composeAsStory by remember { mutableStateOf(false) }
    var composeMediaType by remember { mutableStateOf("status") }
    var showCameraInComposer by remember { mutableStateOf(false) }

    var activeStory by remember { mutableStateOf<Moment?>(null) }
    var activeCommentMoment by remember { mutableStateOf<Moment?>(null) }
    var activePlaybackVideoPath by remember { mutableStateOf<String?>(null) }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            composeBitmap = MediaUtils.uriToBitmap(context, uri, 640)
            composeMediaType = "photo"
            showComposeDialog = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Moments", style = MaterialTheme.typography.headlineLarge, color = colors.textPrimary)
                Text("Daily stories & updates", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onOpenCamera,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.card)
                        .border(1.dp, colors.border, CircleShape)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Open camera", tint = HotPink)
                }
                IconButton(
                    onClick = {
                        composeBitmap = null
                        composeVideoPath = null
                        composeMediaType = "status"
                        showComposeDialog = true
                    },
                    modifier = Modifier
                        .testTag("new_moment_button")
                        .clip(CircleShape)
                        .background(PChatBrandGradient)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Post status or moment", tint = colors.onGradientText)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Daily Stories Row
            item {
                Column {
                    Text(
                        text = "Daily Stories",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Add Your Story tile
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    composeAsStory = true
                                    mediaPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .background(colors.card)
                                        .border(2.dp, colors.accentBlue, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Add story", tint = colors.accentBlue)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Add Story", style = MaterialTheme.typography.labelSmall, color = colors.accentBlue)
                            }
                        }

                        items(stories, key = { it.id }) { story ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(68.dp)
                                    .clickable { activeStory = story }
                            ) {
                                UserAvatar(
                                    name = story.authorName,
                                    colorHex = "#FF2E93",
                                    showStoryRing = true,
                                    size = 62.dp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = story.authorName.substringBefore(" "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Quick Post Status / Photo / Video Bar
            item {
                Surface(
                    color = colors.card,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                composeBitmap = null
                                composeVideoPath = null
                                composeMediaType = "status"
                                composeAsStory = false
                                showComposeDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Status", color = colors.accentBlue)
                        }
                        OutlinedButton(
                            onClick = {
                                composeAsStory = false
                                composeVideoPath = null
                                mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Photo", color = NeonPurple)
                        }
                        OutlinedButton(
                            onClick = onOpenCamera,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Snap", color = HotPink)
                        }
                    }
                }
            }

            // Moments Feed
            if (moments.isEmpty()) {
                item {
                    Surface(
                        color = colors.card,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .border(1.dp, NeonPurple.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(NeonPurple.copy(alpha = 0.2f))
                                    .border(1.dp, NeonPurple, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = NeonPurple)
                            }
                            Text(
                                "Share Your First Moment",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Live P-CHAT camera is coming soon — share photos from your Gallery or post a status update for friends.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = onOpenCamera,
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = colors.onGradientText),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Camera (Soon)")
                                }
                                OutlinedButton(
                                    onClick = {
                                        composeBitmap = null
                                        composeVideoPath = null
                                        composeMediaType = "status"
                                        showComposeDialog = true
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Write Status", color = colors.textPrimary)
                                }
                            }
                        }
                    }
                }
            } else {
                items(moments, key = { it.id }) { moment ->
                    val isLiked = viewModel.currentUserId in moment.likedByUids
                    MomentCard(
                        moment = moment,
                        localVideoPath = localMomentVideos[moment.id],
                        isLiked = isLiked,
                        onToggleLike = { viewModel.toggleLikeMoment(moment) },
                        onOpenComments = { activeCommentMoment = moment },
                        onPlayVideo = { path -> activePlaybackVideoPath = path }
                    )
                }
            }
        }
    }

    if (showComposeDialog) {
        AlertDialog(
            onDismissRequest = { showComposeDialog = false },
            containerColor = colors.surface,
            title = { Text(if (composeAsStory) "Post Daily Story" else "Share Moment", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (composeVideoPath != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(VoidBlack)
                        ) {
                            LoopingMp4Player(
                                videoPath = composeVideoPath!!,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else if (composeBitmap != null) {
                        Image(
                            bitmap = composeBitmap!!.asImageBitmap(),
                            contentDescription = "Selected photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showCameraInComposer = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = HotPink, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Camera (Soon)", color = colors.textPrimary)
                            }
                            OutlinedButton(
                                onClick = {
                                    mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = colors.accentBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gallery", color = colors.textPrimary)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = composeCaption,
                        onValueChange = { composeCaption = it },
                        label = { Text("What's on your mind?") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.card,
                            unfocusedContainerColor = colors.card,
                            focusedBorderColor = colors.accentBlue,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedLabelColor = colors.accentBlue,
                            unfocusedLabelColor = colors.textSecondary
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = composeAsStory, onCheckedChange = { composeAsStory = it })
                        Text("Feature in Daily Stories", color = colors.textPrimary)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val b64 = composeBitmap?.let { MediaUtils.bitmapToBase64(it, 640, 72) } ?: ""
                        viewModel.createMoment(
                            caption = composeCaption.ifBlank { "Living in neon ✨" },
                            mediaType = composeMediaType,
                            imageBase64 = b64,
                            isStory = composeAsStory,
                            localVideoPath = composeVideoPath.orEmpty()
                        ) {
                            composeCaption = ""
                            composeBitmap = null
                            composeVideoPath = null
                            showComposeDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentBlue,
                        contentColor = colors.primaryButtonContent
                    )
                ) {
                    Text("Post")
                }
            },
            dismissButton = {
                TextButton(onClick = { showComposeDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Full-screen Story Viewer (intentionally dark backdrop)
    if (activeStory != null) {
        val story = activeStory!!
        Dialog(
            onDismissRequest = { activeStory = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VoidBlack)
            ) {
                val bmp = remember(story.imageBase64) { MediaUtils.base64ToBitmap(story.imageBase64) }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Story image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(listOf(DeepNavy, NeonPurple.copy(alpha = 0.5f), VoidBlack))
                            )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VoidBlack.copy(alpha = 0.28f))
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(name = story.authorName, showStoryRing = true, size = 44.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(story.authorName, color = colors.onGradientText, fontWeight = FontWeight.Bold)
                                Text(MediaUtils.formatTimestamp(story.createdAt?.seconds), color = colors.onGradientText.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = { activeStory = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close story", tint = colors.onGradientText)
                        }
                    }

                    Surface(
                        color = VoidBlack.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    ) {
                        Text(
                            text = story.caption,
                            color = colors.onGradientText,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }

    // Comments Bottom Sheet
    if (activeCommentMoment != null) {
        val targetMoment = activeCommentMoment!!
        val commentsFlow = remember(targetMoment.id) { viewModel.observeMomentComments(targetMoment.id) }
        val comments by commentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
        var commentDraft by remember { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = { activeCommentMoment = null },
            containerColor = colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Comments (${comments.size})", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (comments.isEmpty()) {
                        item {
                            Text("Be the first to comment!", color = colors.textSecondary)
                        }
                    } else {
                        items(comments, key = { it.id }) { c ->
                            Surface(color = colors.card, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(c.authorName, color = colors.accentBlue, style = MaterialTheme.typography.labelLarge)
                                    Text(c.text, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = commentDraft,
                        onValueChange = { commentDraft = it },
                        placeholder = { Text("Write a comment...", color = colors.textMuted) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.card,
                            unfocusedContainerColor = colors.card,
                            focusedBorderColor = colors.accentBlue,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (commentDraft.isNotBlank()) {
                                viewModel.addMomentComment(targetMoment, commentDraft)
                                commentDraft = ""
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(PChatBrandGradient)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send comment", tint = colors.onGradientText)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showCameraInComposer) {
        DeviceCameraCaptureDialog(
            title = if (composeAsStory) "Story Camera" else "Moment Camera",
            allowVideo = true,
            onDismiss = { showCameraInComposer = false },
            onPhotoCaptured = { bmp, _ ->
                showCameraInComposer = false
                composeBitmap = bmp
                composeVideoPath = null
                composeMediaType = "photo"
                showComposeDialog = true
            },
            onVideoCaptured = { file, thumb, _, _ ->
                showCameraInComposer = false
                composeBitmap = thumb
                composeVideoPath = file.absolutePath
                composeMediaType = "video"
                showComposeDialog = true
            }
        )
    }

    if (activePlaybackVideoPath != null) {
        Dialog(
            onDismissRequest = { activePlaybackVideoPath = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VoidBlack)
            ) {
                LoopingMp4Player(
                    videoPath = activePlaybackVideoPath!!,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { activePlaybackVideoPath = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 42.dp, end = 16.dp)
                        .clip(CircleShape)
                        .background(VoidBlack.copy(alpha = 0.7f))
                        .border(1.dp, NeonPurple, CircleShape)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close video", tint = colors.onGradientText)
                }
            }
        }
    }
}

@Composable
private fun MomentCard(
    moment: Moment,
    localVideoPath: String?,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onOpenComments: () -> Unit,
    onPlayVideo: (String) -> Unit
) {
    val colors = PChatTheme.colors
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .border(1.dp, colors.border.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(
                    name = moment.authorName,
                    colorHex = "#9D4EDD",
                    showStoryRing = moment.isStory,
                    size = 42.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(moment.authorName, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text(
                        text = MediaUtils.formatTimestamp(moment.createdAt?.seconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                }
                if (moment.filterTag.isNotBlank()) {
                    Surface(color = colors.elevated, shape = CircleShape) {
                        Text(
                            text = moment.filterTag,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accentBlue,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (moment.caption.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(moment.caption, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            }

            val bmp = remember(moment.imageBase64) { MediaUtils.base64ToBitmap(moment.imageBase64) }
            if (bmp != null || localVideoPath != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(VoidBlack)
                        .clickable(enabled = localVideoPath != null) {
                            localVideoPath?.let(onPlayVideo)
                        }
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Moment photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (moment.mediaType == "video") {
                        Icon(
                            Icons.Filled.PlayCircle,
                            contentDescription = "Play video moment",
                            tint = colors.onGradientText.copy(alpha = 0.92f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(54.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onToggleLike)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like moment",
                        tint = if (isLiked) HotPink else colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${moment.likedByUids.size}", color = colors.textPrimary)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onOpenComments)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "Comment",
                        tint = colors.accentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${moment.commentCount}", color = colors.textPrimary)
                }
            }
        }
    }
}
