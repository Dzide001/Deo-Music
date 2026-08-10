// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.player

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ArtworkAccentTest {

    private fun lightnessOf(color: Color) = ArtworkAccent.toHsl(color).third

    private fun saturationOf(color: Color) = ArtworkAccent.toHsl(color).second

    private fun hueOf(color: Color) = ArtworkAccent.toHsl(color).first

    @Test
    fun `hsl survives a round trip`() {
        listOf(
            Color(0xFFEFB01E),
            Color(0xFF1E88E5),
            Color(0xFF7C4DFF),
            Color(0xFF43A047),
        ).forEach { original ->
            val (h, s, l) = ArtworkAccent.toHsl(original)
            val restored = ArtworkAccent.fromHsl(h, s, l)

            assertTrue(
                "expected $original, got $restored",
                abs(restored.red - original.red) < TOLERANCE &&
                    abs(restored.green - original.green) < TOLERANCE &&
                    abs(restored.blue - original.blue) < TOLERANCE,
            )
        }
    }

    /**
     * Near-black cover art is common, and the raw swatch would be invisible on a dark
     * background — which is exactly where it would be drawn.
     */
    @Test
    fun `a nearly black swatch is lifted into the dark theme's band`() {
        val accent = ArtworkAccent.condition(Color(0xFF0A0C14), dark = true)

        // Compared with a tolerance because the accent round-trips through a packed
        // colour on the way back out, which costs a fraction of a percent.
        assertTrue("lightness ${lightnessOf(accent)}", lightnessOf(accent) >= 0.55f - TOLERANCE)
    }

    @Test
    fun `a nearly white swatch is pushed down for the light theme`() {
        val accent = ArtworkAccent.condition(Color(0xFFFDFDFB), dark = false)

        assertTrue("lightness ${lightnessOf(accent)}", lightnessOf(accent) <= 0.50f + TOLERANCE)
    }

    /** A grey swatch tints nothing; forcing saturation makes it read as a choice. */
    @Test
    fun `a grey swatch gains enough saturation to look intentional`() {
        val accent = ArtworkAccent.condition(Color(0xFF808080), dark = true)

        assertTrue("saturation ${saturationOf(accent)}", saturationOf(accent) >= 0.35f - TOLERANCE)
    }

    /** Whatever else changes, the accent has to stay recognisably the cover's colour. */
    @Test
    fun `hue is never altered`() {
        val seed = Color(0xFF1E88E5)
        val originalHue = hueOf(seed)

        listOf(true, false).forEach { dark ->
            val accent = ArtworkAccent.condition(seed, dark)
            assertEquals(originalHue, hueOf(accent), HUE_TOLERANCE)
        }
    }

    @Test
    fun `an already suitable swatch comes back essentially unchanged`() {
        val seed = ArtworkAccent.fromHsl(hue = 40f, saturation = 0.7f, lightness = 0.65f)

        val accent = ArtworkAccent.condition(seed, dark = true)

        assertEquals(0.7f, saturationOf(accent), TOLERANCE)
        assertEquals(0.65f, lightnessOf(accent), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.01f
        const val HUE_TOLERANCE = 0.5f
    }
}
