package com.example.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberSpark
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.HotPink
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatBrandGradient
import com.example.ui.theme.PChatTheme
import com.example.ui.theme.VoidBlack

val WalletGold = Color(0xFFFFD166)
val WalletGoldDeep = Color(0xFFE08E00)
val WalletLavender = Color(0xFFE0AAFF)

@Composable
fun WalletCosmicBackdrop(modifier: Modifier = Modifier) {
    val colors = PChatTheme.colors
    if (!colors.isDark) {
        Box(modifier = modifier.background(colors.background))
        return
    }
    Canvas(modifier = modifier.background(VoidBlack)) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonPurple.copy(alpha = 0.28f),
                    CyberBlue.copy(alpha = 0.14f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.55f, size.height * 0.08f),
                radius = size.width * 0.9f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    ElectricBlue.copy(alpha = 0.14f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.15f, size.height * 0.32f),
                radius = size.width * 0.65f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    HotPink.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.85f, size.height * 0.62f),
                radius = size.width * 0.7f
            )
        )
    }
}

@Composable
fun WalletPurpleIconArt(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Soft purple glow behind wallet
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(NeonPurple.copy(alpha = 0.48f), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.52f),
                radius = w * 0.65f
            )
        )
        // Back flap
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFF6A1B9A), NeonPurple)),
            topLeft = Offset(w * 0.12f, h * 0.14f),
            size = Size(w * 0.72f, h * 0.45f),
            cornerRadius = CornerRadius(w * 0.14f, w * 0.14f)
        )
        // Front wallet body
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFF9D4EDD), Color(0xFF7B2CBF), Color(0xFF4A148C))
            ),
            topLeft = Offset(w * 0.08f, h * 0.24f),
            size = Size(w * 0.80f, h * 0.64f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f)
        )
        // Gloss highlight
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)),
            topLeft = Offset(w * 0.11f, h * 0.27f),
            size = Size(w * 0.74f, h * 0.22f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
        )
        // Strap clasp
        drawRoundRect(
            color = Color(0xFF3C096C),
            topLeft = Offset(w * 0.60f, h * 0.45f),
            size = Size(w * 0.32f, h * 0.24f),
            cornerRadius = CornerRadius(w * 0.10f, w * 0.10f)
        )
        // Gold clasp stud
        drawCircle(
            brush = Brush.radialGradient(listOf(WalletGold, WalletGoldDeep)),
            radius = w * 0.068f,
            center = Offset(w * 0.73f, h * 0.57f)
        )
    }
}

@Composable
fun WalletCyberGlobeArt(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val radius = (minOf(w, h) * 0.44f)
        val center = Offset(w * 0.5f, h * 0.5f)

        // Outer neon halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ElectricBlue.copy(alpha = 0.45f), NeonPurple.copy(alpha = 0.22f), Color.Transparent),
                center = center,
                radius = radius * 1.45f
            ),
            radius = radius * 1.45f,
            center = center
        )

        // Globe sphere
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ElectricBlue, CyberBlue, Color(0xFF1A0B4B), VoidBlack),
                center = Offset(center.x - radius * 0.28f, center.y - radius * 0.25f),
                radius = radius * 1.35f
            ),
            radius = radius,
            center = center
        )

        // Latitude / longitude cyber wireframe arcs
        val strokeColor = Color(0xFF9DF0FF).copy(alpha = 0.55f)
        drawCircle(
            color = ElectricBlue.copy(alpha = 0.85f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.2f)
        )
        drawOval(
            color = strokeColor,
            topLeft = Offset(center.x - radius * 0.48f, center.y - radius),
            size = Size(radius * 0.96f, radius * 2f),
            style = Stroke(width = 1.6f)
        )
        drawOval(
            color = NeonPurple.copy(alpha = 0.65f),
            topLeft = Offset(center.x - radius, center.y - radius * 0.34f),
            size = Size(radius * 2f, radius * 0.68f),
            style = Stroke(width = 1.6f)
        )
        drawLine(
            color = strokeColor,
            start = Offset(center.x - radius, center.y),
            end = Offset(center.x + radius, center.y),
            strokeWidth = 1.4f
        )
        drawLine(
            color = strokeColor,
            start = Offset(center.x, center.y - radius),
            end = Offset(center.x, center.y + radius),
            strokeWidth = 1.4f
        )

        // Glowing network nodes
        listOf(
            Offset(center.x - radius * 0.35f, center.y - radius * 0.28f),
            Offset(center.x + radius * 0.28f, center.y - radius * 0.14f),
            Offset(center.x + radius * 0.12f, center.y + radius * 0.36f),
            Offset(center.x - radius * 0.18f, center.y + radius * 0.24f)
        ).forEach { pt ->
            drawCircle(color = Color.White, radius = 2.6f, center = pt)
        }
    }
}

@Composable
fun WalletGoldCoinsStackArt(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Ambient golden glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AmberSpark.copy(alpha = 0.42f), NeonPurple.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(w * 0.52f, h * 0.52f),
                radius = w * 0.65f
            )
        )

        // Stacked coins on left
        val stackX = w * 0.08f
        val stackW = w * 0.48f
        val coinH = h * 0.13f
        listOf(0.76f, 0.62f, 0.48f, 0.34f).forEachIndexed { idx, yFrac ->
            val topY = h * yFrac
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(WalletGoldDeep, WalletGold, WalletGoldDeep)),
                topLeft = Offset(stackX, topY),
                size = Size(stackW, coinH),
                cornerRadius = CornerRadius(stackW * 0.5f, coinH * 0.5f)
            )
            if (idx == 3) {
                drawOval(
                    brush = Brush.linearGradient(listOf(Color(0xFFFFF3B0), AmberSpark)),
                    topLeft = Offset(stackX, topY - coinH * 0.35f),
                    size = Size(stackW, coinH * 0.9f)
                )
            }
        }

        // Front Hero Gold Medallion with Purple P-CHAT Emblem
        val coinCenter = Offset(w * 0.62f, h * 0.58f)
        val coinRadius = minOf(w, h) * 0.34f
        drawCircle(
            brush = Brush.linearGradient(listOf(Color(0xFFFFF3B0), WalletGold, WalletGoldDeep)),
            radius = coinRadius,
            center = coinCenter
        )
        drawCircle(
            color = Color(0xFFFFF8D6),
            radius = coinRadius * 0.84f,
            center = coinCenter,
            style = Stroke(width = 2.2f)
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFF4A148C), VoidBlack)),
            radius = coinRadius * 0.64f,
            center = coinCenter
        )
        // Stylized Neon "P" inside medallion
        val pPath = Path().apply {
            val px = coinCenter.x - coinRadius * 0.20f
            val py = coinCenter.y - coinRadius * 0.30f
            val pw = coinRadius * 0.44f
            val ph = coinRadius * 0.62f
            moveTo(px, py + ph)
            lineTo(px, py)
            lineTo(px + pw * 0.68f, py)
            cubicTo(
                px + pw * 1.15f, py,
                px + pw * 1.15f, py + ph * 0.54f,
                px + pw * 0.62f, py + ph * 0.54f
            )
            lineTo(px + pw * 0.14f, py + ph * 0.54f)
        }
        drawPath(
            path = pPath,
            brush = Brush.linearGradient(listOf(ElectricBlue, NeonPurple, HotPink)),
            style = Stroke(width = 4.2f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun WalletVipCrownButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .testTag("wallet_vip_button")
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Canvas(modifier = Modifier.size(width = 28.dp, height = 20.dp)) {
            val w = size.width
            val h = size.height
            val crownPath = Path().apply {
                moveTo(w * 0.08f, h * 0.86f)
                lineTo(w * 0.04f, h * 0.24f)
                lineTo(w * 0.30f, h * 0.54f)
                lineTo(w * 0.50f, h * 0.08f)
                lineTo(w * 0.70f, h * 0.54f)
                lineTo(w * 0.96f, h * 0.24f)
                lineTo(w * 0.92f, h * 0.86f)
                close()
            }
            drawPath(
                path = crownPath,
                brush = Brush.linearGradient(listOf(WalletGold, AmberSpark, WalletGoldDeep))
            )
            drawCircle(color = WalletGold, radius = 2.4f, center = Offset(w * 0.50f, h * 0.06f))
            drawCircle(color = WalletGold, radius = 2.0f, center = Offset(w * 0.04f, h * 0.22f))
            drawCircle(color = WalletGold, radius = 2.0f, center = Offset(w * 0.96f, h * 0.22f))
        }
        Text(
            text = "VIP",
            color = WalletGold,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp
        )
    }
}

@Composable
fun WalletTrustFooterStrip(
    onMultiCurrencyClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onEmblemClick: () -> Unit,
    onGlobalPaymentsClick: () -> Unit,
    onSupportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PChatTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WalletTrustTile(
            icon = Icons.Filled.Public,
            title = "Multi-Currency",
            subtitle = "(USD, EUR, GBP...)",
            accent = ElectricBlue,
            onClick = onMultiCurrencyClick,
            modifier = Modifier.weight(1f)
        )
        WalletTrustTile(
            icon = Icons.Filled.VerifiedUser,
            title = "Fast & Secure",
            subtitle = "Transactions",
            accent = LiveEmerald,
            onClick = onSecurityClick,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(50.dp)
                .shadow(14.dp, CircleShape, ambientColor = NeonPurple, spotColor = ElectricBlue)
                .clip(CircleShape)
                .background(if (colors.isDark) DeepNavy else colors.card)
                .border(2.dp, PChatBrandGradient, CircleShape)
                .clickable(onClick = onEmblemClick),
            contentAlignment = Alignment.Center
        ) {
            PChatBrandEmblem(size = 42.dp)
        }
        WalletTrustTile(
            icon = Icons.Filled.CreditCard,
            title = "Global Payments",
            subtitle = "(All Countries)",
            accent = WalletLavender,
            onClick = onGlobalPaymentsClick,
            modifier = Modifier.weight(1f)
        )
        WalletTrustTile(
            icon = Icons.Filled.HeadsetMic,
            title = "24/7 Support",
            subtitle = "Live Help",
            accent = ElectricBlue,
            onClick = onSupportClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun WalletTrustTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PChatTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (colors.isDark) {
                    Brush.verticalGradient(listOf(Color(0xFF101936), Color(0xFF070B1A)))
                } else {
                    Brush.verticalGradient(listOf(colors.card, colors.elevated))
                }
            )
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = title,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            fontSize = 7.5.sp,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
