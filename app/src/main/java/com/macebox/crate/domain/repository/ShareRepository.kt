package com.macebox.crate.domain.repository

import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.domain.model.Share
import com.macebox.crate.domain.model.SharedWithMe
import com.macebox.crate.domain.model.UserSearchResult

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

    suspend fun sharedWithMe(): ApiResult<SharedWithMe>

    suspend fun removeShare(shareId: Long): ApiResult<Unit>
}
