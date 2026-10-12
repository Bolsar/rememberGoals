package com.bolsar.remembergoals.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bolsar.remembergoals.R

/** Serif for screen title, section heads and goal titles. Single weight; italic for dreams. */
val Serif = FontFamily(
    Font(R.font.instrument_serif),
    Font(R.font.instrument_serif_italic, style = FontStyle.Italic),
)

/** Mono for dates, deadlines, countdowns. Variable font, regular weight. */
val Mono = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal)) // variation axis follows weight by default

private val base = Typography()

// display/headline/title = serif; body/label = platform sans (default). Use Mono explicitly for dates.
val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Serif),
    displayMedium = base.displayMedium.copy(fontFamily = Serif),
    displaySmall = base.displaySmall.copy(fontFamily = Serif),
    headlineLarge = base.headlineLarge.copy(fontFamily = Serif),
    headlineMedium = base.headlineMedium.copy(fontFamily = Serif),
    headlineSmall = base.headlineSmall.copy(fontFamily = Serif),
    titleLarge = base.titleLarge.copy(fontFamily = Serif, fontSize = 24.sp),
    titleMedium = base.titleMedium.copy(fontFamily = Serif, fontSize = 20.sp),
    titleSmall = base.titleSmall.copy(fontFamily = Serif),
)

/** Dates / deadlines / countdowns. */
val DateStyle = TextStyle(fontFamily = Mono, fontSize = 12.sp, letterSpacing = 0.sp)
