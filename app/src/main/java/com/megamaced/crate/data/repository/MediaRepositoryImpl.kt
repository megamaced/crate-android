package com.megamaced.crate.data.repository

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.CrateApiService
import com.megamaced.crate.data.api.CrateBinaryService
import com.megamaced.crate.data.api.apiCall
import com.megamaced.crate.data.db.dao.MediaItemDao
import com.megamaced.crate.data.db.dao.PlaylistDao
import com.megamaced.crate.data.mapper.MediaItemJsonCodec
import com.megamaced.crate.data.mapper.toDomain
import com.megamaced.crate.data.mapper.toEntity
import com.megamaced.crate.data.mapper.toRequest
import com.megamaced.crate.data.prefs.AccountPrefs
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.MediaItemDraft
import com.megamaced.crate.domain.model.Status
import com.megamaced.crate.domain.repository.MediaRepository
import com.megamaced.crate.domain.repository.MediaRepository.RefreshResult
import com.megamaced.crate.domain.repository.MediaRepository.SyncResult
import com.megamaced.crate.util.ExifStrip
import com.megamaced.crate.util.ExifStripFailedException
import com.megamaced.crate.util.ServerTimestamps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
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
        private val playlistDao: PlaylistDao,
        private val accountPrefs: AccountPrefs,
        private val codec: MediaItemJsonCodec,
    ) : MediaRepository {
        // Both collection reads are scoped to the signed-in user. Rows belonging
        // to someone else land in the same table whenever a shared item or a
        // shared playlist is opened, and they are not part of your collection —
        // see the predicate and its fail-open null case in MediaItemDao.
        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observeAll(): Flow<List<MediaItem>> =
            accountPrefs.currentUserIdFlow.flatMapLatest { ownerId ->
                dao.observeAll(ownerId).map { rows -> rows.map { it.toDomain(codec) } }
            }

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observeByCategory(
            category: Category,
            status: Status?,
        ): Flow<List<MediaItem>> =
            accountPrefs.currentUserIdFlow.flatMapLatest { ownerId ->
                dao
                    .observeByCategory(category.apiValue, status?.apiValue, ownerId)
                    .map { rows -> rows.map { it.toDomain(codec) } }
            }

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

        override suspend fun wipeCollection(scopes: List<String>): ApiResult<Unit> =
            apiCall {
                api.deleteAllMedia(scopes = scopes.joinToString(","))
                // The server deletes only the scopes it was sent, so the local
                // DB must match it exactly. Clearing everything used to empty
                // the phone for a wipe of one category, and nothing put the
                // other categories back until the next sync — up to six hours,
                // and never while offline.
                val categories = scopes.mapNotNull { Category.fromApi(it) }.map { it.apiValue }
                if (categories.isNotEmpty()) {
                    dao.deleteByCategories(categories, accountPrefs.currentUserId())
                }
                // Playlists are their own scope; the cross-refs go with them
                // through the FK cascade.
                if (MediaRepository.PLAYLISTS_SCOPE in scopes) {
                    playlistDao.deleteAll()
                }
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
                // Fail closed. The app promises metadata is stripped before an
                // image leaves the device, so an image the platform can't strip
                // is refused rather than uploaded intact. ExifStrip logs why;
                // this names the upload it happened on.
                if (sanitised == null) {
                    Timber.w("Artwork for item %d refused: its metadata could not be stripped", id)
                    throw ExifStripFailedException()
                }
                // Label the part with the type the bytes actually are now —
                // stripping re-encodes HEIC/WebP to JPEG.
                val body = sanitised.bytes.toRequestBody(mediaTypeFor(sanitised.mimeType))
                val part = MultipartBody.Part.createFormData(
                    "file",
                    "artwork" + extensionFor(sanitised.mimeType),
                    body,
                )
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
                if (sanitised == null) {
                    Timber.w("Photo %d for item %d refused: its metadata could not be stripped", slot, id)
                    throw ExifStripFailedException()
                }
                val body = sanitised.bytes.toRequestBody(mediaTypeFor(sanitised.mimeType))
                val part = MultipartBody.Part.createFormData(
                    "file",
                    "photo" + extensionFor(sanitised.mimeType),
                    body,
                )
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
            cursorId: Long?,
            lastSeenWipedAt: String?,
        ): ApiResult<SyncResult> =
            apiCall {
                // Deliberately probed WITHOUT updatedSince: one 1-item request then
                // reports the whole collection, giving us the server's wipe marker,
                // its authoritative item count, and the id of the owning user.
                val probe = api.getMedia(limit = 1, offset = 0)
                val serverWipedAt = probe.wipedAt
                // Ask the server who we are rather than inferring it from the
                // probe row: an empty collection returns no row, and that is
                // exactly the case where pruning matters (the last item was
                // deleted on the web). Cache it — the collection queries are
                // scoped by it.
                val ownerId = (
                    try {
                        api.getMe().userId.takeIf { it.isNotBlank() }
                    } catch (e: CancellationException) {
                        // Never swallow cancellation: it has to reach the caller
                        // for the sweep to actually stop.
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "Sync couldn't read /me; falling back to the probe row for the owner id")
                        null
                    }
                ) ?: probe.items.firstOrNull()?.userId
                if (ownerId != null) accountPrefs.setCurrentUserId(ownerId)

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

                val fullSweep = wiped || drifted || updatedSince == null
                // What the caller handed us, and the floor the sweep may never
                // fall below: a cursor is only ever replaced by a later one.
                val stored = if (updatedSince == null) null else MediaCursor(updatedSince, cursorId)
                // A delta resumes from the stored cursor. When only its
                // timestamp is known — the first sync after upgrading, or a
                // server too old to hand out the id half — ask from one second
                // earlier: those servers filter strictly-greater against
                // second-resolution stamps, so a row written in the same second
                // as one already fetched, but after that page's query ran,
                // would never be returned again. Re-seeing rows costs nothing,
                // because every write below is an upsert.
                var cursor =
                    when {
                        fullSweep -> {
                            null
                        }

                        cursorId != null -> {
                            stored
                        }

                        else -> {
                            MediaCursor(ServerTimestamps.minusOneSecond(updatedSince), null)
                        }
                    }

                var offset = 0
                // The last resume point the server handed back, and the highest
                // stamp the sweep saw. The first is exact; the second is the
                // fallback for a full sweep and for servers predating the
                // keyset cursor.
                var keyset: MediaCursor? = null
                var maxSeen: String? = null
                var reportedTotal = 0
                // Offset pagination is only lossless while the server's sort is
                // total, and while nothing is edited mid-sweep: a row updated
                // between two pages moves to the end of an updatedAt-ordered
                // delta, pushing an unseen row back across the page boundary.
                // Track distinct ids so that failure is loud instead of showing
                // up months later as a short collection count.
                val seenIds = mutableSetOf<Long>()
                while (true) {
                    val page =
                        api.getMedia(
                            updatedSince = cursor?.updatedSince,
                            updatedSinceId = cursor?.id,
                            limit = SYNC_PAGE_SIZE,
                            offset = offset,
                        )
                    if (page.items.isEmpty()) break
                    reportedTotal = page.total
                    dao.upsertAll(page.items.map { it.toEntity(codec) })
                    page.items.forEach { dto ->
                        seenIds += dto.id
                        val candidate = dto.updatedAt
                        val current = maxSeen
                        if (candidate != null && (current == null || ServerTimestamps.isNewer(candidate, current))) {
                            maxSeen = candidate
                        }
                    }
                    val more = page.items.size >= SYNC_PAGE_SIZE
                    // On a delta the server hands back the (updatedAt, id) of
                    // this page's last row. Sending that pair as the next
                    // page's cursor is an exact resume point: no offset to
                    // drift under a concurrent edit, and no row lost to a
                    // shared second.
                    val next = page.nextCursor
                    if (next != null) {
                        keyset = MediaCursor(next.updatedSince, next.updatedSinceId)
                        cursor = keyset
                        offset = 0
                    } else {
                        offset += SYNC_PAGE_SIZE
                    }
                    if (!more) break
                }
                val sweptEverything = seenIds.size >= reportedTotal
                if (!sweptEverything) {
                    Timber.w(
                        "Sync incomplete: server reported %d items, pagination yielded %d distinct. " +
                            "Holding the cursor so the next sync re-reads from the same point.",
                        reportedTotal,
                        seenIds.size,
                    )
                }

                // Committing a cursor past rows this sweep never saw makes the
                // gap permanent: the next delta starts after them, and an edit
                // changes no row count, so the drift check never escalates to a
                // full sweep either. An incomplete sweep therefore keeps the
                // cursor exactly where it was and tries again next time.
                val committed =
                    when {
                        !sweptEverything -> {
                            stored
                        }

                        keyset != null -> {
                            keyset
                        }

                        maxSeen != null && (stored == null || ServerTimestamps.isNewer(maxSeen, stored.updatedSince)) -> {
                            MediaCursor(maxSeen, null)
                        }

                        else -> {
                            stored
                        }
                    }

                // A full sweep enumerates everything the server holds for us, so
                // anything local and absent from it has been deleted server-side.
                // Three guards, because getting this wrong deletes the user's data:
                // only a full sweep is authoritative (a delta says nothing about
                // rows it filtered out); an incomplete sweep would prune rows that
                // pagination merely skipped; and an empty response must not empty
                // the database unless the server said the collection is empty.
                // The empty case is authoritative only when the server itself
                // reported an empty collection: probe.total is that statement,
                // whereas an empty page could equally be a sweep that failed to
                // return anything.
                val authoritative = sweptEverything && (seenIds.isNotEmpty() || probe.total == 0)
                if (fullSweep && ownerId != null && authoritative) {
                    val stale = dao.idsOwnedBy(ownerId).filterNot { it in seenIds }
                    if (stale.isNotEmpty()) {
                        // Chunked: one bound parameter per id would blow SQLite's
                        // variable ceiling on a large clean-up.
                        stale.chunked(DELETE_CHUNK).forEach { dao.deleteByIds(it) }
                        Timber.i("Sync pruned %d items deleted on the server", stale.size)
                    }
                }

                SyncResult(
                    cursor = committed?.updatedSince,
                    cursorId = committed?.id,
                    wipedAt = serverWipedAt,
                )
            }

        /**
         * A delta resume point: the `(updatedAt, id)` of the last row consumed.
         * [id] is null against a server that predates the keyset cursor, where
         * only the timestamp half exists.
         */
        private data class MediaCursor(
            val updatedSince: String,
            val id: Long?,
        )

        companion object {
            private const val SYNC_PAGE_SIZE = 200

            /** Ids per DELETE, kept well under SQLite's bound-variable ceiling. */
            private const val DELETE_CHUNK = 400
        }
    }

/**
 * A wildcard or otherwise unparseable type (ContentResolver.getType can return
 * null, and the UI falls back to the literal "image/\*") would make
 * `toMediaType` throw. The server sniffs content rather than trusting the
 * declared type, so an opaque fallback is safe.
 */
private fun mediaTypeFor(mimeType: String): MediaType = mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()

private fun extensionFor(mimeType: String): String =
    when (mimeType) {
        "image/png" -> ".png"
        "image/jpeg" -> ".jpg"
        "image/webp" -> ".webp"
        "image/gif" -> ".gif"
        else -> ""
    }
