// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.backup

/**
 * The settings half of a backup, as an interface.
 *
 * It lives here, in the leaf module both sides can see, because the two ends belong
 * to modules that must not depend on each other: the backup itself is assembled in
 * :core:data next to the database, while the preferences it needs live in
 * :core:datastore. Pointing either at the other would run the dependency the wrong
 * way — a library layer that knows about preference storage, or preference storage
 * that knows about the library.
 */
interface BackupSettingsBridge {
    suspend fun export(): Map<String, String>

    /** Returns how many keys were applied, so the report can say. */
    suspend fun restore(values: Map<String, String>): Int
}
