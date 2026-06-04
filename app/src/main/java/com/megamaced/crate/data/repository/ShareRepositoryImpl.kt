package com.megamaced.crate.data.repository

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.CrateApiService
import com.megamaced.crate.data.api.apiCall
import com.megamaced.crate.data.api.dto.ShareRequest
import com.megamaced.crate.data.mapper.toDomain
import com.megamaced.crate.domain.model.Share
import com.megamaced.crate.domain.model.SharedWithMe
import com.megamaced.crate.domain.model.UserSearchResult
import com.megamaced.crate.domain.repository.ShareRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareRepositoryImpl
    @Inject
    constructor(
        private val api: CrateApiService,
    ) : ShareRepository {
        override suspend fun searchUsers(query: String): ApiResult<List<UserSearchResult>> =
            apiCall { api.searchUsers(query).map { it.toDomain() } }

        override suspend fun listAlbumShares(albumId: Long): ApiResult<List<Share>> =
            apiCall { api.listAlbumShares(albumId).map { it.toDomain() } }

        override suspend fun shareAlbum(
            albumId: Long,
            targetUserId: String,
        ): ApiResult<Share> = apiCall { api.shareAlbum(albumId, ShareRequest(targetUserId)).toDomain() }

        override suspend fun listPlaylistShares(playlistId: Long): ApiResult<List<Share>> =
            apiCall { api.listPlaylistShares(playlistId).map { it.toDomain() } }

        override suspend fun sharePlaylist(
            playlistId: Long,
            targetUserId: String,
        ): ApiResult<Share> = apiCall { api.sharePlaylist(playlistId, ShareRequest(targetUserId)).toDomain() }

        override suspend fun sharedWithMe(): ApiResult<SharedWithMe> = apiCall { api.sharedWithMe().toDomain() }

        override suspend fun removeShare(shareId: Long): ApiResult<Unit> = apiCall { api.removeShare(shareId) }
    }
