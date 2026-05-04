package com.macebox.crate.ui.navigation

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
}
