package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class PChatColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val card: Color,
    val elevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentBlue: Color,
    val accentEmerald: Color,
    val onGradientText: Color,
    val primaryButtonContent: Color,
    val bottomBarBg: Color,
    val surfaceGradient: Brush
)

val DarkPChatColors = PChatColors(
    isDark = true,
    background = VoidBlack,
    surface = DeepNavy,
    card = NavyCard,
    elevated = NavyElevated,
    border = NavyBorder,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textMuted = TextMuted,
    accentBlue = ElectricBlue,
    accentEmerald = LiveEmerald,
    onGradientText = TextPrimary,
    primaryButtonContent = VoidBlack,
    bottomBarBg = DeepNavy.copy(alpha = 0.98f),
    surfaceGradient = Brush.verticalGradient(listOf(DeepNavy, VoidBlack))
)

val LightPChatColors = PChatColors(
    isDark = false,
    background = PearlWhite,
    surface = PureWhite,
    card = PureWhite,
    elevated = IceSurface,
    border = LightBorder,
    textPrimary = MidnightInk,
    textSecondary = SlateInk,
    textMuted = MutedSlate,
    accentBlue = ElectricBlueDark,
    accentEmerald = LiveEmeraldDark,
    onGradientText = PureWhite,
    primaryButtonContent = PureWhite,
    bottomBarBg = PureWhite.copy(alpha = 0.98f),
    surfaceGradient = Brush.verticalGradient(listOf(PureWhite, PearlWhite))
)

val LocalPChatColors = staticCompositionLocalOf { DarkPChatColors }

private val PChatDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = VoidBlack,
    primaryContainer = NavyElevated,
    onPrimaryContainer = ElectricBlue,
    secondary = NeonPurple,
    onSecondary = TextPrimary,
    secondaryContainer = VioletGlow,
    onSecondaryContainer = TextPrimary,
    tertiary = HotPink,
    onTertiary = TextPrimary,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = DeepNavy,
    onSurface = TextPrimary,
    surfaceVariant = NavyCard,
    onSurfaceVariant = TextSecondary,
    outline = NavyBorder,
    error = ErrorCrimson,
    onError = TextPrimary
)

private val PChatLightColorScheme = lightColorScheme(
    primary = ElectricBlueDark,
    onPrimary = PureWhite,
    primaryContainer = SoftLilacTint,
    onPrimaryContainer = MidnightInk,
    secondary = NeonPurple,
    onSecondary = PureWhite,
    secondaryContainer = SoftLilacTint,
    onSecondaryContainer = MidnightInk,
    tertiary = HotPink,
    onTertiary = PureWhite,
    background = PearlWhite,
    onBackground = MidnightInk,
    surface = PureWhite,
    onSurface = MidnightInk,
    surfaceVariant = IceSurface,
    onSurfaceVariant = SlateInk,
    outline = LightBorder,
    error = ErrorCrimson,
    onError = PureWhite
)

val PChatShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

object PChatTheme {
    val colors: PChatColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPChatColors.current
}

@Composable
fun PChatTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPChatColors else LightPChatColors
    val colorScheme = if (darkTheme) PChatDarkColorScheme else PChatLightColorScheme
    CompositionLocalProvider(LocalPChatColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PChatTypography,
            shapes = PChatShapes,
            content = content
        )
    }
}
