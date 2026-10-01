package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ElectricEmerald,
    onPrimary = MidnightNavy,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = Color(0xFF9FFFE0),
    secondary = BkashPink,
    onSecondary = Color.White,
    secondaryContainer = BkashContainerDark,
    onSecondaryContainer = BkashPinkSoft,
    tertiary = NagadOrange,
    onTertiary = MidnightNavy,
    tertiaryContainer = NagadContainerDark,
    onTertiaryContainer = NagadOrangeSoft,
    background = MidnightNavy,
    onBackground = Color(0xFFEBF1FF),
    surface = DeepSlateNavy,
    onSurface = Color(0xFFEBF1FF),
    surfaceVariant = CardDarkNavy,
    onSurfaceVariant = Color(0xFFB4C5E4),
    error = DangerCoral,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryEmeraldLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8FBE5),
    onPrimaryContainer = Color(0xFF003825),
    secondary = SecondaryBkashLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E5),
    onSecondaryContainer = Color(0xFF3E001B),
    tertiary = TertiaryNagadLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE2C0),
    onTertiaryContainer = Color(0xFF3B1E00),
    background = LightBackground,
    onBackground = MidnightNavy,
    surface = LightSurface,
    onSurface = MidnightNavy,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF3E4E6C),
    error = Color(0xFFD32F2F),
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
