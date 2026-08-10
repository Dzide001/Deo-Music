// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.player

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Conditioning for a colour pulled out of album artwork.
 *
 * A raw swatch is not usable as an accent. Cover art is routinely near-black, near-
 * white, or unsaturated, and any of those produce a "colour" that is invisible against
 * the surface it is drawn on or indistinguishable from plain text. The rules here are
 * deliberately blunt: force enough saturation that the accent reads as a colour, and
 * push lightness into a band that stays legible on the theme it will sit on.
 *
 * Pure and free of android.graphics on purpose — this is the part with judgement in
 * it, so it is the part worth testing.
 */
internal object ArtworkAccent {

    /** Below this a swatch is grey in all but name, and tinting with it looks broken. */
    private const val MIN_SATURATION = 0.35f

    /** Legible lightness bands, measured against the dark and light surfaces. */
    private const val DARK_MIN_LIGHTNESS = 0.55f
    private const val DARK_MAX_LIGHTNESS = 0.80f
    private const val LIGHT_MIN_LIGHTNESS = 0.28f
    private const val LIGHT_MAX_LIGHTNESS = 0.50f

    fun condition(seed: Color, dark: Boolean): Color {
        val (hue, saturation, lightness) = toHsl(seed)
        val boosted = max(saturation, MIN_SATURATION)
        val bounded = if (dark) {
            lightness.coerceIn(DARK_MIN_LIGHTNESS, DARK_MAX_LIGHTNESS)
        } else {
            lightness.coerceIn(LIGHT_MIN_LIGHTNESS, LIGHT_MAX_LIGHTNESS)
        }
        return fromHsl(hue, boosted, bounded)
    }

    fun toHsl(color: Color): Triple<Float, Float, Float> {
        val r = color.red
        val g = color.green
        val b = color.blue
        val maxComponent = max(r, max(g, b))
        val minComponent = min(r, min(g, b))
        val delta = maxComponent - minComponent
        val lightness = (maxComponent + minComponent) / 2f

        if (delta == 0f) return Triple(0f, 0f, lightness)

        val saturation = delta / (1f - abs(2f * lightness - 1f))
        val hue = when (maxComponent) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        return Triple(if (hue < 0f) hue + 360f else hue, saturation.coerceIn(0f, 1f), lightness)
    }

    fun fromHsl(hue: Float, saturation: Float, lightness: Float): Color {
        val c = (1f - abs(2f * lightness - 1f)) * saturation
        val x = c * (1f - abs(((hue / 60f) % 2f) - 1f))
        val m = lightness - c / 2f
        val (r, g, b) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(
            red = (r + m).coerceIn(0f, 1f),
            green = (g + m).coerceIn(0f, 1f),
            blue = (b + m).coerceIn(0f, 1f),
        )
    }
}
