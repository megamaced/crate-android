package com.macebox.crate.domain.repository

import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.domain.model.Category
import com.macebox.crate.domain.model.MediaItem
import com.macebox.crate.domain.model.MediaItemDraft
import com.macebox.crate.domain.model.Status
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun observeAll(): Flow<List<MediaItem>>

    fun observeByCategory(
        category: Category,
        status: Status? = null,
    ): Flow<List<MediaItem>>

    fun observe(id: Long): Flow<MediaItem?>

    /** Force a refresh from the network. Returns the page metadata so callers can paginate. */
    suspend fun refresh(
        category: Category? = null,
        status: Status? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): ApiResult<RefreshResult>

    suspend fun refreshSingle(id: Long): ApiResult<MediaItem>

    suspend fun create(draft: MediaItemDraft): ApiResult<MediaItem>

    suspend fun update(
        id: Long,
        draft: MediaItemDraft,
    ): ApiResult<MediaItem>

    suspend fun delete(id: Long): ApiResult<Unit>

    suspend fun deleteAll(): ApiResult<Unit>

    suspend fun wipeCollection(scopes: List<String>): ApiResult<Unit>

    suspend fun uploadArtwork(
        id: Long,
        bytes: ByteArray,
        mimeType: String,
    ): ApiResult<Unit>

    suspend fun deleteArtwork(id: Long): ApiResult<Unit>

    /**
     * Pages through `GET /api/v1/media?updatedSince={cursor}` writing every
     * row into Room, and returns the new high-water `updatedAt` to persist
     * as the next cursor (or `null` when nothing has changed).
     */
    suspend fun syncDelta(updatedSince: String?): ApiResult<String?>

    data class RefreshResult(
        val total: Int,
        val limit: Int,
        val offset: Int,
        val itemCount: Int,
    )
}
