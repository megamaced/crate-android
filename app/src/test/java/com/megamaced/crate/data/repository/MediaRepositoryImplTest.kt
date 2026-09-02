package com.megamaced.crate.data.repository

import app.cash.turbine.test
import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.CrateBinaryService
import com.megamaced.crate.data.api.dto.MediaItemDto
import com.megamaced.crate.data.api.dto.PaginatedMediaDto
import com.megamaced.crate.data.db.entity.MediaItemEntity
import com.megamaced.crate.data.db.entity.PlaylistEntity
import com.megamaced.crate.data.mapper.MediaItemJsonCodec
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.MediaItemDraft
import com.megamaced.crate.domain.model.Status
import com.megamaced.crate.domain.repository.MediaRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRepositoryImplTest {
    private val codec = MediaItemJsonCodec(Json)
    private val dao = FakeMediaItemDao()
    private val api = FakeCrateApiService()
    private val binary = NoopBinaryService()
    private val playlistDao = FakePlaylistDao()
    private val accountPrefs = FakeAccountPrefs()
    private val repo = MediaRepositoryImpl(api, binary, dao, playlistDao, accountPrefs, codec)

    init {
        // Every sync asks the server who it is talking to; the fake would
        // otherwise throw the way any unstubbed endpoint does.
        api.nextMe = meDto(OWNER)
    }

    @Test
    fun `refresh writes API page into DAO and surfaces total`() =
        runTest {
            api.nextPage =
                PaginatedMediaDto(
                    items =
                        listOf(
                            mediaDto(1, "OK Computer", category = "music"),
                            mediaDto(2, "Kid A", category = "music"),
                        ),
                    total = 2,
                    limit = 50,
                    offset = 0,
                )

            val result = repo.refresh(category = Category.Music)

            assertTrue(result is ApiResult.Success)
            val refresh = (result as ApiResult.Success).value
            assertEquals(2, refresh.total)
            assertEquals(2, refresh.itemCount)
            assertEquals(2, dao.snapshot().size)
        }

    @Test
    fun `observeByCategory maps Room rows to domain models`() =
        runTest {
            dao.seed(
                listOf(
                    entity(1, "Tracks", category = "music"),
                    entity(2, "The Wall", category = "music"),
                ),
            )

            repo.observeByCategory(Category.Music).test {
                val items = awaitItem()
                assertEquals(2, items.size)
                assertEquals("Tracks", items[0].title)
                assertEquals(Category.Music, items[0].category)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `create posts to API and writes returned item to DAO`() =
        runTest {
            api.nextCreated = mediaDto(99, "Hounds of Love", category = "music")

            val result = repo.create(MediaItemDraft(title = "Hounds of Love", artist = "Kate Bush", format = "LP"))

            assertTrue(result is ApiResult.Success)
            assertEquals("Hounds of Love", (result as ApiResult.Success).value.title)
            assertEquals(1, dao.snapshot().size)
            assertEquals(99L, dao.snapshot().first().id)
        }

    @Test
    fun `delete clears row from DAO on success`() =
        runTest {
            dao.seed(listOf(entity(7, "Dummy", category = "music")))

            val result = repo.delete(7)

            assertTrue(result is ApiResult.Success)
            assertEquals(0, dao.snapshot().size)
            assertEquals(7L, api.deletedIds.single())
        }

    @Test
    fun `syncDelta pages through the whole collection and stores every row`() =
        runTest {
            // Three pages: two full, one short to terminate the sweep. The sweep
            // must land all 450 rows — a paging bug that drops or repeats rows
            // between pages shows up here as a short DAO snapshot.
            val pageSize = 200
            api.pagesByOffset[0] = page(offset = 0, ids = 1L..200L, total = 450)
            api.pagesByOffset[pageSize] = page(offset = pageSize, ids = 201L..400L, total = 450)
            api.pagesByOffset[pageSize * 2] = page(offset = pageSize * 2, ids = 401L..450L, total = 450)

            val result = repo.syncDelta(updatedSince = null, lastSeenWipedAt = null)

            assertTrue(result is ApiResult.Success)
            assertEquals(450, dao.snapshot().size)
            assertEquals(
                450,
                dao
                    .snapshot()
                    .map { it.id }
                    .distinct()
                    .size,
            )
            // Short final page ends the sweep; no wasted request past the end.
            assertEquals(listOf(0, 0, pageSize, pageSize * 2), api.getMediaCalls.map { it.second })
        }

    @Test
    fun `full sweep prunes rows deleted on the server`() =
        runTest {
            // Local DB holds three rows; the server now only has two of them, as
            // happens after a clean-up in the web UI. A sweep only ever upserts,
            // so without reconciliation row 3 would linger for good.
            dao.seed(
                listOf(
                    entity(1, "Kept", category = "music", userId = OWNER),
                    entity(2, "Also kept", category = "music", userId = OWNER),
                    entity(3, "Deleted on server", category = "music", userId = OWNER),
                ),
            )
            api.pagesByOffset[0] =
                PaginatedMediaDto(
                    items = listOf(
                        mediaDto(1, "Kept", category = "music", userId = OWNER),
                        mediaDto(2, "Also kept", category = "music", userId = OWNER),
                    ),
                    total = 2,
                    limit = 200,
                    offset = 0,
                )

            val result = repo.syncDelta(updatedSince = "2026-01-01 00:00:00", lastSeenWipedAt = null)

            assertTrue(result is ApiResult.Success)
            assertEquals(listOf(1L, 2L), dao.snapshot().map { it.id }.sorted())
        }

    @Test
    fun `a shared item cached locally survives a sweep of our own collection`() =
        runTest {
            // Opening a shared item's detail view caches the owner's row here. A
            // sweep of our items says nothing about it, so it must not be pruned.
            dao.seed(
                listOf(
                    entity(1, "Mine", category = "music", userId = OWNER),
                    entity(99, "Shared with me", category = "music", userId = "someone@else"),
                ),
            )
            api.pagesByOffset[0] =
                PaginatedMediaDto(
                    items = listOf(mediaDto(1, "Mine", category = "music", userId = OWNER)),
                    total = 1,
                    limit = 200,
                    offset = 0,
                )

            repo.syncDelta(updatedSince = null, lastSeenWipedAt = null)

            assertEquals(listOf(1L, 99L), dao.snapshot().map { it.id }.sorted())
        }

    @Test
    fun `an incomplete sweep prunes nothing`() =
        runTest {
            // Server claims 500 items but pagination only yields one page of 2.
            // Those 498 unseen rows were skipped, not deleted — pruning here would
            // destroy the collection.
            dao.seed(
                listOf(
                    entity(1, "One", category = "music", userId = OWNER),
                    entity(2, "Two", category = "music", userId = OWNER),
                    entity(3, "Three", category = "music", userId = OWNER),
                ),
            )
            api.pagesByOffset[0] =
                PaginatedMediaDto(
                    items = listOf(
                        mediaDto(1, "One", category = "music", userId = OWNER),
                        mediaDto(2, "Two", category = "music", userId = OWNER),
                    ),
                    total = 500,
                    limit = 200,
                    offset = 0,
                )

            repo.syncDelta(updatedSince = null, lastSeenWipedAt = null)

            assertEquals(3, dao.snapshot().size)
        }

    @Test
    fun `count drift escalates a delta sync to a full sweep`() =
        runTest {
            // A cursor is supplied, so this would normally be a delta sync. The
            // local count disagrees with the server's, which only a full sweep can
            // reconcile — so the sweep must run unfiltered.
            dao.seed(
                listOf(
                    entity(1, "One", category = "music", userId = OWNER),
                    entity(2, "Stale", category = "music", userId = OWNER),
                ),
            )
            api.pagesByOffset[0] =
                PaginatedMediaDto(
                    items = listOf(mediaDto(1, "One", category = "music", userId = OWNER)),
                    total = 1,
                    limit = 200,
                    offset = 0,
                )

            repo.syncDelta(updatedSince = "2026-01-01 00:00:00", lastSeenWipedAt = null)

            // Sweep ran with no updatedSince, and the stale row is gone.
            assertEquals(listOf(null, null), api.getMediaUpdatedSince)
            assertEquals(listOf(1L), dao.snapshot().map { it.id })
        }

    @Test
    fun `the collection leaves out items owned by someone else`() =
        runTest {
            // Opening a shared item caches the owner's row in the same table.
            // It is not part of this user's collection and must not show up in
            // Collection, Search or the offline Home feed.
            accountPrefs.setCurrentUserId(OWNER)
            dao.seed(
                listOf(
                    entity(1, "Mine", category = "music", userId = OWNER),
                    entity(2, "Pre-userId row", category = "music"),
                    entity(99, "Shared with me", category = "music", userId = "someone@else"),
                ),
            )

            repo.observeAll().test {
                assertEquals(listOf(1L, 2L), awaitItem().map { it.id }.sorted())
                cancelAndIgnoreRemainingEvents()
            }
            repo.observeByCategory(Category.Music).test {
                assertEquals(listOf(1L, 2L), awaitItem().map { it.id }.sorted())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an unknown current user leaves the collection unfiltered`() =
        runTest {
            // Before /me has ever been read, filtering on an unknown id would
            // hide the entire collection — so the predicate has to fail open.
            dao.seed(listOf(entity(1, "Mine", category = "music", userId = OWNER)))

            repo.observeAll().test {
                assertEquals(1, awaitItem().size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a scoped wipe clears only the selected categories`() =
        runTest {
            dao.seed(
                listOf(
                    entity(1, "A game", category = "game", userId = OWNER),
                    entity(2, "An album", category = "music", userId = OWNER),
                    entity(3, "A film", category = "film", userId = OWNER),
                ),
            )

            val result = repo.wipeCollection(listOf("game"))

            assertTrue(result is ApiResult.Success)
            assertEquals(listOf("game"), api.wipeScopes)
            // The server deleted only the games, so the phone must match it —
            // clearing everything left the collection empty until the next sync.
            assertEquals(listOf(2L, 3L), dao.snapshot().map { it.id }.sorted())
        }

    @Test
    fun `wiping the playlists scope clears playlists but keeps the items`() =
        runTest {
            dao.seed(listOf(entity(1, "An album", category = "music", userId = OWNER)))
            playlistDao.seedPlaylists(listOf(PlaylistEntity(id = 5, name = "Sunday")))

            repo.wipeCollection(listOf(MediaRepository.PLAYLISTS_SCOPE))

            assertEquals(emptyList<Long>(), playlistDao.snapshot().map { it.id })
            assertEquals(listOf(1L), dao.snapshot().map { it.id })
        }

    @Test
    fun `deleting the last server item prunes the local row`() =
        runTest {
            // total = 0 means the probe returns no row to infer an owner from,
            // which used to skip the count check and both pruning guards — so
            // the last item deleted on the web lingered on the phone for good.
            dao.seed(listOf(entity(1, "The last one", category = "music", userId = OWNER)))
            api.nextPage = PaginatedMediaDto(items = emptyList(), total = 0, limit = 1, offset = 0)

            val result = repo.syncDelta(updatedSince = null, lastSeenWipedAt = null)

            assertTrue(result is ApiResult.Success)
            assertEquals(emptyList<Long>(), dao.snapshot().map { it.id })
        }

    @Test
    fun `a delta re-requests the second before the cursor without rewinding it`() =
        runTest {
            // Server stamps are second-resolution and the filter is strictly
            // greater, so an item written in the same second as one already
            // fetched — but after that page's query ran — is never returned
            // again. The one-second overlap re-delivers it.
            dao.seed(listOf(entity(1, "One", category = "music", userId = OWNER)))
            api.nextPage =
                PaginatedMediaDto(
                    items = listOf(mediaDto(1, "One", category = "music", userId = OWNER, updatedAt = "2026-01-01 12:00:05")),
                    total = 1,
                    limit = 200,
                    offset = 0,
                )

            val result = repo.syncDelta(updatedSince = "2026-01-01 12:00:00", lastSeenWipedAt = null)

            // Probe is deliberately unfiltered; the sweep asks one second back.
            assertEquals(listOf(null, "2026-01-01 11:59:59"), api.getMediaUpdatedSince)
            // The cursor itself still moves forward — seeding it with the
            // rewound value would walk it backwards a second per sync.
            assertEquals("2026-01-01 12:00:05", (result as ApiResult.Success).value.cursor)
        }

    private fun page(
        offset: Int,
        ids: LongRange,
        total: Int,
    ) = PaginatedMediaDto(
        items = ids.map { mediaDto(it, "Item $it", category = "music") },
        total = total,
        limit = 200,
        offset = offset,
    )

    private fun mediaDto(
        id: Long,
        title: String,
        category: String,
        userId: String? = null,
        updatedAt: String? = null,
    ) = MediaItemDto(
        id = id,
        userId = userId,
        title = title,
        artist = "test",
        format = "LP",
        status = Status.Owned.apiValue,
        category = category,
        updatedAt = updatedAt,
    )

    private fun entity(
        id: Long,
        title: String,
        category: String,
        userId: String? = null,
    ) = MediaItemEntity(
        id = id,
        userId = userId,
        title = title,
        artist = "test",
        format = "LP",
        status = Status.Owned.apiValue,
        category = category,
    )

    private companion object {
        const val OWNER = "david@macemail.co.uk"
    }
}

private class NoopBinaryService : CrateBinaryService {
    override suspend fun getArtwork(
        itemId: Long,
        size: String?,
    ) = error("not used")

    override suspend fun uploadArtwork(
        itemId: Long,
        file: okhttp3.MultipartBody.Part,
    ) = error("not used")

    override suspend fun deleteArtwork(itemId: Long) = error("not used")

    override suspend fun getPhoto(
        itemId: Long,
        slot: Int,
        size: String?,
    ) = error("not used")

    override suspend fun uploadPhoto(
        itemId: Long,
        slot: Int,
        file: okhttp3.MultipartBody.Part,
    ) = error("not used")

    override suspend fun deletePhoto(
        itemId: Long,
        slot: Int,
    ) = error("not used")

    override suspend fun export(
        format: String,
        scope: String,
        category: String,
        includeEnriched: Int,
        includeMarket: Int,
    ) = error("not used")
}
