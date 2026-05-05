package com.macebox.crate.data.repository

import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.data.api.CrateApiService
import com.macebox.crate.data.api.CrateBinaryService
import com.macebox.crate.data.api.apiCall
import com.macebox.crate.data.db.dao.MediaItemDao
import com.macebox.crate.data.mapper.MediaItemJsonCodec
import com.macebox.crate.data.mapper.toDomain
import com.macebox.crate.data.mapper.toEntity
import com.macebox.crate.data.mapper.toRequest
import com.macebox.crate.domain.model.Category
import com.macebox.crate.domain.model.MediaItem
import com.macebox.crate.domain.model.MediaItemDraft
import com.macebox.crate.domain.model.Status
import com.macebox.crate.domain.repository.MediaRepository
import com.macebox.crate.domain.repository.MediaRepository.RefreshResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl
    @Inject
    constructor(
        private val api: CrateApiService,
        private val binary: CrateBinaryService,
        private val dao: MediaItemDao,
        private val codec: MediaItemJsonCodec,
    ) : MediaRepository {
        override fun observeAll(): Flow<List<MediaItem>> = dao.observeAll().map { rows -> rows.map { it.toDomain(codec) } }

        override fun observeByCategory(
            category: Category,
            status: Status?,
        ): Flow<List<MediaItem>> =
            dao
                .observeByCategory(category.apiValue, status?.apiValue)
                .map { rows -> rows.map { it.toDomain(codec) } }

        override fun observe(id: Long): Flow<MediaItem?> = dao.observe(id).map { it?.toDomain(codec) }

        override suspend fun refresh(
            category: Category?,
            status: Status?,
            limit: Int,
            offset: Int,
        ): ApiResult<RefreshResult> =
            apiCall {
                val page =
                    api.getMedia(
                        category = category?.apiValue,
                        status = status?.apiValue,
                        limit = limit,
                        offset = offset,
                    )
                dao.upsertAll(page.items.map { it.toEntity(codec) })
                RefreshResult(
                    total = page.total,
                    limit = page.limit,
                    offset = page.offset,
                    itemCount = page.items.size,
                )
            }

        override suspend fun refreshSingle(id: Long): ApiResult<MediaItem> =
            apiCall {
                val dto = api.getMediaItem(id)
                dao.upsert(dto.toEntity(codec))
                dto.toDomain()
            }

        override suspend fun create(draft: MediaItemDraft): ApiResult<MediaItem> =
            apiCall {
                val dto = api.createMedia(draft.toRequest())
                dao.upsert(dto.toEntity(codec))
                dto.toDomain()
            }

        override suspend fun update(
            id: Long,
            draft: MediaItemDraft,
        ): ApiResult<MediaItem> =
            apiCall {
                val dto = api.updateMediaItem(id, draft.toRequest())
                dao.upsert(dto.toEntity(codec))
                dto.toDomain()
            }

        override suspend fun delete(id: Long): ApiResult<Unit> =
            apiCall {
                api.deleteMediaItem(id)
                dao.delete(id)
            }

        override suspend fun deleteAll(): ApiResult<Unit> =
            apiCall {
                api.deleteAllMedia()
                dao.deleteAll()
            }

        override suspend fun wipeCollection(scopes: List<String>): ApiResult<Unit> =
            apiCall {
                api.deleteAllMedia(scopes = scopes.joinToString(","))
                dao.deleteAll()
            }

        override suspend fun uploadArtwork(
            id: Long,
            bytes: ByteArray,
            mimeType: String,
        ): ApiResult<Unit> =
            apiCall {
                val body = bytes.toRequestBody(mimeType.toMediaType())
                val part = MultipartBody.Part.createFormData("file", "artwork", body)
                binary.uploadArtwork(id, part).close()
                // Refresh so updatedAt advances and Coil cache key changes.
                val dto = api.getMediaItem(id)
                dao.upsert(dto.toEntity(codec))
            }

        override suspend fun deleteArtwork(id: Long): ApiResult<Unit> =
            apiCall {
                binary.deleteArtwork(id)
                val dto = api.getMediaItem(id)
                dao.upsert(dto.toEntity(codec))
            }

        override suspend fun syncDelta(updatedSince: String?): ApiResult<String?> =
            apiCall {
                var offset = 0
                var maxUpdatedAt: String? = updatedSince
                while (true) {
                    val page = api.getMedia(updatedSince = updatedSince, limit = SYNC_PAGE_SIZE, offset = offset)
                    if (page.items.isEmpty()) break
                    dao.upsertAll(page.items.map { it.toEntity(codec) })
                    page.items.forEach { dto ->
                        val candidate = dto.updatedAt
                        if (candidate != null && (maxUpdatedAt == null || candidate > maxUpdatedAt!!)) {
                            maxUpdatedAt = candidate
                        }
                    }
                    if (page.items.size < SYNC_PAGE_SIZE) break
                    offset += SYNC_PAGE_SIZE
                }
                maxUpdatedAt
            }

        companion object {
            private const val SYNC_PAGE_SIZE = 200
        }
    }
