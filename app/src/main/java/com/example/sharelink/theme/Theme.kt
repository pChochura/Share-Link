package com.example.sharelink.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ShareLinkDarkScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = DeepNavy,
    primaryContainer = CyanDark,
    onPrimaryContainer = CyanLight,
    secondary = AmberAccent,
    onSecondary = DeepNavy,
    secondaryContainer = AmberAccent.copy(alpha = 0.2f),
    onSecondaryContainer = AmberLight,
    tertiary = PurpleAccent,
    onTertiary = DeepNavy,
    tertiaryContainer = PurpleAccent.copy(alpha = 0.2f),
    onTertiaryContainer = PurpleLight,
    error = ErrorRed,
    onError = DeepNavy,
    errorContainer = ErrorRed.copy(alpha = 0.2f),
    onErrorContainer = ErrorRed,
    background = DeepNavy,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderMuted,
    inverseSurface = TextPrimary,
    inverseOnSurface = DeepNavy,
    inversePrimary = CyanDark,
    surfaceContainerLowest = DeepNavy,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = CardSurface,
    surfaceContainerHigh = ElevatedSurface,
    surfaceContainerHighest = ElevatedSurface,
)

@Composable
fun ShareLinkTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ShareLinkDarkScheme,
        typography = Typography,
        content = content,
    )
}
