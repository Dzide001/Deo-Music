// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The type scale.
 *
 * Deliberately built on the platform font rather than a bundled one: it keeps the APK
 * small, it honours the user's font choice on OEM skins, and a music library is
 * mostly other people's text — track titles in scripts we cannot predict — where the
 * system font has far better coverage than anything shipped.
 *
 * Expressive character comes from weight and tracking instead. Titles are heavier and
 * tighter than the Material default so a list of tracks has a clear first read.
 */
private val Default = FontFamily.Default

/** Trims the extra leading Compose adds, so dense list rows stay on rhythm. */
private val Trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val DeoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Bold,
        fontSize = 52.sp, lineHeight = 58.sp, letterSpacing = (-0.025).em,
        lineHeightStyle = Trim,
    ),
    displayMedium = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Bold,
        fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.022).em,
        lineHeightStyle = Trim,
    ),
    headlineLarge = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.02).em,
        lineHeightStyle = Trim,
    ),
    headlineMedium = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Bold,
        fontSize = 25.sp, lineHeight = 31.sp, letterSpacing = (-0.018).em,
        lineHeightStyle = Trim,
    ),
    // The screen title — "Library", "Now Playing".
    titleLarge = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.ExtraBold,
        fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.015).em,
        lineHeightStyle = Trim,
    ),
    titleMedium = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.006).em,
        lineHeightStyle = Trim,
    ),
    // Track titles in a list. Heavier than Material's default so the title wins
    // against the artist line beneath it.
    bodyLarge = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.em,
        lineHeightStyle = Trim,
    ),
    bodyMedium = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.005.em,
        lineHeightStyle = Trim,
    ),
    bodySmall = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Normal,
        fontSize = 12.5f.sp, lineHeight = 17.sp, letterSpacing = 0.01.em,
        lineHeightStyle = Trim,
    ),
    labelLarge = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.01.em,
        lineHeightStyle = Trim,
    ),
    // Artist/album lines, tab labels.
    labelMedium = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.02.em,
        lineHeightStyle = Trim,
    ),
    // Section eyebrows. Uppercase is applied at the call site, not baked in, so the
    // style stays usable for text that must not be transformed.
    labelSmall = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.08.em,
        lineHeightStyle = Trim,
    ),
)
