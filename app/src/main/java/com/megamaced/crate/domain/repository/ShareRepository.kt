package com.megamaced.crate.domain.repository

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.domain.model.Share
import com.megamaced.crate.domain.model.SharedWithMe
import com.megamaced.crate.domain.model.UserSearchResult

interface ShareRepository {
    suspend fun searchUsers(query: String): ApiResult<List<UserSearchResult>>

    suspend fun listAlbumShares(albumId: Long): ApiResult<List<Share>>

    suspend fun shareAlbum(
        albumId: Long,
        targetUserId: String,
    ): ApiResult<Share>

    suspend fun listPlaylistShares(playlistId: Long): ApiResult<List<Share>>

    suspend fun sharePlaylist(
        playlistId: Long,
        targetUserId: String,
    ): ApiResult<Share>

    suspend fun listLibraryShares(): ApiResult<List<Share>>

    suspend fun shareLibrary(targetUserId: String): ApiResult<Share>

    suspend fun listCategoryShares(category: String): ApiResult<List<Share>>

    suspend fun shareCategory(
        category: String,
        targetUserId: String,
    ): ApiResult<Share>

    suspend fun sharedWithMe(): ApiResult<SharedWithMe>

    suspend fun removeShare(shareId: Long): ApiResult<Unit>
}
