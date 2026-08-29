// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

import com.deox9.musicplayer.audio.AudioChainConfig
import com.deox9.musicplayer.audio.DspStage
import com.deox9.musicplayer.audio.OutputRoute
import com.deox9.musicplayer.audio.SignalChain
import com.deox9.musicplayer.audio.SignalChainStages
import com.deox9.musicplayer.audio.StreamFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries the signal chain from the service, which knows it, to the UI, which shows it.
 *
 * A shared singleton rather than session extras: the two live in the same process, and
 * routing a description of the audio path through a Bundle and an IPC boundary would
 * be ceremony for no gain. It would matter if the session were ever driven from
 * another process, which nothing here does.
 *
 * Each part arrives when it becomes known — the source format at preparation, the
 * decoder once created, the output only when the sink has one — so updates are merged
 * into what is already there rather than replacing it.
 */
@Singleton
class SignalChainReporter @Inject constructor() {

    private val _chain = MutableStateFlow(SignalChain())
    val chain: StateFlow<SignalChain> = _chain.asStateFlow()

    fun setSource(format: StreamFormat?) = _chain.update { it.copy(source = format) }

    fun setDecoder(name: String?) = _chain.update { it.copy(decoderName = name) }

    fun setOutput(format: StreamFormat?) = _chain.update { it.copy(output = format) }

    /**
     * Records where the audio is going and whether a saved profile is shaping it.
     *
     * On the readout because the equaliser curve on screen is no longer necessarily
     * the one being applied once profiles are on — without this there is nothing to
     * tell a listener which of the two they are hearing.
     */
    fun setRoute(route: OutputRoute, usingProfile: Boolean) = _chain.update {
        it.copy(outputRoute = route.label, usingOutputProfile = usingProfile)
    }

    fun setStages(config: AudioChainConfig, limiterReductionDb: Double) =
        _chain.update { it.copy(stages = SignalChainStages.from(config, limiterReductionDb)) }

    fun setStages(stages: List<DspStage>) = _chain.update { it.copy(stages = stages) }

    /**
     * Nothing clears the stream fields on a track change, deliberately.
     *
     * Clearing them was the obvious thing and it was wrong: Media3 reuses a decoder
     * between items whose formats match, and a reused decoder never announces itself
     * again — so the name was wiped on every transition and never came back, leaving
     * the readout showing a dash for the rest of the queue. Each callback overwrites
     * its own field when there is something new to say, and a value that persists is
     * a value that is still true.
     */
}
