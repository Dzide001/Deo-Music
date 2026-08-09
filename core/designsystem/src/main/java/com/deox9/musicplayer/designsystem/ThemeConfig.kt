// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Which light/dark scheme the app should use.
 *
 * Tri-state rather than a boolean: "follow the system" is a genuinely different
 * answer from "always light", and the previous `darkThemeEnabled` flag could not
 * express it — the app was permanently pinned to whatever the user last chose.
 */
enum class ThemeMode { System, Light, Dark }

/**
 * Everything that decides what the app looks like.
 *
 * @param seedFromArtwork colour sampled from the current track's artwork, or null
 *   when nothing is playing or extraction failed. Highest-priority seed.
 */
data class ThemeConfig(
    val mode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val amoled: Boolean = false,
    val seedFromArtwork: Color? = null,
)

/**
 * Where the scheme's seed colour comes from, in priority order.
 *
 * Wallpaper-derived colour needs API 31, and the app supports 26, so the fallback
 * is not an edge case — it is what a third of the supported range actually gets.
 * Resolving this as data rather than inline branching keeps it testable.
 */
sealed interface SeedSource {
    /** Sampled from the playing track's cover. */
    data class Artwork(val color: Color) : SeedSource

    /** The system wallpaper palette, API 31+ only. */
    data object Wallpaper : SeedSource

    /** The app's own brand seed. Always available. */
    data object Brand : SeedSource
}

/**
 * Picks the seed for the current state.
 *
 * Artwork wins when present because it is the most specific thing on screen; the
 * wallpaper is a reasonable second because the user chose it; the brand seed is the
 * floor so the app always has a defined identity.
 */
fun resolveSeedSource(
    config: ThemeConfig,
    supportsWallpaperColor: Boolean,
): SeedSource = when {
    !config.dynamicColor -> SeedSource.Brand
    config.seedFromArtwork != null -> SeedSource.Artwork(config.seedFromArtwork)
    supportsWallpaperColor -> SeedSource.Wallpaper
    else -> SeedSource.Brand
}

/** Resolves [ThemeMode] against the current system setting. */
fun resolveDarkTheme(mode: ThemeMode, systemInDarkTheme: Boolean): Boolean = when (mode) {
    ThemeMode.System -> systemInDarkTheme
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}
