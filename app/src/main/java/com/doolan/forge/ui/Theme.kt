package com.doolan.forge.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0B0B0E)
val SurfaceDark = Color(0xFF15151A)
val CardDark = Color(0xFF1C1C23)
val CardHigh = Color(0xFF26262F)
val Accent = Color(0xFF4DD0A8)
val AccentSoft = Color(0xFF1B3A32)
val Muted = Color(0xFFA0A0AC)
val Warn = Color(0xFFFFB454)
val Danger = Color(0xFFFF6B6B)
val Blue = Color(0xFF7C8CFF)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00201A),
    primaryContainer = AccentSoft,
    onPrimaryContainer = Accent,
    secondary = Blue,
    background = Bg,
    onBackground = Color.White,
    surface = SurfaceDark,
    onSurface = Color.White,
    surfaceVariant = CardDark,
    onSurfaceVariant = Muted,
    surfaceContainer = CardDark,
    surfaceContainerHigh = CardHigh,
    outline = Color(0xFF3A3A46),
    outlineVariant = Color(0xFF2C2C36),
    error = Danger
)

@Composable
fun ForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
