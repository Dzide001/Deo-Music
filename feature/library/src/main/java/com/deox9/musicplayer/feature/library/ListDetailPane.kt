// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.deox9.musicplayer.ui.PaneLayout
import com.deox9.musicplayer.ui.rememberWindowLayout

/**
 * A list that either hands the whole window to its detail, or shares it.
 *
 * Every collection screen had the same shape written out by hand: keep the selection
 * in local state, and if it is non-null render the detail and `return` before the
 * list. That works on a phone and is exactly wrong on a tablet, where replacing a
 * 1000dp list with one album's track listing wastes most of the window and loses the
 * context you were browsing.
 *
 * The window is read here rather than passed in, so the six call sites did not each
 * have to thread a flag down from the activity.
 */
@Composable
internal fun ListDetailPane(
    detail: (@Composable () -> Unit)?,
    list: @Composable () -> Unit,
) {
    val twoPane = rememberWindowLayout().paneLayout == PaneLayout.TwoPane

    when {
        detail == null -> list()

        !twoPane -> detail()

        else -> Row(modifier = Modifier.fillMaxSize()) {
            // The list keeps a little over a third. Wide enough for a title and a
            // subtitle without wrapping, narrow enough that the detail — which is
            // where the artwork is — gets the majority.
            Box(modifier = Modifier.weight(LIST_WEIGHT)) { list() }
            VerticalDivider()
            Box(modifier = Modifier.weight(DETAIL_WEIGHT)) { detail() }
        }
    }
}

private const val LIST_WEIGHT = 0.38f
private const val DETAIL_WEIGHT = 0.62f
