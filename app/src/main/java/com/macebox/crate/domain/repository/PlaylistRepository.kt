package com.macebox.crate.domain.repository

import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.domain.model.Playlist
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun observeAll(): Flow<List<Playlist>>

    fun observe(id: Long): Flow<Playlist?>

    suspend fun refresh(): ApiResult<Unit>

    suspend fun refresh(id: Long): ApiResult<Playlist>

    suspend fun create(name: String): ApiResult<Playlist>

    suspend fun rename(
        id: Long,
        name: String,
    ): ApiResult<Playlist>

    suspend fun delete(id: Long): ApiResult<Unit>

    suspend fun addItem(
        playlistId: Long,
        mediaItemId: Long,
    ): ApiResult<Playlist>

    suspend fun removeItem(
        playlistId: Long,
        mediaItemId: Long,
    ): ApiResult<Playlist>
}
