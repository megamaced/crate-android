package com.megamaced.crate.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistDto(
    val id: Long,
    val name: String,
    val userId: String? = null,
    val items: List<MediaItemDto>? = null,
    val itemCount: Int? = null,
    val coverId: Long? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    // Present only when the playlist arrives via a share. "read"/"readwrite"
    // plus the resolved boolean; absent (null) on own-collection payloads.
    val permission: String? = null,
    val canWrite: Boolean? = null,
)

@Serializable
data class CreatePlaylistRequest(
    val name: String,
)

@Serializable
data class AddPlaylistItemRequest(
    val mediaItemId: Long,
)
