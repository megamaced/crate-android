package com.megamaced.crate.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// NC sends `{uid, displayName}` from /api/v1/users/search — Kotlin property
// stays userId for ergonomics; @SerialName aligns the wire shape.
@Serializable
data class UserSearchResultDto(
    @SerialName("uid")
    val userId: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

// NC's CrateShare::jsonSerialize sends:
//   {id, ownerUserId, sharedWithUserId, shareableType, shareableId,
//    shareableCategory, createdAt}
// targetDisplayName is not in the NC payload; left here for forward-compat
// if we ever fold a user-search join into the response.
@Serializable
data class ShareDto(
    val id: Long,
    val ownerUserId: String? = null,
    @SerialName("sharedWithUserId")
    val targetUserId: String,
    val targetDisplayName: String? = null,
    @SerialName("shareableType")
    val resourceType: String? = null,
    @SerialName("shareableId")
    val resourceId: Long? = null,
    val shareableCategory: String? = null,
    val createdAt: String? = null,
    // "read" (default) or "readwrite"; `canWrite` is the resolved boolean the
    // backend derives from it. Older servers omit both — default to read-only.
    val permission: String? = null,
    val canWrite: Boolean? = null,
)

// NC's share controllers accept `{userId}` plus an optional access level in
// the request body. `permission` is "read" (default) or "readwrite".
@Serializable
data class ShareRequest(
    @SerialName("userId")
    val targetUserId: String,
    val permission: String = PERMISSION_READ,
) {
    companion object {
        const val PERMISSION_READ = "read"
        const val PERMISSION_READWRITE = "readwrite"
    }
}

// Wrapper records for a shared whole library / category — each carries the
// owner uid plus the resolved items so the View can render groupings
// without per-item GETs.
@Serializable
data class LibraryShareDto(
    val shareId: Long,
    val sharedByUser: String,
    val createdAt: String? = null,
    // Access level for the whole scope — sits on the wrapper, not per item.
    val permission: String? = null,
    val canWrite: Boolean? = null,
    val items: List<MediaItemDto> = emptyList(),
)

@Serializable
data class CategoryShareDto(
    val shareId: Long,
    val sharedByUser: String,
    val category: String,
    val createdAt: String? = null,
    // Access level for the whole scope — sits on the wrapper, not per item.
    val permission: String? = null,
    val canWrite: Boolean? = null,
    val items: List<MediaItemDto> = emptyList(),
)

@Serializable
data class SharedWithMeDto(
    val albums: List<MediaItemDto> = emptyList(),
    val playlists: List<PlaylistDto> = emptyList(),
    val libraries: List<LibraryShareDto> = emptyList(),
    val categories: List<CategoryShareDto> = emptyList(),
)
