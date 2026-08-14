// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

import android.os.Bundle
import androidx.media3.common.PlaybackException

/**
 * Says why a track would not play, in words worth showing someone.
 *
 * [PlaybackException.errorCodeName] is the obvious alternative and reads
 * `ERROR_CODE_PARSING_CONTAINER_MALFORMED`, which tells a listener nothing. The codes
 * are grouped by what the listener can actually do about them: a missing file, a file
 * the app may not read, and a file that is simply broken are three different problems,
 * while the five ways an HTTP fetch can fail are one.
 */
fun playbackErrorMessage(errorCode: Int): String = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
        "The file is missing — it may have been moved or deleted."

    PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
        "This app is not allowed to read the file."

    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
    PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
        "The track could not be reached over the network."

    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ->
        "The file could not be read."

    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
        "The file is damaged."

    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
        "This format is not supported on this device."

    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
        "This device cannot decode a file of this quality."

    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED ->
        "The file could not be decoded."

    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ->
        "The audio output could not be opened."

    PlaybackException.ERROR_CODE_TIMEOUT ->
        "The track took too long to load."

    else ->
        "The track could not be played."
}

/** What to do with the queue once an item has failed. */
enum class ErrorRecovery {
    /** Step over the failed item and carry on. */
    AdvanceToNext,

    /** Stay put and let the failure stand, because advancing would not help. */
    StopAndReport,
}

/**
 * Decides whether a failed item is worth stepping over.
 *
 * One bad file should not end the queue, which is what stalling on it amounts to. A
 * queue where *everything* fails is a different situation — a revoked permission or an
 * unmounted card — and racing through it firing one failure per item helps nobody, so
 * the walk stops once it has been round the queue or hit [MAX_CONSECUTIVE_SKIPS],
 * whichever comes first.
 *
 * @param consecutiveFailures how many items have failed in a row, counting this one,
 *   so the first failure of a run is 1. Reset as soon as anything plays.
 */
fun errorRecoveryFor(
    hasNextItem: Boolean,
    consecutiveFailures: Int,
    queueSize: Int,
): ErrorRecovery {
    if (!hasNextItem) return ErrorRecovery.StopAndReport

    val attemptsAllowed = minOf(queueSize, MAX_CONSECUTIVE_SKIPS)
    return if (consecutiveFailures < attemptsAllowed) {
        ErrorRecovery.AdvanceToNext
    } else {
        ErrorRecovery.StopAndReport
    }
}

/** The most items to step over before concluding it is not one bad file. */
const val MAX_CONSECUTIVE_SKIPS = 10

/**
 * Carries a failure from the service to the UI through the session extras.
 *
 * The controller's own [androidx.media3.common.Player.Listener.onPlayerError] is the
 * tempting channel and is not reliable here: the service recovers from the error inside
 * its own listener callback, so the error flag can be cleared before the session next
 * flushes player state across to controllers, and the UI would see nothing. Extras are
 * a value the service sets deliberately, and they stay set.
 */
internal object PlaybackErrorExtras {
    private const val KEY_ID = "com.deox9.musicplayer.playback_error.id"
    private const val KEY_URI = "com.deox9.musicplayer.playback_error.uri"
    private const val KEY_TITLE = "com.deox9.musicplayer.playback_error.title"
    private const val KEY_MESSAGE = "com.deox9.musicplayer.playback_error.message"

    fun toBundle(error: PlaybackError): Bundle = Bundle().apply {
        putLong(KEY_ID, error.id)
        putString(KEY_URI, error.trackUri)
        putString(KEY_TITLE, error.trackTitle)
        putString(KEY_MESSAGE, error.message)
    }

    /** Null for the empty bundle the service publishes once a failed track plays. */
    fun fromBundle(extras: Bundle): PlaybackError? {
        val id = extras.getLong(KEY_ID, 0L)
        if (id <= 0L) return null

        return PlaybackError(
            trackUri = extras.getString(KEY_URI).orEmpty(),
            trackTitle = extras.getString(KEY_TITLE).orEmpty(),
            message = extras.getString(KEY_MESSAGE).orEmpty(),
            id = id,
        )
    }
}
