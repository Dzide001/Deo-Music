// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.deox9.musicplayer.audio.OutputRoute
import com.deox9.musicplayer.audio.OutputRouteType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches where audio is going.
 *
 * Android does not offer "the output currently in use" as a question it will answer.
 * `AudioTrack.getRoutedDevice` comes closest but needs the track, which lives inside
 * the sink; `getCommunicationDevice` is about calls, not media. So this reads the
 * connected outputs and applies the same precedence the platform does — see
 * [OutputRouteType.precedence] — which is a deduction rather than a reading, and is
 * wrong only in cases where the user has overridden the route by hand.
 */
@Singleton
class OutputRouteMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val audioManager by lazy { context.getSystemService(AudioManager::class.java) }
    private val handler = Handler(Looper.getMainLooper())

    private val _route = MutableStateFlow(OutputRoute.Speaker)
    val route: StateFlow<OutputRoute> = _route.asStateFlow()

    private var callback: AudioDeviceCallback? = null

    fun start() {
        if (callback != null) return
        val manager = audioManager ?: return

        val deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
        }
        callback = deviceCallback
        manager.registerAudioDeviceCallback(deviceCallback, handler)
        refresh()
    }

    fun stop() {
        val manager = audioManager
        val deviceCallback = callback
        if (manager != null && deviceCallback != null) {
            manager.unregisterAudioDeviceCallback(deviceCallback)
        }
        callback = null
    }

    /** Re-reads the connected outputs and publishes the winner. */
    fun refresh() {
        val manager = audioManager ?: return
        val connected = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .mapNotNull { it.asOutputRoute() }
        _route.value = OutputRoute.activeAmong(connected)
    }

    /**
     * A platform device as a route, or null for outputs that are not somewhere a
     * listener is listening.
     *
     * Telephony and the earpiece are dropped: a profile for them would never be
     * used, and letting them into the list risks one outranking the real output.
     */
    private fun AudioDeviceInfo.asOutputRoute(): OutputRoute? {
        val routeType = when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
            -> OutputRouteType.Speaker

            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            -> OutputRouteType.WiredHeadphones

            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            -> OutputRouteType.Bluetooth

            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            -> OutputRouteType.Usb

            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            -> OutputRouteType.Hdmi

            // SCO is the mono voice channel used for calls; media goes over A2DP.
            // A phone with Bluetooth connected reports both, and SCO names itself
            // after the phone rather than the headset — so mapping it would add a
            // second Bluetooth route that ties with the real one on precedence and
            // can win, filing the headphones' profile under the phone's model.
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            AudioDeviceInfo.TYPE_TELEPHONY,
            -> return null

            else -> return null
        }

        // The built-in speaker reports the phone's own model as its product name,
        // which would file its profile under something that reads like a separate
        // device. Its type is the whole identity.
        val name = if (routeType == OutputRouteType.Speaker) "" else productName?.toString().orEmpty()
        return OutputRoute(routeType, name)
    }
}
