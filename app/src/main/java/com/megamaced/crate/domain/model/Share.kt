package com.megamaced.crate.domain.model

data class UserSearchResult(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
)

data class Share(
    val id: Long,
    val ownerUserId: String?,
    val targetUserId: String,
    val targetDisplayName: String?,
    val resourceType: String?,
    val resourceId: Long?,
    val shareableCategory: String?,
    val createdAt: String?,
    // Access level granted to the target user. `canWrite` is the resolved
    // boolean; older servers report neither and default to read-only.
    val permission: String? = null,
    val canWrite: Boolean = false,
)

data class LibraryShare(
    val shareId: Long,
    val sharedByUser: String,
    val createdAt: String?,
    val canWrite: Boolean = false,
    val items: List<MediaItem>,
)

data class CategoryShare(
    val shareId: Long,
    val sharedByUser: String,
    val category: Category?,
    val createdAt: String?,
    val canWrite: Boolean = false,
    val items: List<MediaItem>,
)

data class SharedWithMe(
    val albums: List<MediaItem>,
    val playlists: List<Playlist>,
    val libraries: List<LibraryShare>,
    val categories: List<CategoryShare>,
)
