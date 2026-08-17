// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.deox9.musicplayer.library.Popularimeter
import dagger.hilt.android.qualifiers.ApplicationContext
import ealvatag.audio.AudioFileIO
import ealvatag.tag.FieldKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a star rating into the file itself.
 *
 * This is the first thing in the app that modifies someone's music, which is worth
 * saying plainly rather than burying: everything else reads. It exists because a
 * rating kept only in this app's database is a rating that dies with the app, and
 * the whole point of using POPM is that other software can read it.
 *
 * Only the rating frame is touched. eAlvaTag rewrites the tag in place and leaves
 * every other frame as it found it, so a file with artwork, lyrics and ReplayGain
 * keeps all of them.
 */
@Singleton
class RatingWriter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** What happened, so the UI can say something true rather than assume success. */
    sealed interface Result {
        data object Written : Result

        /**
         * The system will grant write access if the user agrees.
         *
         * Scoped storage refuses writes to media this app did not create, and the
         * documented way through is to ask: the system shows its own dialog naming
         * the file, and a grant makes both the content URI and the path writable.
         */
        data class NeedsConsent(val request: IntentSender) : Result

        /** Not writable and no way to ask — below API 30, or a read-only card. */
        data object NotPermitted : Result

        /** No path, no file, or a container whose tags this cannot write. */
        data class Failed(val reason: String) : Result
    }

    suspend fun write(
        filePath: String?,
        mediaUri: String?,
        stars: Int,
    ): Result = withContext(Dispatchers.IO) {
        val path = filePath?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.Failed("no file path")
        val file = File(path)
        if (!file.isFile) return@withContext Result.Failed("file is missing")

        // Checked before attempting, so the common scoped-storage refusal is reported
        // as what it is rather than as a corrupt-file error from deep inside the tag
        // library.
        if (!file.canWrite()) return@withContext consentFor(mediaUri)

        runCatching {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag.orNull() ?: audioFile.setNewDefaultTag()
            val value = Popularimeter.format(stars)
            if (value == null) {
                // Clearing rather than writing zero: zero is a rating in some readers
                // and "no opinion" in others, and the frame's absence is unambiguous.
                runCatching { tag.deleteField(FieldKey.RATING) }
            } else {
                tag.setField(FieldKey.RATING, value)
            }
            audioFile.save()
        }.fold(
            onSuccess = { Result.Written },
            onFailure = { error ->
                // A permission failure can still surface from inside the library on
                // some devices, so it is mapped here too rather than reported as a
                // mystery.
                if (error is SecurityException || error.message?.contains("EACCES") == true) {
                    consentFor(mediaUri)
                } else {
                    Result.Failed(error.message ?: error::class.java.simpleName)
                }
            },
        )
    }

    /**
     * Asks the system for permission to write this one file.
     *
     * Only from API 30. Below that the route is WRITE_EXTERNAL_STORAGE, a
     * whole-library permission this app does not ask for and should not start asking
     * for to set a star rating.
     */
    private fun consentFor(mediaUri: String?): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Result.NotPermitted
        val uri = mediaUri?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return Result.NotPermitted
        return runCatching {
            Result.NeedsConsent(
                MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender,
            )
        }.getOrDefault(Result.NotPermitted)
    }
}
