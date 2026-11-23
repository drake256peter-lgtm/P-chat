package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.payment.ApprovedPaymentProvider
import com.example.data.payment.CountryCurrencyInfo
import com.example.data.payment.CurrencyConversionEngine
import com.example.data.payment.FxRatesSnapshot
import com.example.data.payment.FxTransferQuote
import com.example.data.payment.PaymentProviderGateway
import com.example.model.UserProfile
import com.example.model.WalletTransaction
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.HotPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatBrandGradient
import com.example.ui.theme.PChatTheme
import com.example.util.MediaUtils
import java.util.Locale

private fun formatUsd(cents: Long): String {
    val absDollars = kotlin.math.abs(cents) / 100.0
    val formatted = String.format(Locale.US, "$%.2f", absDollars)
    return if (cents < 0) "-$formatted" else formatted
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InternationalSendDialog(
    friends: List<UserProfile>,
    preselectedUserId: String?,
    maxCents: Long,
    maxCoins: Long,
    fxRates: FxRatesSnapshot,
    isFxRefreshing: Boolean,
    isSettling: Boolean,
    onRefreshFx: () -> Unit,
    onDismiss: () -> Unit,
    onSendInternational: (UserProfile, FxTransferQuote, String) -> Unit,
    onSendCoinsGift: (UserProfile, Long, String) -> Unit
) {
    val colors = PChatTheme.colors
    var selectedRecipient by remember(friends, preselectedUserId) {
        mutableStateOf(
            friends.firstOrNull { it.userId == preselectedUserId } ?: friends.firstOrNull()
        )
    }
    var sendModeCoins by remember { mutableStateOf(false) }
    var valueText by remember { mutableStateOf("25") }
    var note by remember { mutableStateOf("") }

    // Automatically identify recipient country and select their local currency whenever recipient changes
    val autoCountryCurrency = remember(selectedRecipient?.userId, selectedRecipient?.country, selectedRecipient?.countryFlag) {
        CurrencyConversionEngine.resolveCountryCurrency(
            country = selectedRecipient?.country,
            countryFlag = selectedRecipient?.countryFlag
        )
    }
    var activeDestination by remember(autoCountryCurrency.currencyCode, selectedRecipient?.userId) {
        mutableStateOf(autoCountryCurrency)
    }
    var showCurrencyMenu by remember { mutableStateOf(false) }

    val approvedProviders = remember(activeDestination.currencyCode) {
        PaymentProviderGateway.providersForCurrency(activeDestination.currencyCode)
    }
    var selectedProvider by remember(activeDestination.currencyCode) {
        mutableStateOf(PaymentProviderGateway.defaultProviderForCurrency(activeDestination.currencyCode))
    }

    LaunchedEffect(approvedProviders) {
        if (approvedProviders.none { it.id == selectedProvider.id }) {
            selectedProvider = approvedProviders.firstOrNull()
                ?: PaymentProviderGateway.APPROVED_PROVIDERS.first()
        }
    }

    val amountCents = if (!sendModeCoins) ((valueText.toDoubleOrNull() ?: 0.0) * 100).toLong() else 0L
    val coins = if (sendModeCoins) (valueText.toLongOrNull() ?: 0L) else 0L

    val quote = remember(amountCents, activeDestination, selectedProvider, fxRates, sendModeCoins) {
        if (!sendModeCoins && amountCents >= 100L) {
            CurrencyConversionEngine.calculateInternationalQuote(
                sendUsdCents = amountCents,
                recipientCountry = activeDestination,
                provider = selectedProvider,
                snapshot = fxRates
            )
        } else {
            null
        }
    }

    val hasEnoughForFx = quote != null && quote.totalDebitedCents <= maxCents
    val isCoinsValid = sendModeCoins && coins in 1L..maxCoins
    val canConfirm = !isSettling && selectedRecipient != null &&
        ((!sendModeCoins && hasEnoughForFx) || isCoinsValid)

    AlertDialog(
        onDismissRequest = { if (!isSettling) onDismiss() },
        containerColor = colors.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (sendModeCoins) "Send P-Coins Gift" else "International Transfer",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (sendModeCoins) "Instant zero-fee P-Coins" else "Auto-FX • Verified Provider Rails",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonPurple
                    )
                }
                IconButton(
                    onClick = onRefreshFx,
                    enabled = !isFxRefreshing && !isSettling,
                    modifier = Modifier.testTag("refresh_fx_button")
                ) {
                    if (isFxRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.accentBlue
                        )
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh live exchange rates",
                            tint = colors.accentBlue
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (friends.isEmpty()) {
                    Text(
                        text = "No discoverable P-CHAT users available yet. Connect with friends first to send international transfers!",
                        color = colors.textSecondary
                    )
                } else {
                    Text(
                        text = "1. Select Recipient",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        friends.take(8).forEach { user ->
                            val userCountry = CurrencyConversionEngine.resolveCountryCurrency(
                                user.country,
                                user.countryFlag
                            )
                            FilterChip(
                                selected = selectedRecipient?.userId == user.userId,
                                onClick = { if (!isSettling) selectedRecipient = user },
                                leadingIcon = {
                                    UserAvatar(
                                        name = user.displayName,
                                        photoBase64 = user.photoBase64,
                                        colorHex = user.avatarColorHex,
                                        size = 20.dp
                                    )
                                },
                                label = {
                                    Text("${userCountry.flag} ${user.displayName.substringBefore(" ")}")
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonPurple.copy(alpha = 0.24f),
                                    selectedLabelColor = colors.textPrimary
                                )
                            )
                        }
                    }

                    // Mode toggle: International FX vs P-Coins
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !sendModeCoins,
                            onClick = {
                                if (!isSettling) {
                                    sendModeCoins = false
                                    valueText = "25"
                                }
                            },
                            label = { Text("FX Cash (${formatUsd(maxCents)})") },
                            modifier = Modifier.testTag("mode_intl_fx_chip")
                        )
                        FilterChip(
                            selected = sendModeCoins,
                            onClick = {
                                if (!isSettling) {
                                    sendModeCoins = true
                                    valueText = "50"
                                }
                            },
                            label = { Text("P-Coins ($maxCoins PC)") },
                            modifier = Modifier.testTag("mode_coins_chip")
                        )
                    }

                    if (!sendModeCoins && selectedRecipient != null) {
                        // Auto-detected Recipient Country & Local Currency Pill
                        Box {
                            Surface(
                                color = colors.card,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(enabled = !isSettling) { showCurrencyMenu = true }
                                    .border(1.dp, NeonPurple.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                    .testTag("auto_currency_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Public,
                                        contentDescription = null,
                                        tint = NeonPurple,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Recipient Country: ${activeDestination.flag} ${activeDestination.countryName}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Local Currency: ${activeDestination.currencyCode} • ${activeDestination.currencyName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.accentBlue
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = activeDestination.currencyCode,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = HotPink,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Change",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.accentBlue
                                        )
                                    }
                                }
                            }
                        }

                        if (showCurrencyMenu) {
                            CountryCurrencyPickerDialog(
                                selectedCurrencyCode = activeDestination.currencyCode,
                                fxRates = fxRates,
                                onSelect = { info ->
                                    activeDestination = info
                                    showCurrencyMenu = false
                                },
                                onDismiss = { showCurrencyMenu = false }
                            )
                        }
                    }

                    // Preset quick amounts for USD
                    if (!sendModeCoins) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("10", "25", "50", "100").forEach { preset ->
                                FilterChip(
                                    selected = valueText == preset,
                                    onClick = { if (!isSettling) valueText = preset },
                                    label = { Text("$$preset") }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = valueText,
                        onValueChange = {
                            if (!isSettling) {
                                valueText = it.filter { ch -> ch.isDigit() || (!sendModeCoins && ch == '.') }
                            }
                        },
                        label = {
                            Text(if (sendModeCoins) "P-Coins Amount" else "You Send (USD)")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("send_amount_input")
                    )

                    if (!sendModeCoins) {
                        Text(
                            text = "2. Approved Payment Provider",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textSecondary
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            approvedProviders.forEach { provider ->
                                FilterChip(
                                    selected = selectedProvider.id == provider.id,
                                    onClick = { if (!isSettling) selectedProvider = provider },
                                    label = {
                                        Text(
                                            text = "${provider.badge.substringBefore(" ")} ${provider.displayName}",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.accentBlue.copy(alpha = 0.2f),
                                        selectedLabelColor = colors.accentBlue
                                    )
                                )
                            }
                        }

                        // Pre-Confirmation Exact Local Amount & Fee Breakdown Card
                        if (quote != null) {
                            val localFormatted = CurrencyConversionEngine.formatLocalAmount(
                                quote.recipientLocalAmountMinor,
                                quote.recipientCurrency
                            )
                            val rateFormatted = CurrencyConversionEngine.formatExchangeRate(
                                quote.exchangeRate,
                                quote.recipientCurrency.currencyCode
                            )

                            Surface(
                                color = colors.card,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.2.dp, PChatBrandGradient, RoundedCornerShape(16.dp))
                                    .testTag("fx_quote_summary_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Recipient Receives Exactly (${quote.recipientCurrency.currencyCode})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.textSecondary
                                    )
                                    Text(
                                        text = localFormatted,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accentEmerald,
                                        modifier = Modifier.testTag("fx_recipient_exact_amount")
                                    )
                                    Text(
                                        text = "$rateFormatted • ${quote.rateSourceLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.accentBlue
                                    )
                                    HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
                                    QuoteLineRow("Send Amount", formatUsd(quote.sendUsdCents))
                                    QuoteLineRow(
                                        "FX Conversion Fee (${quote.provider.fxMarkupPercent}%)",
                                        formatUsd(quote.fxConversionFeeCents)
                                    )
                                    QuoteLineRow(
                                        "${quote.provider.displayName} Rail Fee",
                                        formatUsd(quote.providerRailFeeCents)
                                    )
                                    QuoteLineRow(
                                        "Settlement Clearing Fee",
                                        formatUsd(quote.networkSettlementFeeCents)
                                    )
                                    QuoteLineRow(
                                        "Total Transaction Fees",
                                        formatUsd(quote.totalFeeCents),
                                        valueColor = HotPink
                                    )
                                    HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
                                    QuoteLineRow(
                                        "Total Debited (USD)",
                                        formatUsd(quote.totalDebitedCents),
                                        bold = true,
                                        valueColor = if (hasEnoughForFx) colors.textPrimary else ErrorCrimson
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.Shield,
                                            contentDescription = null,
                                            tint = colors.accentEmerald,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Approved ${quote.provider.regulatoryLicenseId} • ${quote.provider.settlementSla}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.accentEmerald
                                        )
                                    }
                                    if (!hasEnoughForFx) {
                                        Text(
                                            text = "Insufficient USD balance. Need ${formatUsd(quote.totalDebitedCents)} (Available: ${formatUsd(maxCents)}).",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ErrorCrimson
                                        )
                                    }
                                }
                            }
                        } else if (amountCents in 1L..99L) {
                            Text(
                                text = "Minimum international transfer is $1.00 USD.",
                                style = MaterialTheme.typography.labelSmall,
                                color = ErrorCrimson
                            )
                        }
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { if (!isSettling) note = it },
                        label = { Text("Transfer note (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isSettling) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = NeonPurple
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Verifying settlement with ${selectedProvider.displayName}...",
                                style = MaterialTheme.typography.labelLarge,
                                color = NeonPurple
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rec = selectedRecipient ?: return@Button
                    if (sendModeCoins) {
                        onSendCoinsGift(rec, coins, note)
                    } else if (quote != null) {
                        onSendInternational(rec, quote, note)
                    }
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonPurple,
                    contentColor = colors.onGradientText
                ),
                modifier = Modifier.testTag("confirm_international_send_btn")
            ) {
                if (isSettling) {
                    Text("Settling...")
                } else if (!sendModeCoins && quote != null) {
                    val localFmt = CurrencyConversionEngine.formatLocalAmount(
                        quote.recipientLocalAmountMinor,
                        quote.recipientCurrency
                    )
                    Text("Send $localFmt")
                } else {
                    Text("Send Gift")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSettling
            ) {
                Text("Cancel", color = colors.textSecondary)
            }
        }
    )
}

@Composable
private fun QuoteLineRow(
    label: String,
    value: String,
    bold: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color? = null
) {
    val colors = PChatTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (bold) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
            color = if (bold) colors.textPrimary else colors.textSecondary,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            color = valueColor ?: colors.textPrimary,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
fun TransactionReceiptDialog(
    transaction: WalletTransaction,
    onDismiss: () -> Unit
) {
    val colors = PChatTheme.colors
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    val hasLocalFx = transaction.localCurrency.isNotBlank() &&
        transaction.localAmountMinor > 0L
    val localInfo = CurrencyConversionEngine.currencyByCode(
        transaction.localCurrency.ifBlank { "USD" }
    )
    val localFormatted = if (hasLocalFx) {
        CurrencyConversionEngine.formatLocalAmount(transaction.localAmountMinor, localInfo)
    } else {
        formatUsd(kotlin.math.abs(transaction.amountCents))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(NeonPurple, HotPink))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (transaction.settlementVerified) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.AutoMirrored.Filled.ReceiptLong
                        },
                        contentDescription = null,
                        tint = colors.onGradientText
                    )
                }
                Column {
                    Text(
                        text = "P-CHAT Wallet Receipt",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (transaction.settlementVerified) {
                            "Settlement Verified ✓"
                        } else {
                            transaction.status.replaceFirstChar { it.uppercase() }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accentEmerald,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Hero Local Amount Banner
                Surface(
                    color = colors.card,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, PChatBrandGradient, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (transaction.txType == "transfer_in") {
                                "Credited in Local Currency"
                            } else if (hasLocalFx) {
                                "Recipient Received (${localInfo.currencyCode})"
                            } else {
                                transaction.title
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary
                        )
                        Text(
                            text = localFormatted,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentEmerald,
                            modifier = Modifier.testTag("receipt_hero_amount")
                        )
                        if (transaction.baseAmountCents > 0L) {
                            Text(
                                text = "USD Value: ${formatUsd(transaction.baseAmountCents)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.accentBlue
                            )
                        }
                    }
                }

                // Itemized Receipt Details
                Surface(
                    color = colors.card,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (transaction.receiptId.isNotBlank()) {
                            QuoteLineRow("Receipt ID", transaction.receiptId, bold = true)
                        }
                        if (transaction.settlementRef.isNotBlank()) {
                            QuoteLineRow("Settlement Ref", transaction.settlementRef, valueColor = colors.accentEmerald)
                        }
                        if (transaction.counterpartyName.isNotBlank()) {
                            val roleLabel = if (transaction.txType == "transfer_in") "Sender" else "Recipient"
                            QuoteLineRow(roleLabel, transaction.counterpartyName)
                        }
                        if (transaction.recipientCountry.isNotBlank()) {
                            val countryInfo = CurrencyConversionEngine.resolveCountryCurrency(transaction.recipientCountry)
                            QuoteLineRow("Destination", "${countryInfo.flag} ${transaction.recipientCountry}")
                        }
                        if (transaction.paymentProvider.isNotBlank()) {
                            QuoteLineRow("Payment Provider", transaction.paymentProvider)
                        }
                        if (hasLocalFx && transaction.exchangeRate > 0.0) {
                            QuoteLineRow(
                                "Exchange Rate",
                                CurrencyConversionEngine.formatExchangeRate(
                                    transaction.exchangeRate,
                                    transaction.localCurrency
                                ),
                                valueColor = colors.accentBlue
                            )
                        }
                        if (transaction.baseAmountCents > 0L) {
                            QuoteLineRow("Transfer Principal", formatUsd(transaction.baseAmountCents))
                        }
                        if (transaction.feeCents > 0L) {
                            QuoteLineRow("Transaction Fees", formatUsd(transaction.feeCents), valueColor = HotPink)
                        }
                        if (transaction.amountCents != 0L) {
                            val label = if (transaction.amountCents < 0) "Total Paid (USD)" else "USD Equivalent"
                            QuoteLineRow(label, formatUsd(kotlin.math.abs(transaction.amountCents)), bold = true)
                        }
                        if (transaction.coinDelta != 0L) {
                            QuoteLineRow("P-Coins", "${transaction.coinDelta} PC", bold = true)
                        }
                        val timeText = MediaUtils.formatTimestamp(transaction.createdAt?.seconds)
                        if (timeText.isNotBlank()) {
                            QuoteLineRow("Timestamp", timeText)
                        }
                        if (transaction.note.isNotBlank()) {
                            HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
                            Text(
                                text = "Note: ${transaction.note}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }

                if (transaction.receiptId.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            val copyPayload = buildString {
                                appendLine("P-CHAT Verified Receipt")
                                appendLine("Receipt ID: ${transaction.receiptId}")
                                if (transaction.settlementRef.isNotBlank()) {
                                    appendLine("Settlement Ref: ${transaction.settlementRef}")
                                }
                                appendLine("Local Amount: $localFormatted")
                                if (transaction.paymentProvider.isNotBlank()) {
                                    appendLine("Provider: ${transaction.paymentProvider}")
                                }
                            }
                            clipboard.setText(AnnotatedString(copyPayload))
                            copied = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (copied) "Receipt Copied!" else "Copy Receipt Details")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonPurple,
                    contentColor = colors.onGradientText
                ),
                modifier = Modifier.testTag("receipt_done_button")
            ) {
                Text("Done")
            }
        }
    )
}

@Composable
fun CountryCurrencyPickerDialog(
    selectedCurrencyCode: String?,
    fxRates: FxRatesSnapshot? = null,
    title: String = "Select Country & Currency",
    onSelect: (CountryCurrencyInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = PChatTheme.colors
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) { CurrencyConversionEngine.searchCountries(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Column {
                Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                Text(
                    text = "${CurrencyConversionEngine.SUPPORTED_COUNTRIES.size} countries & currencies supported",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accentBlue
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search country, currency, or code (e.g. TZS, EUR, Japan)") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = colors.accentBlue) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("country_currency_search_input")
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 330.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { "${it.countryName}_${it.currencyCode}" }) { info ->
                        val isSelected = selectedCurrencyCode.equals(info.currencyCode, ignoreCase = true)
                        Surface(
                            color = if (isSelected) NeonPurple.copy(alpha = 0.22f) else colors.card,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelect(info) }
                                .border(
                                    1.dp,
                                    if (isSelected) colors.accentBlue else colors.border,
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(info.flag, fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = info.countryName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val rateText = fxRates?.ratesFromUsd?.get(info.currencyCode)?.let { r ->
                                        " • ${CurrencyConversionEngine.formatExchangeRate(r, info.currencyCode)}"
                                    }.orEmpty()
                                    Text(
                                        text = "${info.currencyName}$rateText",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = if (isSelected) colors.accentBlue else colors.elevated,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = info.currencyCode,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) colors.primaryButtonContent else colors.accentBlue,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = colors.accentBlue)
            }
        }
    )
}

