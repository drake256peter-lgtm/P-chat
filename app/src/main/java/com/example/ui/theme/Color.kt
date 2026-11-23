package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Core P-CHAT Dark Navy & Black Surfaces (Black Theme)
val VoidBlack = Color(0xFF040711)
val DeepNavy = Color(0xFF080E1E)
val NavySurface = Color(0xFF0E162C)
val NavyCard = Color(0xFF141F3D)
val NavyElevated = Color(0xFF1C2B52)
val NavyBorder = Color(0xFF27396B)

// Core P-CHAT Clean Daylight Surfaces (White Theme)
val PearlWhite = Color(0xFFF5F7FD)
val PureWhite = Color(0xFFFFFFFF)
val IceSurface = Color(0xFFECF1FB)
val SoftLilacTint = Color(0xFFE3EAF9)
val LightBorder = Color(0xFFCED8EF)

// Neon Brand Accents
val ElectricBlue = Color(0xFF00D4FF)
val ElectricBlueDark = Color(0xFF007FC4)
val CyberBlue = Color(0xFF2979FF)
val NeonPurple = Color(0xFF9D4EDD)
val VioletGlow = Color(0xFF7B2CBF)
val HotPink = Color(0xFFFF2E93)
val MagentaPulse = Color(0xFFFF4D6D)
val LiveEmerald = Color(0xFF00F5A0)
val LiveEmeraldDark = Color(0xFF009B65)
val AmberSpark = Color(0xFFFFB703)

// Dark Theme Text & Semantic Colors
val TextPrimary = Color(0xFFF4F7FF)
val TextSecondary = Color(0xFFA7B4D6)
val TextMuted = Color(0xFF6B7AA6)

// White Theme Text & Semantic Colors
val MidnightInk = Color(0xFF091124)
val SlateInk = Color(0xFF475575)
val MutedSlate = Color(0xFF7986A3)

val ErrorCrimson = Color(0xFFFF4D6D)

val PChatBrandGradient = Brush.linearGradient(
    colors = listOf(ElectricBlue, NeonPurple, HotPink)
)

val PChatBluePurpleGradient = Brush.linearGradient(
    colors = listOf(ElectricBlue, CyberBlue, NeonPurple)
)

val PChatFollowGradient = Brush.horizontalGradient(
    colors = listOf(ElectricBlue, CyberBlue, NeonPurple, HotPink)
)

val PChatSurfaceGradient = Brush.verticalGradient(
    colors = listOf(DeepNavy, VoidBlack)
)
