// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.designsystem

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

/**
 * Picks a seed colour from album artwork.
 *
 * Preference order is vibrant before muted, dark-vibrant before light: the seed only
 * anchors the scheme — MaterialKolor derives every tone from it — so a saturated
 * input produces a scheme with some life in it, while a muted one collapses toward
 * grey and makes dynamic colour look broken rather than subtle.
 *
 * Returns null when the artwork has no usable swatch at all (a solid black cover, for
 * instance), which the caller must treat as "fall back", not as a colour.
 */
object ArtworkSeed {

    /**
     * @param bitmap artwork to sample. Downscale before calling — Palette walks every
     *   pixel, and a full-size cover is wasted work on a value that gets quantised
     *   into a tonal palette anyway.
     */
    fun from(bitmap: Bitmap): Color? {
        val palette = runCatching { Palette.from(bitmap).clearFilters().generate() }.getOrNull()
            ?: return null

        val swatch = palette.vibrantSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.mutedSwatch
            ?: palette.darkMutedSwatch
            ?: palette.lightMutedSwatch
            ?: palette.dominantSwatch
            ?: return null

        return Color(swatch.rgb)
    }

    /** Sample size that keeps extraction cheap without changing the result. */
    const val SAMPLE_SIZE_PX = 128
}
