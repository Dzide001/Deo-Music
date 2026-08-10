// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Where top-level navigation lives at a given window width.
 *
 * A bottom bar on a tablet wastes the one dimension a tablet has least of — vertical
 * space next to a thumb that is nowhere near the bottom edge — and puts the controls
 * at the far end of a 1000dp reach.
 */
enum class NavigationStyle { BottomBar, Rail }

/**
 * Whether a list and its detail share the window or replace each other.
 */
enum class PaneLayout { Single, TwoPane }

/**
 * Material's window size classes, by the width of the window the app is drawn in —
 * not the width of the screen. A phone-sized split-window on a tablet is Compact, and
 * has to be laid out like a phone.
 */
enum class WidthClass { Compact, Medium, Expanded }

/** The Material breakpoints. Below 600dp is a phone in portrait; 840dp and up fits two panes. */
private const val MEDIUM_MIN_DP = 600
private const val EXPANDED_MIN_DP = 840

fun widthClassFor(widthDp: Int): WidthClass = when {
    widthDp < MEDIUM_MIN_DP -> WidthClass.Compact
    widthDp < EXPANDED_MIN_DP -> WidthClass.Medium
    else -> WidthClass.Expanded
}

/**
 * The rail appears from Medium, not from Expanded.
 *
 * A 700dp window is already wider than a thumb travels comfortably, and it is the
 * common case for a foldable opened flat or a tablet in portrait.
 */
fun navigationStyleFor(widthClass: WidthClass): NavigationStyle = when (widthClass) {
    WidthClass.Compact -> NavigationStyle.BottomBar
    WidthClass.Medium, WidthClass.Expanded -> NavigationStyle.Rail
}

/**
 * Two panes need Expanded, one step later than the rail.
 *
 * At Medium the window is wide enough that a bottom bar is wrong but not wide enough
 * for a list and a detail to both be usable: splitting 700dp gives each pane less than
 * a phone has.
 */
fun paneLayoutFor(widthClass: WidthClass): PaneLayout = when (widthClass) {
    WidthClass.Compact, WidthClass.Medium -> PaneLayout.Single
    WidthClass.Expanded -> PaneLayout.TwoPane
}

/**
 * Whether the player should put artwork beside the controls rather than above them.
 *
 * Keyed on the window being short rather than on it being wide: a phone in landscape
 * is Compact and still cannot stack a square cover above a transport row without one
 * of them disappearing. Height is what actually decides this.
 */
fun playerUsesSideBySide(widthDp: Int, heightDp: Int): Boolean =
    heightDp < PLAYER_MIN_STACKED_HEIGHT_DP && widthDp > heightDp

/** Below this the stacked player has no room for a cover worth showing. */
private const val PLAYER_MIN_STACKED_HEIGHT_DP = 480

/**
 * The layout decisions for the window the app is currently drawn in.
 *
 * All of it derives from two numbers, so the rules live in [widthClassFor] and friends
 * as plain functions and this is only the wiring that reads the window.
 */
@Immutable
data class WindowLayout(
    val widthDp: Int,
    val heightDp: Int,
    val widthClass: WidthClass,
    val navigationStyle: NavigationStyle,
    val paneLayout: PaneLayout,
    val playerSideBySide: Boolean,
)

/**
 * Measures the app's *window*, not the device's screen.
 *
 * `LocalWindowInfo.containerSize` is the size the app was actually given, so a
 * phone-width split-window on a tablet reports as a phone and gets laid out like one.
 * Reading the display instead would give a tablet layout to a 400dp column.
 */
@Composable
fun rememberWindowLayout(): WindowLayout {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current

    return remember(containerSize, density) {
        val widthDp = with(density) { containerSize.width.toDp() }.value.toInt()
        val heightDp = with(density) { containerSize.height.toDp() }.value.toInt()
        val widthClass = widthClassFor(widthDp)

        WindowLayout(
            widthDp = widthDp,
            heightDp = heightDp,
            widthClass = widthClass,
            navigationStyle = navigationStyleFor(widthClass),
            paneLayout = paneLayoutFor(widthClass),
            playerSideBySide = playerUsesSideBySide(widthDp, heightDp),
        )
    }
}
