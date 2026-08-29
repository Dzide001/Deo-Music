// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.library

import com.deox9.musicplayer.database.dao.LibraryDao
import com.deox9.musicplayer.database.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** A bookmark as the player needs it, without the database's row identity leaking out. */
data class Bookmark(
    val id: Long,
    val positionMs: Long,
    val label: String,
)

/**
 * Named points inside tracks.
 *
 * Keyed by the track's content URI at this boundary, because that is what the player
 * knows; the database keys by row id, and translating here keeps the player from
 * caring which is which.
 */
@Singleton
class BookmarksRepository @Inject constructor(
    private val dao: LibraryDao,
) {

    fun observe(trackUri: String): Flow<List<Bookmark>> {
        // A URI the library has never indexed has no row to hang bookmarks off, so
        // it yields an empty list rather than a database error.
        val trackId = trackIdCache[trackUri]
        return if (trackId == null) {
            flowOf(emptyList())
        } else {
            dao.observeBookmarks(trackId).map { rows ->
                rows.map { Bookmark(it.id, it.positionMs, it.label) }
            }
        }
    }

    /** Resolves and remembers the row id, so the flow above can stay synchronous. */
    suspend fun prepare(trackUri: String) {
        if (trackUri.isNotBlank() && trackUri !in trackIdCache) {
            dao.trackIdForUri(trackUri)?.let { trackIdCache[trackUri] = it }
        }
    }

    suspend fun add(trackUri: String, positionMs: Long, label: String = "") {
        val trackId = dao.trackIdForUri(trackUri) ?: return
        trackIdCache[trackUri] = trackId
        dao.addBookmark(
            BookmarkEntity(
                trackId = trackId,
                positionMs = positionMs.coerceAtLeast(0L),
                label = label.trim(),
                createdAtMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun remove(id: Long) = dao.deleteBookmark(id)

    suspend fun rename(id: Long, label: String) {
        val cleaned = label.trim()
        // A blank name is not an error: it means "show the timestamp instead", which
        // is what an unnamed bookmark already does.
        dao.renameBookmark(id, cleaned)
    }

    private val trackIdCache = mutableMapOf<String, Long>()
}
