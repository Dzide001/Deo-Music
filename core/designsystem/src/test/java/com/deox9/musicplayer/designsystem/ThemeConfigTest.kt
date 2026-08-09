// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeConfigTest {

    private val artwork = Color(0xFF3F7AE0)

    @Test
    fun `system mode follows the system setting`() {
        assertTrue(resolveDarkTheme(ThemeMode.System, systemInDarkTheme = true))
        assertFalse(resolveDarkTheme(ThemeMode.System, systemInDarkTheme = false))
    }

    /** The point of the tri-state: an explicit choice overrides the system. */
    @Test
    fun `explicit modes ignore the system setting`() {
        assertTrue(resolveDarkTheme(ThemeMode.Dark, systemInDarkTheme = false))
        assertFalse(resolveDarkTheme(ThemeMode.Light, systemInDarkTheme = true))
    }

    @Test
    fun `artwork outranks wallpaper and brand`() {
        val source = resolveSeedSource(
            ThemeConfig(seedFromArtwork = artwork),
            supportsWallpaperColor = true,
        )

        assertEquals(SeedSource.Artwork(artwork), source)
    }

    @Test
    fun `wallpaper is used when there is no artwork and the platform supports it`() {
        val source = resolveSeedSource(ThemeConfig(), supportsWallpaperColor = true)

        assertEquals(SeedSource.Wallpaper, source)
    }

    /**
     * The API 26–30 path. Wallpaper colour needs API 31, so a large slice of the
     * supported range always lands here and must still get a defined identity.
     */
    @Test
    fun `brand seed is used when the platform cannot supply wallpaper colour`() {
        val source = resolveSeedSource(ThemeConfig(), supportsWallpaperColor = false)

        assertEquals(SeedSource.Brand, source)
    }

    /** Turning dynamic colour off must beat every other source, including artwork. */
    @Test
    fun `disabling dynamic colour forces the brand seed`() {
        val source = resolveSeedSource(
            ThemeConfig(dynamicColor = false, seedFromArtwork = artwork),
            supportsWallpaperColor = true,
        )

        assertEquals(SeedSource.Brand, source)
    }

    @Test
    fun `amoled flattens the dark scheme grounds to black`() {
        val scheme = buildColorScheme(ThemeConfig(amoled = true), dark = true)

        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.Black, scheme.surface)
    }

    /**
     * Containers and accents must survive the AMOLED transform — Material uses tonal
     * surfaces instead of shadows, so flattening them too would erase every
     * elevation cue and leave the UI unreadable.
     */
    @Test
    fun `amoled keeps containers and accents tonal`() {
        val plain = buildColorScheme(ThemeConfig(), dark = true)
        val amoled = buildColorScheme(ThemeConfig(amoled = true), dark = true)

        assertEquals(plain.primary, amoled.primary)
        assertEquals(plain.primaryContainer, amoled.primaryContainer)
        assertEquals(plain.outline, amoled.outline)
        assertTrue("surfaceContainer should not be pure black", amoled.surfaceContainer != Color.Black)
    }

    /** Applied to a light scheme this would be black on black, so it must not apply. */
    @Test
    fun `amoled does not touch the light scheme`() {
        val plain = buildColorScheme(ThemeConfig(), dark = false)
        val withFlag = buildColorScheme(ThemeConfig(amoled = true), dark = false)

        assertEquals(plain.background, withFlag.background)
        assertTrue("light background should stay light", withFlag.background != Color.Black)
    }

    @Test
    fun `different artwork seeds produce different schemes`() {
        val warm = buildColorScheme(ThemeConfig(seedFromArtwork = Color(0xFFF0A64A)), dark = true)
        val cool = buildColorScheme(ThemeConfig(seedFromArtwork = Color(0xFF63C7C0)), dark = true)

        assertTrue("seed should drive the primary role", warm.primary != cool.primary)
    }

    @Test
    fun `light and dark schemes differ for the same seed`() {
        val config = ThemeConfig(seedFromArtwork = artwork)

        assertTrue(buildColorScheme(config, dark = true).background != buildColorScheme(config, dark = false).background)
    }
}
