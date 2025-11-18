package com.qualiorstudio.aiadventultimate.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ColorPalette.DarkPrimary,
    onPrimary = ColorPalette.DarkOnPrimary,
    primaryContainer = ColorPalette.DarkPrimaryContainer,
    onPrimaryContainer = ColorPalette.DarkOnPrimaryContainer,
    secondary = ColorPalette.DarkSecondary,
    onSecondary = ColorPalette.DarkOnSecondary,
    secondaryContainer = ColorPalette.DarkSecondaryContainer,
    onSecondaryContainer = ColorPalette.DarkOnSecondaryContainer,
    background = ColorPalette.DarkBackground,
    onBackground = ColorPalette.DarkOnBackground,
    surface = ColorPalette.DarkSurface,
    onSurface = ColorPalette.DarkOnSurface,
    surfaceVariant = ColorPalette.DarkSurfaceVariant,
    onSurfaceVariant = ColorPalette.DarkOnSurfaceVariant,
    outline = ColorPalette.DarkOutline,
    outlineVariant = ColorPalette.DarkOutlineVariant,
    inverseOnSurface = ColorPalette.DarkInverseOnSurface,
    inverseSurface = ColorPalette.DarkInverseSurface,
    surfaceTint = ColorPalette.DarkPrimary,
    tertiary = ColorPalette.DarkTertiary,
    onTertiary = ColorPalette.DarkOnTertiary
)

private val LightColorScheme = lightColorScheme(
    primary = ColorPalette.LightPrimary,
    onPrimary = ColorPalette.LightOnPrimary,
    primaryContainer = ColorPalette.LightPrimaryContainer,
    onPrimaryContainer = ColorPalette.LightOnPrimaryContainer,
    secondary = ColorPalette.LightSecondary,
    onSecondary = ColorPalette.LightOnSecondary,
    secondaryContainer = ColorPalette.LightSecondaryContainer,
    onSecondaryContainer = ColorPalette.LightOnSecondaryContainer,
    background = ColorPalette.LightBackground,
    onBackground = ColorPalette.LightOnBackground,
    surface = ColorPalette.LightSurface,
    onSurface = ColorPalette.LightOnSurface,
    surfaceVariant = ColorPalette.LightSurfaceVariant,
    onSurfaceVariant = ColorPalette.LightOnSurfaceVariant,
    outline = ColorPalette.LightOutline,
    outlineVariant = ColorPalette.LightOutlineVariant,
    inverseOnSurface = ColorPalette.LightInverseOnSurface,
    inverseSurface = ColorPalette.LightInverseSurface,
    surfaceTint = ColorPalette.LightPrimary,
    tertiary = ColorPalette.LightTertiary,
    onTertiary = ColorPalette.LightOnTertiary
)

@Composable
fun AiAdventUltimateTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

private object ColorPalette {
    val DarkPrimary = Color(0xFF4D8DFF)
    val DarkOnPrimary = Color(0xFF0E1420)
    val DarkPrimaryContainer = Color(0xFF1A3B80)
    val DarkOnPrimaryContainer = Color(0xFFCBDCFF)
    val DarkSecondary = Color(0xFF58657A)
    val DarkOnSecondary = Color(0xFFE7EBF2)
    val DarkSecondaryContainer = Color(0xFF191D25)
    val DarkOnSecondaryContainer = Color(0xFFE5E7EB)
    val DarkTertiary = Color(0xFF3AA0B8)
    val DarkOnTertiary = Color(0xFF002F38)
    val DarkBackground = Color(0xFF111216)
    val DarkOnBackground = Color(0xFFE1E5EB)
    val DarkSurface = Color(0xFF141820)
    val DarkOnSurface = Color(0xFFE1E5EB)
    val DarkSurfaceVariant = Color(0xFF14171C)
    val DarkOnSurfaceVariant = Color(0xFF8D95A6)
    val DarkOutline = Color(0xFF1E2026)
    val DarkOutlineVariant = Color(0xFF2C3038)
    val DarkInverseOnSurface = Color(0xFF0F141D)
    val DarkInverseSurface = Color(0xFFE1E6F0)

    val LightPrimary = Color(0xFF3C64F4)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFFDBE2FF)
    val LightOnPrimaryContainer = Color(0xFF0A1A5C)
    val LightSecondary = Color(0xFF5B6575)
    val LightOnSecondary = Color(0xFFFFFFFF)
    val LightSecondaryContainer = Color(0xFFE5E9F3)
    val LightOnSecondaryContainer = Color(0xFF212A3B)
    val LightTertiary = Color(0xFF2F8CA3)
    val LightOnTertiary = Color(0xFFFFFFFF)
    val LightBackground = Color(0xFFF3F4F8)
    val LightOnBackground = Color(0xFF111827)
    val LightSurface = Color(0xFFFFFFFF)
    val LightOnSurface = Color(0xFF111827)
    val LightSurfaceVariant = Color(0xFFE4E7EE)
    val LightOnSurfaceVariant = Color(0xFF4B5565)
    val LightOutline = Color(0xFFCBD5E1)
    val LightOutlineVariant = Color(0xFFD5D9E3)
    val LightInverseOnSurface = Color(0xFFE9EDF5)
    val LightInverseSurface = Color(0xFF1F2430)
}

