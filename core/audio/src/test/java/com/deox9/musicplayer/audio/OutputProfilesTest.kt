// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputProfilesTest {

    private val headphones = OutputRoute(OutputRouteType.Bluetooth, "WH-1000XM4")
    private val speaker = OutputRoute.Speaker
    private val band = EqBand(EqBandType.Peaking, 3_000.0, gainDb = 4.0)

    // ---- route identity -----------------------------------------------------

    /**
     * A profile is filed under this, so it has to survive the device being
     * unplugged and reconnected — which is exactly what AudioDeviceInfo.getId does
     * not do.
     */
    @Test
    fun `a route key is the same for the same device twice`() {
        val first = OutputRoute(OutputRouteType.Bluetooth, "WH-1000XM4")
        val second = OutputRoute(OutputRouteType.Bluetooth, "WH-1000XM4")

        assertEquals(first.key, second.key)
    }

    @Test
    fun `different devices of the same kind get different keys`() {
        val other = OutputRoute(OutputRouteType.Bluetooth, "Car")

        assertNotEquals(headphones.key, other.key)
    }

    @Test
    fun `the same name on different kinds of output is not the same route`() {
        val bluetooth = OutputRoute(OutputRouteType.Bluetooth, "Studio")
        val usb = OutputRoute(OutputRouteType.Usb, "Studio")

        assertNotEquals(bluetooth.key, usb.key)
    }

    /** An unnamed device still needs a key, or it cannot hold a profile at all. */
    @Test
    fun `a device with no name falls back to its kind`() {
        val unnamed = OutputRoute(OutputRouteType.Usb, "")

        assertEquals("Usb", unnamed.key)
        assertEquals("USB audio", unnamed.label)
    }

    @Test
    fun `surrounding whitespace does not create a second profile`() {
        val padded = OutputRoute(OutputRouteType.Bluetooth, "  WH-1000XM4  ")

        assertEquals(headphones.key, padded.key)
        assertEquals("WH-1000XM4", padded.label)
    }

    @Test
    fun `a named device is labelled by its name`() {
        assertEquals("WH-1000XM4", headphones.label)
        assertEquals("Phone speaker", speaker.label)
    }

    // ---- which route is active ---------------------------------------------

    /**
     * Plugging something in is a deliberate act and the speaker should lose to it.
     * Getting this backwards would apply the headphone correction to the speaker.
     */
    @Test
    fun `anything connected beats the speaker`() {
        listOf(
            OutputRouteType.WiredHeadphones,
            OutputRouteType.Bluetooth,
            OutputRouteType.Usb,
            OutputRouteType.Hdmi,
        ).forEach { type ->
            val active = OutputRoute.activeAmong(listOf(speaker, OutputRoute(type)))

            assertEquals(type, active.type)
        }
    }

    @Test
    fun `a USB DAC outranks everything else`() {
        val active = OutputRoute.activeAmong(
            listOf(speaker, OutputRoute(OutputRouteType.Bluetooth), OutputRoute(OutputRouteType.Usb)),
        )

        assertEquals(OutputRouteType.Usb, active.type)
    }

    @Test
    fun `wired headphones outrank bluetooth`() {
        val active = OutputRoute.activeAmong(
            listOf(OutputRoute(OutputRouteType.Bluetooth), OutputRoute(OutputRouteType.WiredHeadphones)),
        )

        assertEquals(OutputRouteType.WiredHeadphones, active.type)
    }

    /** Detection failing must not leave the chain with no route at all. */
    @Test
    fun `nothing connected falls back to the speaker`() {
        assertEquals(OutputRoute.Speaker, OutputRoute.activeAmong(emptyList()))
    }

    /** The same inputs must give the same answer, or the profile would flicker. */
    @Test
    fun `two devices of equal rank resolve the same way every time`() {
        val a = OutputRoute(OutputRouteType.Bluetooth, "A")
        val b = OutputRoute(OutputRouteType.Bluetooth, "B")

        repeat(10) {
            assertEquals(a, OutputRoute.activeAmong(listOf(a, b)))
        }
    }

    // ---- choosing a profile -------------------------------------------------

    @Test
    fun `a saved profile is found for its route`() {
        val profiles = OutputProfiles(enabled = true)
            .with(OutputProfile(headphones.key, bands = listOf(band)))

        assertEquals(listOf(band), profiles.forRoute(headphones)?.bands)
    }

    @Test
    fun `a route with no profile yields nothing rather than an empty one`() {
        val profiles = OutputProfiles(enabled = true)
            .with(OutputProfile(headphones.key, bands = listOf(band)))

        assertNull(profiles.forRoute(speaker))
    }

    /**
     * The distinction the null return exists for: a profile deliberately saved flat
     * must stay flat, not fall back to whatever the global equaliser is set to.
     */
    @Test
    fun `a profile saved with no bands is not the same as no profile`() {
        val profiles = OutputProfiles(enabled = true).with(OutputProfile(speaker.key))

        val resolved = resolveChainConfig(
            gainDb = 0.0,
            defaultBands = listOf(band),
            defaultLimiterEnabled = true,
            profiles = profiles,
            route = speaker,
        )

        assertTrue(resolved.bands.isEmpty())
    }

    @Test
    fun `profiles are ignored while the feature is off`() {
        val profiles = OutputProfiles(enabled = false)
            .with(OutputProfile(headphones.key, bands = listOf(band)))

        assertNull(profiles.forRoute(headphones))
    }

    @Test
    fun `saving a profile for a route replaces the previous one`() {
        val replacement = EqBand(EqBandType.LowShelf, 100.0, gainDb = -3.0)

        val profiles = OutputProfiles(enabled = true)
            .with(OutputProfile(headphones.key, bands = listOf(band)))
            .with(OutputProfile(headphones.key, bands = listOf(replacement)))

        assertEquals(1, profiles.profiles.size)
        assertEquals(listOf(replacement), profiles.forRoute(headphones)?.bands)
    }

    @Test
    fun `a deleted profile stops being found`() {
        val profiles = OutputProfiles(enabled = true)
            .with(OutputProfile(headphones.key, bands = listOf(band)))
            .without(headphones.key)

        assertNull(profiles.forRoute(headphones))
        assertFalse(profiles.has(headphones))
    }

    // ---- resolving the chain ------------------------------------------------

    @Test
    fun `the defaults apply when no profile matches`() {
        val config = resolveChainConfig(
            gainDb = -2.0,
            defaultBands = listOf(band),
            defaultLimiterEnabled = true,
            profiles = OutputProfiles(enabled = true),
            route = headphones,
        )

        assertEquals(listOf(band), config.bands)
        assertTrue(config.limiterEnabled)
    }

    @Test
    fun `a matching profile replaces the default bands rather than adding to them`() {
        val profileBand = EqBand(EqBandType.HighShelf, 8_000.0, gainDb = 2.0)
        val profiles = OutputProfiles(enabled = true)
            .with(OutputProfile(headphones.key, bands = listOf(profileBand), limiterEnabled = false))

        val config = resolveChainConfig(
            gainDb = 0.0,
            defaultBands = listOf(band),
            defaultLimiterEnabled = true,
            profiles = profiles,
            route = headphones,
        )

        assertEquals(listOf(profileBand), config.bands)
        assertFalse(config.limiterEnabled)
    }

    /**
     * ReplayGain is a property of the recording and identical on every output.
     * Per-route gain would mean the same track playing at different levels
     * depending on what was plugged in, which is the opposite of the point.
     */
    @Test
    fun `gain is not affected by which output is in use`() {
        val profiles = OutputProfiles(enabled = true)
            .with(OutputProfile(headphones.key, bands = listOf(band)))

        val onHeadphones = resolveChainConfig(0.0, emptyList(), true, profiles, headphones)
        val onSpeaker = resolveChainConfig(0.0, emptyList(), true, profiles, speaker)

        assertEquals(-4.5, resolveChainConfig(-4.5, emptyList(), true, profiles, headphones).gainDb, 0.0)
        assertEquals(onSpeaker.gainDb, onHeadphones.gainDb, 0.0)
    }
}
