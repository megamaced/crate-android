package com.macebox.crate.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserSearchResultDto(
    val userId: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class ShareDto(
    val id: Long,
    val targetUserId: String,
    val targetDisplayName: String? = null,
    val resourceType: String? = null,
    val resourceId: Long? = null,
    val createdAt: String? = null,
)

@Serializable
data class ShareRequest(
    val targetUserId: String,
)

@Serializable
data class SharedWithMeDto(
    val albums: List<MediaItemDto> = emptyList(),
    val playlists: List<PlaylistDto> = emptyList(),
)
