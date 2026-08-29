// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

/**
 * Counts the times the sink rebuilds its audio path.
 *
 * Inactive on purpose: Media3 configures and flushes every processor but only routes
 * audio through the active ones, so this reports what happened without being part of
 * what happened. That is what lets the same instrument sit in both the chain under
 * test and the bare control chain and mean the same thing in each.
 */
@OptIn(UnstableApi::class)
class RecordingAudioProcessor : BaseAudioProcessor() {

    private val configures = AtomicInteger()
    private val flushes = AtomicInteger()

    val configureCount: Int get() = configures.get()
    val flushCount: Int get() = flushes.get()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        configures.incrementAndGet()
        return inputAudioFormat
    }

    override fun isActive(): Boolean = false

    override fun queueInput(inputBuffer: ByteBuffer) = Unit

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        flushes.incrementAndGet()
    }
}
