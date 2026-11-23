package com.example.ui.components

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import com.example.R
import com.example.model.CallSession
import com.example.util.MediaUtils
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.HotPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatBrandGradient
import com.example.ui.theme.PChatTheme
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.VoidBlack

object PChatRoutes {
    const val CHAT = "chat"
    const val CONNECT = "connect"
    const val PLUS = "plus"
    const val MOMENTS = "moments"
    const val ME = "me"
    const val WALLET = "wallet"
    const val MALAK = "malak"
    const val CHAT_DETAIL = "chat_detail/{conversationId}"
    const val CALL = "call/{callId}"

    fun chatDetail(conversationId: String) = "chat_detail/$conversationId"
    fun call(callId: String) = "call/$callId"
}

@Composable
fun PChatBrandEmblem(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(10.dp, RoundedCornerShape(14.dp), ambientColor = ElectricBlue, spotColor = HotPink)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(VoidBlack, DeepNavy, NeonPurple.copy(alpha = 0.35f))
                )
            )
            .border(1.5.dp, PChatBrandGradient, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_pchat_launcher_fg),
            contentDescription = "P-CHAT glowing emblem",
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun PChatLogo(
    modifier: Modifier = Modifier,
    showLiveBadge: Boolean = true,
    showEmblem: Boolean = true,
    fontSizeSp: Int = 24
) {
    val colors = PChatTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showEmblem) {
            PChatBrandEmblem(size = 42.dp)
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "P-CHAT",
                    style = TextStyle(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        fontSize = fontSizeSp.sp,
                        brush = PChatBrandGradient,
                        letterSpacing = 0.8.sp
                    )
                )
                if (showLiveBadge) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.accentEmerald.copy(alpha = 0.16f))
                            .border(1.dp, colors.accentEmerald.copy(alpha = 0.55f), RoundedCornerShape(50))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.accentEmerald.copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accentEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = "Chat • Connect • Explore",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

@Composable
fun PChatTopActionHeader(
    showLiveBadge: Boolean = false,
    walletBalanceLabel: String? = null,
    notificationCount: Int = 0,
    onToggleTheme: (() -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null,
    onAddFriendOrQuickClick: (() -> Unit)? = null,
    onWalletClick: (() -> Unit)? = null,
    onNotificationsClick: (() -> Unit)? = null
) {
    val colors = PChatTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        PChatLogo(
            showLiveBadge = showLiveBadge,
            showEmblem = true,
            fontSizeSp = 22
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (walletBalanceLabel != null && onWalletClick != null) {
                Row(
                    modifier = Modifier
                        .testTag("header_wallet_chip")
                        .clip(RoundedCornerShape(50))
                        .background(colors.elevated)
                        .border(1.dp, colors.accentBlue.copy(alpha = 0.65f), RoundedCornerShape(50))
                        .clickable(onClick = onWalletClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.AccountBalanceWallet,
                        contentDescription = "Open Wallet",
                        tint = colors.accentBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = walletBalanceLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (onToggleTheme != null) {
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .testTag("header_theme_toggle")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (colors.isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = if (colors.isDark) "Switch to White Theme" else "Switch to Black Theme",
                        tint = colors.accentBlue
                    )
                }
            }
            if (onSearchClick != null) {
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = colors.textPrimary
                    )
                }
            }
            if (onAddFriendOrQuickClick != null) {
                IconButton(
                    onClick = onAddFriendOrQuickClick,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Filled.PersonAdd,
                        contentDescription = "Quick add or group",
                        tint = colors.textPrimary
                    )
                }
            }
            if (onNotificationsClick != null) {
                Box {
                    IconButton(
                        onClick = onNotificationsClick,
                        modifier = Modifier
                            .testTag("header_notifications_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (notificationCount > 0) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                            contentDescription = "Notifications",
                            tint = colors.textPrimary
                        )
                    }
                    if (notificationCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-6).dp, y = 6.dp)
                                .clip(CircleShape)
                                .background(ErrorCrimson)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = if (notificationCount > 9) "9+" else "$notificationCount",
                                color = colors.onGradientText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

fun parseAvatarColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        ElectricBlue
    }
}

@Composable
fun UserAvatar(
    name: String,
    colorHex: String = "#00D4FF",
    isOnline: Boolean = false,
    isLive: Boolean = false,
    size: Dp = 48.dp,
    showStoryRing: Boolean = false,
    modifier: Modifier = Modifier,
    photoBase64: String = ""
) {
    val colors = PChatTheme.colors
    val baseColor = parseAvatarColor(colorHex)
    val photoBitmap = remember(photoBase64) {
        if (photoBase64.isNotBlank()) MediaUtils.base64ToBitmap(photoBase64)?.asImageBitmap() else null
    }
    val initials = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { "P" }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        val ringModifier = if (showStoryRing || isLive) {
            Modifier
                .size(size)
                .clip(CircleShape)
                .border(2.5.dp, PChatBrandGradient, CircleShape)
                .padding(3.dp)
        } else {
            Modifier
                .size(size)
                .clip(CircleShape)
                .border(2.dp, Brush.linearGradient(listOf(NeonPurple, HotPink)), CircleShape)
                .padding(2.5.dp)
        }

        Box(
            modifier = ringModifier
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            baseColor.copy(alpha = if (colors.isDark) 0.42f else 0.72f),
                            NeonPurple.copy(alpha = if (colors.isDark) 0.52f else 0.78f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (photoBitmap != null) {
                Image(
                    bitmap = photoBitmap,
                    contentDescription = "$name profile picture",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = initials,
                    color = colors.onGradientText,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.36f).sp
                )
            }
        }

        if (isOnline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-1).dp, y = (-1).dp)
                    .size((size.value * 0.27f).dp.coerceAtLeast(11.dp))
                    .clip(CircleShape)
                    .background(colors.background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(colors.accentEmerald)
            )
        }
    }
}

@Composable
fun PChatBottomBar(
    currentRoute: String,
    unreadCount: Int,
    pendingAlertCount: Int = 0,
    onNavigate: (String) -> Unit
) {
    val colors = PChatTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
    ) {
        Surface(
            color = colors.bottomBarBg,
            tonalElevation = if (colors.isDark) 10.dp else 4.dp,
            shadowElevation = if (colors.isDark) 0.dp else 10.dp,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(
                    thickness = 1.5.dp,
                    color = NeonPurple.copy(alpha = if (colors.isDark) 0.65f else 0.4f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomNavTab(
                        label = "Chat",
                        selected = currentRoute == PChatRoutes.CHAT,
                        selectedIcon = Icons.Filled.ChatBubble,
                        unselectedIcon = Icons.Outlined.ChatBubbleOutline,
                        badgeCount = unreadCount,
                        showDot = false,
                        testTag = "nav_chat",
                        onClick = { onNavigate(PChatRoutes.CHAT) }
                    )
                    BottomNavTab(
                        label = "Connect",
                        selected = currentRoute == PChatRoutes.CONNECT,
                        selectedIcon = Icons.Filled.Public,
                        unselectedIcon = Icons.Outlined.Public,
                        badgeCount = 0,
                        showDot = false,
                        testTag = "nav_connect",
                        onClick = { onNavigate(PChatRoutes.CONNECT) }
                    )
                    Spacer(modifier = Modifier.width(64.dp))
                    BottomNavTab(
                        label = "Moments",
                        selected = currentRoute == PChatRoutes.MOMENTS,
                        selectedIcon = Icons.Filled.Star,
                        unselectedIcon = Icons.Outlined.StarOutline,
                        badgeCount = 0,
                        showDot = false,
                        testTag = "nav_moments",
                        onClick = { onNavigate(PChatRoutes.MOMENTS) }
                    )
                    BottomNavTab(
                        label = "Me",
                        selected = currentRoute == PChatRoutes.ME,
                        selectedIcon = Icons.Filled.Person,
                        unselectedIcon = Icons.Outlined.Person,
                        badgeCount = 0,
                        showDot = pendingAlertCount > 0,
                        testTag = "nav_me",
                        onClick = { onNavigate(PChatRoutes.ME) }
                    )
                }
            }
        }

        // Elevated glowing circular Plus (+) camera/create button centered above bar
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .testTag("nav_plus")
                .minimumInteractiveComponentSize()
                .size(58.dp)
                .shadow(16.dp, CircleShape, ambientColor = NeonPurple, spotColor = HotPink)
                .clip(CircleShape)
                .background(
                    if (colors.isDark) {
                        Brush.radialGradient(listOf(DeepNavy, VoidBlack))
                    } else {
                        PChatBrandGradient
                    }
                )
                .border(2.5.dp, PChatBrandGradient, CircleShape)
                .clickable { onNavigate(PChatRoutes.PLUS) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Create and share content",
                tint = colors.onGradientText,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun BottomNavTab(
    label: String,
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    badgeCount: Int,
    showDot: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val colors = PChatTheme.colors
    Column(
        modifier = Modifier
            .testTag(testTag)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = if (selected) NeonPurple else colors.textSecondary,
                modifier = Modifier.size(24.dp)
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-5).dp)
                        .clip(CircleShape)
                        .background(ErrorCrimson)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else "$badgeCount",
                        color = colors.onGradientText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (showDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-2).dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ErrorCrimson)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) NeonPurple else colors.textSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        if (selected) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(2.5.dp)
                    .clip(CircleShape)
                    .background(PChatBrandGradient)
            )
        } else {
            Spacer(modifier = Modifier.height(2.5.dp))
        }
    }
}

@Composable
fun IncomingCallBanner(
    call: CallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val colors = PChatTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = colors.elevated,
        tonalElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PChatBrandGradient, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(CyberBlue.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (call.callType.contains("video")) Icons.Filled.Videocam else Icons.Filled.Call,
                    contentDescription = "Incoming call",
                    tint = colors.accentBlue
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${call.callerName} is calling",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (call.callType.contains("video")) "Incoming HD Video Call" else "Incoming Voice Call",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.accentEmerald
                )
            }
            IconButton(
                onClick = onDecline,
                modifier = Modifier
                    .testTag("decline_call_btn")
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .background(ErrorCrimson.copy(alpha = 0.22f))
            ) {
                Icon(Icons.Filled.CallEnd, contentDescription = "Decline call", tint = ErrorCrimson)
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onAccept,
                modifier = Modifier
                    .testTag("accept_call_btn")
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .background(colors.accentEmerald.copy(alpha = 0.22f))
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Accept call", tint = colors.accentEmerald)
            }
        }
    }
}
