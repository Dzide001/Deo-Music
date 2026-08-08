// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.library

import android.content.ContentUris
import android.net.Uri

/**
 * Album artwork URIs.
 *
 * The artwork lives under `content://media/external/audio/albumart/<albumId>`, which
 * is a different provider path from the album row itself
 * (`MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI + id`). The row URI is a database
 * record, not an image: opening it fails, so anything pointed at it renders no
 * artwork. Verified on-device — the row URI returns an error while the albumart URI
 * returns JPEG data.
 *
 * There is no public MediaStore constant for this path; the string is the documented
 * legacy location that `MediaStore.Audio.Albums.ALBUM_ART` used to resolve to, and it
 * is what every other Android player uses.
 */
object AlbumArt {
    private val BASE_URI: Uri = Uri.parse("content://media/external/audio/albumart")

    fun forAlbumId(albumId: Long): Uri = ContentUris.withAppendedId(BASE_URI, albumId)
}
