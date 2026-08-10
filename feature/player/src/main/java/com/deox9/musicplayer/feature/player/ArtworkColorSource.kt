// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.player

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
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
 */
@Composable
internal fun rememberArtworkColor(artworkUri: String?, dark: Boolean): State<Color?> {
    val context = LocalContext.current
    return produceState<Color?>(initialValue = null, key1 = artworkUri, key2 = dark) {
        value = artworkColor(context, artworkUri, dark)
    }
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
