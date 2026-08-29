// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import javax.inject.Inject

/**
 * Enriches scanned tracks with tags read from the files themselves.
 *
 * This is the roadmap's "thorough" pass. It is much slower than reading MediaStore,
 * because it opens every file, so it is opt-in and runs after the fast pass has
 * already made the library usable.
 */
class ThoroughTagPass @Inject constructor(
    private val fileReader: EAlvaTagReader,
    private val retrieverReader: RetrieverTagReader,
) {

    fun enrich(tracks: List<ScannedTrack>, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<ScannedTrack> =
        tracks.mapIndexed { index, track ->
            onProgress(index + 1, tracks.size)
            track.mergedWith(readTags(track))
        }

    /**
     * Reads a file's tags, preferring the real parser.
     *
     * eAlvaTag is tried first because it is the only one that can see ReplayGain and
     * sort-order tags. The retriever fallback covers files with no usable filesystem
     * path, which scoped storage makes common.
     */
    private fun readTags(track: ScannedTrack): TrackTags {
        val fromFile = fileReader.read(track.filePath)
        if (!fromFile.isEmpty) return fromFile

        return retrieverReader.read(track.mediaUri)
    }
}
