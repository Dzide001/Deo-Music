// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.deox9.musicplayer.player.PlaybackState
import com.deox9.musicplayer.ui.formatDuration
import com.deox9.musicplayer.ui.rememberWindowLayout

/**
 * The expanded player.
 *
 * The screen this replaces was one 907-line composable with a cyclomatic complexity of
 * 81: a title bar, an artwork/lyrics tab pager, two sliders, two alternative transport
 * rows, a secondary action bar and six dialogs, all in one function body with about
 * twenty pieces of `remember`ed state between them. Nothing in it could be looked at
 * in isolation, which is why the queue button sat unwired for as long as it did.
 *
 * The layout is now what a full-bleed player looks like: artwork large and centred on
 * a ground tinted by the artwork's own colour, the metadata under it, and one row of
 * transport controls with Play as the only filled thing on screen. Lyrics moved out of
 * a tab beside the artwork — putting them there meant choosing between seeing the
 * cover and reading along — and into the panel that already existed for them.
 */
@Composable
fun ExpandedNowPlayingScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    session: PlaybackState?,
    onMinimize: () -> Unit,
    onOpenQueue: () -> Unit,
    onGoToArtist: (String) -> Unit,
    onViewAlbum: (String) -> Unit,
) {
    val playback = viewModel.playback
    val favourites by viewModel.favourites.collectAsState()
    val dark = isSystemInDarkTheme()
    val artworkColor by rememberArtworkColor(session?.albumArtUri, dark)
    val accent = rememberPlayerAccent(artworkColor)

    var dialog by remember { mutableStateOf(PlayerDialog.None) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val hasTrack = session != null
    val currentUri = session?.uri.orEmpty()
    val windowLayout = rememberWindowLayout()

    // The backdrop is on the outer box so it reaches the screen edges; the insets are
    // on the inner column so the controls do not. The host draws this with no padding
    // of its own, which is why the screen has to inset itself.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .artworkBackdrop(accent)
            .swipeDownToDismiss(onMinimize),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp),
        ) {
            NowPlayingTopBar(
                album = session?.album.orEmpty(),
                showMoreMenu = showMoreMenu,
                onShowMoreMenuChange = { showMoreMenu = it },
                onMinimize = onMinimize,
                session = session,
                onOpenQueue = onOpenQueue,
                onGoToArtist = onGoToArtist,
                onViewAlbum = onViewAlbum,
                onRequestDialog = { dialog = it },
            )

            val artwork: @Composable (Modifier) -> Unit = { modifier ->
                NowPlayingArtwork(session = session, modifier = modifier)
            }
            val controls: @Composable (Modifier) -> Unit = { modifier ->
                NowPlayingControls(
                    session = session,
                    accent = accent,
                    isFavourite = currentUri in favourites,
                    hasTrack = hasTrack,
                    playback = playback,
                    onGoToArtist = onGoToArtist,
                    onToggleFavourite = { viewModel.toggleFavourite(currentUri) },
                    onOpenQueue = onOpenQueue,
                    onRequestDialog = { dialog = it },
                    modifier = modifier,
                )
            }

            if (windowLayout.playerSideBySide) {
                // A phone in landscape has no room to stack a square cover above a
                // transport row without losing one of them, so they go side by side.
                // The decision is on height, not width: a tall tablet in landscape
                // has plenty of room to stack and gets an enormous cover for it.
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    artwork(Modifier.weight(1f).fillMaxHeight())
                    Spacer(modifier = Modifier.width(24.dp))
                    controls(Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                artwork(Modifier.weight(1f).fillMaxWidth())
                Spacer(modifier = Modifier.height(24.dp))
                controls(Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    PlayerDialogs(
        dialog = dialog,
        onDismiss = { dialog = PlayerDialog.None },
        onShowDialog = { dialog = it },
        session = session,
        onMinimize = onMinimize,
    )
}

/**
 * The cover, as large as the space it is handed allows.
 *
 * Sized from the constraints rather than to a fixed height so it shrinks on a short
 * window instead of pushing the transport row off the bottom — which is what the
 * fixed 260dp pager it replaced used to do.
 */
@Composable
private fun NowPlayingArtwork(session: PlaybackState?, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        BoxWithConstraints {
            val side = minOf(maxWidth, maxHeight)
            ArtworkImage(
                artworkUri = session?.albumArtUri,
                contentDescription = session?.let { "Album art for ${it.title}" },
                cornerRadius = 24.dp,
                modifier = Modifier
                    .size(side)
                    .aspectRatio(1f),
            )
        }
    }
}

/**
 * Everything below (or beside) the cover: metadata, seek bar, transport, extras.
 *
 * Grouped so the two window layouts differ only in whether this sits under the
 * artwork or next to it, rather than in two copies of the same call sequence — which
 * is how the screen this replaced ended up with two divergent transport rows.
 */
@Composable
private fun NowPlayingControls(
    session: PlaybackState?,
    accent: Color,
    isFavourite: Boolean,
    hasTrack: Boolean,
    playback: com.deox9.musicplayer.player.PlaybackConnection,
    onGoToArtist: (String) -> Unit,
    onToggleFavourite: () -> Unit,
    onOpenQueue: () -> Unit,
    onRequestDialog: (PlayerDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        TrackHeadline(
            title = session?.title ?: "Nothing playing",
            artist = session?.artist?.takeIf(String::isNotBlank) ?: "Pick a track from your library",
            onArtistClick = {
                val name = session?.artist.orEmpty().trim()
                if (name.isNotBlank()) onGoToArtist(name)
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        SeekBar(session = session, accent = accent, onSeek = playback::seekTo)

        Spacer(modifier = Modifier.height(8.dp))

        TransportControls(
            isPlaying = session?.isPlaying == true,
            shuffleEnabled = session?.shuffleEnabled == true,
            repeatMode = session?.repeatMode ?: REPEAT_OFF,
            enabled = hasTrack,
            accent = accent,
            onShuffle = playback::toggleShuffle,
            onPrevious = playback::skipPrevious,
            onPlayPause = playback::togglePlayPause,
            onNext = playback::skipNext,
            onRepeat = playback::cycleRepeat,
        )

        Spacer(modifier = Modifier.height(8.dp))

        SecondaryActions(
            isFavourite = isFavourite,
            enabled = hasTrack,
            accent = accent,
            onToggleFavourite = onToggleFavourite,
            onOpenLyrics = { onRequestDialog(PlayerDialog.Lyrics) },
            onOpenQueue = onOpenQueue,
            onOpenAudioSettings = { onRequestDialog(PlayerDialog.AudioSettings) },
        )
    }
}

/**
 * A vertical wash of the artwork's colour behind everything.
 *
 * Blur was the obvious alternative and is not available: RenderEffect needs API 31 and
 * this app supports 26, so half the range would have got a flat rectangle where the
 * other half got the effect. A gradient from the same extracted colour looks
 * deliberate everywhere.
 */
private fun Modifier.artworkBackdrop(accent: Color): Modifier = this.then(
    Modifier.background(
        Brush.linearGradient(
            colors = listOf(accent.copy(alpha = BACKDROP_ALPHA), Color.Transparent),
            start = Offset.Zero,
            end = Offset(0f, Float.POSITIVE_INFINITY),
        ),
    ),
)

/** Drag down anywhere to collapse, which is what the gesture does in every player. */
private fun Modifier.swipeDownToDismiss(onDismiss: () -> Unit): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        var travelled = 0f
        detectVerticalDragGestures(
            onDragEnd = {
                if (travelled > DISMISS_DRAG_PX) onDismiss()
                travelled = 0f
            },
            onDragCancel = { travelled = 0f },
        ) { change, dragAmount ->
            change.consume()
            travelled += dragAmount
        }
    },
)

@Composable
private fun TrackHeadline(
    title: String,
    artist: String,
    onArtistClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = artist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // A one-line label is about 24dp tall, well under the 48dp minimum, and
            // "double-tap to activate" does not say what activating does.
            modifier = Modifier
                .clickable(onClick = onArtistClick, onClickLabel = "Go to artist")
                .minimumInteractiveComponentSize()
                .padding(vertical = 2.dp),
        )
    }
}

/**
 * Position and duration.
 *
 * Held locally while a drag is in progress: the state flow keeps emitting the *old*
 * position for up to a second after the finger moves, so a slider bound straight to it
 * springs back under the thumb.
 */
@Composable
private fun SeekBar(
    session: PlaybackState?,
    accent: Color,
    onSeek: (Long) -> Unit,
) {
    val durationMs = (session?.durationMs ?: 0L).coerceAtLeast(1L)
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(session?.positionMs, session?.uri) {
        if (!scrubbing) {
            scrubPosition = (session?.positionMs ?: 0L).coerceIn(0L, durationMs).toFloat()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = scrubPosition.coerceIn(0f, durationMs.toFloat()),
            onValueChange = {
                scrubbing = true
                scrubPosition = it
            },
            onValueChangeFinished = {
                onSeek(scrubPosition.toLong())
                scrubbing = false
            },
            valueRange = 0f..durationMs.toFloat(),
            enabled = session != null,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(scrubPosition.toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                // Counting down rather than showing the total: "how much is left" is
                // the question a running track raises.
                text = "-${formatDuration((durationMs - scrubPosition.toLong()).coerceAtLeast(0L))}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SecondaryActions(
    isFavourite: Boolean,
    enabled: Boolean,
    accent: Color,
    onToggleFavourite: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenAudioSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = onToggleFavourite, enabled = enabled) {
            Icon(
                imageVector = if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isFavourite) "Remove from favourites" else "Add to favourites",
                tint = if (isFavourite) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenLyrics, enabled = enabled) {
            Icon(
                // Was a MoreVert glyph, which is the "more options" icon and already
                // meant something else two rows above it.
                imageVector = Icons.Filled.Lyrics,
                contentDescription = "Lyrics",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenQueue, enabled = enabled) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = "Queue",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenAudioSettings) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Audio settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NowPlayingTopBar(
    album: String,
    showMoreMenu: Boolean,
    onShowMoreMenuChange: (Boolean) -> Unit,
    onMinimize: () -> Unit,
    session: PlaybackState?,
    onOpenQueue: () -> Unit,
    onGoToArtist: (String) -> Unit,
    onViewAlbum: (String) -> Unit,
    onRequestDialog: (PlayerDialog) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMinimize) {
            // A chevron down, not a back arrow: this collapses a sheet, it does not
            // navigate anywhere, and the old screen offered both in two places.
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Collapse player")
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = TITLE_MAX_WIDTH),
        ) {
            Text(
                text = "Playing from",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = album.ifBlank { "your library" },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        Box {
            IconButton(onClick = { onShowMoreMenuChange(!showMoreMenu) }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
            }
            NowPlayingMenu(
                expanded = showMoreMenu,
                session = session,
                onDismiss = { onShowMoreMenuChange(false) },
                onOpenQueue = onOpenQueue,
                onGoToArtist = onGoToArtist,
                onViewAlbum = onViewAlbum,
                onRequestDialog = onRequestDialog,
            )
        }
    }
}

private const val BACKDROP_ALPHA = 0.28f
private const val DISMISS_DRAG_PX = 160f
private val TITLE_MAX_WIDTH = 200.dp
