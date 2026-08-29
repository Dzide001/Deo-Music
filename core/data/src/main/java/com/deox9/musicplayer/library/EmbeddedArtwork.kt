// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.library

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import ealvatag.audio.AudioFileIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cover art out of the file itself, for tracks MediaStore has none for.
 *
 * The roadmap's fallback chain is embedded art, then a `folder.jpg` beside the
 * track, then an online lookup. Only the first is implemented, and deliberately:
 *
 * Reading a `folder.jpg` on API 33+ needs READ_MEDIA_IMAGES, which grants an app
 * every photo on the phone. For a music player that is a wildly disproportionate
 * ask, and the sort of permission that turns up in an article about what music
 * players read. The audio permission the app already holds is enough to read the
 * audio file, and the art is usually inside it anyway.
 *
 * An online lookup is not implemented for the same reason online lyrics are off by
 * default: it would tell a stranger what is being played.
 *
 * Extracted art is cached as a file in the app's own cache directory, so the work
 * happens once per track rather than once per time the row scrolls past.
 */
@Singleton
class EmbeddedArtwork @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val directory: File by lazy {
        File(context.cacheDir, "embedded-art").apply { mkdirs() }
    }

    // Two rows for the same album can ask at once; without this they race to write
    // the same file and one reads it half-written.
    private val lock = Mutex()

    /**
     * A URI for the track's embedded cover, or null when it has none.
     *
     * [filePath] is the track's own path. Null when the file has no path — a
     * document-tree source, say — in which case there is nothing to read.
     */
    suspend fun artworkUriFor(filePath: String?): String? {
        val path = filePath?.takeIf { it.isNotBlank() } ?: return null
        val cached = File(directory, cacheName(path))

        if (cached.isFile && cached.length() > 0) return cached.toUri().toString()

        return lock.withLock {
            // Re-checked inside the lock: another caller may have written it while
            // this one was waiting.
            if (cached.isFile && cached.length() > 0) {
                return@withLock cached.toUri().toString()
            }
            extract(path, cached)
        }
    }

    private suspend fun extract(path: String, destination: File): String? =
        withContext(Dispatchers.IO) {
            val source = File(path)
            if (!source.isFile || !source.canRead()) return@withContext null

            // A corrupt header or an unsupported container costs this one cover, not
            // the screen it was being drawn on.
            val bytes = runCatching {
                AudioFileIO.read(source).tag.orNull()?.firstArtwork?.orNull()?.binaryData
            }.getOrNull()

            if (bytes == null || bytes.isEmpty()) {
                // A zero-length marker, so a track with no embedded art is not
                // re-parsed every time it appears. Cheap to write, and distinguishable
                // from a real cover by its length.
                runCatching { destination.createNewFile() }
                return@withContext null
            }

            runCatching {
                destination.writeBytes(bytes)
                destination.toUri().toString()
            }.getOrNull()
        }

    /**
     * A file name for a track's cached art.
     *
     * Hashed because a path contains characters a file name cannot, and because the
     * paths are long enough to exceed the name limit on their own.
     */
    private fun cacheName(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(path.toByteArray())
        return digest.joinToString("") { "%02x".format(it) } + ".img"
    }

    /** Drops the cache, for when the library is rescanned and files may have changed. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        directory.listFiles()?.forEach { it.delete() }
        Unit
    }
}
