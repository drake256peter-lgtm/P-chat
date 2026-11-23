package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Atm
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.payment.CountryCurrencyInfo
import com.example.data.payment.CurrencyConversionEngine
import com.example.model.UiState
import com.example.model.WalletTransaction
import com.example.model.WalletTransfer
import com.example.ui.components.CountryCurrencyPickerDialog
import com.example.ui.components.InternationalSendDialog
import com.example.ui.components.PChatBrandEmblem
import com.example.ui.components.TransactionReceiptDialog
import com.example.ui.components.WalletCosmicBackdrop
import com.example.ui.components.WalletCyberGlobeArt
import com.example.ui.components.WalletDepositDialog
import com.example.ui.components.WalletExchangeCoinsDialog
import com.example.ui.components.WalletGetCoinsDialog
import com.example.ui.components.WalletGold
import com.example.ui.components.WalletGoldCoinsStackArt
import com.example.ui.components.WalletInfoModal
import com.example.ui.components.WalletLavender
import com.example.ui.components.WalletPurpleIconArt
import com.example.ui.components.WalletTrustFooterStrip
import com.example.ui.components.WalletVipCrownButton
import com.example.ui.components.WalletWithdrawDialog
import com.example.ui.components.formatUsdCents
import com.example.ui.theme.AmberSpark
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.HotPink
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatBrandGradient
import com.example.ui.theme.PChatTheme
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.VoidBlack
import com.example.ui.viewmodel.PChatViewModel
import com.example.util.MediaUtils
import java.text.NumberFormat
import java.util.Locale

fun formatCents(cents: Long): String = formatUsdCents(cents)

private val QUICK_CURRENCY_CODES = listOf("USD", "GBP", "EUR", "UGX", "KES")

@Composable
fun WalletScreen(
    viewModel: PChatViewModel,
    initialRecipientUserId: String? = null,
    onBack: () -> Unit
) {
    val colors = PChatTheme.colors
    val myProfileState by viewModel.myProfile.collectAsStateWithLifecycle()
    val walletState by viewModel.walletSummary.collectAsStateWithLifecycle()
    val txState by viewModel.walletTransactions.collectAsStateWithLifecycle()
    val transfersState by viewModel.walletTransfers.collectAsStateWithLifecycle()
    val usersState by viewModel.discoverableUsers.collectAsStateWithLifecycle()
    val fxRates by viewModel.fxRates.collectAsStateWithLifecycle()
    val isFxRefreshing by viewModel.isFxRefreshing.collectAsStateWithLifecycle()
    val isSettling by viewModel.isSettlementInProgress.collectAsStateWithLifecycle()
    val activeReceipt by viewModel.activeReceipt.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshFxRates(force = false)
    }

    val myProfile = (myProfileState as? UiState.Success)?.data
    val wallet = (walletState as? UiState.Success)?.data
    val transactions = (txState as? UiState.Success)?.data.orEmpty()
    val transfers = (transfersState as? UiState.Success)?.data.orEmpty()
    val otherUsers = (usersState as? UiState.Success)?.data.orEmpty()
        .filter { it.userId != viewModel.currentUserId }
    val pendingIncoming = transfers.filter {
        it.recipientId == viewModel.currentUserId && it.status == "sent"
    }

    val defaultLocalCurrency = remember(myProfile?.country, myProfile?.countryFlag) {
        val resolved = CurrencyConversionEngine.resolveCountryCurrency(myProfile?.country, myProfile?.countryFlag)
        if (resolved.currencyCode == "USD") CurrencyConversionEngine.currencyByCode("UGX") else resolved
    }
    var selectedDisplayCurrency by remember { mutableStateOf(CurrencyConversionEngine.currencyByCode("USD")) }
    var previewLocalCurrency by remember(defaultLocalCurrency.currencyCode) {
        mutableStateOf(defaultLocalCurrency)
    }
    var pickingCurrencyTarget by remember { mutableStateOf<String?>(null) } // "primary" or "preview"
    var showAllTransactions by remember { mutableStateOf(false) }

    var showDepositDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var showConvertDialog by remember { mutableStateOf(false) }
    var showGetCoinsDialog by remember { mutableStateOf(false) }
    var showSendDialog by remember(initialRecipientUserId) {
        mutableStateOf(!initialRecipientUserId.isNullOrBlank())
    }
    var infoModal by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    val balanceCents = wallet?.balanceCents ?: 0L
    val coins = wallet?.coins ?: 0L
    val intFormatter = remember { NumberFormat.getIntegerInstance(Locale.US) }

    Box(modifier = Modifier.fillMaxSize()) {
        WalletCosmicBackdrop(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Branded Top Bar matching P-CHAT Wallet screen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .testTag("wallet_back_button")
                        .size(44.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                }
                PChatBrandEmblem(size = 38.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "P-CHAT",
                        style = TextStyle(
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontStyle = FontStyle.Italic,
                            fontSize = 20.sp,
                            brush = PChatBrandGradient
                        )
                    )
                    Text(
                        text = "Chat • Connect • Share",
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = FontStyle.Italic,
                        color = colors.textSecondary
                    )
                }

                Box {
                    IconButton(
                        onClick = {
                            infoModal = Triple(
                                "Wallet Notifications",
                                if (pendingIncoming.isNotEmpty()) {
                                    "You have ${pendingIncoming.size} incoming transfer(s) ready to claim in your local currency below."
                                } else {
                                    "Instant alerts are active for international transfers, P-Coins gifts, and settlement receipts."
                                },
                                if (pendingIncoming.isNotEmpty()) "${pendingIncoming.size} Pending" else "All Caught Up"
                            )
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = "Wallet notifications",
                            tint = colors.textPrimary
                        )
                    }
                    if (pendingIncoming.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-6).dp, y = 6.dp)
                                .clip(CircleShape)
                                .background(ErrorCrimson)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "${pendingIncoming.size}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        viewModel.refreshFxRates(force = true)
                        infoModal = Triple(
                            "Wallet & FX Settings",
                            "Live international FX conversion supports ${CurrencyConversionEngine.SUPPORTED_COUNTRIES.size} countries with automatic recipient currency detection and approved settlement rails.",
                            if (fxRates.isLive) "Live FX Connected" else "Reference FX"
                        )
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Wallet settings", tint = colors.textPrimary)
                }

                WalletVipCrownButton(
                    onClick = {
                        infoModal = Triple(
                            "P-CHAT VIP Wallet",
                            "Your current balance holds ${intFormatter.format(coins)} P-Coins. Enjoy priority international settlement and instant zero-fee P-Coins gifting.",
                            "VIP Member"
                        )
                    }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // "My Wallet" Banner Header
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (colors.isDark) {
                                    Brush.horizontalGradient(
                                        listOf(
                                            NeonPurple.copy(alpha = 0.28f),
                                            Color(0xFF140B29).copy(alpha = 0.92f),
                                            ElectricBlue.copy(alpha = 0.16f)
                                        )
                                    )
                                } else {
                                    Brush.horizontalGradient(listOf(colors.surface, colors.elevated))
                                }
                            )
                            .border(1.dp, NeonPurple.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WalletPurpleIconArt(modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "My Wallet",
                                    fontSize = 21.sp,
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "Your Money. Your Coins. Worldwide.",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = FontStyle.Italic,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(LiveEmerald.copy(alpha = 0.12f))
                                    .border(1.dp, LiveEmerald.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    Icons.Filled.VerifiedUser,
                                    contentDescription = "Secured",
                                    tint = LiveEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Column {
                                    Text(
                                        text = "Secured",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "Trusted",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LiveEmerald
                                    )
                                }
                            }
                        }
                    }
                }

                // Dual Side-by-Side Hero Balance Cards
                item {
                    val primaryDisplayText = remember(balanceCents, selectedDisplayCurrency, fxRates) {
                        if (selectedDisplayCurrency.currencyCode == "USD") {
                            formatUsdCents(balanceCents)
                        } else {
                            val rate = fxRates.rateFor(selectedDisplayCurrency.currencyCode)
                            val minor = CurrencyConversionEngine.convertUsdCentsToLocalMinor(
                                usdCents = balanceCents,
                                rate = rate,
                                decimals = selectedDisplayCurrency.minorUnitDecimals
                            )
                            CurrencyConversionEngine.formatLocalAmount(minor, selectedDisplayCurrency)
                        }
                    }
                    val secondaryPreviewText = remember(balanceCents, previewLocalCurrency, fxRates) {
                        val rate = fxRates.rateFor(previewLocalCurrency.currencyCode)
                        val minor = CurrencyConversionEngine.convertUsdCentsToLocalMinor(
                            usdCents = balanceCents,
                            rate = rate,
                            decimals = previewLocalCurrency.minorUnitDecimals
                        )
                        "≈ ${CurrencyConversionEngine.formatLocalAmount(minor, previewLocalCurrency)}"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Left Card: Real Money Balance
                        Column(
                            modifier = Modifier
                                .weight(1.18f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (colors.isDark) {
                                        Brush.radialGradient(
                                            listOf(Color(0xFF14275C), Color(0xFF070C1F))
                                        )
                                    } else {
                                        Brush.verticalGradient(listOf(colors.card, colors.elevated))
                                    }
                                )
                                .border(
                                    1.5.dp,
                                    Brush.linearGradient(listOf(ElectricBlue, NeonPurple)),
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Real Money Balance",
                                    fontSize = 11.sp,
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "Real money balance info",
                                    tint = NeonPurple,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            infoModal = Triple(
                                                "Real Money Balance",
                                                "Your USD balance (${formatUsdCents(balanceCents)}) converts automatically at live exchange rates across ${CurrencyConversionEngine.SUPPORTED_COUNTRIES.size} countries. Tap any flag below or '+ More' to preview balances in local currency.",
                                                "Verified Wallet"
                                            )
                                        }
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                WalletCyberGlobeArt(modifier = Modifier.size(52.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { pickingCurrencyTarget = "primary" }
                                    ) {
                                        Text(
                                            text = primaryDisplayText,
                                            fontSize = 21.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f, fill = false)
                                                .testTag("wallet_usd_balance")
                                        )
                                        Icon(
                                            Icons.Filled.KeyboardArrowDown,
                                            contentDescription = "Choose display currency",
                                            tint = ElectricBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(VoidBlack.copy(alpha = 0.55f))
                                            .border(1.dp, NeonPurple.copy(alpha = 0.65f), RoundedCornerShape(50))
                                            .clickable { pickingCurrencyTarget = "preview" }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = secondaryPreviewText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ElectricBlue,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Quick Country Flag Currency Strip + More
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                QUICK_CURRENCY_CODES.forEach { code ->
                                    val info = remember(code) { CurrencyConversionEngine.currencyByCode(code) }
                                    val isSelected = selectedDisplayCurrency.currencyCode == code
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) ElectricBlue.copy(alpha = 0.22f) else Color.Transparent
                                            )
                                            .border(
                                                width = if (isSelected) 1.dp else 0.dp,
                                                color = if (isSelected) ElectricBlue else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                selectedDisplayCurrency = info
                                                if (code != "USD") previewLocalCurrency = info
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(info.flag, fontSize = 13.sp)
                                        Text(
                                            text = info.currencyCode,
                                            fontSize = 8.5.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            color = if (isSelected) ElectricBlue else colors.textSecondary
                                        )
                                    }
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .testTag("wallet_more_currencies")
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { pickingCurrencyTarget = "preview" }
                                        .padding(horizontal = 3.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, colors.textSecondary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = "More countries & currencies",
                                            tint = colors.textPrimary,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "+ More",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }

                        // Right Card: P-CHAT Coins
                        Column(
                            modifier = Modifier
                                .weight(0.88f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (colors.isDark) {
                                        Brush.radialGradient(
                                            listOf(Color(0xFF2B1638), Color(0xFF10071C))
                                        )
                                    } else {
                                        Brush.verticalGradient(listOf(colors.card, colors.elevated))
                                    }
                                )
                                .border(
                                    1.5.dp,
                                    Brush.linearGradient(listOf(NeonPurple, AmberSpark, WalletGold)),
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "P-CHAT Coins",
                                    fontSize = 11.sp,
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "P-CHAT Coins info",
                                    tint = NeonPurple,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            infoModal = Triple(
                                                "P-CHAT Coins",
                                                "Send gifts, unlock creator rewards, and tip friends worldwide. Exchange anytime at $1.00 = 100 P-Coins.",
                                                "100 PC = $1.00"
                                            )
                                        }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                WalletGoldCoinsStackArt(modifier = Modifier.size(50.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = intFormatter.format(coins),
                                        fontSize = 23.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = WalletGold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("wallet_coins_balance")
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VoidBlack.copy(alpha = 0.4f))
                                    .clickable { showGetCoinsDialog = true }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = WalletGold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "≈ ${intFormatter.format(coins / 10)} Gifts & Rewards",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WalletGold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // 5 Quick Action Buttons Row matching Screenshot
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WalletQuickActionTile(
                            icon = Icons.Filled.Add,
                            title = "Add Money",
                            subtitle = "(Deposit)",
                            accent = NeonPurple,
                            fillIconCircle = true,
                            testTag = "wallet_top_up_btn",
                            modifier = Modifier.weight(1f),
                            onClick = { showDepositDialog = true }
                        )
                        WalletQuickActionTile(
                            icon = Icons.AutoMirrored.Filled.Send,
                            title = "Send Money",
                            subtitle = "(To User)",
                            accent = WalletLavender,
                            fillIconCircle = false,
                            testTag = "wallet_send_btn",
                            modifier = Modifier.weight(1f),
                            onClick = { showSendDialog = true }
                        )
                        WalletQuickActionTile(
                            icon = Icons.Filled.Atm,
                            title = "Withdraw",
                            subtitle = "(Cash Out)",
                            accent = WalletLavender,
                            fillIconCircle = false,
                            testTag = "wallet_withdraw_btn",
                            modifier = Modifier.weight(1f),
                            onClick = { showWithdrawDialog = true }
                        )
                        WalletQuickActionTile(
                            icon = Icons.Filled.CardGiftcard,
                            title = "Get Coins",
                            subtitle = "(Tasks & Gifts)",
                            accent = NeonPurple,
                            fillIconCircle = false,
                            testTag = "wallet_get_coins_btn",
                            modifier = Modifier.weight(1f),
                            onClick = { showGetCoinsDialog = true }
                        )
                        WalletQuickActionTile(
                            icon = Icons.Filled.CurrencyExchange,
                            title = "Exchange",
                            subtitle = "(Money ⇄ Coins)",
                            accent = HotPink,
                            fillIconCircle = false,
                            testTag = "wallet_convert_btn",
                            modifier = Modifier.weight(1f),
                            onClick = { showConvertDialog = true }
                        )
                    }
                }

                // Claimable Incoming Transfers (if any)
                if (pendingIncoming.isNotEmpty()) {
                    items(pendingIncoming, key = { "incoming_${it.id}" }) { transfer ->
                        IncomingTransferBanner(
                            transfer = transfer,
                            onClaim = { viewModel.claimTransfer(transfer) }
                        )
                    }
                }

                // Recent Transactions Section Container matching Screenshot
                item {
                    val displayedTransactions = if (showAllTransactions) transactions else transactions.take(4)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (colors.isDark) {
                                    Brush.verticalGradient(listOf(Color(0xFF140924), Color(0xFF080814)))
                                } else {
                                    Brush.verticalGradient(listOf(colors.card, colors.surface))
                                }
                            )
                            .border(
                                1.3.dp,
                                Brush.horizontalGradient(listOf(NeonPurple, HotPink, ElectricBlue)),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.History,
                                    contentDescription = null,
                                    tint = WalletLavender,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Recent Transactions",
                                    fontSize = 15.sp,
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showAllTransactions = !showAllTransactions }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (showAllTransactions && transactions.size > 4) "Show Less" else "View All",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = NeonPurple.copy(alpha = 0.28f))

                        when (val state = txState) {
                            is UiState.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = ElectricBlue)
                                }
                            }
                            is UiState.Error -> {
                                Text(
                                    text = state.message,
                                    color = ErrorCrimson,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                            is UiState.Success -> {
                                if (displayedTransactions.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "No wallet transactions yet",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Tap Add Money, Get Coins, or Send Money to get started.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    displayedTransactions.forEachIndexed { idx, tx ->
                                        RecentTransactionItemRow(
                                            tx = tx,
                                            onClick = { viewModel.openReceipt(tx) }
                                        )
                                        if (idx < displayedTransactions.lastIndex) {
                                            HorizontalDivider(color = colors.border.copy(alpha = 0.35f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Trust Feature Strip matching Screenshot
            WalletTrustFooterStrip(
                onMultiCurrencyClick = { pickingCurrencyTarget = "preview" },
                onSecurityClick = {
                    infoModal = Triple(
                        "Fast & Secure Transactions",
                        "All transfers verify balance, fee disclosure, and payment provider settlement before updating ledgers and issuing instant digital receipts.",
                        "256-bit Verified"
                    )
                },
                onEmblemClick = { viewModel.refreshFxRates(force = true) },
                onGlobalPaymentsClick = { showSendDialog = true },
                onSupportClick = {
                    infoModal = Triple(
                        "24/7 P-CHAT Wallet Support",
                        "Need help with a transfer, Mobile Money deposit, or FX quote? Tap any transaction row above to inspect your verified Settlement Ref & Receipt ID.",
                        "24/7 Live Help"
                    )
                },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }

    // Currency Picker for Primary Card or Local Equivalent Preview
    pickingCurrencyTarget?.let { target ->
        CountryCurrencyPickerDialog(
            selectedCurrencyCode = if (target == "primary") selectedDisplayCurrency.currencyCode else previewLocalCurrency.currencyCode,
            fxRates = fxRates,
            title = if (target == "primary") "Select Display Currency" else "Select Local Equivalent Currency",
            onSelect = { info: CountryCurrencyInfo ->
                if (target == "primary") {
                    selectedDisplayCurrency = info
                } else {
                    previewLocalCurrency = info
                }
                pickingCurrencyTarget = null
            },
            onDismiss = { pickingCurrencyTarget = null }
        )
    }

    if (showDepositDialog) {
        WalletDepositDialog(
            onDismiss = { showDepositDialog = false },
            onConfirm = { cents, method ->
                showDepositDialog = false
                viewModel.depositToWallet(cents, method)
            }
        )
    }

    if (showWithdrawDialog) {
        WalletWithdrawDialog(
            maxCents = balanceCents,
            onDismiss = { showWithdrawDialog = false },
            onConfirm = { cents, dest ->
                showWithdrawDialog = false
                viewModel.withdrawFromWallet(cents, dest)
            }
        )
    }

    if (showConvertDialog) {
        WalletExchangeCoinsDialog(
            maxCents = balanceCents,
            onDismiss = { showConvertDialog = false },
            onConfirm = { cents ->
                showConvertDialog = false
                viewModel.convertUsdToCoins(cents)
            }
        )
    }

    if (showGetCoinsDialog) {
        WalletGetCoinsDialog(
            currentCoins = coins,
            maxCents = balanceCents,
            onClaimTask = { amount, title ->
                viewModel.claimCoinReward(amount, title)
            },
            onOpenExchange = { showConvertDialog = true },
            onDismiss = { showGetCoinsDialog = false }
        )
    }

    if (showSendDialog) {
        InternationalSendDialog(
            friends = otherUsers,
            preselectedUserId = initialRecipientUserId,
            maxCents = balanceCents,
            maxCoins = coins,
            fxRates = fxRates,
            isFxRefreshing = isFxRefreshing,
            isSettling = isSettling,
            onRefreshFx = { viewModel.refreshFxRates(force = true) },
            onDismiss = { showSendDialog = false },
            onSendInternational = { recipient, quote, note ->
                viewModel.sendInternationalTransfer(
                    recipient = recipient,
                    quote = quote,
                    note = note,
                    onComplete = { showSendDialog = false }
                )
            },
            onSendCoinsGift = { recipient, giftCoins, note ->
                showSendDialog = false
                viewModel.sendWalletTransfer(recipient, 0L, giftCoins, note)
            }
        )
    }

    activeReceipt?.let { receiptTx ->
        TransactionReceiptDialog(
            transaction = receiptTx,
            onDismiss = { viewModel.dismissReceipt() }
        )
    }

    infoModal?.let { (title, body, badge) ->
        WalletInfoModal(
            title = title,
            body = body,
            badgeText = badge,
            fxRates = fxRates,
            onDismiss = { infoModal = null }
        )
    }
}

@Composable
private fun WalletQuickActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    fillIconCircle: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = PChatTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .testTag(testTag)
            .height(74.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (colors.isDark) {
                    Brush.verticalGradient(listOf(Color(0xFF151C3D), Color(0xFF090C1C)))
                } else {
                    Brush.verticalGradient(listOf(colors.card, colors.elevated))
                }
            )
            .border(
                1.2.dp,
                Brush.linearGradient(listOf(ElectricBlue.copy(alpha = 0.7f), NeonPurple.copy(alpha = 0.7f))),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 6.dp)
    ) {
        if (fillIconCircle) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(NeonPurple, HotPink))),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        } else {
            Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            fontSize = 8.sp,
            fontStyle = FontStyle.Italic,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RecentTransactionItemRow(
    tx: WalletTransaction,
    onClick: () -> Unit
) {
    val colors = PChatTheme.colors
    val isDeposit = tx.txType == "deposit" || tx.txType == "transfer_in"
    val isCoinPositive = tx.coinDelta > 0L && tx.amountCents == 0L
    val icon: ImageVector = when {
        tx.txType == "deposit" -> Icons.Filled.ArrowDownward
        tx.txType == "gift" -> Icons.Filled.CardGiftcard
        tx.txType == "convert" -> Icons.Filled.CurrencyExchange
        isCoinPositive -> Icons.Filled.Star
        else -> Icons.AutoMirrored.Filled.Send
    }
    val iconTint = when {
        tx.txType == "deposit" -> Color.White
        isCoinPositive -> WalletGold
        tx.txType == "gift" -> NeonPurple
        else -> WalletLavender
    }
    val iconBg = when {
        tx.txType == "deposit" -> Color(0xFF009B55)
        isCoinPositive -> WalletGold.copy(alpha = 0.22f)
        tx.txType == "gift" -> NeonPurple.copy(alpha = 0.20f)
        else -> ElectricBlue.copy(alpha = 0.14f)
    }

    val deltaText = buildString {
        if (tx.localCurrency.isNotBlank() && tx.localAmountMinor > 0L && tx.localCurrency != "USD") {
            val info = CurrencyConversionEngine.currencyByCode(tx.localCurrency)
            val sign = when (tx.txType) {
                "transfer_in" -> "+"
                "transfer_out" -> "-"
                else -> ""
            }
            append("$sign${CurrencyConversionEngine.formatLocalAmount(tx.localAmountMinor, info)}")
        } else if (tx.amountCents != 0L) {
            val sign = if (tx.amountCents > 0L) "+" else ""
            append("$sign${formatUsdCents(tx.amountCents)}")
        }
        if (tx.coinDelta != 0L) {
            if (isNotEmpty()) append(" • ")
            val sign = if (tx.coinDelta > 0L) "+" else ""
            append("$sign${tx.coinDelta} Coins")
        }
    }
    val deltaColor = when {
        tx.coinDelta > 0L && tx.amountCents <= 0L -> WalletGold
        tx.amountCents > 0L || isDeposit -> LiveEmerald
        else -> ErrorCrimson
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tx.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = MediaUtils.formatTimestamp(tx.createdAt?.seconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
                if (deltaText.isNotBlank()) {
                    Text(
                        text = " • $deltaText",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = deltaColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Surface(
            color = Color(0xFF0A472A),
            shape = RoundedCornerShape(50),
            modifier = Modifier.border(1.dp, LiveEmerald.copy(alpha = 0.65f), RoundedCornerShape(50))
        ) {
            Text(
                text = if (tx.status.equals("pending", ignoreCase = true)) "Pending" else "Completed",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = "View receipt",
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun IncomingTransferBanner(
    transfer: WalletTransfer,
    onClaim: () -> Unit
) {
    val colors = PChatTheme.colors
    val targetCurrency = transfer.recipientCurrency.ifBlank { "USD" }
    val targetInfo = CurrencyConversionEngine.currencyByCode(targetCurrency)
    val localText = if (transfer.recipientLocalAmountMinor > 0L && targetCurrency != "USD") {
        CurrencyConversionEngine.formatLocalAmount(transfer.recipientLocalAmountMinor, targetInfo)
    } else if (transfer.amountCents > 0L) {
        formatUsdCents(transfer.amountCents)
    } else {
        "${transfer.coins} P-Coins"
    }

    Surface(
        color = colors.elevated,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, LiveEmerald.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = LiveEmerald)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${transfer.senderName} sent $localText",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                if (transfer.paymentProvider.isNotBlank()) {
                    Text(
                        text = "Verified via ${transfer.paymentProvider}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricBlue
                    )
                }
            }
            Button(
                onClick = onClaim,
                colors = ButtonDefaults.buttonColors(containerColor = LiveEmerald, contentColor = VoidBlack),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Claim", fontWeight = FontWeight.Bold)
            }
        }
    }
}
