package com.macebox.crate.data.api.dto

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
)

@Serializable
data class CreatePlaylistRequest(
    val name: String,
)

@Serializable
data class AddPlaylistItemRequest(
    val mediaItemId: Long,
)
