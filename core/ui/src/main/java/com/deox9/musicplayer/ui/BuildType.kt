// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.ui

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * Whether this is a debuggable build.
 *
 * Read from the application info rather than a BuildConfig constant so it works
 * from any module without each one generating its own BuildConfig.
 */
fun isDebugBuild(context: Context): Boolean =
    (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
