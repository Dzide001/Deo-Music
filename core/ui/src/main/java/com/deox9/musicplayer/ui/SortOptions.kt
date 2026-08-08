// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.ui

/**
 * Sort orders offered by the library lists.
 *
 * Shared because the header raises them and the feature modules apply them, and
 * those now live in different modules.
 */
enum class SongSortOption { Title, Artist, Album, Duration }

enum class AlbumSortOption { Name, Artist, TrackCount }

enum class CollectionSortOption { Name, TrackCount }

fun SongSortOption.label(): String = when (this) {
    SongSortOption.Title -> "Title"
    SongSortOption.Artist -> "Artist"
    SongSortOption.Album -> "Album"
    SongSortOption.Duration -> "Duration"
}

fun AlbumSortOption.label(): String = when (this) {
    AlbumSortOption.Name -> "Name"
    AlbumSortOption.Artist -> "Artist"
    AlbumSortOption.TrackCount -> "Track count"
}

fun CollectionSortOption.label(): String = when (this) {
    CollectionSortOption.Name -> "Name"
    CollectionSortOption.TrackCount -> "Track count"
}
