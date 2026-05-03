package com.macebox.crate.domain.model

data class UserSearchResult(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
)

data class Share(
    val id: Long,
    val targetUserId: String,
    val targetDisplayName: String?,
    val resourceType: String?,
    val resourceId: Long?,
    val createdAt: String?,
)

data class SharedWithMe(
    val albums: List<MediaItem>,
    val playlists: List<Playlist>,
)
