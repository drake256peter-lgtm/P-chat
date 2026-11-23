package com.example.ui.screens

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.auth.AuthLogRedactor
import com.example.auth.PREFERRED_LANGUAGES
import com.example.model.AppThemeMode
import com.example.model.AuthProviderType
import com.example.model.UiState
import com.example.ui.components.CountryCurrencyPickerDialog
import com.example.ui.components.FilterStudioSheet
import com.example.ui.components.UserAvatar
import com.example.ui.components.parseAvatarColor
import com.example.ui.theme.AmberSpark
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.HotPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatBrandGradient
import com.example.ui.theme.PChatTheme
import com.example.ui.theme.PureWhite
import com.example.ui.theme.VoidBlack
import com.example.ui.viewmodel.PChatViewModel
import com.example.util.MediaUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileMeScreen(
    viewModel: PChatViewModel,
    onOpenConnect: () -> Unit,
    onOpenWallet: () -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val colors = PChatTheme.colors

    val profileState by viewModel.myProfile.collectAsStateWithLifecycle()
    val settingsState by viewModel.privateSettings.collectAsStateWithLifecycle()
    val friendshipsState by viewModel.friendships.collectAsStateWithLifecycle()
    val walletState by viewModel.walletSummary.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val discoverFilters by viewModel.discoverFilters.collectAsStateWithLifecycle()
    val serverResponse by viewModel.serverApiResponse.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data
    val settings = (settingsState as? UiState.Success)?.data
    val wallet = (walletState as? UiState.Success)?.data
    val acceptedFriends = (friendshipsState as? UiState.Success)?.data.orEmpty()
        .filter { it.status == "accepted" }

    var showEditDialog by remember { mutableStateOf(false) }
    var showFilterStudio by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Profile Hero Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(242.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_pchat_hero_banner),
                    contentDescription = "Profile banner",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(155.dp),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = { showEditDialog = true },
                    modifier = Modifier
                        .testTag("edit_profile_button")
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .clip(CircleShape)
                        .background(VoidBlack.copy(alpha = 0.65f))
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit profile", tint = colors.onGradientText)
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UserAvatar(
                        name = profile?.displayName ?: viewModel.myDisplayName(),
                        photoBase64 = profile?.photoBase64.orEmpty(),
                        colorHex = profile?.avatarColorHex ?: "#00D4FF",
                        isOnline = profile?.isOnline ?: true,
                        isLive = profile?.isLive ?: false,
                        size = 86.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile?.displayName ?: viewModel.myDisplayName(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = colors.textPrimary
                        )
                        if (profile?.verified == true) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Filled.Verified, contentDescription = "Verified", tint = colors.accentBlue, modifier = Modifier.size(20.dp))
                        }
                    }
                    Text(
                        text = "@${profile?.username ?: viewModel.myUsername()} • ${profile?.countryFlag ?: "🌍"} ${profile?.country ?: "Global"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.accentBlue
                    )
                }
            }
        }

        // P-CHAT Wallet Card
        item {
            Surface(
                color = colors.card,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .testTag("profile_wallet_card")
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(onClick = onOpenWallet)
                    .border(1.5.dp, PChatBrandGradient, RoundedCornerShape(22.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    NeonPurple.copy(alpha = if (colors.isDark) 0.25f else 0.12f),
                                    colors.accentBlue.copy(alpha = if (colors.isDark) 0.14f else 0.08f)
                                )
                            )
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(colors.accentBlue.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.AccountBalanceWallet,
                            contentDescription = "P-CHAT Wallet",
                            tint = colors.accentBlue
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "P-CHAT Wallet",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${formatCents(wallet?.balanceCents ?: 0L)}  •  ${wallet?.coins ?: 0L} P-Coins",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.accentEmerald,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Open Wallet",
                        tint = colors.textPrimary
                    )
                }
            }
        }

        // Appearance (Black & White Themes) & Filter Server Studio
        item {
            SectionCard(
                title = "Appearance & Filter Server",
                icon = Icons.Filled.Palette,
                accent = colors.accentBlue
            ) {
                Text(
                    text = "Switch between P-CHAT Black Theme and White Theme, synced live to your Cloud Server profile.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isBlackSelected = themeMode == AppThemeMode.BLACK
                    Surface(
                        color = if (isBlackSelected) VoidBlack else VoidBlack.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("profile_theme_black")
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = if (isBlackSelected) 2.dp else 1.dp,
                                brush = if (isBlackSelected) PChatBrandGradient else Brush.linearGradient(
                                    listOf(colors.border, colors.border)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { viewModel.setThemeMode(AppThemeMode.BLACK) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Filled.DarkMode,
                                contentDescription = "Black Theme",
                                tint = if (isBlackSelected) Color(0xFF00D4FF) else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Black Theme",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    val isWhiteSelected = themeMode == AppThemeMode.WHITE
                    Surface(
                        color = PureWhite,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("profile_theme_white")
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = if (isWhiteSelected) 2.dp else 1.dp,
                                brush = if (isWhiteSelected) PChatBrandGradient else Brush.linearGradient(
                                    listOf(colors.border, colors.border)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { viewModel.setThemeMode(AppThemeMode.WHITE) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Filled.LightMode,
                                contentDescription = "White Theme",
                                tint = NeonPurple,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "White Theme",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0E122B)
                            )
                        }
                    }
                }

                val activeLens = viewModel.activeCameraFilter
                Text(
                    text = "Server Lens: ${activeLens.badge} ${activeLens.label} • ${serverResponse?.let { "${it.endpoint} (${it.latencyMs}ms)" } ?: "Cloud Server Ready"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accentEmerald
                )

                OutlinedButton(
                    onClick = { showFilterStudio = true },
                    modifier = Modifier
                        .testTag("profile_open_filter_studio")
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = colors.accentBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open Filter Studio & Server API",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Bio, Languages & Status
        item {
            Surface(
                color = colors.card,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = profile?.bio?.ifBlank { "Be Closer. Share More. Feel Happy." } ?: "Be Closer. Share More. Feel Happy.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Languages: ${profile?.languages?.ifBlank { "English" } ?: "English"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                    Text(
                        text = "Status: ${profile?.statusText?.ifBlank { "Online on P-CHAT" } ?: "Online on P-CHAT"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accentEmerald
                    )
                }
            }
        }

        // Friends Summary Card
        item {
            Surface(
                color = colors.card,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenConnect)
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Group, contentDescription = null, tint = colors.accentBlue)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Friends List", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        Text("${acceptedFriends.size} connected friends", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    }
                    Text("Manage", color = colors.accentBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Privacy Controls
        item {
            SectionCard(title = "Privacy Controls", icon = Icons.Filled.Lock, accent = NeonPurple) {
                ToggleRow(
                    title = "Discoverable in Connect",
                    subtitle = "Allow others to find your @username",
                    checked = profile?.isDiscoverable ?: true,
                    onCheckedChange = { checked ->
                        if (profile != null) {
                            viewModel.updateProfile(
                                displayName = profile.displayName,
                                username = profile.username,
                                bio = profile.bio,
                                statusText = profile.statusText,
                                country = profile.country,
                                countryFlag = profile.countryFlag,
                                languages = profile.languages,
                                avatarColorHex = profile.avatarColorHex,
                                isOnline = profile.isOnline,
                                isLive = profile.isLive,
                                isDiscoverable = checked
                            )
                        }
                    }
                )
                ToggleRow(
                    title = "Show Online Status",
                    subtitle = "Display glowing active dot to friends",
                    checked = settings?.showOnlineStatus ?: true,
                    onCheckedChange = { checked ->
                        if (settings != null) {
                            viewModel.updatePrivateSettings(
                                showOnlineStatus = checked,
                                readReceiptsEnabled = settings.readReceiptsEnabled,
                                allowCallsFrom = settings.allowCallsFrom,
                                notifyMessages = settings.notifyMessages,
                                notifyCalls = settings.notifyCalls
                            )
                        }
                    }
                )
                ToggleRow(
                    title = "Read Receipts",
                    subtitle = "Let friends know when you've seen chats",
                    checked = settings?.readReceiptsEnabled ?: true,
                    onCheckedChange = { checked ->
                        if (settings != null) {
                            viewModel.updatePrivateSettings(
                                showOnlineStatus = settings.showOnlineStatus,
                                readReceiptsEnabled = checked,
                                allowCallsFrom = settings.allowCallsFrom,
                                notifyMessages = settings.notifyMessages,
                                notifyCalls = settings.notifyCalls
                            )
                        }
                    }
                )
                Text("Allow Calls From", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("everyone", "friends", "nobody").forEach { option ->
                        val selected = (settings?.allowCallsFrom ?: "friends") == option
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (settings != null) {
                                    viewModel.updatePrivateSettings(
                                        showOnlineStatus = settings.showOnlineStatus,
                                        readReceiptsEnabled = settings.readReceiptsEnabled,
                                        allowCallsFrom = option,
                                        notifyMessages = settings.notifyMessages,
                                        notifyCalls = settings.notifyCalls
                                    )
                                }
                            },
                            label = { Text(option.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        }

        // Settings & Notifications
        item {
            SectionCard(title = "Settings & Notifications", icon = Icons.Filled.Notifications, accent = colors.accentBlue) {
                ToggleRow(
                    title = "Message Alerts",
                    subtitle = "Instant notifications for new chats",
                    checked = settings?.notifyMessages ?: true,
                    onCheckedChange = { checked ->
                        if (settings != null) {
                            viewModel.updatePrivateSettings(
                                showOnlineStatus = settings.showOnlineStatus,
                                readReceiptsEnabled = settings.readReceiptsEnabled,
                                allowCallsFrom = settings.allowCallsFrom,
                                notifyMessages = checked,
                                notifyCalls = settings.notifyCalls
                            )
                        }
                    }
                )
                ToggleRow(
                    title = "Call Notifications",
                    subtitle = "Ring for incoming HD video & voice calls",
                    checked = settings?.notifyCalls ?: true,
                    onCheckedChange = { checked ->
                        if (settings != null) {
                            viewModel.updatePrivateSettings(
                                showOnlineStatus = settings.showOnlineStatus,
                                readReceiptsEnabled = settings.readReceiptsEnabled,
                                allowCallsFrom = settings.allowCallsFrom,
                                notifyMessages = settings.notifyMessages,
                                notifyCalls = checked
                            )
                        }
                    }
                )
                ToggleRow(
                    title = "Broadcast LIVE Badge",
                    subtitle = "Show neon LIVE ring on your avatar",
                    checked = profile?.isLive ?: true,
                    onCheckedChange = { checked ->
                        if (profile != null) {
                            viewModel.updateProfile(
                                displayName = profile.displayName,
                                username = profile.username,
                                bio = profile.bio,
                                statusText = profile.statusText,
                                country = profile.country,
                                countryFlag = profile.countryFlag,
                                languages = profile.languages,
                                avatarColorHex = profile.avatarColorHex,
                                isOnline = profile.isOnline,
                                isLive = checked,
                                isDiscoverable = profile.isDiscoverable
                            )
                        }
                    }
                )
            }
        }

        // Account Management
        item {
            val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
            val providerType = AuthProviderType.fromId(
                profile?.authProvider?.ifBlank { null } ?: settings?.authProvider
            )
            val firebaseUser = remember(statusBanner, settings?.emailVerified) {
                runCatching { Firebase.auth.currentUser }.getOrNull()
            }
            val maskedId = when {
                !settings?.email.isNullOrBlank() -> AuthLogRedactor.maskEmail(settings?.email.orEmpty())
                !settings?.phoneNumber.isNullOrBlank() -> AuthLogRedactor.maskPhone(settings?.phoneNumber.orEmpty())
                !firebaseUser?.email.isNullOrBlank() -> AuthLogRedactor.maskEmail(firebaseUser?.email.orEmpty())
                !firebaseUser?.phoneNumber.isNullOrBlank() -> AuthLogRedactor.maskPhone(firebaseUser?.phoneNumber.orEmpty())
                else -> "UID ${viewModel.currentUserId.take(8)}"
            }
            val emailVerified = firebaseUser?.isEmailVerified == true || settings?.emailVerified == true
            val phoneVerified = !firebaseUser?.phoneNumber.isNullOrBlank() || settings?.phoneVerified == true

            SectionCard(title = "Account Management", icon = Icons.Filled.Settings, accent = HotPink) {
                Text(
                    text = "Signed in via ${providerType.label} ($maskedId)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (firebaseUser?.email != null || !settings?.email.isNullOrBlank()) {
                        Surface(
                            color = (if (emailVerified) colors.accentEmerald else AmberSpark).copy(alpha = 0.14f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = if (emailVerified) "✅ Email Verified" else "⚠️ Email Unverified",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (emailVerified) colors.accentEmerald else AmberSpark,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (phoneVerified) {
                        Surface(
                            color = colors.accentEmerald.copy(alpha = 0.14f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "📱 Phone Verified",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accentEmerald,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (firebaseUser?.email != null && !emailVerified) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.sendVerificationEmail() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Send Verification Email", color = colors.accentBlue)
                        }
                        OutlinedButton(
                            onClick = { viewModel.refreshEmailVerification() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Check Verification", color = colors.accentEmerald)
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showDiagnosticsDialog = true },
                    modifier = Modifier
                        .testTag("profile_open_diagnostics_button")
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = NeonPurple)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sessions, Devices & Auth Diagnostics", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { viewModel.signOutSecurely(context, credentialManager, scope, onSignedOut) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.SwitchAccount, contentDescription = null, tint = colors.accentBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Switch Account / Sign-In Options", color = colors.accentBlue, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = {
                        viewModel.signOutSecurely(context, credentialManager, scope, onSignedOut)
                    },
                    modifier = Modifier
                        .testTag("sign_out_button")
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = ErrorCrimson)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out", color = ErrorCrimson, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showFilterStudio) {
        FilterStudioSheet(
            viewModel = viewModel,
            onDismiss = { showFilterStudio = false }
        )
    }

    if (showDiagnosticsDialog) {
        AuthDiagnosticsDialog(
            viewModel = viewModel,
            onDismiss = { showDiagnosticsDialog = false }
        )
    }

    if (showEditDialog && profile != null) {
        var nameDraft by remember { mutableStateOf(profile.displayName) }
        var fullNameDraft by remember { mutableStateOf(profile.fullName.ifBlank { profile.displayName }) }
        var usernameDraft by remember { mutableStateOf(profile.username) }
        var bioDraft by remember { mutableStateOf(profile.bio) }
        var statusDraft by remember { mutableStateOf(profile.statusText) }
        var countryDraft by remember { mutableStateOf(profile.country) }
        var flagDraft by remember { mutableStateOf(profile.countryFlag) }
        var languagesDraft by remember { mutableStateOf(profile.languages) }
        var preferredLanguageDraft by remember { mutableStateOf(profile.preferredLanguage.ifBlank { "English" }) }
        var colorDraft by remember { mutableStateOf(profile.avatarColorHex) }
        var photoDraft by remember { mutableStateOf(profile.photoBase64) }

        val avatarPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                val bmp = MediaUtils.uriToBitmap(context, uri, 360)
                if (bmp != null) {
                    photoDraft = MediaUtils.bitmapToBase64(bmp, 360, 72)
                }
            }
        }

        var showCountryPicker by remember { mutableStateOf(false) }
        val countryPresets = listOf(
            "🌍" to "Global",
            "🇺🇬" to "Uganda",
            "🇰🇪" to "Kenya",
            "🇺🇸" to "United States",
            "🇬🇧" to "United Kingdom",
            "🇨🇳" to "China"
        )

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = colors.surface,
            title = { Text("Edit Profile", color = colors.textPrimary) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(
                            name = nameDraft,
                            photoBase64 = photoDraft,
                            colorHex = colorDraft,
                            isOnline = true,
                            size = 54.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedButton(
                            onClick = {
                                avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (photoDraft.isNotBlank()) "Change Photo" else "Pick Photo")
                        }
                    }
                    OutlinedTextField(value = nameDraft, onValueChange = { nameDraft = it }, label = { Text("Display Name") }, singleLine = true)
                    OutlinedTextField(value = fullNameDraft, onValueChange = { fullNameDraft = it }, label = { Text("Full Name") }, singleLine = true)
                    OutlinedTextField(value = usernameDraft, onValueChange = { usernameDraft = it }, label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(value = languagesDraft, onValueChange = { languagesDraft = it }, label = { Text("Spoken Languages") }, singleLine = true)
                    Text("Preferred Language", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PREFERRED_LANGUAGES.take(6).forEach { lang ->
                            FilterChip(
                                selected = preferredLanguageDraft == lang,
                                onClick = { preferredLanguageDraft = lang },
                                label = { Text(lang) }
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        countryPresets.forEach { (flag, name) ->
                            FilterChip(
                                selected = countryDraft == name,
                                onClick = {
                                    flagDraft = flag
                                    countryDraft = name
                                },
                                label = { Text("$flag $name") }
                            )
                        }
                        FilterChip(
                            selected = countryPresets.none { it.second == countryDraft } && countryDraft.isNotBlank(),
                            onClick = { showCountryPicker = true },
                            label = { Text("🌐 All Countries...") }
                        )
                    }
                    OutlinedTextField(value = bioDraft, onValueChange = { bioDraft = it }, label = { Text("Bio") }, maxLines = 2)
                    OutlinedTextField(value = statusDraft, onValueChange = { statusDraft = it }, label = { Text("Status") }, singleLine = true)
                    Text("Avatar Glow Color", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("#00D4FF", "#9D4EDD", "#FF2E93", "#00F5A0").forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(parseAvatarColor(hex))
                                    .border(
                                        width = if (colorDraft == hex) 3.dp else 1.dp,
                                        color = if (colorDraft == hex) colors.textPrimary else colors.border,
                                        shape = CircleShape
                                    )
                                    .clickable { colorDraft = hex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateProfile(
                            displayName = nameDraft,
                            username = usernameDraft,
                            bio = bioDraft,
                            statusText = statusDraft,
                            country = countryDraft,
                            countryFlag = flagDraft,
                            languages = languagesDraft,
                            avatarColorHex = colorDraft,
                            isOnline = profile.isOnline,
                            isLive = profile.isLive,
                            isDiscoverable = profile.isDiscoverable,
                            fullName = fullNameDraft,
                            preferredLanguage = preferredLanguageDraft,
                            photoBase64 = photoDraft
                        )
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentBlue,
                        contentColor = colors.primaryButtonContent
                    )
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )

        if (showCountryPicker) {
            CountryCurrencyPickerDialog(
                selectedCurrencyCode = null,
                title = "Choose Your Country",
                onSelect = { info ->
                    countryDraft = info.countryName
                    flagDraft = info.flag
                    showCountryPicker = false
                },
                onDismiss = { showCountryPicker = false }
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    content: @Composable () -> Unit
) {
    val colors = PChatTheme.colors
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            }
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = PChatTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.primaryButtonContent,
                checkedTrackColor = colors.accentBlue
            )
        )
    }
}
