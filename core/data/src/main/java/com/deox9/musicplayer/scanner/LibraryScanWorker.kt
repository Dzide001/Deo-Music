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
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val minimumDurationMs = inputData.getLong(KEY_MINIMUM_DURATION_MS, 0L)

        return when (scanner.scan(minimumDurationMs)) {
            is LibraryScanner.State.Complete -> Result.success()
            // Retry rather than fail: a scan interrupted by the media store being
            // busy or storage being unmounted is worth another attempt.
            is LibraryScanner.State.Failed -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "library-scan"
        const val KEY_MINIMUM_DURATION_MS = "minimum_duration_ms"

        /**
         * Enqueues a scan, keeping any already running.
         *
         * KEEP rather than REPLACE so a burst of MediaStore change notifications
         * during a large file copy does not restart the scan repeatedly.
         */
        fun enqueue(context: Context, minimumDurationMs: Long = 0L) {
            val request = OneTimeWorkRequestBuilder<LibraryScanWorker>()
                .setInputData(
                    androidx.work.Data.Builder()
                        .putLong(KEY_MINIMUM_DURATION_MS, minimumDurationMs)
                        .build(),
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
