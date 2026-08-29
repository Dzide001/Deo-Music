// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.widget

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.deox9.musicplayer.player.storage.PlaybackSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The Now Playing home-screen widget.
 *
 * State comes from the persisted session in DataStore rather than from a bound
 * controller: the widget has to render whether or not the service is running, and
 * binding one just to draw a title would keep the process alive for nothing.
 *
 * [provideContent] keeps a coroutine alive for as long as the host is listening, so
 * collecting the store here is what makes the widget live — no broadcast from the
 * service, and therefore no dependency pointing from `:core:media` back at this module.
 */
class NowPlayingWidget : GlanceAppWidget() {

    // Responsive rather than Exact: the host tells us the size and we pick a layout,
    // instead of asking it to re-render for every intermediate drag width.
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = PlaybackSessionRepository(context)
        // The service rewrites the session every second while playing, to keep the
        // resume position fresh. None of that reaches the widget, so it is collapsed
        // here — otherwise the widget would re-render once a second forever.
        val states = repository.observe()
            .map { it.toWidgetState() }
            .distinctUntilChanged()

        provideContent {
            val state by states.collectAsState(initial = WidgetState.Empty)
            GlanceTheme {
                WidgetContent(state)
            }
        }
    }

    private companion object {
        /** One row of controls, no artwork — a 2x1 slot. */
        val SMALL = DpSize(180.dp, 60.dp)

        /** Artwork beside the text — the default 4x1. */
        val MEDIUM = DpSize(260.dp, 100.dp)

        /** Artwork above the text, for a taller 4x2 slot. */
        val LARGE = DpSize(260.dp, 180.dp)
    }
}

@Composable
private fun WidgetContent(state: WidgetState) {
    val size = LocalSize.current
    val stacked = size.height >= STACKED_MIN_HEIGHT
    val showArtwork = size.width >= ARTWORK_MIN_WIDTH

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(WIDGET_CORNER)
            .padding(12.dp)
            .clickable(actionRunCallback<OpenAppAction>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stacked && showArtwork) {
            WidgetArtwork(state.albumArtUri, ARTWORK_LARGE)
            Spacer(modifier = GlanceModifier.height(8.dp))
            TrackText(state, modifier = GlanceModifier.fillMaxWidth())
            Spacer(modifier = GlanceModifier.height(4.dp))
            TransportRow(state)
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showArtwork) {
                    WidgetArtwork(state.albumArtUri, ARTWORK_SMALL)
                    Spacer(modifier = GlanceModifier.width(10.dp))
                }
                TrackText(state, modifier = GlanceModifier.defaultWeight())
                TransportRow(state)
            }
        }
    }
}

@Composable
private fun TrackText(state: WidgetState, modifier: GlanceModifier = GlanceModifier) {
    Column(modifier = modifier) {
        Text(
            text = state.title,
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontWeight = FontWeight.Medium,
            ),
        )
        if (state.artist.isNotBlank()) {
            Text(
                text = state.artist,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun TransportRow(state: WidgetState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        WidgetControl(
            resId = R.drawable.ic_widget_previous,
            description = "Previous",
            enabled = state.hasTrack,
            command = WidgetCommand.Previous,
        )
        WidgetControl(
            resId = if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            description = if (state.isPlaying) "Pause" else "Play",
            enabled = state.hasTrack,
            command = WidgetCommand.PlayPause,
        )
        WidgetControl(
            resId = R.drawable.ic_widget_next,
            description = "Next",
            enabled = state.hasTrack,
            command = WidgetCommand.Next,
        )
    }
}

@Composable
private fun WidgetControl(
    resId: Int,
    description: String,
    enabled: Boolean,
    command: WidgetCommand,
) {
    // Kept at 48dp even in the small layout. A widget sits under a finger aiming at a
    // home screen, which is a worse aiming context than an app, not a better one.
    val base = GlanceModifier.size(CONTROL_SIZE).padding(6.dp)
    Image(
        provider = ImageProvider(resId),
        contentDescription = description,
        colorFilter = androidx.glance.ColorFilter.tint(
            if (enabled) GlanceTheme.colors.onSurface else GlanceTheme.colors.onSurfaceVariant,
        ),
        modifier = if (enabled) base.clickable(commandAction(command)) else base,
    )
}

/**
 * Album art, decoded here rather than handed to the host as a URI.
 *
 * A widget runs in the launcher's process, which holds no read permission on our
 * MediaStore URIs; passing the URI straight through renders nothing. Decoding to a
 * bitmap in our own process and sending that across is the supported route.
 */
@Composable
private fun WidgetArtwork(artworkUri: String?, size: androidx.compose.ui.unit.Dp) {
    val context = androidx.glance.LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = artworkUri) {
        value = artworkUri?.let { decodeArtwork(context, it) }
    }

    val modifier = GlanceModifier.size(size).cornerRadius(ARTWORK_CORNER)
    val current = bitmap
    if (current != null) {
        Image(
            provider = ImageProvider(current),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_placeholder),
            contentDescription = null,
            colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            modifier = modifier.background(GlanceTheme.colors.surfaceVariant).padding(8.dp),
        )
    }
}

private suspend fun decodeArtwork(context: Context, uri: String): android.graphics.Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                // Widget images cross a Binder transaction with a hard size limit, so
                // this is downscaled well below anything a home screen can show.
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = ARTWORK_SAMPLE_SIZE },
                )
            }
        }.getOrNull()
    }

/** The receiver the launcher talks to. Declared in this module's manifest. */
class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}

private val STACKED_MIN_HEIGHT = 140.dp
private val ARTWORK_MIN_WIDTH = 200.dp
private val ARTWORK_SMALL = 48.dp
private val ARTWORK_LARGE = 72.dp
private val ARTWORK_CORNER = 8.dp
private val WIDGET_CORNER = 16.dp
private val CONTROL_SIZE = 48.dp
private const val ARTWORK_SAMPLE_SIZE = 4
