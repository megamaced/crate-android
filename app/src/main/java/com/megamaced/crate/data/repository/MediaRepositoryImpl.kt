package com.megamaced.crate.data.repository

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.CrateApiService
import com.megamaced.crate.data.api.CrateBinaryService
import com.megamaced.crate.data.api.apiCall
import com.megamaced.crate.data.db.dao.MediaItemDao
import com.megamaced.crate.data.mapper.MediaItemJsonCodec
import com.megamaced.crate.data.mapper.toDomain
import com.megamaced.crate.data.mapper.toEntity
import com.megamaced.crate.data.mapper.toRequest
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.MediaItemDraft
import com.megamaced.crate.domain.model.Status
import com.megamaced.crate.domain.repository.MediaRepository
import com.megamaced.crate.domain.repository.MediaRepository.RefreshResult
import com.megamaced.crate.domain.repository.MediaRepository.SyncResult
import com.megamaced.crate.util.ExifStrip
import com.megamaced.crate.util.ServerTimestamps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
                // Strip EXIF/GPS client-side before sending. The server also
                // re-encodes through GD, but stripping here protects the bytes
                // in transit (logs, proxies) and shields users on older servers.
                // The decode/re-encode is CPU-bound, so keep it off the main
                // thread (apiCall's block runs in the caller's context until
                // the first real suspension).
                val sanitised = withContext(Dispatchers.Default) { ExifStrip.strip(bytes, mimeType) }
                val body = sanitised.toRequestBody(mimeType.toMediaType())
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

        override suspend fun uploadPhoto(
            id: Long,
            slot: Int,
            bytes: ByteArray,
            mimeType: String,
        ): ApiResult<Unit> =
            apiCall {
                // Strip EXIF/GPS client-side. Photos are the "receipts and
                // personal photos" slot — phone-gallery uploads commonly
                // carry GPS, timestamps, camera serials. Decode/re-encode is
                // CPU-bound; keep it off the main thread.
                val sanitised = withContext(Dispatchers.Default) { ExifStrip.strip(bytes, mimeType) }
                val body = sanitised.toRequestBody(mimeType.toMediaType())
                val part = MultipartBody.Part.createFormData("file", "photo", body)
                binary.uploadPhoto(id, slot, part).close()
                // Refresh so hasPhoto{slot} flips true and updatedAt advances
                // (drives Coil cache-key invalidation in the UI).
                val dto = api.getMediaItem(id)
                dao.upsert(dto.toEntity(codec))
            }

        override suspend fun deletePhoto(
            id: Long,
            slot: Int,
        ): ApiResult<Unit> =
            apiCall {
                binary.deletePhoto(id, slot)
                val dto = api.getMediaItem(id)
                dao.upsert(dto.toEntity(codec))
            }

        override suspend fun syncDelta(
            updatedSince: String?,
            lastSeenWipedAt: String?,
        ): ApiResult<SyncResult> =
            apiCall {
                // Probe with a 1-item page to learn the server's current wipedAt
                // before committing to a delta vs. full resync.
                val probe = api.getMedia(updatedSince = updatedSince, limit = 1, offset = 0)
                val serverWipedAt = probe.wipedAt

                // If the server has been wiped since our last sync, our local
                // rows are stale (re-import generates new IDs, so delta sync
                // would just append duplicates). Drop the local DB and refetch.
                val effectiveSince =
                    if (serverWipedAt != null &&
                        (lastSeenWipedAt == null || ServerTimestamps.isNewer(serverWipedAt, lastSeenWipedAt))
                    ) {
                        dao.deleteAll()
                        null
                    } else {
                        updatedSince
                    }

                var offset = 0
                var maxUpdatedAt: String? = effectiveSince
                while (true) {
                    val page = api.getMedia(updatedSince = effectiveSince, limit = SYNC_PAGE_SIZE, offset = offset)
                    if (page.items.isEmpty()) break
                    dao.upsertAll(page.items.map { it.toEntity(codec) })
                    page.items.forEach { dto ->
                        val candidate = dto.updatedAt
                        val currentMax = maxUpdatedAt
                        if (candidate != null && (currentMax == null || ServerTimestamps.isNewer(candidate, currentMax))) {
                            maxUpdatedAt = candidate
                        }
                    }
                    if (page.items.size < SYNC_PAGE_SIZE) break
                    offset += SYNC_PAGE_SIZE
                }
                SyncResult(cursor = maxUpdatedAt, wipedAt = serverWipedAt)
            }

        companion object {
            private const val SYNC_PAGE_SIZE = 200
        }
    }
