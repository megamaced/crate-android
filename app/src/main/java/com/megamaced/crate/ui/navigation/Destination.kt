package com.megamaced.crate.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Destination {
    // Top-level (bottom bar) destinations
    @Serializable
    data object Home : Destination

    @Serializable
    data object Collection : Destination

    @Serializable
    data object Playlists : Destination

    @Serializable
    data object Search : Destination

    @Serializable
    data object Settings : Destination

    // Secondary destinations
    @Serializable
    data object Login : Destination

    @Serializable
    data class Detail(
        val itemId: Long,
    ) : Destination

    @Serializable
    data class AddEdit(
        val itemId: Long? = null,
        val category: String? = null,
        val prefillJson: String? = null,
        // Uid of another user whose collection the new item should be created
        // in — set when adding into a read/write shared library or category.
        // Null (the default) creates in the caller's own collection. When set
        // together with a non-null [category] (a category share), the category
        // picker is locked to that category.
        val owner: String? = null,
    ) : Destination

    @Serializable
    data class Scan(
        val category: String? = null,
    ) : Destination

    @Serializable
    data class PlaylistDetail(
        val playlistId: Long,
    ) : Destination

    @Serializable
    data object SharedWithMe : Destination

    // A single shared category subpage (apiValue: music/film/book/game/comic).
    @Serializable
    data class SharedCategory(
        val category: String,
    ) : Destination
}
