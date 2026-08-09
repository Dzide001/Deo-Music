// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.dynamicColorScheme

/**
 * The app's theme.
 *
 * Colour is generated from a seed rather than hand-authored, so every role stays
 * tonally consistent no matter where the seed came from. The seed priority is
 * artwork, then wallpaper, then the app's own brand colour — see [resolveSeedSource].
 *
 * Wallpaper-derived colour requires API 31 and the app supports 26, so the fallback
 * path is not an edge case: it is what a large slice of the supported range gets, and
 * it has to look deliberate rather than degraded.
 */
@Composable
fun DeoTheme(
    config: ThemeConfig = ThemeConfig(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = resolveDarkTheme(config.mode, isSystemInDarkTheme())
    val supportsWallpaper = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val scheme = remember(config, dark, supportsWallpaper) {
        val base = when (val seed = resolveSeedSource(config, supportsWallpaper)) {
            is SeedSource.Artwork -> dynamicColorScheme(seed.color, isDark = dark)
            // resolveSeedSource only returns Wallpaper when supportsWallpaper is
            // true, but that is a runtime fact lint cannot follow, so the version
            // check is repeated here rather than suppressed.
            SeedSource.Wallpaper ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else {
                    dynamicColorScheme(BrandSeed, isDark = dark)
                }
            SeedSource.Brand -> dynamicColorScheme(BrandSeed, isDark = dark)
        }

        // Gated on dark: applied to a light scheme this would render black on black.
        if (dark && config.amoled) base.toAmoled() else base
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = DeoTypography,
        shapes = DeoShapes,
        content = content,
    )
}

/**
 * Builds the scheme without composing it, for previews and tests.
 *
 * Kept separate from [DeoTheme] so the colour decisions can be exercised without a
 * Compose runtime; the composable is then only responsible for wiring.
 */
fun buildColorScheme(
    config: ThemeConfig,
    dark: Boolean,
): ColorScheme {
    // Wallpaper needs a Context, so this non-composable path never selects it and
    // resolves to the brand seed instead.
    val base = when (val seed = resolveSeedSource(config, supportsWallpaperColor = false)) {
        is SeedSource.Artwork -> dynamicColorScheme(seed.color, isDark = dark)
        else -> dynamicColorScheme(BrandSeed, isDark = dark)
    }
    return if (dark && config.amoled) base.toAmoled() else base
}
