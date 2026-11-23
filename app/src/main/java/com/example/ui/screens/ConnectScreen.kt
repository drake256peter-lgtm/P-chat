package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.FirestoreServerFilterApi
import com.example.model.AppThemeMode
import com.example.model.Friendship
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.components.FilterStudioSheet
import com.example.ui.components.PChatTopActionHeader
import com.example.ui.components.UserAvatar
import com.example.ui.theme.HotPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatFollowGradient
import com.example.ui.theme.PChatTheme
import com.example.ui.viewmodel.PChatViewModel

private enum class ConnectSegment(val label: String) {
    DISCOVER("Discover"),
    FRIENDS("Friends"),
    FOLLOWING("Following")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectScreen(
    viewModel: PChatViewModel,
    onOpenConversation: (String) -> Unit,
    onOpenWallet: (String?) -> Unit = {}
) {
    val colors = PChatTheme.colors
    val usersState by viewModel.discoverableUsers.collectAsStateWithLifecycle()
    val friendshipsState by viewModel.friendships.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversations.collectAsStateWithLifecycle()
    val discoverFilters by viewModel.discoverFilters.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val serverApiResponse by viewModel.serverApiResponse.collectAsStateWithLifecycle()

    var selectedSegment by remember { mutableStateOf(ConnectSegment.DISCOVER) }
    var search by remember { mutableStateOf("") }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showQuickChips by remember { mutableStateOf(false) }
    var showAllSuggested by remember { mutableStateOf(false) }
    var showRequestsDialog by remember { mutableStateOf(false) }

    val myUid = viewModel.currentUserId
    val allUsers = (usersState as? UiState.Success)?.data.orEmpty()
        .filter { it.userId != myUid }
    val friendships = (friendshipsState as? UiState.Success)?.data.orEmpty()
    val incomingRequests = friendships.filter { it.recipientId == myUid && it.status == "pending" }
    val acceptedFriendships = friendships.filter { it.status == "accepted" }
    val outgoingFollowing = friendships.filter { it.requesterId == myUid && it.status == "pending" }

    val unreadChats = (conversationsState as? UiState.Success)?.data.orEmpty()
        .count { myUid in it.unreadByUids }
    val notificationCount = incomingRequests.size + unreadChats

    val friendshipByOtherUid: Map<String, Friendship> = remember(friendships, myUid) {
        friendships.associateBy { f ->
            if (f.requesterId == myUid) f.recipientId else f.requesterId
        }
    }

    val filteredUsers = remember(allUsers, search, discoverFilters) {
        FirestoreServerFilterApi.applyClientAndServerFilters(
            users = allUsers,
            searchQuery = search,
            state = discoverFilters
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Branded Top Header faithful to P-CHAT Connect visual identity + Black/White theme toggle
        PChatTopActionHeader(
            showLiveBadge = false,
            walletBalanceLabel = null,
            notificationCount = notificationCount,
            onToggleTheme = { viewModel.toggleThemeMode() },
            onSearchClick = { showQuickChips = !showQuickChips },
            onAddFriendOrQuickClick = { selectedSegment = ConnectSegment.DISCOVER },
            onWalletClick = null,
            onNotificationsClick = { showRequestsDialog = true }
        )

        // Discover | Friends | Following Segmented Pill Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConnectSegmentTab(
                label = "Discover",
                icon = Icons.Filled.Public,
                selected = selectedSegment == ConnectSegment.DISCOVER,
                modifier = Modifier.weight(1f),
                onClick = { selectedSegment = ConnectSegment.DISCOVER }
            )
            ConnectSegmentTab(
                label = if (acceptedFriendships.isEmpty()) "Friends" else "Friends (${acceptedFriendships.size})",
                icon = Icons.Filled.Group,
                selected = selectedSegment == ConnectSegment.FRIENDS,
                modifier = Modifier.weight(1f),
                onClick = { selectedSegment = ConnectSegment.FRIENDS }
            )
            ConnectSegmentTab(
                label = if (outgoingFollowing.isEmpty()) "Following" else "Following (${outgoingFollowing.size})",
                icon = Icons.Filled.PeopleOutline,
                selected = selectedSegment == ConnectSegment.FOLLOWING,
                modifier = Modifier.weight(1f),
                onClick = { selectedSegment = ConnectSegment.FOLLOWING }
            )
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = NeonPurple.copy(alpha = if (colors.isDark) 0.7f else 0.4f),
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Search pill + Server Filter Studio button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .testTag("connect_search_input")
                    .weight(1f),
                placeholder = {
                    Text(
                        "Search friends, languages or countries...",
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = colors.textSecondary) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = colors.textSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.card,
                    unfocusedContainerColor = colors.card,
                    focusedBorderColor = NeonPurple,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                )
            )

            Box {
                val activeCount = discoverFilters.activeFilterCount
                IconButton(
                    onClick = { showFilterSheet = true },
                    modifier = Modifier
                        .testTag("connect_filter_button")
                        .minimumInteractiveComponentSize()
                        .size(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (activeCount > 0) NeonPurple.copy(alpha = 0.24f) else colors.card)
                        .border(
                            1.dp,
                            if (activeCount > 0) NeonPurple else colors.border,
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = "Open Filters and Server API",
                        tint = colors.textPrimary
                    )
                }
                if (activeCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .clip(CircleShape)
                            .background(HotPink)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "$activeCount",
                            color = colors.onGradientText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Quick Filter & White/Black Theme Strip (expanded via search/quick toggle or when filters active)
        if (showQuickChips || discoverFilters.activeFilterCount > 0) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = themeMode == AppThemeMode.WHITE,
                        onClick = { viewModel.toggleThemeMode() },
                        leadingIcon = {
                            Icon(
                                imageVector = if (themeMode == AppThemeMode.WHITE) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(if (themeMode == AppThemeMode.WHITE) "Switch to Black Theme" else "Switch to White Theme")
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = colors.card,
                            labelColor = colors.textPrimary,
                            selectedContainerColor = colors.accentBlue.copy(alpha = 0.2f),
                            selectedLabelColor = colors.accentBlue
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = discoverFilters.onlineOnly,
                        onClick = {
                            viewModel.updateDiscoverFilters(
                                discoverFilters.copy(onlineOnly = !discoverFilters.onlineOnly),
                                searchQuery = search
                            )
                        },
                        label = { Text("Online Now") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = colors.card,
                            labelColor = colors.textSecondary,
                            selectedContainerColor = colors.accentEmerald.copy(alpha = 0.22f),
                            selectedLabelColor = colors.accentEmerald
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = discoverFilters.verifiedOnly,
                        onClick = {
                            viewModel.updateDiscoverFilters(
                                discoverFilters.copy(verifiedOnly = !discoverFilters.verifiedOnly),
                                searchQuery = search
                            )
                        },
                        label = { Text("Verified") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = colors.card,
                            labelColor = colors.textSecondary,
                            selectedContainerColor = colors.accentBlue.copy(alpha = 0.22f),
                            selectedLabelColor = colors.accentBlue
                        )
                    )
                }
                items(listOf("English", "Luganda", "Swahili", "Chinese", "Russian")) { lang ->
                    val selected = discoverFilters.language == lang
                    FilterChip(
                        selected = selected,
                        onClick = {
                            viewModel.updateDiscoverFilters(
                                discoverFilters.copy(language = if (selected) "" else lang),
                                searchQuery = search
                            )
                        },
                        label = { Text(lang) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = colors.card,
                            labelColor = colors.textSecondary,
                            selectedContainerColor = NeonPurple.copy(alpha = 0.26f),
                            selectedLabelColor = colors.textPrimary
                        )
                    )
                }
            }
        }

        val apiResponse = serverApiResponse
        if (apiResponse != null && discoverFilters.activeFilterCount > 0) {
            Surface(
                color = colors.elevated,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .border(1.dp, colors.accentEmerald.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .clickable { showFilterSheet = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Filled.CloudDone,
                            contentDescription = null,
                            tint = colors.accentEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Filter API: ${filteredUsers.size} results • ${apiResponse.latencyMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accentEmerald,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelSmall,
                        color = HotPink,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.resetDiscoverFilters(search) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (incomingRequests.isNotEmpty()) {
                item {
                    Text(
                        text = "Incoming Friend Requests (${incomingRequests.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = HotPink
                    )
                }
                items(incomingRequests, key = { it.id }) { req ->
                    Surface(
                        color = colors.elevated,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, HotPink.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(name = req.requesterName, colorHex = "#FF2E93", size = 48.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(req.requesterName, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                                Text("Wants to connect with you", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            }
                            IconButton(
                                onClick = { viewModel.respondToFriendRequest(req.id, accept = false) }
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Decline request", tint = colors.textSecondary)
                            }
                            Button(
                                onClick = { viewModel.respondToFriendRequest(req.id, accept = true) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentEmerald,
                                    contentColor = colors.primaryButtonContent
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Accept", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            when (selectedSegment) {
                ConnectSegment.DISCOVER -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = NeonPurple,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Suggested Friends",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showAllSuggested = !showAllSuggested }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (showAllSuggested) "Show less" else "See all",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NeonPurple
                                )
                                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    val suggestedUsers = filteredUsers
                        .filter { friendshipByOtherUid[it.userId]?.status != "accepted" }
                        .let { list -> if (showAllSuggested) list else list.take(12) }

                    if (suggestedUsers.isEmpty()) {
                        item {
                            EmptyConnectCard(
                                title = "No matching profiles",
                                subtitle = if (discoverFilters.activeFilterCount > 0 || search.isNotBlank()) {
                                    "Try clearing a filter in Filters & Server API or adjusting your search."
                                } else {
                                    "Ask friends to sign in to P-CHAT with Connect Discovery enabled, or check your Friends tab."
                                }
                            )
                        }
                    } else {
                        items(suggestedUsers, key = { it.userId }) { user ->
                            val relation = friendshipByOtherUid[user.userId]
                            ConnectPersonCard(
                                user = user,
                                relationStatus = relation?.status,
                                isOutgoingPending = relation?.requesterId == myUid && relation.status == "pending",
                                onConnectOrFollow = { viewModel.sendFriendRequest(user) },
                                onChat = { viewModel.openDirectChat(user, onOpenConversation) },
                                onSendGift = { onOpenWallet(user.userId) },
                                onCancelOrUnfriend = {
                                    if (relation != null) viewModel.removeFriendship(relation.id)
                                }
                            )
                        }
                    }
                }

                ConnectSegment.FRIENDS -> {
                    item {
                        Text(
                            text = "Connected Friends (${acceptedFriendships.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.accentEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (acceptedFriendships.isEmpty()) {
                        item {
                            EmptyConnectCard(
                                title = "No connected friends yet",
                                subtitle = "Tap Follow on Suggested Friends in Discover to build your circle."
                            )
                        }
                    } else {
                        items(acceptedFriendships, key = { it.id }) { friendship ->
                            val otherUid = if (friendship.requesterId == myUid) friendship.recipientId else friendship.requesterId
                            val otherName = if (friendship.requesterId == myUid) friendship.recipientName else friendship.requesterName
                            val userProfile = allUsers.firstOrNull { it.userId == otherUid } ?: UserProfile(
                                userId = otherUid,
                                displayName = otherName,
                                username = otherName.lowercase().replace(" ", "_")
                            )
                            ConnectPersonCard(
                                user = userProfile,
                                relationStatus = "accepted",
                                isOutgoingPending = false,
                                onConnectOrFollow = {},
                                onChat = { viewModel.openDirectChat(userProfile, onOpenConversation) },
                                onSendGift = { onOpenWallet(userProfile.userId) },
                                onCancelOrUnfriend = { viewModel.removeFriendship(friendship.id) }
                            )
                        }
                    }
                }

                ConnectSegment.FOLLOWING -> {
                    item {
                        Text(
                            text = "Pending & Following (${outgoingFollowing.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.accentBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (outgoingFollowing.isEmpty()) {
                        item {
                            EmptyConnectCard(
                                title = "Not following anyone yet",
                                subtitle = "Requests you send from Discover appear here until accepted."
                            )
                        }
                    } else {
                        items(outgoingFollowing, key = { it.id }) { friendship ->
                            val userProfile = allUsers.firstOrNull { it.userId == friendship.recipientId } ?: UserProfile(
                                userId = friendship.recipientId,
                                displayName = friendship.recipientName,
                                username = friendship.recipientName.lowercase().replace(" ", "_")
                            )
                            ConnectPersonCard(
                                user = userProfile,
                                relationStatus = friendship.status,
                                isOutgoingPending = true,
                                onConnectOrFollow = {},
                                onChat = { viewModel.openDirectChat(userProfile, onOpenConversation) },
                                onSendGift = { onOpenWallet(userProfile.userId) },
                                onCancelOrUnfriend = { viewModel.removeFriendship(friendship.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterStudioSheet(
            viewModel = viewModel,
            currentSearchQuery = search,
            onDismiss = { showFilterSheet = false }
        )
    }

    if (showRequestsDialog) {
        AlertDialog(
            onDismissRequest = { showRequestsDialog = false },
            containerColor = colors.surface,
            title = { Text("Notifications", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "• ${incomingRequests.size} pending friend request(s)",
                        color = if (incomingRequests.isNotEmpty()) HotPink else colors.textSecondary
                    )
                    Text(
                        text = "• $unreadChats unread chat thread(s)",
                        color = if (unreadChats > 0) colors.accentBlue else colors.textSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showRequestsDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentBlue,
                        contentColor = colors.primaryButtonContent
                    )
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun ConnectSegmentTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = PChatTheme.colors
    val containerModifier = if (selected) {
        modifier
            .shadow(10.dp, RoundedCornerShape(50), ambientColor = NeonPurple, spotColor = HotPink)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        NeonPurple.copy(alpha = if (colors.isDark) 0.52f else 0.9f),
                        HotPink.copy(alpha = if (colors.isDark) 0.45f else 0.85f)
                    )
                )
            )
            .border(1.5.dp, Brush.horizontalGradient(listOf(NeonPurple, HotPink)), RoundedCornerShape(50))
    } else {
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Transparent)
    }

    val activeTint = if (selected) colors.onGradientText else colors.textSecondary

    Row(
        modifier = containerModifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = activeTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = activeTint,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ConnectPersonCard(
    user: UserProfile,
    relationStatus: String?,
    isOutgoingPending: Boolean,
    onConnectOrFollow: () -> Unit,
    onChat: () -> Unit,
    onSendGift: () -> Unit,
    onCancelOrUnfriend: () -> Unit
) {
    val colors = PChatTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = colors.card.copy(alpha = if (colors.isDark) 0.92f else 1f),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = if (colors.isDark) 0.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                NeonPurple.copy(alpha = if (colors.isDark) 0.35f else 0.25f),
                RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onChat)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                name = user.displayName,
                photoBase64 = user.photoBase64,
                colorHex = user.avatarColorHex,
                isOnline = user.isOnline,
                isLive = user.isLive,
                size = 58.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (user.verified) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = "Verified",
                            tint = colors.accentBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${user.countryFlag.ifBlank { "🌍" }} ${user.country.ifBlank { "Global" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ChatBubbleOutline,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = user.languages.ifBlank { "English" },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            when {
                relationStatus == "accepted" -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.accentEmerald.copy(alpha = 0.18f))
                            .border(1.dp, colors.accentEmerald.copy(alpha = 0.65f), RoundedCornerShape(50))
                            .clickable(onClick = onChat)
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ChatBubble, contentDescription = null, tint = colors.accentEmerald, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Chat", color = colors.accentEmerald, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                isOutgoingPending -> {
                    OutlinedButton(
                        onClick = onCancelOrUnfriend,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Pending", color = colors.textSecondary, fontSize = 13.sp)
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier
                            .shadow(10.dp, RoundedCornerShape(50), ambientColor = colors.accentBlue, spotColor = HotPink)
                            .clip(RoundedCornerShape(50))
                            .background(PChatFollowGradient)
                            .clickable(onClick = onConnectOrFollow)
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = colors.onGradientText, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Follow", color = colors.onGradientText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More actions", tint = colors.textSecondary)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open Chat") },
                        leadingIcon = { Icon(Icons.Filled.ChatBubble, contentDescription = null, tint = colors.accentBlue) },
                        onClick = {
                            menuExpanded = false
                            onChat()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Send Wallet Gift") },
                        leadingIcon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = colors.accentEmerald) },
                        onClick = {
                            menuExpanded = false
                            onSendGift()
                        }
                    )
                    if (relationStatus == "accepted" || isOutgoingPending) {
                        DropdownMenuItem(
                            text = { Text(if (relationStatus == "accepted") "Remove Friend" else "Cancel Request") },
                            leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null, tint = HotPink) },
                            onClick = {
                                menuExpanded = false
                                onCancelOrUnfriend()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyConnectCard(title: String, subtitle: String) {
    val colors = PChatTheme.colors
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
    }
}

