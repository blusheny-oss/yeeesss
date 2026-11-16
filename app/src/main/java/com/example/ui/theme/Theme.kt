package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = NavyPrimaryContainer,
    onPrimaryContainer = OnNavyPrimaryContainer,
    secondary = EmeraldSecondary,
    onSecondary = Color.White,
    secondaryContainer = EmeraldSecondaryContainer,
    onSecondaryContainer = OnEmeraldSecondaryContainer,
    tertiary = AmberTertiary,
    onTertiary = Color.White,
    tertiaryContainer = AmberTertiaryContainer,
    onTertiaryContainer = OnAmberTertiaryContainer,
    background = AlabasterBackground,
    onBackground = InkOnSurface,
    surface = PureWhiteSurface,
    onSurface = InkOnSurface,
    surfaceVariant = SoftSlateSurfaceVariant,
    onSurfaceVariant = InkMuted,
    outline = SlateBorder,
    error = DefectRed,
    errorContainer = DefectRedContainer,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkNavyPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = NavyPrimary,
    onPrimaryContainer = DarkInk,
    secondary = DarkEmeraldSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = OnEmeraldSecondaryContainer,
    onSecondaryContainer = EmeraldSecondaryContainer,
    tertiary = DarkAmberTertiary,
    onTertiary = OnAmberTertiaryContainer,
    tertiaryContainer = AmberTertiary,
    onTertiaryContainer = AmberTertiaryContainer,
    background = DarkSlateBackground,
    onBackground = DarkInk,
    surface = DarkSlateSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSlateSurfaceVariant,
    onSurfaceVariant = DarkInkMuted,
    outline = DarkSlateBorder,
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF7F1D1D),
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AktZhilyaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
