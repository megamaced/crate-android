package com.megamaced.crate.domain.model

data class Playlist(
    val id: Long,
    val name: String,
    val userId: String?,
    val items: List<MediaItem>,
    val itemCount: Int = 0,
    val createdAt: String?,
    val updatedAt: String?,
    // Set when the playlist is visible via a read/write share; null/false for
    // own playlists. Not persisted to Room — carried from the network response.
    val canWrite: Boolean = false,
)
