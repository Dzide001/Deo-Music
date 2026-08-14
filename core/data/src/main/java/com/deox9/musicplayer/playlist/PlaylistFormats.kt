// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.playlist

/**
 * One track in a playlist file.
 *
 * [durationSeconds] and [title] are what the file claims, not what the library
 * knows. They are kept because writing them back is what makes the file readable by
 * anything else, and because a file whose tracks cannot be found is still worth
 * showing by name.
 */
data class PlaylistFileEntry(
    val path: String,
    val durationSeconds: Int = -1,
    val title: String = "",
)

/**
 * Reading and writing the playlist formats everything else understands.
 *
 * M3U is barely a format — a list of paths, with optional `#EXTINF` lines carrying a
 * duration and a name. That looseness is the whole difficulty: every player writes a
 * slightly different dialect, and being strict about any of it means refusing files
 * that work everywhere else.
 */
object PlaylistFormats {

    private const val HEADER = "#EXTM3U"
    private const val INFO_PREFIX = "#EXTINF:"

    /**
     * Writes an M3U8 playlist.
     *
     * UTF-8 and the .m3u8 extension rather than M3U's Latin-1, because a library
     * with any non-Latin titles in it — which is most libraries — cannot be
     * represented otherwise, and the older readers that cannot cope are long gone.
     *
     * [relativeTo] writes paths relative to that directory where possible. Relative
     * paths survive the whole library moving, which is the common case for a
     * playlist file kept beside the music; absolute paths survive the playlist file
     * moving on its own. Neither is right in general, so it is the caller's choice.
     */
    fun writeM3u(entries: List<PlaylistFileEntry>, relativeTo: String? = null): String =
        buildString {
            appendLine(HEADER)
            entries.forEach { entry ->
                if (entry.durationSeconds >= 0 || entry.title.isNotBlank()) {
                    appendLine("$INFO_PREFIX${entry.durationSeconds},${entry.title}")
                }
                appendLine(relativise(entry.path, relativeTo))
            }
        }

    /**
     * Reads M3U, M3U8 or PLS, deciding which by looking at the content.
     *
     * By content rather than by extension, because a file's name is a claim and its
     * first line is evidence — and playlists get renamed.
     */
    fun parse(text: String, baseDirectory: String? = null): List<PlaylistFileEntry> =
        if (text.lineSequence().any { it.trim().equals("[playlist]", ignoreCase = true) }) {
            parsePls(text, baseDirectory)
        } else {
            parseM3u(text, baseDirectory)
        }

    fun parseM3u(text: String, baseDirectory: String? = null): List<PlaylistFileEntry> {
        val entries = mutableListOf<PlaylistFileEntry>()
        var pendingDuration = -1
        var pendingTitle = ""

        text.lineSequence().forEach { raw ->
            // Trimmed for the stray carriage returns a file written on Windows
            // carries, which otherwise become part of the last path segment.
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit

                line.startsWith(INFO_PREFIX) -> {
                    val payload = line.removePrefix(INFO_PREFIX)
                    // "#EXTINF:225,Artist - Title" — and a title may contain commas,
                    // so only the first one separates.
                    pendingDuration = payload.substringBefore(',').trim().toIntOrNull() ?: -1
                    pendingTitle = payload.substringAfter(',', missingDelimiterValue = "").trim()
                }

                // Every other directive is ignored rather than treated as a path.
                line.startsWith("#") -> Unit

                else -> {
                    entries += PlaylistFileEntry(
                        path = absolutise(line, baseDirectory),
                        durationSeconds = pendingDuration,
                        title = pendingTitle,
                    )
                    pendingDuration = -1
                    pendingTitle = ""
                }
            }
        }
        return entries
    }

    /**
     * Reads a PLS playlist.
     *
     * An INI file, where the entries are numbered rather than ordered by position,
     * so the numbers are what put them in order — a file listing File2 before File1
     * still means File1 first.
     */
    fun parsePls(text: String, baseDirectory: String? = null): List<PlaylistFileEntry> {
        val files = sortedMapOf<Int, String>()
        val titles = mutableMapOf<Int, String>()
        val lengths = mutableMapOf<Int, Int>()

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            val key = line.substringBefore('=', missingDelimiterValue = "").lowercase()
            val value = line.substringAfter('=', missingDelimiterValue = "").trim()
            if (key.isEmpty() || value.isEmpty()) return@forEach

            when {
                key.startsWith("file") -> key.drop(4).toIntOrNull()?.let { files[it] = value }
                key.startsWith("title") -> key.drop(5).toIntOrNull()?.let { titles[it] = value }
                key.startsWith("length") -> key.drop(6).toIntOrNull()?.let {
                    lengths[it] = value.toIntOrNull() ?: -1
                }
            }
        }

        return files.map { (index, path) ->
            PlaylistFileEntry(
                path = absolutise(path, baseDirectory),
                durationSeconds = lengths[index] ?: -1,
                title = titles[index].orEmpty(),
            )
        }
    }

    /**
     * Resolves a relative path against the playlist's own directory.
     *
     * Left alone when it is already absolute, or when it is a URL — a playlist may
     * point at a stream, and turning `http://…` into a path under the music folder
     * would be worse than leaving something this app cannot open.
     */
    private fun absolutise(path: String, baseDirectory: String?): String = when {
        baseDirectory.isNullOrBlank() -> path
        path.startsWith('/') -> path
        path.contains("://") -> path
        else -> "${baseDirectory.trimEnd('/')}/$path"
    }

    private fun relativise(path: String, relativeTo: String?): String {
        if (relativeTo.isNullOrBlank()) return path
        val base = relativeTo.trimEnd('/') + "/"
        return if (path.startsWith(base)) path.removePrefix(base) else path
    }
}
