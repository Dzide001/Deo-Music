// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

/** A track as a saved queue remembers it. */
data class SavedQueueTrack(
    val uri: String,
    val title: String = "",
    val artist: String = "",
)

/**
 * A queue that keeps its place while another one plays.
 *
 * The feature this exists for: pausing an album halfway through, listening to
 * something else entirely, and coming back to find the album still on track seven at
 * 2:14 rather than at the beginning. Most players have one queue, so the second
 * thing you play destroys the first.
 */
data class SavedQueue(
    val id: String,
    val name: String,
    val tracks: List<SavedQueueTrack> = emptyList(),
    /** Where playback had reached, so switching back resumes rather than restarts. */
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    val updatedAtMs: Long = 0L,
) {
    val isEmpty: Boolean get() = tracks.isEmpty()

    /** The index, clamped — a saved index can outlive the track it pointed at. */
    val safeIndex: Int
        get() = currentIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))

    val currentTrack: SavedQueueTrack?
        get() = tracks.getOrNull(safeIndex)
}

/**
 * All the saved queues, and which one is playing.
 *
 * Kept as a value rather than a mutable store so the rules — how many, what happens
 * to the active one, what a duplicate name does — are testable without a database
 * or a player.
 */
data class SavedQueues(
    val queues: List<SavedQueue> = emptyList(),
    val activeId: String? = null,
) {
    val active: SavedQueue? get() = queues.firstOrNull { it.id == activeId }

    /**
     * Saves or updates a queue, keeping the list newest-first.
     *
     * Newest-first because the list is a switcher, not an archive: the queue you
     * want next is almost always one you touched recently.
     */
    fun save(queue: SavedQueue): SavedQueues {
        val others = queues.filterNot { it.id == queue.id }
        val ordered = (listOf(queue) + others).sortedByDescending { it.updatedAtMs }

        // The queue being played is never evicted, however old it is. It is the one
        // someone would notice losing — audio would carry on from a queue that no
        // longer exists — and "least recently updated" is exactly wrong for a queue
        // that has been playing untouched for an hour.
        val protectedIds = setOfNotNull(activeId, queue.id)
        val trimmed = if (ordered.size <= MAX_QUEUES) {
            ordered
        } else {
            val kept = ordered.filter { it.id in protectedIds }
            val rest = ordered.filterNot { it.id in protectedIds }
            (kept + rest.take((MAX_QUEUES - kept.size).coerceAtLeast(0)))
                .sortedByDescending { it.updatedAtMs }
        }

        return copy(
            queues = trimmed,
            activeId = activeId ?: queue.id,
        )
    }

    fun remove(id: String): SavedQueues {
        val remaining = queues.filterNot { it.id == id }
        return copy(
            queues = remaining,
            activeId = if (id == activeId) remaining.firstOrNull()?.id else activeId,
        )
    }

    fun rename(id: String, name: String): SavedQueues {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return this
        return copy(queues = queues.map { if (it.id == id) it.copy(name = cleaned) else it })
    }

    fun activate(id: String): SavedQueues =
        if (queues.any { it.id == id }) copy(activeId = id) else this

    /**
     * A name that is not already taken.
     *
     * Duplicates are allowed rather than refused — two queues may genuinely be
     * "Album" — but the default name for a new one counts up so a switcher does not
     * fill with identical entries.
     */
    fun suggestName(base: String = DEFAULT_NAME): String {
        val taken = queues.map { it.name }.toSet()
        if (base !in taken) return base
        var n = 2
        while ("$base $n" in taken) n++
        return "$base $n"
    }

    companion object {
        /**
         * Enough to be useful, few enough to pick from without a search box.
         *
         * The switcher is a list someone reads at a glance; past about this many it
         * needs its own management screen, which is a different feature.
         */
        const val MAX_QUEUES = 10
        const val DEFAULT_NAME = "Queue"
    }
}
