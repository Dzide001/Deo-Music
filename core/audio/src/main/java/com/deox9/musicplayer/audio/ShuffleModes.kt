// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

/**
 * What "shuffle" means, which is not one thing.
 *
 * Shuffling every track apart is right for a playlist of singles and wrong for an
 * album collection, where it scatters a record someone chose to hear in order. The
 * grouped modes keep each album or folder intact and shuffle the groups instead —
 * which is how most people actually listen to a large library.
 */
enum class ShuffleMode {
    /** Play in the order given. */
    Off,

    /** Every track independently. */
    Tracks,

    /** Albums in a random order, each album played through in its own order. */
    Albums,

    /** The same, grouped by the folder the files sit in. */
    Folders,
    ;

    val label: String
        get() = when (this) {
            Off -> "Off"
            Tracks -> "Tracks"
            Albums -> "Albums"
            Folders -> "Folders"
        }
}

/** The minimum a queue item has to expose to be shuffled. */
interface Shuffleable {
    val groupAlbum: String
    val groupFolder: String
}

/**
 * Reorders a queue.
 *
 * [random] is passed in rather than created here, so a test can pin the order and a
 * caller can reshuffle differently each time. Nothing else about the result is
 * random: the grouped modes are deterministic given the same generator.
 */
fun <T : Shuffleable> shuffleQueue(
    items: List<T>,
    mode: ShuffleMode,
    random: kotlin.random.Random,
): List<T> = when (mode) {
    ShuffleMode.Off -> items
    ShuffleMode.Tracks -> items.shuffled(random)
    ShuffleMode.Albums -> shuffleGroups(items, random) { it.groupAlbum }
    ShuffleMode.Folders -> shuffleGroups(items, random) { it.groupFolder }
}

/**
 * Shuffles the groups, keeping each group's own order.
 *
 * Grouping preserves first-appearance order before shuffling, so the result depends
 * only on the generator and not on the hashing of whatever the key happens to be —
 * otherwise the same seed would give different answers between runs.
 */
private fun <T> shuffleGroups(
    items: List<T>,
    random: kotlin.random.Random,
    key: (T) -> String,
): List<T> {
    if (items.isEmpty()) return items
    val groups = LinkedHashMap<String, MutableList<T>>()
    items.forEach { groups.getOrPut(key(it)) { mutableListOf() }.add(it) }
    return groups.keys.toList().shuffled(random).flatMap { groups.getValue(it) }
}

/**
 * A queue that plays everything once before repeating anything.
 *
 * True random is what people ask for and rarely what they want: over a few hundred
 * tracks it will repeat one before playing others at all, and that reads as the
 * shuffle being broken. This walks a shuffled order and reshuffles when it runs out,
 * so nothing repeats within a pass.
 */
class ShuffleSequence<T : Shuffleable>(
    private val items: List<T>,
    private val mode: ShuffleMode,
    private val random: kotlin.random.Random,
) {
    private var order: List<T> = shuffleQueue(items, mode, random)
    private var cursor: Int = 0

    /** How many are left before the order is exhausted and reshuffled. */
    val remaining: Int get() = (order.size - cursor).coerceAtLeast(0)

    /**
     * The next track, reshuffling once the pass is done.
     *
     * The reshuffle avoids starting the new pass with the track that just played,
     * which is the one repeat a listener always notices.
     */
    fun next(): T? {
        if (items.isEmpty()) return null
        if (cursor >= order.size) {
            val previous = order.lastOrNull()
            order = reshuffledAvoiding(previous)
            cursor = 0
        }
        return order.getOrNull(cursor++)
    }

    private fun reshuffledAvoiding(previous: T?): List<T> {
        if (items.size < 2 || previous == null) return shuffleQueue(items, mode, random)
        repeat(RESHUFFLE_ATTEMPTS) {
            val candidate = shuffleQueue(items, mode, random)
            if (candidate.firstOrNull() != previous) return candidate
        }
        // Deliberately gives up rather than looping: with two tracks, half of all
        // orders start with the one that just played, and refusing them all would
        // spin.
        return shuffleQueue(items, mode, random)
    }

    private companion object {
        const val RESHUFFLE_ATTEMPTS = 8
    }
}
