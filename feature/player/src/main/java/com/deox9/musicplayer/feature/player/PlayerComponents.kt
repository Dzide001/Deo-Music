// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.deox9.musicplayer.audio.ShuffleMode

/**
 * Album artwork with a placeholder underneath it.
 *
 * The placeholder is drawn *below* the image rather than chosen instead of it, so a
 * URI that fails to load falls back without a flash of empty space. This is the same
 * arrangement the library rows use; it lives here as well because the player module
 * cannot depend on the library one.
 */
@Composable
internal fun ArtworkImage(
    artworkUri: String?,
    contentDescription: String?,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(PLACEHOLDER_FRACTION),
        )
        if (!artworkUri.isNullOrBlank()) {
            AsyncImage(
                model = artworkUri,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Shuffle, previous, play/pause, next, repeat.
 *
 * Replaces a row of five equally weighted outlined buttons whose labels were emoji —
 * "⏮", "🔀", "▶", "🔁", "⏭" — rendered through the system font. Those had no fixed
 * metrics, no tint that followed the theme, and no meaning to a screen reader beyond
 * whatever the font decided the character was called. Play is now the one filled
 * control, because it is the one control anyone is aiming at.
 */
@Composable
internal fun TransportControls(
    isPlaying: Boolean,
    shuffleMode: ShuffleMode,
    repeatMode: Int,
    enabled: Boolean,
    accent: Color,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onShuffle, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                // Names the mode rather than saying "on". Four modes behind one
                // button is only workable if pressing it tells you where you landed,
                // and a screen reader gets nothing from a highlighted icon.
                contentDescription = when (shuffleMode) {
                    ShuffleMode.Off -> "Shuffle off"
                    ShuffleMode.Tracks -> "Shuffle tracks"
                    ShuffleMode.Albums -> "Shuffle albums"
                    ShuffleMode.Folders -> "Shuffle folders"
                },
                tint = if (shuffleMode != ShuffleMode.Off) accent else inactive,
            )
        }
        IconButton(onClick = onPrevious, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Previous",
                modifier = Modifier.size(SKIP_ICON),
            )
        }

        Surface(
            color = accent,
            contentColor = MaterialTheme.colorScheme.surface,
            shape = CircleShape,
            modifier = Modifier.size(PLAY_BUTTON),
        ) {
            IconButton(onClick = onPlayPause, enabled = enabled) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(PLAY_ICON),
                )
            }
        }

        IconButton(onClick = onNext, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Next",
                modifier = Modifier.size(SKIP_ICON),
            )
        }
        IconButton(onClick = onRepeat, enabled = enabled) {
            Icon(
                // Repeat-one gets its own glyph rather than a badge, so the three
                // states are distinguishable without relying on the tint.
                imageVector = if (repeatMode == REPEAT_ONE) {
                    Icons.Filled.RepeatOne
                } else {
                    Icons.Filled.Repeat
                },
                contentDescription = when (repeatMode) {
                    REPEAT_OFF -> "Repeat off"
                    REPEAT_ONE -> "Repeat one"
                    else -> "Repeat all"
                },
                tint = if (repeatMode == REPEAT_OFF) inactive else accent,
            )
        }
    }
}

/**
 * The accent the player tints itself with.
 *
 * Follows the theme by default and shifts to the artwork's own colour when one could
 * be read, which is the whole point of a full-bleed player: a screen that is mostly
 * one album's artwork should not be trimmed in an unrelated hue. Animated because the
 * colour arrives after the image decodes, and snapping is more distracting than the
 * change itself.
 */
@Composable
internal fun rememberPlayerAccent(artworkColor: Color?): Color {
    val fallback = MaterialTheme.colorScheme.primary
    val target = artworkColor ?: fallback
    val animated by animateColorAsState(targetValue = target, label = "playerAccent")
    return animated
}

internal const val REPEAT_OFF = 0
internal const val REPEAT_ONE = 1

private const val PLACEHOLDER_FRACTION = 0.4f
private val PLAY_BUTTON = 64.dp
private val PLAY_ICON = 32.dp
private val SKIP_ICON = 32.dp
