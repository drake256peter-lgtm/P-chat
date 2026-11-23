package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberSpark
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatBrandGradient
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import com.example.util.SnapFilter
import kotlin.math.roundToInt

/**
 * Pure optical color-tint wash rendered over live CameraX feed.
 * Zero cartoon shapes, stickers, ears, crowns, or simulated illustrations.
 */
@Composable
fun LiveCameraFilterOverlay(
    filter: SnapFilter,
    modifier: Modifier = Modifier
) {
    if (filter == SnapFilter.NONE) return

    Canvas(modifier = modifier) {
        when (filter) {
            SnapFilter.BEAUTY_GLOW -> {
                drawRect(color = Color(0x14FFE6F0))
            }

            SnapFilter.GOLDEN_HOUR -> {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0x28FFB347), Color(0x14FF7733)),
                        start = Offset(size.width, 0f),
                        end = Offset(0f, size.height)
                    )
                )
            }

            SnapFilter.NEON_NIGHT -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            NeonPurple.copy(alpha = 0.16f),
                            Color.Transparent,
                            Color(0x246A0DAD)
                        )
                    )
                )
            }

            SnapFilter.MONO_FILM -> {
                drawRect(color = Color(0xFF9EA6B8).copy(alpha = 0.72f), blendMode = BlendMode.Saturation)
            }

            SnapFilter.NONE -> Unit
        }
    }
}

/**
 * Circular optical color-grade selector in P-CHAT black & neon-purple styling.
 */
@Composable
fun FilterLensCarousel(
    selectedFilter: SnapFilter,
    onSelectFilter: (SnapFilter) -> Unit,
    testTagPrefix: String = "filter_lens",
    modifier: Modifier = Modifier
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items(SnapFilter.entries, key = { it.id }) { filter ->
            val selected = selectedFilter == filter
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.08f else 0.96f,
                animationSpec = spring(dampingRatio = 0.68f),
                label = "lens_scale"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .scale(scale)
                    .testTag("${testTagPrefix}_${filter.id}")
                    .clickable { onSelectFilter(filter) }
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) NeonPurple.copy(alpha = 0.34f)
                            else VoidBlack.copy(alpha = 0.72f)
                        )
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            brush = if (selected) PChatBrandGradient else Brush.linearGradient(
                                listOf(NeonPurple.copy(alpha = 0.35f), NeonPurple.copy(alpha = 0.35f))
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(filter.badge, fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = filter.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color(0xFFE0AAFF) else TextSecondary
                )
            }
        }
    }
}

/**
 * Subtle 3x3 rule-of-thirds composition grid for real camera framing.
 */
@Composable
fun RuleOfThirdsGridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val lineColor = TextPrimary.copy(alpha = 0.22f)
        val stroke = 1.5f
        val x1 = size.width / 3f
        val x2 = size.width * 2f / 3f
        val y1 = size.height / 3f
        val y2 = size.height * 2f / 3f

        drawLine(lineColor, Offset(x1, 0f), Offset(x1, size.height), strokeWidth = stroke)
        drawLine(lineColor, Offset(x2, 0f), Offset(x2, size.height), strokeWidth = stroke)
        drawLine(lineColor, Offset(0f, y1), Offset(size.width, y1), strokeWidth = stroke)
        drawLine(lineColor, Offset(0f, y2), Offset(size.width, y2), strokeWidth = stroke)
    }
}

/**
 * Animated tap-to-focus metering reticle shown at touch coordinates.
 */
@Composable
fun CameraFocusReticle(
    focusOffset: Offset?,
    modifier: Modifier = Modifier
) {
    if (focusOffset == null) return
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.55f),
        label = "focus_reticle"
    )
    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    x = (focusOffset.x - 36.dp.toPx()).roundToInt(),
                    y = (focusOffset.y - 36.dp.toPx()).roundToInt()
                )
            }
            .size(72.dp)
            .scale(scale)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val seg = w * 0.26f
            val color = Color(0xFFE0AAFF)
            val sw = 4f
            drawLine(color, Offset(0f, 0f), Offset(seg, 0f), strokeWidth = sw)
            drawLine(color, Offset(0f, 0f), Offset(0f, seg), strokeWidth = sw)

            drawLine(color, Offset(w, 0f), Offset(w - seg, 0f), strokeWidth = sw)
            drawLine(color, Offset(w, 0f), Offset(w, seg), strokeWidth = sw)

            drawLine(color, Offset(0f, h), Offset(seg, h), strokeWidth = sw)
            drawLine(color, Offset(0f, h), Offset(0f, h - seg), strokeWidth = sw)

            drawLine(color, Offset(w, h), Offset(w - seg, h), strokeWidth = sw)
            drawLine(color, Offset(w, h), Offset(w, h - seg), strokeWidth = sw)

            drawCircle(color = AmberSpark.copy(alpha = 0.9f), radius = 4f, center = Offset(w / 2f, h / 2f))
        }
        Text(
            text = "AF",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFE0AAFF),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 3.dp)
        )
    }
}
