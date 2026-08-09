// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import com.deox9.musicplayer.database.dao.LibraryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a library scan and reports progress.
 *
 * Scans are serialised by a mutex: a content-observer change and a manual refresh can
 * easily overlap, and two passes writing the same rows would fight over ids.
 */
@Singleton
class LibraryScanner @Inject constructor(
    private val mediaStoreSource: MediaStoreSource,
    private val thoroughTagPass: ThoroughTagPass,
    private val indexer: LibraryIndexer,
    private val dao: LibraryDao,
) {

    sealed interface State {
        data object Idle : State
        data object Scanning : State

        /** Reading tags out of files, which is slow enough to be worth reporting. */
        data class ReadingTags(val done: Int, val total: Int) : State
        data class Complete(val trackCount: Int, val durationMs: Long) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val mutex = Mutex()

    /**
     * Indexes everything MediaStore reports.
     *
     * Tracks that MediaStore no longer lists are removed, so deletions made outside
     * the app do not leave dead rows behind.
     */
    suspend fun scan(minimumDurationMs: Long = 0L, thorough: Boolean = false): State = mutex.withLock {
        _state.value = State.Scanning
        val startedAt = System.currentTimeMillis()

        val result = runCatching {
            withContext(Dispatchers.IO) {
                val scanned = mediaStoreSource.readTracks(minimumDurationMs)

                // The thorough pass opens every file, so it is opt-in and runs after
                // the fast pass has already produced a usable library.
                val enriched = if (thorough) {
                    thoroughTagPass.enrich(scanned) { done, total ->
                        _state.value = State.ReadingTags(done, total)
                    }
                } else {
                    scanned
                }

                indexer.index(enriched)
                pruneMissing(enriched)
                dao.trackCount()
            }
        }

        val next = result.fold(
            onSuccess = { count -> State.Complete(count, System.currentTimeMillis() - startedAt) },
            onFailure = { error -> State.Failed(error.message ?: error::class.java.simpleName) },
        )
        _state.value = next
        return next
    }

    /**
     * Drops indexed tracks that the source no longer reports.
     *
     * Deliberately not a "delete everything not in this list" query: that would wipe
     * rows from any other source the moment a MediaStore-only pass ran. Only tracks
     * carrying a MediaStore sourceId are considered.
     */
    private suspend fun pruneMissing(scanned: List<ScannedTrack>) {
        val seen = scanned.mapTo(HashSet()) { it.mediaUri }
        val stale = dao.mediaStoreTrackUris().filterNot { it in seen }
        if (stale.isNotEmpty()) {
            stale.chunked(SQLITE_VARIABLE_LIMIT).forEach { dao.deleteTracksByUri(it) }
        }
    }

    private companion object {
        /** SQLite caps bound variables per statement; stay well under it. */
        const val SQLITE_VARIABLE_LIMIT = 400
    }
}
