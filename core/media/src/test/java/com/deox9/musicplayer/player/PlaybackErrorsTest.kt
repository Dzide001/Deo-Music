// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorMessageTest {

    @Test
    fun `a missing file says so rather than blaming the format`() {
        assertEquals(
            "The file is missing — it may have been moved or deleted.",
            playbackErrorMessage(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND),
        )
    }

    @Test
    fun `a file that will not decode reports decoding`() {
        assertEquals(
            "The file could not be decoded.",
            playbackErrorMessage(PlaybackException.ERROR_CODE_DECODING_FAILED),
        )
    }

    @Test
    fun `a damaged container is distinguished from an unsupported one`() {
        assertNotEquals(
            playbackErrorMessage(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED),
            playbackErrorMessage(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED),
        )
    }

    @Test
    fun `the network codes share one message`() {
        val timeout = playbackErrorMessage(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
        assertEquals(
            playbackErrorMessage(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED),
            timeout,
        )
        assertEquals(playbackErrorMessage(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS), timeout)
    }

    /**
     * The point of the mapping. An unmapped code must still produce a sentence, not
     * a blank string or an ERROR_CODE_ constant name.
     */
    @Test
    fun `an unknown code still yields a readable sentence`() {
        val message = playbackErrorMessage(errorCode = -12345)

        assertEquals("The track could not be played.", message)
        assertTrue(message.endsWith("."))
        assertTrue("leaked an error code name", !message.contains("ERROR_CODE"))
    }

    @Test
    fun `every documented code is readable`() {
        val codes = listOf(
            PlaybackException.ERROR_CODE_UNSPECIFIED,
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        )

        codes.forEach { code ->
            val message = playbackErrorMessage(code)
            assertTrue("blank message for $code", message.isNotBlank())
            assertTrue("unterminated message for $code", message.endsWith("."))
        }
    }
}

class ErrorRecoveryTest {

    @Test
    fun `one bad file in a queue is stepped over`() {
        assertEquals(
            ErrorRecovery.AdvanceToNext,
            errorRecoveryFor(hasNextItem = true, consecutiveFailures = 1, queueSize = 20),
        )
    }

    @Test
    fun `the last item in the queue has nowhere to advance to`() {
        assertEquals(
            ErrorRecovery.StopAndReport,
            errorRecoveryFor(hasNextItem = false, consecutiveFailures = 1, queueSize = 20),
        )
    }

    @Test
    fun `a single unplayable track stops instead of retrying itself`() {
        assertEquals(
            ErrorRecovery.StopAndReport,
            errorRecoveryFor(hasNextItem = false, consecutiveFailures = 1, queueSize = 1),
        )
    }

    /**
     * A short queue where everything fails is walked exactly once. Round two would be
     * the same files failing the same way.
     */
    @Test
    fun `a short queue that fails throughout is walked once and then left alone`() {
        val queueSize = 3

        assertEquals(
            ErrorRecovery.AdvanceToNext,
            errorRecoveryFor(hasNextItem = true, consecutiveFailures = 1, queueSize = queueSize),
        )
        assertEquals(
            ErrorRecovery.AdvanceToNext,
            errorRecoveryFor(hasNextItem = true, consecutiveFailures = 2, queueSize = queueSize),
        )
        assertEquals(
            ErrorRecovery.StopAndReport,
            errorRecoveryFor(hasNextItem = true, consecutiveFailures = 3, queueSize = queueSize),
        )
    }

    /**
     * The case this bound exists for: an unmounted card or a revoked permission fails
     * every track, and a thousand-item queue should not fire a thousand failures.
     */
    @Test
    fun `a long queue that fails throughout gives up after the skip limit`() {
        val queueSize = 1_000

        assertEquals(
            ErrorRecovery.AdvanceToNext,
            errorRecoveryFor(
                hasNextItem = true,
                consecutiveFailures = MAX_CONSECUTIVE_SKIPS - 1,
                queueSize = queueSize,
            ),
        )
        assertEquals(
            ErrorRecovery.StopAndReport,
            errorRecoveryFor(
                hasNextItem = true,
                consecutiveFailures = MAX_CONSECUTIVE_SKIPS,
                queueSize = queueSize,
            ),
        )
    }

    /** The counter resets on any success, so an isolated failure always advances. */
    @Test
    fun `failures spread through a queue never exhaust the budget`() {
        repeat(50) {
            assertEquals(
                ErrorRecovery.AdvanceToNext,
                errorRecoveryFor(hasNextItem = true, consecutiveFailures = 1, queueSize = 50),
            )
        }
    }
}
