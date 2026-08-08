// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.library

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.favouritesDataStore: DataStore<Preferences> by preferencesDataStore(name = "favourites_store")

class FavouritesRepository(
    private val context: Context
) {
    fun observe(): Flow<Set<String>> {
        return context.favouritesDataStore.data.map { prefs ->
            decodeSet(prefs[Keys.FAV_URIS_JSON] ?: "[]")
        }
    }

    suspend fun toggle(uri: String) {
        context.favouritesDataStore.edit { prefs ->
            val current = decodeSet(prefs[Keys.FAV_URIS_JSON] ?: "[]").toMutableSet()
            if (!current.add(uri)) {
                current.remove(uri)
            }
            prefs[Keys.FAV_URIS_JSON] = encodeSet(current)
        }
    }

    private fun decodeSet(raw: String): Set<String> {
        return try {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i)
                    if (item.isNotBlank()) add(item)
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun encodeSet(set: Set<String>): String {
        val arr = JSONArray()
        set.forEach { arr.put(it) }
        return arr.toString()
    }

    private object Keys {
        val FAV_URIS_JSON = stringPreferencesKey("favourite_uris_json")
    }
}
