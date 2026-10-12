package com.bolsar.remembergoals.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()

// Platform sans only, with deliberate weights. Used slots:
// headlineLarge = screen title, headlineSmall = sheet title, titleMedium = card title, bodyMedium = body,
// labelLarge = meta (dates, countdowns, chips).
val AppTypography = Typography(
    headlineLarge = base.headlineLarge.copy(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
    labelLarge = base.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
)
