// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.player

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The accent colour for whatever is playing, read from its artwork.
 *
 * Decoded through ContentResolver rather than through the image loader: the loader
 * hands back hardware bitmaps, which Palette cannot read at all, and downscaling here
 * means the whole job is a few hundred kilobytes regardless of how large the embedded
 * cover is.
 *
 * Null is a normal answer — no artwork, an unreadable URI, artwork with nothing but
 * greys in it. Callers fall back to the theme's own primary.
 *
 * The colour is held across a track change until the next one has been worked out.
 * produceState was the obvious way to write this and it caused a visible flash on
 * every skip: it resets its value to the initial one when a key changes, so the
 * accent dropped to null — and the whole backdrop to the theme default — for as long
 * as it took to read the file, decode a bitmap and run Palette over it. Then it
 * snapped to the new colour. Holding the last answer means the backdrop changes once,
 * when there is something to change it to.
 */
@Composable
internal fun rememberArtworkColor(artworkUri: String?, dark: Boolean): State<Color?> {
    val context = LocalContext.current
    val color = remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(artworkUri, dark) {
        if (artworkUri.isNullOrBlank()) {
            // Nothing to wait for, so there is no flash to avoid — and holding the
            // previous cover's colour over a track that has no art of its own would
            // be worse than showing the theme's.
            color.value = null
            return@LaunchedEffect
        }
        val resolved = artworkColor(context, artworkUri, dark)
        // Only replaced when something was found. A cover that yields no usable
        // swatch keeps the previous accent rather than flashing to the default and
        // back on the track after it.
        if (resolved != null) color.value = resolved
    }

    return color
}

private suspend fun artworkColor(context: Context, artworkUri: String?, dark: Boolean): Color? {
    if (artworkUri.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = context.contentResolver
                .openInputStream(Uri.parse(artworkUri))
                ?.use { stream ->
                    BitmapFactory.decodeStream(
                        stream,
                        null,
                        BitmapFactory.Options().apply { inSampleSize = SAMPLE_SIZE },
                    )
                }
                ?: return@runCatching null

            val palette = Palette.from(bitmap)
                .maximumColorCount(MAX_COLORS)
                .generate()
            bitmap.recycle()

            // Vibrant first because it is the one that looks like a deliberate choice;
            // the muted swatches are the fallback for covers that have no strong hue,
            // and dominant is the last resort before giving up.
            val swatch = palette.vibrantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.mutedSwatch
                ?: palette.dominantSwatch
                ?: return@runCatching null

            ArtworkAccent.condition(Color(swatch.rgb), dark)
        }.getOrNull()
    }
}

/** A quarter in each dimension is far more resolution than a palette needs. */
private const val SAMPLE_SIZE = 4
private const val MAX_COLORS = 16
