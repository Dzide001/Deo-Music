// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.deox9.musicplayer.ui.AlphabetBucket
import com.deox9.musicplayer.ui.sampleBuckets
import kotlinx.coroutines.launch

/**
 * Album artwork for a list row.
 *
 * The placeholder sits *under* the image rather than being chosen instead of it, so a
 * URI that turns out to be unreadable degrades to the placeholder without a flash of
 * empty space. Half a library having no embedded art is normal, and a column of gaps
 * reads as a broken screen.
 */
@Composable
internal fun TrackArtwork(
    artworkUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = ARTWORK_SIZE,
    cornerRadius: Dp = ARTWORK_CORNER,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size / 2),
        )
        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

/**
 * Alphabet rail for jumping through a long list.
 *
 * A flick through 4,000 tracks is dozens of gestures; the rail makes it one. It only
 * makes sense over a list actually sorted by the label, which is why callers gate on
 * [com.deox9.musicplayer.ui.shouldShowFastScroll] rather than showing it always.
 *
 * Press and drag are handled by one pointer loop instead of separate tap and drag
 * detectors: a tap is a press that ends where it started, and running two detectors
 * over the same narrow strip makes the first touch after a scroll get eaten.
 */
@Composable
internal fun FastScrollRail(
    buckets: List<AlphabetBucket>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    // One letter is not a rail, it is a button that does nothing useful.
    if (buckets.size < MIN_BUCKETS) return

    val scope = rememberCoroutineScope()
    var activeLabel by remember { mutableStateOf<Char?>(null) }
    var railHeightPx by remember { mutableIntStateOf(0) }

    fun jumpTo(y: Float) {
        if (railHeightPx == 0) return
        // Coerced below 1 so the bottom edge lands on the last bucket rather than
        // one past it.
        val fraction = (y / railHeightPx).coerceIn(0f, LAST_FRACTION)
        val bucket = buckets[(fraction * buckets.size).toInt()]
        if (bucket.label != activeLabel) {
            activeLabel = bucket.label
            scope.launch { listState.scrollToItem(bucket.firstIndex) }
        }
    }

    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Anchored to the rail's vertical centre rather than following the finger:
        // under the finger it is exactly where the hand already is.
        if (activeLabel != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(BUBBLE_CORNER),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text(
                    text = activeLabel.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        BoxWithConstraints {
            // Printed labels are thinned to what the rail is tall enough to show.
            // Without this the Column overflows and the tail of the alphabet is
            // clipped rather than dropped, which reads as a rendering bug.
            // Scaled by the font setting: the labels are text, so at a 200% scale
            // half as many fit, and a fixed 18dp would have them overlapping.
            val labelHeight = LABEL_HEIGHT * LocalDensity.current.fontScale
            val maxLabels = (maxHeight / labelHeight).toInt()
            val shown = sampleBuckets(buckets, maxLabels)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .width(RAIL_WIDTH)
                    .fillMaxHeight()
                    .onSizeChanged { railHeightPx = it.height }
                    .pointerInput(buckets) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                jumpTo(down.position.y)
                                var pressed = true
                                while (pressed) {
                                    val change = awaitPointerEvent().changes
                                        .firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed) {
                                        pressed = false
                                    } else {
                                        jumpTo(change.position.y)
                                        change.consume()
                                    }
                                }
                                activeLabel = null
                            }
                        }
                    }
                    // The rail duplicates navigation the list already offers, and read
                    // aloud it is 27 unlabelled characters, so it is hidden from
                    // accessibility services rather than announced.
                    .clearAndSetSemantics { },
            ) {
                shown.forEach { bucket ->
                    Text(
                        text = bucket.label.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (bucket.label == activeLabel) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/**
 * Width to keep clear on the trailing edge of a list that shows the rail, so rows
 * end before the letters begin instead of running underneath them.
 */
internal val FastScrollGutter = 28.dp

private const val MIN_BUCKETS = 2
private const val LAST_FRACTION = 0.999f
private val RAIL_WIDTH = 24.dp

/** Line box a rail label occupies; used to work out how many of them fit. */
private val LABEL_HEIGHT = 18.dp
private val BUBBLE_CORNER = 12.dp
private val ARTWORK_SIZE = 48.dp
private val ARTWORK_CORNER = 8.dp
