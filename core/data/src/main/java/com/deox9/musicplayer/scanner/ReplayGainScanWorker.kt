// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Measures loudness in the background, a batch at a time.
 *
 * Constrained to run only when the battery is not low: a full decode per track is the
 * most expensive thing this app does, and nobody wants their remaining 8% spent on
 * normalising an album they are not listening to.
 */
@HiltWorker
class ReplayGainScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scanner: ReplayGainScanner,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val progress = scanner.measureBatch()

        // Re-enqueue while there is more to do, rather than looping inside one job.
        // A worker that runs until the whole library is measured is a worker the
        // system will eventually kill, losing the batch it was in the middle of.
        //
        // Only when something was actually measured: if a whole batch failed to
        // decode, re-enqueueing would retry the same undecodable files forever.
        if (progress.measured > 0 && scanner.remainingCount(limit = 1) > 0) {
            enqueue(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "replaygain-scan"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<ReplayGainScanWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
