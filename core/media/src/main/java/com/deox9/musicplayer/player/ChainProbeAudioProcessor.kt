// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Reports when the sink rebuilds its audio path, without touching the audio.
 *
 * A control for the gapless measurement. Removing the DSP processor also removes the
 * only instrument that reported configure and flush, so the comparison would be
 * between a signal and no signal rather than between two behaviours. This reports the
 * same two events while being excluded from the pipeline: `isActive` is false, so
 * Media3 configures and flushes it but never routes audio through it.
 */
@OptIn(UnstableApi::class)
class ChainProbeAudioProcessor : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        Log.i(TAG, "configure: ${inputAudioFormat.sampleRate} Hz, ${inputAudioFormat.channelCount} ch")
        return inputAudioFormat
    }

    /** Never in the path, so the signal chain is what it would be with no processor. */
    override fun isActive(): Boolean = false

    override fun queueInput(inputBuffer: ByteBuffer) = Unit

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        Log.i(TAG, "flush")
    }

    private companion object {
        const val TAG = "DeoChainProbe"
    }
}
