// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

/**
 * A playlist as read from MediaStore, before it is resolved into database rows.
 *
 * [memberContentUris] is the membership in play order, as content:// track URIs —
 * the same identifier [LibraryIndexer] keys indexed tracks by, so the importer can
 * resolve each entry with a plain lookup.
 */
data class ScannedPlaylist(
    val mediaStoreId: Long,
    val name: String,
    val memberContentUris: List<String>,
)
