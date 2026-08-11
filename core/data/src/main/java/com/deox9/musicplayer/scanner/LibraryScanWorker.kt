// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs the library scan off the UI, surviving the app being backgrounded.
 *
 * A scan over a large library is long enough that tying it to a composition or an
 * Activity scope would mean it silently dies when the user leaves the screen.
 */
@HiltWorker
class LibraryScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scanner: LibraryScanner,
    private val replayGainScanner: ReplayGainScanner,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val minimumDurationMs = inputData.getLong(KEY_MINIMUM_DURATION_MS, 0L)
        val thorough = inputData.getBoolean(KEY_THOROUGH, false)

        return when (scanner.scan(minimumDurationMs, thorough)) {
            is LibraryScanner.State.Complete -> {
                // A small batch inline, then the rest handed to its own worker.
                //
                // Inline because a separate worker is not reliably dispatched: on the
                // test device the ReplayGain job sits in JobScheduler as READY with
                // its constraints satisfied and is never run, while this worker starts
                // every time. Measuring a few tracks here means loudness data appears
                // at all on such a device, rather than only on ones whose scheduler is
                // less aggressive.
                replayGainScanner.measureBatch(limit = INLINE_MEASURE_BATCH)
                ReplayGainScanWorker.enqueue(applicationContext)
                Result.success()
            }
            // Retry rather than fail: a scan interrupted by the media store being
            // busy or storage being unmounted is worth another attempt.
            is LibraryScanner.State.Failed -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "library-scan"

        /**
         * Kept small: this runs inside the library scan, and a full decode per track
         * is the most expensive thing the app does.
         */
        private const val INLINE_MEASURE_BATCH = 10
        const val KEY_MINIMUM_DURATION_MS = "minimum_duration_ms"
        const val KEY_THOROUGH = "thorough"

        /**
         * Enqueues a scan, keeping any already running.
         *
         * KEEP rather than REPLACE so a burst of MediaStore change notifications
         * during a large file copy does not restart the scan repeatedly.
         */
        fun enqueue(context: Context, minimumDurationMs: Long = 0L, thorough: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<LibraryScanWorker>()
                .setInputData(
                    androidx.work.Data.Builder()
                        .putLong(KEY_MINIMUM_DURATION_MS, minimumDurationMs)
                        .putBoolean(KEY_THOROUGH, thorough)
                        .build(),
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
