package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.provider.OpenableColumns
import android.widget.Toast
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.CallSession
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.model.UiState
import com.example.ui.components.DeviceCameraCaptureDialog
import com.example.ui.components.UserAvatar
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.HotPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatBrandGradient
import com.example.ui.theme.PChatTheme
import com.example.ui.theme.VoidBlack
import com.example.ui.viewmodel.PChatViewModel
import com.example.util.LoopingMp4Player
import com.example.util.MediaUtils
import com.example.util.SnapFilter
import com.example.util.VoiceNotePlayer
import com.example.util.VoiceNoteRecorder
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun ChatDetailScreen(
    conversationId: String,
    viewModel: PChatViewModel,
    onBack: () -> Unit,
    onCallStarted: (CallSession) -> Unit,
    onOpenWalletForRecipient: (String?) -> Unit = {}
) {
    val context = LocalContext.current
    val colors = PChatTheme.colors
    val conversationsState by viewModel.conversations.collectAsStateWithLifecycle()
    val conversation = (conversationsState as? UiState.Success)?.data
        ?.firstOrNull { it.id == conversationId }

    val messagesFlow = remember(conversationId) { viewModel.observeMessages(conversationId) }
    val messages by messagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var draft by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var searchInThread by remember { mutableStateOf("") }
    var showEmojiTray by remember { mutableStateOf(false) }
    var reactionTargetMsgId by remember { mutableStateOf<String?>(null) }

    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var playingMsgId by remember { mutableStateOf<String?>(null) }
    var showCameraCapture by remember { mutableStateOf(false) }
    var activeVideoPath by remember { mutableStateOf<String?>(null) }

    val voiceRecorder = remember(context) { VoiceNoteRecorder(context) }
    val voicePlayer = remember { VoiceNotePlayer() }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.stopAndDiscard()
            voicePlayer.stop()
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordingSeconds = 0
            while (isRecordingVoice) {
                delay(1000L)
                recordingSeconds += 1
            }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && conversation != null) {
            val bmp = MediaUtils.uriToBitmap(context, uri, 640)
            if (bmp != null) {
                val base64 = MediaUtils.bitmapToBase64(bmp, 640, 72)
                viewModel.sendMessage(
                    conversation = conversation,
                    text = "Shared a photo",
                    messageType = "image",
                    imageBase64 = base64
                )
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && conversation != null) {
            var fileName = "Shared File"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    fileName = cursor.getString(idx) ?: fileName
                }
            }
            viewModel.sendMessage(
                conversation = conversation,
                text = fileName,
                messageType = "file",
                mediaMeta = fileName
            )
        }
    }

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isRecordingVoice = voiceRecorder.startRecording()
        } else {
            Toast.makeText(context, "Microphone permission is required to record voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Header
        Surface(color = colors.surface, tonalElevation = 6.dp) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                    }
                    UserAvatar(
                        name = conversation?.title ?: "Chat",
                        colorHex = if (conversation?.isGroup == true) "#9D4EDD" else "#00D4FF",
                        isOnline = true,
                        size = 42.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = conversation?.title ?: "Conversation",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = if (conversation?.isGroup == true) "${conversation.memberUids.size} members • Online" else "Online now",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accentEmerald
                        )
                    }
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search messages", tint = colors.textSecondary)
                    }
                    if (conversation != null) {
                        val isPinned = viewModel.currentUserId in conversation.pinnedByUids
                        IconButton(onClick = { viewModel.togglePinConversation(conversation) }) {
                            Icon(
                                if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = "Pin chat",
                                tint = if (isPinned) colors.accentBlue else colors.textSecondary
                            )
                        }
                        IconButton(
                            onClick = {
                                val type = if (conversation.isGroup) "group_voice" else "voice"
                                viewModel.startCall(conversation, type, onCallStarted)
                            },
                            modifier = Modifier.testTag("start_voice_call_button")
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = "Voice call", tint = colors.accentBlue)
                        }
                        IconButton(
                            onClick = {
                                val type = if (conversation.isGroup) "group_video" else "video"
                                viewModel.startCall(conversation, type, onCallStarted)
                            },
                            modifier = Modifier.testTag("start_video_call_button")
                        ) {
                            Icon(Icons.Filled.Videocam, contentDescription = "HD Video call", tint = HotPink)
                        }
                    }
                }

                if (showSearch) {
                    OutlinedTextField(
                        value = searchInThread,
                        onValueChange = { searchInThread = it },
                        placeholder = { Text("Search in conversation...", color = colors.textMuted) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.card,
                            unfocusedContainerColor = colors.card,
                            focusedBorderColor = colors.accentBlue,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                }
            }
        }

        // Messages list
        val displayedMessages = if (searchInThread.isBlank()) {
            messages
        } else {
            messages.filter { it.text.contains(searchInThread, ignoreCase = true) }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(displayedMessages, key = { it.id }) { msg ->
                val isMine = msg.senderId == viewModel.currentUserId
                MessageBubble(
                    message = msg,
                    isMine = isMine,
                    isPlayingVoice = playingMsgId == msg.id,
                    showReactionBar = reactionTargetMsgId == msg.id,
                    onBubbleTap = {
                        reactionTargetMsgId = if (reactionTargetMsgId == msg.id) null else msg.id
                    },
                    onReact = { emoji ->
                        reactionTargetMsgId = null
                        viewModel.toggleMessageReaction(conversationId, msg, emoji)
                    },
                    onPlayVoice = {
                        if (playingMsgId == msg.id) {
                            voicePlayer.stop()
                            playingMsgId = null
                        } else if (msg.mediaMeta.contains("|")) {
                            val localPath = msg.mediaMeta.substringAfter("|")
                            playingMsgId = msg.id
                            voicePlayer.playFile(localPath) { playingMsgId = null }
                        }
                    },
                    onPlayVideo = { path ->
                        if (File(path).exists()) {
                            activeVideoPath = path
                        }
                    }
                )
            }
        }

        // Quick Emoji tray
        if (showEmojiTray) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf("😀", "🔥", "💜", "😂", "🎉", "✨", "🙌", "😍").forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { draft += emoji }
                            .padding(6.dp)
                    )
                }
            }
        }

        // Voice recording indicator bar
        if (isRecordingVoice && conversation != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HotPink.copy(alpha = 0.16f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recording voice note • ${recordingSeconds}s",
                    color = HotPink,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    IconButton(onClick = {
                        voiceRecorder.stopAndDiscard()
                        isRecordingVoice = false
                    }) {
                        Icon(Icons.Filled.Stop, contentDescription = "Cancel voice note", tint = colors.textSecondary)
                    }
                    IconButton(onClick = {
                        val recorded = voiceRecorder.stopRecording()
                        isRecordingVoice = false
                        if (recorded != null) {
                            val (file, sec) = recorded
                            viewModel.sendMessage(
                                conversation = conversation,
                                text = "Voice Note (${sec}s)",
                                messageType = "voice",
                                mediaMeta = "${sec}s|${file.absolutePath}"
                            )
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send voice note", tint = colors.accentBlue)
                    }
                }
            }
        }

        // Composer
        Surface(color = colors.surface, tonalElevation = 8.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showCameraCapture = true },
                    modifier = Modifier.testTag("chat_camera_button")
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = "Camera (coming soon)", tint = HotPink)
                }
                IconButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.testTag("attach_photo_button")
                ) {
                    Icon(Icons.Filled.Image, contentDescription = "Share photo", tint = colors.accentBlue)
                }
                IconButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.testTag("attach_file_button")
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "Share file", tint = NeonPurple)
                }
                val peerUid = conversation?.memberUids?.firstOrNull { it != viewModel.currentUserId }
                if (peerUid != null) {
                    IconButton(
                        onClick = { onOpenWalletForRecipient(peerUid) },
                        modifier = Modifier.testTag("send_gift_button")
                    ) {
                        Icon(Icons.Filled.CardGiftcard, contentDescription = "Send wallet gift", tint = HotPink)
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier
                        .testTag("chat_message_input")
                        .weight(1f),
                    placeholder = { Text("Message P-CHAT...", color = colors.textMuted) },
                    trailingIcon = {
                        IconButton(onClick = { showEmojiTray = !showEmojiTray }) {
                            Icon(Icons.Filled.AddReaction, contentDescription = "Emojis", tint = HotPink)
                        }
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.card,
                        unfocusedContainerColor = colors.card,
                        focusedBorderColor = colors.accentBlue,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (draft.isBlank()) {
                    IconButton(
                        onClick = {
                            if (isRecordingVoice && conversation != null) {
                                val recorded = voiceRecorder.stopRecording()
                                isRecordingVoice = false
                                if (recorded != null) {
                                    val (file, sec) = recorded
                                    viewModel.sendMessage(
                                        conversation = conversation,
                                        text = "Voice Note (${sec}s)",
                                        messageType = "voice",
                                        mediaMeta = "${sec}s|${file.absolutePath}"
                                    )
                                }
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    isRecordingVoice = voiceRecorder.startRecording()
                                } else {
                                    audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier
                            .testTag("record_voice_button")
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(if (isRecordingVoice) HotPink else colors.elevated)
                    ) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "Record voice note",
                            tint = if (isRecordingVoice) colors.onGradientText else colors.textPrimary
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            val textToSend = draft.trim()
                            if (textToSend.isNotEmpty() && conversation != null) {
                                draft = ""
                                viewModel.sendMessage(conversation, textToSend, "text")
                            }
                        },
                        modifier = Modifier
                            .testTag("send_message_button")
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(PChatBrandGradient)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = colors.onGradientText
                        )
                    }
                }
            }
        }
    }

    if (showCameraCapture && conversation != null) {
        DeviceCameraCaptureDialog(
            title = "Chat Camera",
            allowVideo = true,
            onDismiss = { showCameraCapture = false },
            onPhotoCaptured = { bmp, _ ->
                showCameraCapture = false
                val b64 = MediaUtils.bitmapToBase64(bmp, 640, 72)
                val label = "Camera photo"
                viewModel.sendMessage(
                    conversation = conversation,
                    text = label,
                    messageType = "image",
                    imageBase64 = b64
                )
            },
            onVideoCaptured = { file, thumb, sec, _ ->
                showCameraCapture = false
                val b64 = thumb?.let { MediaUtils.bitmapToBase64(it, 480, 68) }.orEmpty()
                viewModel.sendMessage(
                    conversation = conversation,
                    text = "Video Snap (${sec}s)",
                    messageType = "video",
                    mediaMeta = "${sec}s|${file.absolutePath}",
                    imageBase64 = b64
                )
            }
        )
    }

    if (activeVideoPath != null) {
        Dialog(
            onDismissRequest = { activeVideoPath = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VoidBlack)
            ) {
                LoopingMp4Player(
                    videoPath = activeVideoPath!!,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { activeVideoPath = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .clip(CircleShape)
                        .background(VoidBlack.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close video", tint = colors.onGradientText)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    isPlayingVoice: Boolean,
    showReactionBar: Boolean,
    onBubbleTap: () -> Unit,
    onReact: (String) -> Unit,
    onPlayVoice: () -> Unit,
    onPlayVideo: (String) -> Unit
) {
    val colors = PChatTheme.colors
    val bubbleTextColor = if (isMine) colors.onGradientText else colors.textPrimary
    val bubbleSecondaryColor = if (isMine) colors.onGradientText.copy(alpha = 0.8f) else colors.textSecondary

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        if (!isMine) {
            Text(
                text = message.senderName,
                style = MaterialTheme.typography.labelSmall,
                color = colors.accentBlue,
                modifier = Modifier.padding(start = 6.dp, bottom = 3.dp)
            )
        }

        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isMine) 18.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 18.dp
                    )
                )
                .background(
                    if (isMine) {
                        Brush.linearGradient(listOf(CyberBlue, NeonPurple))
                    } else {
                        Brush.linearGradient(listOf(colors.card, colors.elevated))
                    }
                )
                .then(
                    if (!isMine) {
                        Modifier.border(
                            1.dp,
                            colors.border,
                            RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
                        )
                    } else Modifier
                )
                .clickable(onClick = onBubbleTap)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            when (message.messageType) {
                "image" -> {
                    Column {
                        val bmp = remember(message.imageBase64) {
                            MediaUtils.base64ToBitmap(message.imageBase64)
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Shared photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        Text(message.text, style = MaterialTheme.typography.bodyMedium, color = bubbleTextColor)
                    }
                }

                "video" -> {
                    val videoPath = message.mediaMeta.substringAfter("|", "")
                    val thumb = remember(message.imageBase64) {
                        MediaUtils.base64ToBitmap(message.imageBase64)
                    }
                    Column(
                        modifier = Modifier.clickable(enabled = videoPath.isNotBlank()) {
                            onPlayVideo(videoPath)
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(VoidBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb.asImageBitmap(),
                                    contentDescription = "Video snap thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Icon(
                                Icons.Filled.PlayCircle,
                                contentDescription = "Play video snap",
                                tint = colors.onGradientText.copy(alpha = 0.9f),
                                modifier = Modifier.size(52.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(message.text, style = MaterialTheme.typography.bodyMedium, color = bubbleTextColor)
                    }
                }

                "voice" -> {
                    val durLabel = message.mediaMeta.substringBefore("|").ifBlank { "Voice" }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onPlayVoice)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(bubbleTextColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlayingVoice) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = "Play voice note",
                                tint = bubbleTextColor
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Voice Note • $durLabel", color = bubbleTextColor, fontWeight = FontWeight.SemiBold)
                            Text("Tap to ${if (isPlayingVoice) "stop" else "play"}", style = MaterialTheme.typography.labelSmall, color = bubbleSecondaryColor)
                        }
                    }
                }

                "file" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = "File",
                            tint = if (isMine) colors.onGradientText else colors.accentBlue
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = message.mediaMeta.ifBlank { message.text },
                            color = bubbleTextColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                else -> {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = bubbleTextColor
                    )
                }
            }
        }

        // Reactions summary
        if (message.reactions.isNotEmpty()) {
            val grouped = message.reactions.map { it.substringBefore(":") }.groupingBy { it }.eachCount()
            Row(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                grouped.forEach { (emoji, count) ->
                    Surface(
                        color = colors.elevated,
                        shape = CircleShape,
                        modifier = Modifier.border(1.dp, colors.border, CircleShape)
                    ) {
                        Text(
                            text = "$emoji $count",
                            fontSize = 12.sp,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        if (showReactionBar) {
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("❤️", "🔥", "😂", "👍", "✨").forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onReact(emoji) }
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}
