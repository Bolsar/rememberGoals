package com.bolsar.remembergoals.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Side gutter of the screen and the cards. */
val Gutter = 16.dp

// Every slot components read is set, so no baseline purple leaks in. No dynamic color.
// Status mapping (read these, not the names): secondary(+Container) = ACHIEVED green, error(+Container) = FAILED red,
// tertiary(+Container) = overdue orange, primaryContainer/onPrimaryContainer = draft surface/strong (chunk 9),
// primary = accent blue, surface = card, surfaceVariant = archive grey, onSurfaceVariant = muted text.
private val LightScheme = lightColorScheme(
    primary = Accent, onPrimary = Color.White, primaryContainer = Draft, onPrimaryContainer = DraftStrong, inversePrimary = AccentDark,
    secondary = AchievedStrong, onSecondary = Color.White, secondaryContainer = Achieved, onSecondaryContainer = Ink,
    tertiary = OverdueStrong, onTertiary = Color.White, tertiaryContainer = Overdue, onTertiaryContainer = Ink,
    error = FailedStrong, onError = Color.White, errorContainer = Failed, onErrorContainer = Ink,
    background = Background, onBackground = Ink, surface = Card, onSurface = Ink,
    surfaceVariant = Archive, onSurfaceVariant = Muted, surfaceTint = Color.Transparent,
    inverseSurface = Ink, inverseOnSurface = Card, outline = Muted.copy(alpha = 0.5f), outlineVariant = Muted.copy(alpha = 0.2f),
    surfaceBright = Card, surfaceDim = Background, surfaceContainerLowest = Card, surfaceContainerLow = Card,
    surfaceContainer = Card, surfaceContainerHigh = Card, surfaceContainerHighest = Archive,
)

private val DarkScheme = darkColorScheme(
    primary = AccentDark, onPrimary = Color.White, primaryContainer = DraftDark, onPrimaryContainer = DraftStrongDark, inversePrimary = Accent,
    secondary = AchievedStrongDark, onSecondary = BackgroundDark, secondaryContainer = AchievedDark, onSecondaryContainer = InkDark,
    tertiary = OverdueStrongDark, onTertiary = BackgroundDark, tertiaryContainer = OverdueDark, onTertiaryContainer = InkDark,
    error = FailedStrongDark, onError = BackgroundDark, errorContainer = FailedDark, onErrorContainer = InkDark,
    background = BackgroundDark, onBackground = InkDark, surface = CardDark, onSurface = InkDark,
    surfaceVariant = ArchiveDark, onSurfaceVariant = MutedDark, surfaceTint = Color.Transparent,
    inverseSurface = InkDark, inverseOnSurface = CardDark, outline = MutedDark.copy(alpha = 0.5f), outlineVariant = MutedDark.copy(alpha = 0.2f),
    surfaceBright = CardDark, surfaceDim = BackgroundDark, surfaceContainerLowest = CardDark, surfaceContainerLow = CardDark,
    surfaceContainer = CardDark, surfaceContainerHigh = CardDark, surfaceContainerHighest = ArchiveDark,
)

@Composable
fun RememberGoalsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content,
    )
}
