// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import android.util.Log
import com.deox9.musicplayer.audio.ReplayGain
import com.deox9.musicplayer.database.dao.LibraryDao
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Measures tracks that no tag could supply a gain for.
 *
 * Reading a tag is free, and the thorough pass already does it — but only a minority
 * of files carry one, and a library assembled from downloads and messaging apps
 * carries almost none. Without this, ReplayGain is a setting that appears to work and
 * changes nothing, because every track falls back to the same value.
 *
 * Runs in batches rather than over the whole library at once. A full decode per track
 * is expensive, and a scan that has to finish before it is worth anything would be
 * abandoned halfway on any real library; measuring the most recently added tracks
 * first means the part the user is most likely to play is done soonest.
 */
@Singleton
class ReplayGainScanner @Inject constructor(
    private val dao: LibraryDao,
    private val analyzer: LoudnessAnalyzer,
) {

    data class Progress(val measured: Int, val skipped: Int, val remaining: Int)

    /**
     * Measures up to [limit] unmeasured tracks.
     *
     * A file that cannot be decoded is counted as skipped and left unmeasured, so the
     * next run will try it again. That is deliberate: the usual cause is a transient
     * one — storage not mounted, a file still being copied — and permanently marking
     * it would need a separate "tried and failed" state that nothing else would use.
     */
    suspend fun measureBatch(
        limit: Int = DEFAULT_BATCH,
        onProgress: (Progress) -> Unit = {},
    ): Progress {
        val targets = dao.tracksWithoutReplayGain(limit)
        Log.i(TAG, "batch start: ${targets.size} tracks to measure")
        // Captured once so the decode loop, which is not a coroutine, can still be
        // interrupted when the work is cancelled.
        val job = currentCoroutineContext()[Job]
        var measured = 0
        var skipped = 0

        for ((index, target) in targets.withIndex()) {
            currentCoroutineContext().ensureActive()

            val result = analyzer.measure(target.mediaUri) { job?.isActive == false }

            val lufs = result?.integratedLufs
            if (result != null && lufs != null && ReplayGain.isMeasurementUsable(result)) {
                dao.setMeasuredReplayGain(
                    trackId = target.id,
                    gainDb = ReplayGain.gainDbFor(lufs).toFloat(),
                    peak = result.samplePeak.toFloat(),
                )
                measured++
            } else {
                Log.w(TAG, "could not measure ${target.mediaUri}")
                skipped++
            }

            onProgress(Progress(measured, skipped, targets.size - index - 1))
        }

        Log.i(TAG, "batch done: measured=$measured skipped=$skipped")
        return Progress(measured, skipped, remaining = 0)
    }

    /** How many tracks are still waiting, for a settings screen to report. */
    suspend fun remainingCount(limit: Int = COUNT_CEILING): Int =
        dao.tracksWithoutReplayGain(limit).size

    private companion object {
        const val TAG = "ReplayGainScan"

        /**
         * Small enough that a batch finishes inside a normal WorkManager slot, and
         * large enough that the library gets measured over a few sessions.
         */
        const val DEFAULT_BATCH = 50

        /** Counting is itself a query, so it stops well before "all of them". */
        const val COUNT_CEILING = 1000
    }
}
