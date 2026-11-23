package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.repository.MalakMode
import com.example.data.repository.StudioArtStyle
import com.example.data.repository.buildStyledImagePrompt
import com.example.model.UiState
import com.example.ui.components.DeviceCameraCaptureDialog
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.HotPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatBrandGradient
import com.example.ui.theme.PChatTheme
import com.example.ui.viewmodel.ImageStudioState
import com.example.ui.viewmodel.LiveVoiceState
import com.example.ui.viewmodel.MalakStreamState
import com.example.ui.viewmodel.PChatViewModel
import com.example.util.MediaUtils

@Composable
fun MalakAiScreen(
    viewModel: PChatViewModel,
    initialTab: Int = 0,
    initialEditBitmap: Bitmap? = null,
    onBack: () -> Unit,
    onNavigateToMoments: () -> Unit
) {
    val colors = PChatTheme.colors
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var studioPromptDraft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Top Header
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
                    Image(
                        painter = painterResource(id = R.drawable.img_malak_avatar),
                        contentDescription = "MALAK AI",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, PChatBrandGradient, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("MALAK AI", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                        Text(
                            "Creator Lab • Live Voice • AI Studio",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accentBlue
                        )
                    }
                    if (selectedTab == 0) {
                        IconButton(onClick = { viewModel.clearMalakHistory() }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear AI history", tint = colors.textSecondary)
                        }
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = colors.surface,
                    contentColor = colors.accentBlue
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Create & Chat") },
                        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Live Voice") },
                        icon = { Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("AI Studio") },
                        icon = { Icon(Icons.Filled.Brush, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }

        when (selectedTab) {
            0 -> MalakChatTab(
                viewModel = viewModel,
                onVisualizeInStudio = { ideaText ->
                    studioPromptDraft = ideaText.take(220)
                    selectedTab = 2
                }
            )
            1 -> MalakLiveVoiceTab(viewModel = viewModel)
            2 -> MalakImageStudioTab(
                viewModel = viewModel,
                initialEditBitmap = initialEditBitmap,
                initialPrompt = studioPromptDraft,
                onSharedToMoments = onNavigateToMoments
            )
        }
    }
}

private data class CoolCreationRecipe(
    val title: String,
    val mode: MalakMode,
    val prompt: String
)

private val COOL_CREATION_RECIPES = listOf(
    CoolCreationRecipe(
        title = "🎮 Design a Neon Mini-Game",
        mode = MalakMode.MAKER,
        prompt = "Design an original 2-player neon cyberpunk arcade mini-game we can play in chat or code, with rules, power-ups, and scoring."
    ),
    CoolCreationRecipe(
        title = "💻 Code a Jetpack Compose Card",
        mode = MalakMode.CODE_LAB,
        prompt = "Write a clean Kotlin Jetpack Compose animated neon profile badge card with purple and electric-blue gradient borders."
    ),
    CoolCreationRecipe(
        title = "🎵 Write a Hit Hook & Verse",
        mode = MalakMode.SONG_STORY,
        prompt = "Write a catchy Afrobeats x futuristic synth-pop chorus and 8-bar verse about chasing dreams under city lights."
    ),
    CoolCreationRecipe(
        title = "🧠 Build a 5-Question Quiz",
        mode = MalakMode.QUIZ_LAB,
        prompt = "Create an exciting 5-question multiple-choice quiz on Space & Futuristic Tech with an answer key at the bottom."
    ),
    CoolCreationRecipe(
        title = "✨ Viral P-CHAT Drop",
        mode = MalakMode.SOCIAL_CREATOR,
        prompt = "Create 3 bold P-CHAT bio lines, 2 viral Moment captions, and a cool night photography shot concept."
    )
)

@Composable
private fun MalakChatTab(
    viewModel: PChatViewModel,
    onVisualizeInStudio: (String) -> Unit
) {
    val colors = PChatTheme.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val historyState by viewModel.malakHistory.collectAsStateWithLifecycle()
    val streamState by viewModel.malakStreamState.collectAsStateWithLifecycle()
    val activeMode by viewModel.malakMode.collectAsStateWithLifecycle()

    var prompt by remember { mutableStateOf("") }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showCameraCapture by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            attachedBitmap = MediaUtils.uriToBitmap(context, uri, 800)
        }
    }

    val listState = rememberLazyListState()
    val messages = (historyState as? UiState.Success)?.data.orEmpty()

    LaunchedEffect(messages.size, streamState) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Mode chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(MalakMode.entries) { mode ->
                val selected = activeMode == mode
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setMalakMode(mode) },
                    label = { Text(mode.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = colors.card,
                        labelColor = colors.textSecondary,
                        selectedContainerColor = NeonPurple.copy(alpha = 0.22f),
                        selectedLabelColor = colors.accentBlue
                    )
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty() && streamState is MalakStreamState.Idle) {
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = colors.card,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.2.dp, PChatBrandGradient, RoundedCornerShape(22.dp))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                "MALAK AI Creator Studio ✨",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.accentBlue,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Make cool things with Gemini 3.8 Flash: invent mini-games, generate runnable Kotlin/web code, write hit lyrics, build quizzes, or pick photos for AI vision.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            COOL_CREATION_RECIPES.forEach { recipe ->
                                Surface(
                                    color = colors.elevated,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.dp, NeonPurple.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                        .clickable {
                                            viewModel.setMalakMode(recipe.mode)
                                            viewModel.askMalak(recipe.prompt)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = recipe.title,
                                            color = colors.textPrimary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            tint = NeonPurple,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                val isUser = msg.role == "user"
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isUser) CyberBlueColor else colors.card,
                        modifier = Modifier
                            .widthIn(max = 310.dp)
                            .border(
                                1.dp,
                                if (isUser) colors.accentBlue.copy(alpha = 0.5f) else colors.border,
                                RoundedCornerShape(18.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isUser) colors.onGradientText else colors.textPrimary
                            )
                            if (!isUser) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = NeonPurple.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .border(1.dp, NeonPurple.copy(alpha = 0.55f), RoundedCornerShape(50))
                                            .clickable {
                                                val visualIdea = msg.text.lines().firstOrNull { it.isNotBlank() } ?: msg.text
                                                onVisualizeInStudio("Create visual art inspired by: $visualIdea")
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Filled.Brush,
                                                contentDescription = "Visualize in AI Studio",
                                                tint = NeonPurple,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Visualize",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.accentBlue,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            MediaUtils.shareTextViaChooser(context, msg.text)
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Share,
                                            contentDescription = "Share response",
                                            tint = colors.accentBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(msg.text))
                                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.ContentCopy,
                                            contentDescription = "Copy response",
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            when (val stream = streamState) {
                is MalakStreamState.Loading -> {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.accentBlue, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("MALAK AI is thinking...", color = colors.textSecondary)
                        }
                    }
                }
                is MalakStreamState.Streaming -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = colors.elevated,
                            modifier = Modifier.widthIn(max = 310.dp)
                        ) {
                            Text(
                                text = stream.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textPrimary,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
                is MalakStreamState.Error -> {
                    item {
                        Surface(
                            color = ErrorCrimson.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stream.message,
                                color = ErrorCrimson,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                MalakStreamState.Idle -> Unit
            }
        }

        if (attachedBitmap != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = attachedBitmap!!.asImageBitmap(),
                    contentDescription = "Attached image",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Image attached for MALAK Vision", color = colors.accentBlue, modifier = Modifier.weight(1f))
                IconButton(onClick = { attachedBitmap = null }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove image", tint = colors.textSecondary)
                }
            }
        }

        // Composer
        Surface(color = colors.surface, tonalElevation = 8.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showCameraCapture = true },
                    modifier = Modifier.testTag("malak_camera_button")
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = "Camera (coming soon)", tint = HotPink)
                }
                IconButton(
                    onClick = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                ) {
                    Icon(Icons.Filled.Image, contentDescription = "Attach image for MALAK", tint = colors.accentBlue)
                }
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier
                        .testTag("malak_prompt_input")
                        .weight(1f),
                    placeholder = { Text(activeMode.hint, color = colors.textMuted) },
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
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
                        val q = prompt
                        val bmp = attachedBitmap
                        prompt = ""
                        attachedBitmap = null
                        viewModel.askMalak(q, bmp)
                    },
                    modifier = Modifier
                        .testTag("malak_send_button")
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(PChatBrandGradient)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask MALAK AI", tint = colors.onGradientText)
                }
            }
        }
    }

    if (showCameraCapture) {
        DeviceCameraCaptureDialog(
            title = "MALAK Vision Camera",
            allowVideo = false,
            allowFilters = false,
            defaultFrontCamera = false,
            onDismiss = { showCameraCapture = false },
            onPhotoCaptured = { bmp, _ ->
                showCameraCapture = false
                attachedBitmap = bmp
            }
        )
    }
}

private val CyberBlueColor = Color(0xFF1D4ED8)

@Composable
private fun MalakLiveVoiceTab(viewModel: PChatViewModel) {
    val colors = PChatTheme.colors
    val context = LocalContext.current
    val voiceState by viewModel.liveVoiceState.collectAsStateWithLifecycle()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startMalakLiveVoice()
        } else {
            Toast.makeText(context, "Microphone permission is required for MALAK Live Voice", Toast.LENGTH_SHORT).show()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "voice_orb")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "MALAK Live Voice",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Real-time bidirectional voice powered by Gemini Live API (gemini-3.8-live)",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        val isActive = voiceState is LiveVoiceState.Active
        val isConnecting = voiceState is LiveVoiceState.Connecting

        Box(
            modifier = Modifier
                .size(190.dp)
                .scale(if (isActive) pulseScale else 1f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            if (isActive) colors.accentBlue.copy(alpha = 0.55f) else NeonPurple.copy(alpha = 0.3f),
                            HotPink.copy(alpha = 0.16f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_malak_avatar),
                contentDescription = "MALAK Live Voice Orb",
                modifier = Modifier
                    .size(136.dp)
                    .clip(CircleShape)
                    .border(3.dp, PChatBrandGradient, CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        when (val state = voiceState) {
            LiveVoiceState.Disconnected -> {
                Text("Tap below to talk live with MALAK AI", color = colors.textSecondary)
            }
            LiveVoiceState.Connecting -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.accentBlue, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Connecting to gemini-3.8-live...", color = colors.accentBlue)
                }
            }
            is LiveVoiceState.Active -> {
                Text(
                    text = "LIVE • Listening & Speaking",
                    color = colors.accentEmerald,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            is LiveVoiceState.Error -> {
                Text(
                    text = state.message,
                    color = ErrorCrimson,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                if (isActive || isConnecting) {
                    viewModel.stopMalakLiveVoice()
                } else {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel.startMalakLiveVoice()
                    } else {
                        permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            modifier = Modifier
                .testTag("malak_live_voice_button")
                .height(56.dp)
                .fillMaxWidth(0.75f),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) ErrorCrimson else colors.accentBlue,
                contentColor = if (isActive) colors.onGradientText else colors.primaryButtonContent
            )
        ) {
            Icon(
                imageVector = if (isActive) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (isActive || isConnecting) "End Live Voice" else "Start Live Voice",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MalakImageStudioTab(
    viewModel: PChatViewModel,
    initialEditBitmap: Bitmap?,
    initialPrompt: String = "",
    onSharedToMoments: () -> Unit
) {
    val colors = PChatTheme.colors
    val context = LocalContext.current
    val studioState by viewModel.imageStudioState.collectAsStateWithLifecycle()
    var prompt by remember(initialPrompt) { mutableStateOf(initialPrompt) }
    var selectedStyle by remember { mutableStateOf(StudioArtStyle.CYBERPUNK_3D) }
    var sourceBitmap by remember(initialEditBitmap) { mutableStateOf(initialEditBitmap) }
    var showCameraCapture by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            sourceBitmap = MediaUtils.uriToBitmap(context, uri, 960)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "MALAK AI Image Studio",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary
        )
        Text(
            text = "Create or edit posters, avatars, and art with gemini-nano-banana-2.1.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )

        // Optional source image for editing
        Surface(
            color = colors.card,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.border, RoundedCornerShape(18.dp))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sourceBitmap != null) {
                    Image(
                        bitmap = sourceBitmap!!.asImageBitmap(),
                        contentDescription = "Source image to edit",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Editing selected photo", color = colors.accentBlue, modifier = Modifier.weight(1f))
                    IconButton(onClick = { sourceBitmap = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear photo", tint = colors.textSecondary)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCameraCapture = true },
                            modifier = Modifier
                                .testTag("studio_camera_button")
                                .weight(1f)
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = HotPink, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Camera (Soon)", color = colors.textPrimary)
                        }
                        OutlinedButton(
                            onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = null, tint = colors.accentBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery", color = colors.textPrimary)
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier
                .testTag("studio_prompt_input")
                .fillMaxWidth(),
            label = {
                Text(if (sourceBitmap != null) "Describe how to edit this image" else "Describe the image to create")
            },
            placeholder = {
                Text(
                    if (sourceBitmap != null) "e.g. Add neon cyberpunk sunglasses and purple aura"
                    else "e.g. Futuristic neon tiger wearing a glowing crystal crown, dark navy backdrop"
                )
            },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(18.dp),
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

        // Creative Art Style Presets
        Text(
            text = "Art Style Preset",
            style = MaterialTheme.typography.labelLarge,
            color = colors.accentBlue,
            fontWeight = FontWeight.SemiBold
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(StudioArtStyle.entries) { style ->
                val selected = selectedStyle == style
                FilterChip(
                    selected = selected,
                    onClick = { selectedStyle = style },
                    label = { Text(style.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = colors.card,
                        labelColor = colors.textSecondary,
                        selectedContainerColor = NeonPurple.copy(alpha = 0.25f),
                        selectedLabelColor = ElectricBlue
                    )
                )
            }
        }

        // Quick prompt ideas
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val presets = if (sourceBitmap != null) {
                listOf("Add glowing neon aura", "Turn into cyberpunk anime style", "Add starry night sky behind")
            } else {
                listOf(
                    "Neon cosmic avatar portrait",
                    "Cyberpunk skyline at midnight",
                    "Holographic sneaker concept art",
                    "Futuristic P-CHAT creator gaming setup"
                )
            }
            items(presets) { preset ->
                FilterChip(
                    selected = false,
                    onClick = { prompt = preset },
                    label = { Text(preset) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = colors.card, labelColor = colors.textSecondary)
                )
            }
        }

        Button(
            onClick = {
                val styledPrompt = buildStyledImagePrompt(prompt, selectedStyle)
                viewModel.generateOrEditImage(styledPrompt, sourceBitmap)
            },
            enabled = prompt.isNotBlank() && studioState !is ImageStudioState.Loading,
            modifier = Modifier
                .testTag("studio_generate_button")
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = colors.onGradientText)
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (sourceBitmap != null) "Edit Image with Nano Banana 2.1" else "Create Image with Nano Banana 2.1",
                fontWeight = FontWeight.Bold
            )
        }

        // Output Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(colors.card)
                .border(1.dp, colors.border, RoundedCornerShape(22.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when (val state = studioState) {
                ImageStudioState.Idle -> {
                    Text(
                        text = "Your generated or edited artwork will appear here.",
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
                ImageStudioState.Loading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 32.dp)
                    ) {
                        CircularProgressIndicator(color = colors.accentBlue)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Generating with gemini-nano-banana-2.1 (30-60s)...", color = colors.textPrimary)
                    }
                }
                is ImageStudioState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        color = ErrorCrimson,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
                is ImageStudioState.Success -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            bitmap = state.bitmap.asImageBitmap(),
                            contentDescription = "Generated AI image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(290.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { sourceBitmap = state.bitmap },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Edit", color = colors.accentBlue)
                            }
                            OutlinedButton(
                                onClick = {
                                    val res = MediaUtils.saveBitmapToGallery(context, state.bitmap)
                                    Toast.makeText(
                                        context,
                                        res.getOrElse { it.localizedMessage ?: "Could not save artwork" },
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = "Save artwork", tint = colors.accentBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save", color = colors.textPrimary)
                            }
                            OutlinedButton(
                                onClick = {
                                    MediaUtils.shareBitmapViaChooser(
                                        context = context,
                                        bitmap = state.bitmap,
                                        chooserTitle = "Share MALAK AI artwork",
                                        caption = "Created with MALAK AI on P-CHAT: ${state.prompt}"
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "Share artwork", tint = NeonPurple, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share", color = colors.textPrimary)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val b64 = MediaUtils.bitmapToBase64(state.bitmap, 640, 74)
                                viewModel.createMoment(
                                    caption = "Created with MALAK AI: ${state.prompt}",
                                    mediaType = "ai_art",
                                    filterTag = "Nano Banana 2.1",
                                    imageBase64 = b64,
                                    isStory = false,
                                    onDone = onSharedToMoments
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = HotPink, contentColor = colors.onGradientText)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Post to P-CHAT Moments", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showCameraCapture) {
        DeviceCameraCaptureDialog(
            title = "AI Studio Camera",
            allowVideo = false,
            allowFilters = true,
            defaultFrontCamera = true,
            onDismiss = { showCameraCapture = false },
            onPhotoCaptured = { bmp, _ ->
                showCameraCapture = false
                sourceBitmap = bmp
            }
        )
    }
}
