package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.payment.CurrencyConversionEngine
import com.example.data.payment.FxRatesSnapshot
import com.example.ui.theme.HotPink
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatTheme
import java.util.Locale

fun formatUsdCents(cents: Long): String {
    val absDollars = kotlin.math.abs(cents) / 100.0
    val formatted = String.format(Locale.US, "$%.2f", absDollars)
    return if (cents < 0) "-$formatted" else formatted
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletDepositDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit
) {
    val colors = PChatTheme.colors
    var amountText by remember { mutableStateOf("25") }
    var selectedMethod by remember { mutableStateOf("MTN Mobile Money") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Add Money (Deposit)", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("10", "25", "50", "100").forEach { preset ->
                        FilterChip(
                            selected = amountText == preset,
                            onClick = { amountText = preset },
                            label = { Text("$$preset") }
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Amount (USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Payment Method", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("MTN Mobile Money", "Google Pay", "Debit Card", "Airtel Money").forEach { m ->
                        FilterChip(
                            selected = selectedMethod == m,
                            onClick = { selectedMethod = m },
                            label = { Text(m) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.accentBlue.copy(alpha = 0.22f),
                                selectedLabelColor = colors.accentBlue
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            val parsedCents = ((amountText.toDoubleOrNull() ?: 0.0) * 100).toLong()
            Button(
                onClick = { onConfirm(parsedCents, selectedMethod) },
                enabled = parsedCents >= 100L,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentEmerald,
                    contentColor = colors.primaryButtonContent
                )
            ) {
                Text("Add Funds", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textSecondary) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletWithdrawDialog(
    maxCents: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit
) {
    val colors = PChatTheme.colors
    var amountText by remember { mutableStateOf("10") }
    var dest by remember { mutableStateOf("Mobile Money") }
    val parsedCents = ((amountText.toDoubleOrNull() ?: 0.0) * 100).toLong()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Withdraw (Cash Out)", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Available: ${formatUsdCents(maxCents)}", color = colors.accentBlue)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Amount (USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Mobile Money", "Linked Bank Account", "Debit Card").forEach { d ->
                        FilterChip(selected = dest == d, onClick = { dest = d }, label = { Text(d) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(parsedCents, dest) },
                enabled = parsedCents in 100L..maxCents,
                colors = ButtonDefaults.buttonColors(containerColor = HotPink, contentColor = colors.onGradientText)
            ) {
                Text("Withdraw", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textSecondary) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletExchangeCoinsDialog(
    maxCents: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val colors = PChatTheme.colors
    var selectedDollars by remember { mutableStateOf("5") }
    val cents = ((selectedDollars.toDoubleOrNull() ?: 0.0) * 100).toLong()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CurrencyExchange, contentDescription = null, tint = NeonPurple)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Exchange (Money ⇄ Coins)", color = colors.textPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Rate: $1.00 = 100 P-Coins • Available: ${formatUsdCents(maxCents)}",
                    color = colors.textSecondary
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("1", "5", "10", "25").forEach { p ->
                        FilterChip(selected = selectedDollars == p, onClick = { selectedDollars = p }, label = { Text("$$p") })
                    }
                }
                OutlinedTextField(
                    value = selectedDollars,
                    onValueChange = { selectedDollars = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("USD to Exchange") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (cents >= 100L) {
                    Text("You receive: $cents P-Coins", color = WalletGold, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(cents) },
                enabled = cents in 100L..maxCents,
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = colors.onGradientText)
            ) {
                Text("Exchange Now", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textSecondary) }
        }
    )
}

@Composable
fun WalletGetCoinsDialog(
    currentCoins: Long,
    maxCents: Long,
    onClaimTask: (Long, String) -> Unit,
    onOpenExchange: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = PChatTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = WalletGold)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Get P-CHAT Coins", color = colors.textPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Current Balance: $currentCoins P-Coins (≈ ${currentCoins / 10} Gifts & Rewards)",
                    color = WalletGold,
                    fontWeight = FontWeight.SemiBold
                )
                CoinTaskRow(
                    title = "Daily Wallet Check-In",
                    subtitle = "Instant daily activity bonus",
                    rewardCoins = 50L,
                    onClaim = {
                        onClaimTask(50L, "Daily P-CHAT Coins Check-In")
                        onDismiss()
                    }
                )
                CoinTaskRow(
                    title = "Global Creator Reward",
                    subtitle = "Support P-CHAT Moments & Friends",
                    rewardCoins = 100L,
                    onClaim = {
                        onClaimTask(100L, "Creator & Community Coin Gift")
                        onDismiss()
                    }
                )
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenExchange()
                    },
                    enabled = maxCents >= 100L,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (maxCents >= 100L) "Exchange USD Wallet → P-Coins" else "Add USD funds to exchange P-Coins",
                        color = colors.accentBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = colors.textPrimary)
            }
        }
    )
}

@Composable
private fun CoinTaskRow(
    title: String,
    subtitle: String,
    rewardCoins: Long,
    onClaim: () -> Unit
) {
    val colors = PChatTheme.colors
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, WalletGold.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClaim)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = WalletGold, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            Text("+$rewardCoins", color = WalletGold, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun WalletInfoModal(
    title: String,
    body: String,
    badgeText: String,
    fxRates: FxRatesSnapshot? = null,
    onActionClick: (() -> Unit)? = null,
    actionLabel: String? = null,
    onDismiss: () -> Unit
) {
    val colors = PChatTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = LiveEmerald)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = LiveEmerald.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.border(1.dp, LiveEmerald.copy(alpha = 0.5f), RoundedCornerShape(50))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LiveEmerald, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(badgeText, style = MaterialTheme.typography.labelSmall, color = LiveEmerald, fontWeight = FontWeight.Bold)
                    }
                }
                Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                if (fxRates != null) {
                    Text(
                        text = "FX Feed: ${fxRates.sourceLabel} • ${CurrencyConversionEngine.SUPPORTED_COUNTRIES.size} Countries • Updated ${fxRates.updatedAtLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accentBlue
                    )
                }
            }
        },
        confirmButton = {
            if (onActionClick != null && actionLabel != null) {
                Button(
                    onClick = {
                        onDismiss()
                        onActionClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = colors.onGradientText)
                ) {
                    Text(actionLabel)
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentBlue, contentColor = colors.primaryButtonContent)
                ) {
                    Text("Got It")
                }
            }
        },
        dismissButton = {
            if (onActionClick != null && actionLabel != null) {
                TextButton(onClick = onDismiss) { Text("Close", color = colors.textSecondary) }
            }
        }
    )
}
