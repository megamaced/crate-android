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
import timber.log.Timber
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
                // Deliberately probed WITHOUT updatedSince: one 1-item request then
                // reports the whole collection, giving us the server's wipe marker,
                // its authoritative item count, and the id of the owning user.
                val probe = api.getMedia(limit = 1, offset = 0)
                val serverWipedAt = probe.wipedAt
                // Every row a sweep of our own collection returns belongs to us, so
                // the probe row identifies the owner without a second request.
                val ownerId = probe.items.firstOrNull()?.userId

                // If the server has been wiped since our last sync, our local
                // rows are stale (re-import generates new IDs, so delta sync
                // would just append duplicates). Drop the local DB and refetch.
                val wiped =
                    serverWipedAt != null &&
                        (lastSeenWipedAt == null || ServerTimestamps.isNewer(serverWipedAt, lastSeenWipedAt))
                if (wiped) dao.deleteAll()

                // A delta sweep only adds and updates. It cannot discover that an
                // item was deleted — on the web UI, on another device, or by a
                // bulk clean-up — because a deleted row simply stops being
                // returned, and `updatedSince` filters out everything unchanged.
                // Comparing counts is the cheap way to notice, and a full sweep is
                // the only thing that can reconcile a deletion, so escalate to one.
                val localCount = ownerId?.let { dao.countOwnedBy(it) }
                val drifted = localCount != null && localCount != probe.total
                if (drifted) {
                    Timber.i(
                        "Sync drift: %d local items vs %d on the server — falling back to a full sweep",
                        localCount,
                        probe.total,
                    )
                }

                val effectiveSince = if (wiped || drifted) null else updatedSince

                var offset = 0
                var maxUpdatedAt: String? = effectiveSince
                var reportedTotal = 0
                // Offset pagination is only lossless while the server's sort is
                // total. When it is not, pages overlap and rows go missing, and
                // the cursor below still advances past them — so the gap becomes
                // permanent. Track distinct ids so that failure is loud instead
                // of showing up months later as a short collection count.
                val seenIds = mutableSetOf<Long>()
                while (true) {
                    val page = api.getMedia(updatedSince = effectiveSince, limit = SYNC_PAGE_SIZE, offset = offset)
                    if (page.items.isEmpty()) break
                    reportedTotal = page.total
                    dao.upsertAll(page.items.map { it.toEntity(codec) })
                    page.items.forEach { dto ->
                        seenIds += dto.id
                        val candidate = dto.updatedAt
                        val currentMax = maxUpdatedAt
                        if (candidate != null && (currentMax == null || ServerTimestamps.isNewer(candidate, currentMax))) {
                            maxUpdatedAt = candidate
                        }
                    }
                    if (page.items.size < SYNC_PAGE_SIZE) break
                    offset += SYNC_PAGE_SIZE
                }
                val sweptEverything = seenIds.size >= reportedTotal
                if (!sweptEverything) {
                    Timber.w(
                        "Sync incomplete: server reported %d items, pagination yielded %d distinct. " +
                            "Server sort is likely non-deterministic; upgrade the Crate server app.",
                        reportedTotal,
                        seenIds.size,
                    )
                }

                // A full sweep enumerates everything the server holds for us, so
                // anything local and absent from it has been deleted server-side.
                // Three guards, because getting this wrong deletes the user's data:
                // only a full sweep is authoritative (a delta says nothing about
                // rows it filtered out); an incomplete sweep would prune rows that
                // pagination merely skipped; and an empty response must not empty
                // the database — a genuine wipe arrives via wipedAt instead.
                if (effectiveSince == null && ownerId != null && sweptEverything && seenIds.isNotEmpty()) {
                    val stale = dao.idsOwnedBy(ownerId).filterNot { it in seenIds }
                    if (stale.isNotEmpty()) {
                        // Chunked: one bound parameter per id would blow SQLite's
                        // variable ceiling on a large clean-up.
                        stale.chunked(DELETE_CHUNK).forEach { dao.deleteByIds(it) }
                        Timber.i("Sync pruned %d items deleted on the server", stale.size)
                    }
                }

                SyncResult(cursor = maxUpdatedAt, wipedAt = serverWipedAt)
            }

        companion object {
            private const val SYNC_PAGE_SIZE = 200

            /** Ids per DELETE, kept well under SQLite's bound-variable ceiling. */
            private const val DELETE_CHUNK = 400
        }
    }
