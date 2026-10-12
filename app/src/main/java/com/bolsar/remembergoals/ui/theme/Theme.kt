package com.bolsar.remembergoals.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Notebook left margin for titles and ledger rows. */
val NotebookMargin = 32.dp

// Every slot is set so no baseline-purple leaks into M3 components. No dynamic color.
// Mapping: primary = ink, secondary = olive (ACHIEVED), tertiary/error = vermilion (FAILED, FAB).
private fun paperScheme(paper: Color, ink: Color, olive: Color): ColorScheme {
    // ponytail: lightColorScheme for both modes is fine; every slot components use is overridden.
    return lightColorScheme(
        primary = ink, onPrimary = paper, primaryContainer = ink, onPrimaryContainer = paper,
        inversePrimary = paper,
        secondary = olive, onSecondary = paper, secondaryContainer = olive, onSecondaryContainer = paper,
        tertiary = Vermilion, onTertiary = Paper, tertiaryContainer = Vermilion, onTertiaryContainer = Paper,
        background = paper, onBackground = ink, surface = paper, onSurface = ink,
        surfaceVariant = paper, onSurfaceVariant = ink.copy(alpha = 0.6f), surfaceTint = Color.Transparent,
        inverseSurface = ink, inverseOnSurface = paper,
        error = Vermilion, onError = Paper, errorContainer = Vermilion, onErrorContainer = Paper,
        outline = ink.copy(alpha = 0.35f), outlineVariant = ink.copy(alpha = 0.12f), scrim = Color.Black,
        surfaceBright = paper, surfaceDim = paper,
        surfaceContainerLowest = paper, surfaceContainerLow = paper, surfaceContainer = paper,
        surfaceContainerHigh = paper, surfaceContainerHighest = paper,
    )
}

private val LightScheme = paperScheme(Paper, Ink, Olive)
private val DarkScheme = paperScheme(PaperDark, InkDark, OliveDark)

@Composable
fun RememberGoalsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content,
    )
}
