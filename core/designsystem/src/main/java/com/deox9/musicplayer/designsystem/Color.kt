// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The app's own seed colour, used whenever dynamic colour is off or unavailable.
 *
 * A warm amber: the app is dark-first and listened to at night, and a warm accent on
 * a cold ground reads as analogue gear rather than as system chrome. Everything else
 * in the scheme is generated from this, so it is the only fixed colour in the app.
 */
val BrandSeed = Color(0xFFEFB01E)

/**
 * Flattens a dark scheme's backgrounds to true black for OLED panels.
 *
 * Only the ground surfaces go to black. Containers, outlines and the accent roles
 * keep their tonal values, because taking those to black too would erase the
 * elevation cues Material uses instead of shadows and leave the UI unreadable.
 *
 * Applying this to a light scheme would produce black-on-black, so callers must gate
 * it on the scheme actually being dark — [DeoTheme] does.
 */
fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF101010),
    surfaceContainerHigh = Color(0xFF161616),
    surfaceContainerHighest = Color(0xFF1C1C1C),
    surfaceDim = Color.Black,
)
