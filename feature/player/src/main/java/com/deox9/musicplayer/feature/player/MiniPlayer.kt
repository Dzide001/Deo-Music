// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deox9.musicplayer.player.PlaybackState

/**
 * The bar above the navigation bar.
 *
 * Rebuilt from a transparent column of five outlined text buttons. That version had
 * no surface of its own, so on a list screen the rows behind it showed through and it
 * read as part of the list rather than as a persistent control; and five equally
 * weighted buttons gave "Play" no more prominence than "+10s".
 *
 * What it shows now is the shape every player converges on for a reason: artwork,
 * title and artist, and only the controls worth a thumb at that size. Prev and the
 * seek jump are gone from here — they live in the expanded player, which is one tap
 * away, and both were doing more harm crowding the row than good.
 */
/**
 * Takes the two transport actions rather than the object they live on.
 *
 * It used to take `viewModel: PlayerViewModel = hiltViewModel()` and read
 * `viewModel.playback` for exactly these two calls. That default made the bar look
 * stateless — a session and two callbacks — while quietly requiring a Hilt graph, so
 * it could not be rendered in a screenshot test, or a preview, or anything that is
 * not the whole running app. Two function references cost the one caller a line each.
 */
@Composable
fun MiniPlayerBar(
    session: PlaybackState?,
    onExpand: () -> Unit,
    onOpenQueue: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
) {
    val hasTrack = session != null
    val title = session?.title ?: "Nothing playing"
    val artist = session?.artist?.takeIf(String::isNotBlank) ?: "Pick a track from your library"
    val isPlaying = session?.isPlaying == true

    // surfaceContainer, not surface: the bar has to separate from the list scrolling
    // behind it without a divider doing the work.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            MiniPlayerProgress(session)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = hasTrack,
                        onClick = onExpand,
                        onClickLabel = "Open the player",
                    )
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(
                    artworkUri = session?.albumArtUri,
                    contentDescription = null,
                    cornerRadius = 8.dp,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onOpenQueue, enabled = hasTrack) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
                }
                IconButton(onClick = onTogglePlayPause, enabled = hasTrack) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                    )
                }
                IconButton(onClick = onSkipNext, enabled = hasTrack) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                }
            }
        }
    }
}

/**
 * A hairline of progress along the top edge.
 *
 * Two pixels of information that the old bar spent a whole "+10s" button failing to
 * convey: how far through the track you are. Hidden from accessibility services —
 * the expanded player exposes the same value as a real, seekable slider.
 */
@Composable
private fun MiniPlayerProgress(session: PlaybackState?) {
    val duration = session?.durationMs ?: 0L
    val fraction = if (duration > 0L) {
        (session?.positionMs ?: 0L).toFloat() / duration.toFloat()
    } else {
        0f
    }
    // Position arrives about once a second, so without this the line ticks forward in
    // visible jumps rather than moving.
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        label = "miniPlayerProgress",
    )

    if (session == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PROGRESS_HEIGHT)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        return
    }

    LinearProgressIndicator(
        progress = { animated },
        modifier = Modifier
            .fillMaxWidth()
            .height(PROGRESS_HEIGHT)
            .clearAndSetSemantics { },
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        // The default indicator leaves a gap before the track and draws a stop dot,
        // which at 3dp reads as debris rather than as a progress line.
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

private val PROGRESS_HEIGHT = 3.dp
