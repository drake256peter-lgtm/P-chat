package com.example.ui.screens

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.Conversation
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.components.PChatTopActionHeader
import com.example.ui.components.UserAvatar
import com.example.ui.theme.HotPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatBrandGradient
import com.example.ui.theme.PChatTheme
import com.example.ui.viewmodel.ChatFilter
import com.example.ui.viewmodel.PChatViewModel
import com.example.util.MediaUtils

@Composable
fun ChatHomeScreen(
    viewModel: PChatViewModel,
    onOpenConversation: (String) -> Unit,
    onOpenMalakAi: () -> Unit,
    onOpenConnect: () -> Unit,
    onOpenWallet: () -> Unit = {}
) {
    val colors = PChatTheme.colors
    val conversationsState by viewModel.conversations.collectAsStateWithLifecycle()
    val usersState by viewModel.discoverableUsers.collectAsStateWithLifecycle()
    val friendshipsState by viewModel.friendships.collectAsStateWithLifecycle()
    val walletState by viewModel.walletSummary.collectAsStateWithLifecycle()
    val filter by viewModel.chatFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.chatSearchQuery.collectAsStateWithLifecycle()

    var showCreateGroupDialog by remember { mutableStateOf(false) }

    val allUsers = (usersState as? UiState.Success)?.data.orEmpty()
        .filter { it.userId != viewModel.currentUserId }
    val onlineUsers = allUsers.filter { it.isOnline }
    val walletCents = (walletState as? UiState.Success)?.data?.balanceCents ?: 0L
    val pendingReqs = (friendshipsState as? UiState.Success)?.data.orEmpty()
        .count { it.recipientId == viewModel.currentUserId && it.status == "pending" }
    val unreadCount = (conversationsState as? UiState.Success)?.data.orEmpty()
        .count { viewModel.currentUserId in it.unreadByUids }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Branded Header with P-CHAT glowing emblem, LIVE badge, Wallet chip, Theme Switch, Group create & Notifications
        PChatTopActionHeader(
            showLiveBadge = true,
            walletBalanceLabel = formatCents(walletCents),
            notificationCount = pendingReqs + unreadCount,
            onSearchClick = null,
            onAddFriendOrQuickClick = { showCreateGroupDialog = true },
            onWalletClick = onOpenWallet,
            onNotificationsClick = onOpenConnect,
            onToggleTheme = { viewModel.toggleThemeMode() }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // MALAK AI Hero Entry Card
            item {
                MalakEntryBanner(onClick = onOpenMalakAi)
            }

            // Online Friends Strip
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Online Friends",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "${onlineUsers.size} active",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accentEmerald
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (onlineUsers.isEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                                .clickable { onOpenConnect() },
                            shape = RoundedCornerShape(16.dp),
                            color = colors.card
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(
                                    name = "P",
                                    colorHex = "#00D4FF",
                                    isOnline = true,
                                    size = 40.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Discover and add friends in Connect to see them live here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(onlineUsers, key = { it.userId }) { user ->
                                Column(
                                    modifier = Modifier
                                        .width(66.dp)
                                        .clickable {
                                            viewModel.openDirectChat(user, onOpenConversation)
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    UserAvatar(
                                        name = user.displayName,
                                        photoBase64 = user.photoBase64,
                                        colorHex = user.avatarColorHex,
                                        isOnline = user.isOnline,
                                        isLive = user.isLive,
                                        size = 56.dp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = user.displayName.substringBefore(" "),
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
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setChatSearchQuery(it) },
                    modifier = Modifier
                        .testTag("chat_search_input")
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    placeholder = {
                        Text(
                            "Search participants and messages...",
                            color = colors.textMuted
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = colors.accentBlue)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setChatSearchQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search", tint = colors.textSecondary)
                            }
                        }
                    },
                    singleLine = true,
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
            }

            // Filter Chips: All, Unread, Groups
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChatFilter.entries.forEach { entry ->
                        val selected = filter == entry
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setChatFilter(entry) },
                            label = { Text(entry.label) },
                            modifier = Modifier.testTag("filter_${entry.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = colors.card,
                                labelColor = colors.textSecondary,
                                selectedContainerColor = colors.accentBlue.copy(alpha = 0.20f),
                                selectedLabelColor = colors.accentBlue
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = colors.border,
                                selectedBorderColor = colors.accentBlue
                            )
                        )
                    }
                }
            }

            // Recent Conversations
            item {
                Text(
                    text = "Recent Conversations",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )
            }

            when (val state = conversationsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = colors.accentBlue)
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        Text(
                            text = state.message,
                            color = HotPink,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }

                is UiState.Success -> {
                    val uid = viewModel.currentUserId
                    val filtered = state.data
                        .filter { conv ->
                            when (filter) {
                                ChatFilter.ALL -> true
                                ChatFilter.UNREAD -> uid in conv.unreadByUids
                                ChatFilter.GROUPS -> conv.isGroup
                            }
                        }
                        .filter { conv ->
                            if (searchQuery.isBlank()) true
                            else {
                                val q = searchQuery.trim()
                                conv.title.contains(q, ignoreCase = true) ||
                                    conv.memberNames.contains(q, ignoreCase = true) ||
                                    conv.lastMessageText.contains(q, ignoreCase = true)
                            }
                        }
                        .sortedWith(
                            compareByDescending<Conversation> { uid in it.pinnedByUids }
                                .thenByDescending { it.updatedAt?.seconds ?: 0L }
                        )

                    if (filtered.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .border(1.dp, colors.border, RoundedCornerShape(20.dp)),
                                shape = RoundedCornerShape(20.dp),
                                color = colors.card
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No matching conversations",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Tap Connect to find friends or create a group chat.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { conversation ->
                            val isPinned = uid in conversation.pinnedByUids
                            val isUnread = uid in conversation.unreadByUids
                            ConversationCard(
                                conversation = conversation,
                                isPinned = isPinned,
                                isUnread = isUnread,
                                onClick = {
                                    viewModel.markConversationRead(conversation)
                                    onOpenConversation(conversation.id)
                                },
                                onTogglePin = { viewModel.togglePinConversation(conversation) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            availableUsers = allUsers,
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { title, selected ->
                showCreateGroupDialog = false
                viewModel.createGroupChat(title, selected, onOpenConversation)
            }
        )
    }
}

@Composable
private fun MalakEntryBanner(onClick: () -> Unit) {
    val colors = PChatTheme.colors
    Surface(
        modifier = Modifier
            .testTag("malak_ai_entry_card")
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        color = colors.elevated,
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            NeonPurple.copy(alpha = if (colors.isDark) 0.28f else 0.14f),
                            colors.accentBlue.copy(alpha = if (colors.isDark) 0.16f else 0.10f),
                            HotPink.copy(alpha = if (colors.isDark) 0.18f else 0.10f)
                        )
                    )
                )
                .border(1.dp, PChatBrandGradient, RoundedCornerShape(22.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_malak_avatar),
                contentDescription = "MALAK AI avatar",
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .border(2.dp, PChatBrandGradient, CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MALAK AI",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.accentBlue.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LIVE + STUDIO",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accentBlue
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Chat, homework, voice live calls & Nano Banana image magic",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 2
                )
            }
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = "Open MALAK AI",
                tint = HotPink,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun ConversationCard(
    conversation: Conversation,
    isPinned: Boolean,
    isUnread: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    val colors = PChatTheme.colors
    Surface(
        modifier = Modifier
            .testTag("conversation_${conversation.id}")
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (isPinned) colors.elevated else colors.card
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isUnread) colors.accentBlue.copy(alpha = 0.7f) else colors.border.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                name = conversation.title,
                colorHex = if (conversation.isGroup) "#9D4EDD" else "#00D4FF",
                isOnline = true,
                size = 50.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (conversation.isGroup) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "GROUP",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonPurple,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonPurple.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = conversation.lastMessageText.ifBlank { "Tap to start chatting" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUnread) colors.textPrimary else colors.textSecondary,
                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = MediaUtils.formatTimestamp(conversation.updatedAt?.seconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUnread) colors.accentBlue else colors.textMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isUnread) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(HotPink)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (isPinned) "Unpin conversation" else "Pin conversation",
                            tint = if (isPinned) colors.accentBlue else colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateGroupDialog(
    availableUsers: List<UserProfile>,
    onDismiss: () -> Unit,
    onCreate: (String, List<UserProfile>) -> Unit
) {
    val colors = PChatTheme.colors
    var title by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<UserProfile>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("New Group Chat", color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Group Name") },
                    placeholder = { Text("e.g. Midnight Squad") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (availableUsers.isNotEmpty()) {
                    Text(
                        "Add participants:",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary
                    )
                    availableUsers.take(8).forEach { user ->
                        val isChecked = selected.any { it.userId == user.userId }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (isChecked) selected.removeAll { it.userId == user.userId }
                                    else selected.add(user)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) selected.add(user)
                                    else selected.removeAll { it.userId == user.userId }
                                }
                            )
                            Text(user.displayName, color = colors.textPrimary)
                        }
                    }
                } else {
                    Text(
                        "You can create the group now and invite friends later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title, selected.toList()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentBlue,
                    contentColor = colors.primaryButtonContent
                )
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        }
    )
}
