// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

/**
 * The kind of thing the audio is coming out of.
 *
 * Kept coarse on purpose. The point of the type is to say something useful when
 * there is no product name to show and to order competing routes; distinguishing
 * every USB subtype would not change either.
 */
enum class OutputRouteType {
    Speaker,
    WiredHeadphones,
    Bluetooth,
    Usb,
    Hdmi,
    Other,
    ;

    /**
     * Which route wins when several are connected.
     *
     * Android decides this itself and does not offer the answer directly, so this is
     * the usual precedence: anything the listener deliberately plugged in or paired
     * beats the speaker, and a USB DAC — the most deliberate choice of all — beats
     * the rest. Higher wins.
     */
    val precedence: Int
        get() = when (this) {
            Usb -> 5
            WiredHeadphones -> 4
            Bluetooth -> 3
            Hdmi -> 2
            Other -> 1
            Speaker -> 0
        }

    val defaultName: String
        get() = when (this) {
            Speaker -> "Phone speaker"
            WiredHeadphones -> "Wired headphones"
            Bluetooth -> "Bluetooth"
            Usb -> "USB audio"
            Hdmi -> "HDMI"
            Other -> "Audio output"
        }
}

/**
 * Where the audio is going right now.
 *
 * [key] is what a saved profile is filed under, so it has to survive the device
 * being unplugged and reconnected. `AudioDeviceInfo.getId()` does not — it is
 * assigned per connection — and the Bluetooth MAC address would, but reading it
 * costs a runtime permission that this app has no other reason to hold. Type plus
 * product name is what is left: stable across reconnects, and wrong only if someone
 * owns two different headphones that report the same name.
 */
data class OutputRoute(
    val type: OutputRouteType,
    val productName: String = "",
) {
    val key: String
        get() = if (productName.isBlank()) type.name else "${type.name}:${productName.trim()}"

    /** What to call it on screen. */
    val label: String
        get() = productName.trim().ifBlank { type.defaultName }

    companion object {
        /** Where audio goes when nothing else is connected, and the fallback if detection fails. */
        val Speaker = OutputRoute(OutputRouteType.Speaker)

        /**
         * Picks the route audio is actually going to from everything connected.
         *
         * Ties break towards the earlier entry, which is the order the system
         * reported them in, so the result does not change between identical calls.
         */
        fun activeAmong(connected: List<OutputRoute>): OutputRoute =
            connected.maxByOrNull { it.type.precedence } ?: Speaker
    }
}

/**
 * What the DSP should do when audio is going to a particular output.
 *
 * The reason this exists: a correction that makes a pair of headphones sound right
 * is wrong for the phone's speaker, and both are wrong for a car. Without profiles
 * the listener either re-dials the equaliser every time they plug something in, or
 * gives up and leaves it flat.
 */
data class OutputProfile(
    val routeKey: String,
    val bands: List<EqBand> = emptyList(),
    val limiterEnabled: Boolean = true,
)

/**
 * The saved profiles, and the rule for choosing one.
 *
 * A profile replaces the default settings rather than adding to them. Additive was
 * the alternative and it makes the equaliser impossible to reason about: the curve
 * on screen would not be the curve being applied, and the listener would be tuning
 * against a correction they cannot see.
 */
data class OutputProfiles(
    val profiles: Map<String, OutputProfile> = emptyMap(),
    val enabled: Boolean = false,
) {
    /**
     * The bands and limiter setting to use for [route], or null to use the defaults.
     *
     * Null rather than an empty profile, because "no profile saved for this output"
     * and "a profile saved with no bands" are different: the second is a deliberate
     * flat setting for that output and must not fall back to the global equaliser.
     */
    fun forRoute(route: OutputRoute): OutputProfile? =
        if (!enabled) null else profiles[route.key]

    fun with(profile: OutputProfile): OutputProfiles =
        copy(profiles = profiles + (profile.routeKey to profile))

    fun without(routeKey: String): OutputProfiles =
        copy(profiles = profiles - routeKey)

    fun has(route: OutputRoute): Boolean = profiles.containsKey(route.key)
}

/**
 * Settles what the chain should be set to, given the defaults and the current route.
 *
 * One function so the precedence is stated once. Spread across the service and the
 * view models it would be three nearly-identical decisions that drift apart.
 */
fun resolveChainConfig(
    gainDb: Double,
    defaultBands: List<EqBand>,
    defaultLimiterEnabled: Boolean,
    profiles: OutputProfiles,
    route: OutputRoute,
): AudioChainConfig {
    val profile = profiles.forRoute(route)
    return AudioChainConfig(
        // Gain is deliberately not part of a profile. It is ReplayGain — a property
        // of the recording, the same on every output — and making it per-route would
        // mean the same track played at different levels depending on what was
        // plugged in, which is the opposite of what ReplayGain is for.
        gainDb = gainDb,
        bands = profile?.bands ?: defaultBands,
        limiterEnabled = profile?.limiterEnabled ?: defaultLimiterEnabled,
    )
}
