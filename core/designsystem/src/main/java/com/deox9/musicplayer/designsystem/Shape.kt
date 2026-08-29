// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The corner scale.
 *
 * Rounder than the Material baseline throughout, which is the most legible part of
 * the Expressive shift: soft, generous corners read as a media app rather than a
 * settings screen. The sizes are chosen against the components that use them, not
 * picked off a chart.
 */
val DeoShapes = Shapes(
    // Chips, small badges.
    extraSmall = RoundedCornerShape(8.dp),
    // List row artwork, inline images.
    small = RoundedCornerShape(12.dp),
    // Cards, album tiles.
    medium = RoundedCornerShape(16.dp),
    // Mini player, sheets, the play button.
    large = RoundedCornerShape(20.dp),
    // Full-bleed Now Playing artwork.
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Sizes that recur across screens and must agree between them.
 *
 * The mini player's artwork and the Now Playing cover share a shared-element
 * transition, so their corner radii are related by design rather than by coincidence.
 */
object DeoDimens {
    /** Artwork thumbnail in a list row. */
    val rowArtwork = 44.dp

    /** Artwork in the mini player. */
    val miniArtwork = 40.dp

    /** Minimum touch target. Several current transport buttons are under this. */
    val minTouchTarget = 48.dp

    /** Height of the docked mini player, excluding insets. */
    val miniPlayerHeight = 64.dp
}
