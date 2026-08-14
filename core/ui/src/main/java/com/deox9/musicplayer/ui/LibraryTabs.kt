// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.ui

/**
 * The library's category tabs.
 *
 * Eight of them, which is more than fits on a phone screen at once — so the strip
 * scrolls, and the ones a particular person never opens cost them a swipe every
 * time. Which are shown, and in what order, is theirs to decide.
 */
enum class LibraryTab {
    Songs,
    Albums,
    Artists,
    Playlists,
    Folders,
    Genres,
    Suggested,
    Favourites,
    ;

    val label: String
        get() = when (this) {
            Songs -> "Songs"
            Albums -> "Albums"
            Artists -> "Artists"
            Playlists -> "Playlists"
            Folders -> "Folders"
            Genres -> "Genres"
            Suggested -> "Suggested"
            Favourites -> "Favourites"
        }
}

/**
 * Which tabs to show, in which order.
 *
 * Stored as an order plus a hidden set rather than simply a list of visible tabs,
 * so that hiding a tab and later showing it again puts it back where it was instead
 * of at the end.
 */
data class LibraryTabPreferences(
    val order: List<LibraryTab> = LibraryTab.entries,
    val hidden: Set<LibraryTab> = emptySet(),
) {
    /**
     * The tabs to draw.
     *
     * Never empty: hiding everything would leave the library with no content at all
     * and no way back, since the control for un-hiding is not on this screen.
     */
    val visible: List<LibraryTab>
        get() = resolvedOrder.filterNot { it in hidden }.ifEmpty { listOf(LibraryTab.Songs) }

    /**
     * The stored order, with any tab it does not mention appended.
     *
     * A version that adds a tab would otherwise never show it to anyone who had
     * already reordered theirs — their stored order simply would not contain it.
     */
    val resolvedOrder: List<LibraryTab>
        get() = order.distinct() + LibraryTab.entries.filterNot { it in order }

    fun toggleHidden(tab: LibraryTab): LibraryTabPreferences {
        val next = if (tab in hidden) hidden - tab else hidden + tab
        // Refuses the last one rather than silently allowing an empty strip.
        return if (LibraryTab.entries.none { it !in next }) this else copy(hidden = next)
    }

    /** Moves a tab one place, staying inside the list. */
    fun move(tab: LibraryTab, by: Int): LibraryTabPreferences {
        val current = resolvedOrder.toMutableList()
        val from = current.indexOf(tab)
        if (from < 0) return this
        val to = (from + by).coerceIn(0, current.size - 1)
        if (to == from) return this
        current.removeAt(from)
        current.add(to, tab)
        return copy(order = current)
    }
}
