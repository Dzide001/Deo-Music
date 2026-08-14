// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deox9.musicplayer.audio.OutputRoute
import com.deox9.musicplayer.audio.OutputRouteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The route monitor against the real device.
 *
 * The precedence rules are unit-tested on made-up routes. What only a device can
 * answer is whether the platform's output list maps onto them at all — the type
 * constants are a long list and mapping one wrongly would file a profile under the
 * wrong output, or silently drop the output the listener is actually using.
 */
@RunWith(AndroidJUnit4::class)
class OutputRouteMonitorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val monitor = OutputRouteMonitor(context)

    /** Every phone has a speaker, so detection returning nothing means it is broken. */
    @Test
    fun aRouteIsDetectedOnRealHardware() {
        monitor.refresh()

        val route = monitor.route.value

        assertNotNull(route)
        assertTrue("label was blank", route.label.isNotBlank())
        assertTrue("key was blank", route.key.isNotBlank())
    }

    /**
     * A profile is filed under the key, so two readings a moment apart producing
     * different keys would lose the profile every time anything was re-read.
     */
    @Test
    fun theSameHardwareGivesTheSameKeyTwice() {
        monitor.refresh()
        val first = monitor.route.value
        monitor.refresh()
        val second = monitor.route.value

        assertEquals(first.key, second.key)
    }

    /**
     * The built-in speaker is deliberately keyed by type alone: it reports the
     * phone's model as its product name, which would read like a separate device.
     */
    @Test
    fun theSpeakerIsKeyedByTypeAloneRatherThanByThePhoneModel() {
        val speaker = OutputRoute(OutputRouteType.Speaker)

        assertEquals("Speaker", speaker.key)
        assertFalse(speaker.key.contains("CPH"))
    }

    /**
     * Whatever the device reports has to land on a route this understands, or the
     * output in use could be dropped from the list and lose to the speaker.
     */
    @Test
    fun everyConnectedOutputIsEitherMappedOrDeliberatelyIgnored() {
        val manager = context.getSystemService(AudioManager::class.java)
        val devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        assertTrue("no outputs reported at all", devices.isNotEmpty())

        val ignorable = setOf(
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            AudioDeviceInfo.TYPE_TELEPHONY,
        )
        val unexplained = devices.filter { device ->
            device.type !in ignorable && !device.type.isKnownPlaybackOutput()
        }

        // Reported rather than merely counted, so a device type this does not know
        // about names itself instead of appearing as a number.
        assertTrue(
            "unmapped output types: ${unexplained.map { it.type to it.productName }}",
            unexplained.isEmpty(),
        )
    }

    private fun Int.isKnownPlaybackOutput(): Boolean = this in setOf(
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
    )
}
