package com.megamaced.crate.data.repository

import app.cash.turbine.test
import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.CrateBinaryService
import com.megamaced.crate.data.api.IMAGE_NOT_SANITISED_CODE
import com.megamaced.crate.data.api.dto.MediaCursorDto
import com.megamaced.crate.data.api.dto.MediaItemDto
import com.megamaced.crate.data.api.dto.MediaWatermarkDto
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class MediaRepositoryImplTest {
    private val codec = MediaItemJsonCodec(Json)
    private val dao = FakeMediaItemDao()
    private val api = FakeCrateApiService()
    private val binary = NoopBinaryService()
    private val playlistDao = FakePlaylistDao()
    private val accountPrefs = FakeAccountPrefs()
    private val sweepPrefs = FakeFullSweepPrefs()
    private val repo = MediaRepositoryImpl(api, binary, dao, playlistDao, accountPrefs, sweepPrefs, codec)

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
            // reconcile — so the sweep must start from the beginning of time.
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

            // The sweep ignored the stored cursor, and the stale row is gone.
            assertEquals(listOf(null, "1970-01-01 00:00:00"), api.getMediaUpdatedSince)
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

    @Test
    fun `a delta pages by the cursor the server hands back, not by offset`() =
        runTest {
            // Offsets drift under a concurrent edit: a row updated between two
            // pages moves to the end of an updatedAt-ordered delta and pushes an
            // unseen row back across the boundary. The server's (updatedAt, id)
            // cursor has nothing to drift.
            val ids = 1L..201L
            dao.seed(ids.map { entity(it, "Item $it", category = "music", userId = OWNER) })
            val dto = { id: Long -> mediaDto(id, "Item $id", category = "music", userId = OWNER, updatedAt = "2026-01-01 12:00:05") }
            api.pagesByOffset[0] =
                PaginatedMediaDto(
                    items = (1L..200L).map(dto),
                    total = 201,
                    limit = 200,
                    offset = 0,
                    nextCursor = MediaCursorDto(updatedSince = "2026-01-01 12:00:05", updatedSinceId = 200),
                )
            // Not requested: the sweep pages by cursor. It makes the probe's
            // total the 201 items the server holds, so no drift sweep starts.
            api.pagesByOffset[1] = PaginatedMediaDto(items = listOf(dto(201)), total = 201, limit = 200, offset = 1)
            api.pagesByCursorId[200] =
                PaginatedMediaDto(
                    items = listOf(dto(201)),
                    total = 1,
                    limit = 200,
                    offset = 0,
                    nextCursor = MediaCursorDto(updatedSince = "2026-01-01 12:00:05", updatedSinceId = 201),
                )

            val result = repo.syncDelta(updatedSince = "2026-01-01 12:00:00", lastSeenWipedAt = null)

            // Probe unfiltered; the sweep starts from the stored timestamp alone,
            // then pages on the exact row the server handed back.
            assertEquals(
                listOf(null to null, "2026-01-01 11:59:59" to null, "2026-01-01 12:00:05" to 200L),
                api.getMediaCursors,
            )
            assertEquals("2026-01-01 12:00:05", (result as ApiResult.Success).value.cursor)
        }

    @Test
    fun `the next sweep starts from the watermark, never from the row id`() =
        runTest {
            // Items 4 and 9 share a second and the sweep ends on 9. Kept as
            // (12:00:05, 9), the next sweep would skip item 4 if it were edited
            // within that same second afterwards: its id stays 4, so it matches
            // neither half of the server's predicate (megamaced/crate#37).
            dao.seed(listOf(entity(4, "Four", category = "music", userId = OWNER), entity(9, "Nine", category = "music", userId = OWNER)))
            api.pagesByOffset[0] =
                PaginatedMediaDto(
                    items =
                        listOf(
                            mediaDto(4, "Four", category = "music", userId = OWNER, updatedAt = "2026-01-01 12:00:05"),
                            mediaDto(9, "Nine", category = "music", userId = OWNER, updatedAt = "2026-01-01 12:00:05"),
                        ),
                    total = 2,
                    limit = 200,
                    offset = 0,
                    nextCursor = MediaCursorDto(updatedSince = "2026-01-01 12:00:05", updatedSinceId = 9),
                    watermark = MediaWatermarkDto(updatedSince = "2026-01-01 12:00:05"),
                )

            val first = repo.syncDelta(updatedSince = "2026-01-01 12:00:00", lastSeenWipedAt = null)
            val committed = (first as ApiResult.Success).value.cursor
            assertEquals("2026-01-01 12:00:05", committed)

            api.getMediaCursors.clear()
            repo.syncDelta(updatedSince = committed, lastSeenWipedAt = null)

            // The next sweep asks for the whole boundary second again, with no id.
            assertEquals(listOf(null to null, "2026-01-01 12:00:04" to null), api.getMediaCursors)
        }

    @Test
    fun `an edit made mid-sweep to a row an earlier page returned reaches the next sync`() =
        runTest {
            // Page one returns 1..200 at 12:00:05. Item 4 is then retitled,
            // still within 12:00:05, so the keyset cursor (12:00:05, 200) has
            // passed it; item 201 is edited at 12:00:07 and page two returns it.
            // Keeping 12:00:07 would start the next sync after item 4's edit.
            val sweepEdits = MediaSyncScript(this@MediaRepositoryImplTest::mediaDto, OWNER)
            dao.seed((1L..201L).map { entity(it, "Item $it", category = "music", userId = OWNER) })
            api.getMediaHandler = sweepEdits::serve

            repo
                .syncDelta(updatedSince = "2026-01-01 12:00:00", lastSeenWipedAt = null)
                .let { assertEquals("2026-01-01 12:00:04", (it as ApiResult.Success).value.cursor) }

            sweepEdits.secondSync = true
            repo.syncDelta(updatedSince = "2026-01-01 12:00:04", lastSeenWipedAt = null)

            assertEquals("Four (edited)", dao.snapshot().first { it.id == 4L }.title)
        }

    @Test
    fun `without a server watermark the first page bounds the next sync`() =
        runTest {
            val sweepEdits = MediaSyncScript(this@MediaRepositoryImplTest::mediaDto, OWNER, sendWatermark = false)
            dao.seed((1L..201L).map { entity(it, "Item $it", category = "music", userId = OWNER) })
            api.getMediaHandler = sweepEdits::serve

            val first = repo.syncDelta(updatedSince = "2026-01-01 12:00:00", lastSeenWipedAt = null)
            val committed = (first as ApiResult.Success).value.cursor
            // The first page's newest stamp, not the later page's 12:00:07.
            assertEquals("2026-01-01 12:00:05", committed)

            sweepEdits.secondSync = true
            repo.syncDelta(updatedSince = committed, lastSeenWipedAt = null)

            assertEquals("Four (edited)", dao.snapshot().first { it.id == 4L }.title)
        }

    @Test
    fun `a total that shrinks under offset paging prunes nothing and asks to retry`() =
        runTest {
            // A server without the cursor, paging by offset, newest first. Page
            // one is 401..202 of 401; item 401 is then deleted elsewhere, so page
            // two (offset 200) starts at 200 and item 201 is never returned.
            // 400 distinct ids against a final total of 400 looked complete.
            dao.seed((1L..401L).map { entity(it, "Item $it", category = "music", userId = OWNER) })
            api.getMediaHandler = { since, _, limit, offset ->
                when {
                    since == null -> PaginatedMediaDto(listOf(mediaDto(401, "Item 401", "music", OWNER)), 401, limit, offset)
                    offset == 0 -> PaginatedMediaDto((401L downTo 202L).map { mediaDto(it, "Item $it", "music", OWNER) }, 401, limit, 0)
                    offset == 200 -> PaginatedMediaDto((200L downTo 1L).map { mediaDto(it, "Item $it", "music", OWNER) }, 400, limit, 200)
                    else -> PaginatedMediaDto(emptyList(), 400, limit, offset)
                }
            }

            val result = repo.syncDelta(updatedSince = null, lastSeenWipedAt = null)

            val sync = (result as ApiResult.Success).value
            assertFalse(sync.complete)
            assertEquals(null, sync.cursor)
            assertTrue("live item 201 is not pruned", dao.snapshot().any { it.id == 201L })
        }

    @Test
    fun `a full sweep by cursor survives a concurrent delete and prunes only what is gone`() =
        runTest {
            // Item 201 was deleted on the server before the sweep; item 1 is
            // deleted after page one returned it. Paged by cursor, page two
            // still starts after the last row read, so nothing unseen slides
            // out of reach, and only item 201 is pruned.
            dao.seed((1L..202L).map { entity(it, "Item $it", category = "music", userId = OWNER) })
            val stamp = "2026-01-01 12:00:00"
            api.getMediaHandler = { since, sinceId, limit, offset ->
                when {
                    since == null -> {
                        PaginatedMediaDto(listOf(mediaDto(202, "Item 202", "music", OWNER)), 201, limit, offset)
                    }

                    sinceId == null -> {
                        PaginatedMediaDto(
                            (1L..200L).map { mediaDto(it, "Item $it", "music", OWNER, updatedAt = stamp) },
                            201,
                            limit,
                            offset,
                            nextCursor = MediaCursorDto(stamp, 200),
                        )
                    }

                    else -> {
                        PaginatedMediaDto(listOf(mediaDto(202, "Item 202", "music", OWNER, updatedAt = stamp)), 1, limit, 0)
                    }
                }
            }

            val result = repo.syncDelta(updatedSince = null, lastSeenWipedAt = null)

            assertTrue((result as ApiResult.Success).value.complete)
            assertEquals((1L..200L).toList() + 202L, dao.snapshot().map { it.id }.sorted())
        }

    @Test
    fun `an incomplete delta leaves the stored cursor exactly where it was`() =
        runTest {
            // The counts agree, so this stays a delta. Its offset pages report
            // a total that changed under them, so rows may have slid past
            // unseen; committing a cursor past them would make the gap
            // permanent — an edit changes no row count, so the drift check
            // never escalates to a full sweep either.
            dao.seed(listOf(entity(1, "One", category = "music", userId = OWNER)))
            api.getMediaHandler = { since, _, limit, offset ->
                when {
                    since == null -> {
                        PaginatedMediaDto(listOf(mediaDto(1, "One", "music", OWNER)), 1, limit, offset)
                    }

                    offset == 0 -> {
                        PaginatedMediaDto(
                            (1L..200L).map { mediaDto(it, "Item $it", "music", OWNER, updatedAt = "2026-06-01 09:00:00") },
                            201,
                            limit,
                            0,
                        )
                    }

                    else -> {
                        PaginatedMediaDto(emptyList(), 200, limit, offset)
                    }
                }
            }

            val result = repo.syncDelta(updatedSince = "2026-01-01 12:00:00", lastSeenWipedAt = null)

            val sync = (result as ApiResult.Success).value
            assertFalse(sync.complete)
            assertEquals("2026-01-01 12:00:00", sync.cursor)
        }

    @Test
    fun `an incomplete full sweep is repeated until one completes, whatever the counts say`() =
        runTest {
            // Three rows are missing locally, so the counts drift and a full
            // sweep starts. Paging by offset, newest first, item 401 is deleted
            // once page one has been read, so page two starts at 200 and item
            // 201 is never returned. The sweep leaves 400 rows — 201 missing,
            // 401 still there — against a server total of 400: equal counts,
            // and a delta from the old cursor would never reconcile them.
            dao.seed(
                (1L..401L)
                    .filterNot { it in setOf(100L, 101L, 201L) }
                    .map { entity(it, "Item $it", category = "music", userId = OWNER) },
            )
            var deleted = false
            api.getMediaHandler = { since, _, limit, offset ->
                val live = if (deleted) 1L..400L else 1L..401L
                val total = live.count()
                if (since == null) {
                    PaginatedMediaDto(listOf(mediaDto(live.last, "Item ${live.last}", "music", OWNER)), total, limit, offset)
                } else {
                    val rows =
                        live
                            .reversed()
                            .drop(offset)
                            .take(limit)
                            .map { mediaDto(it, "Item $it", "music", OWNER, updatedAt = EDITED) }
                            .filter { it.updatedAt!! >= since }
                    if (offset == 0) deleted = true
                    PaginatedMediaDto(rows, total, limit, offset)
                }
            }

            val first = (repo.syncDelta(updatedSince = "2026-01-01 00:00:00", lastSeenWipedAt = null) as ApiResult.Success).value
            assertFalse(first.complete)
            assertEquals(
                "201 still missing, deleted 401 not pruned",
                (1L..401L).filterNot { it == 201L },
                dao.snapshot().map { it.id }.sorted(),
            )
            assertTrue("the full sweep is still owed", sweepPrefs.owed)

            // The retry, from the cursor the incomplete sweep held — one that
            // would select a delta.
            val retry = (repo.syncDelta(updatedSince = first.cursor, lastSeenWipedAt = null) as ApiResult.Success).value

            assertTrue(retry.complete)
            assertEquals("the cursor never walks back", first.cursor, retry.cursor)
            assertEquals((1L..400L).toList(), dao.snapshot().map { it.id }.sorted())
            assertFalse("paid once the collection is reconciled", sweepPrefs.owed)
        }

    @Test
    fun `a full sweep cut off by a failure is resumed as one, whatever cursor another sync stores meanwhile`() =
        runTest {
            // The same shifting pages, but the connection drops on the last
            // one, so the failed run stores nothing. Meanwhile a delta that
            // another worker had already decided on finishes and stores its
            // own cursor, which would select a delta for the next run too.
            val stored = "2026-01-01 00:00:00"
            dao.seed(
                (1L..401L)
                    .filterNot { it in setOf(100L, 101L, 201L) }
                    .map { entity(it, "Item $it", category = "music", userId = OWNER) },
            )
            var deleted = false
            var dropConnection = true
            api.getMediaHandler = { since, _, limit, offset ->
                val live = if (deleted) 1L..400L else 1L..401L
                val total = live.count()
                when {
                    since == null -> {
                        PaginatedMediaDto(listOf(mediaDto(live.last, "Item ${live.last}", "music", OWNER)), total, limit, offset)
                    }

                    offset >= 400 && dropConnection -> {
                        dropConnection = false
                        throw IOException("connection reset")
                    }

                    else -> {
                        val rows =
                            live
                                .reversed()
                                .drop(offset)
                                .take(limit)
                                .map { mediaDto(it, "Item $it", "music", OWNER, updatedAt = EDITED) }
                                .filter { it.updatedAt!! >= since }
                        if (offset == 0) deleted = true
                        PaginatedMediaDto(rows, total, limit, offset)
                    }
                }
            }

            assertEquals(ApiResult.NetworkError, repo.syncDelta(updatedSince = stored, lastSeenWipedAt = null))
            assertTrue("the full sweep is still owed", sweepPrefs.owed)
            val storedByTheOtherWorker = "2026-01-01 00:00:01"

            val retry =
                (repo.syncDelta(updatedSince = storedByTheOtherWorker, lastSeenWipedAt = null) as ApiResult.Success).value

            assertTrue(retry.complete)
            assertEquals((1L..400L).toList(), dao.snapshot().map { it.id }.sorted())
            assertFalse(sweepPrefs.owed)
        }

    @Test
    fun `a complete sweep still advances the cursor on a server with no keyset support`() =
        runTest {
            // Older servers return no nextCursor, so the highest stamp seen is
            // the only cursor available — safe here because the sweep was
            // complete, and paired with the one-second rewind on the next run.
            dao.seed(listOf(entity(1, "One", category = "music", userId = OWNER)))
            api.pagesByOffset[0] =
                PaginatedMediaDto(
                    items = listOf(mediaDto(1, "One", category = "music", userId = OWNER, updatedAt = "2026-06-01 09:00:00")),
                    total = 1,
                    limit = 200,
                    offset = 0,
                )

            val result = repo.syncDelta(updatedSince = "2026-01-01 12:00:00", lastSeenWipedAt = null)

            val sync = (result as ApiResult.Success).value
            assertEquals("2026-06-01 09:00:00", sync.cursor)
            // With no id half, the request rewinds a second to cover a server
            // that filters strictly-greater on a second-resolution stamp.
            assertEquals(listOf(null to null, "2026-01-01 11:59:59" to null), api.getMediaCursors)
        }

    @Test
    fun `an unstrippable image is never uploaded`() =
        runTest {
            // ExifStrip can't decode anything on the JVM, which is the same
            // outcome as a device codec that can't handle the image: the app
            // promises metadata is stripped, so the bytes must not be sent.
            val result = repo.uploadArtwork(1, byteArrayOf(1, 2, 3), "image/jpeg")

            assertTrue(result is ApiResult.HttpError)
            assertEquals(IMAGE_NOT_SANITISED_CODE, (result as ApiResult.HttpError).code)
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
        const val EDITED = "2025-06-01 00:00:00"
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
}

/**
 * A server across two syncs. In the first, item 4 is edited between the two
 * pages of the sweep, inside the second page one was read in, and item 201 is
 * edited later; in the second, a delta from at or before 12:00:05 returns
 * both edits.
 */
private class MediaSyncScript(
    private val dto: (Long, String, String, String?, String?) -> MediaItemDto,
    private val owner: String,
    private val sendWatermark: Boolean = true,
) {
    var secondSync = false

    fun serve(
        updatedSince: String?,
        updatedSinceId: Long?,
        limit: Int,
        offset: Int,
    ): PaginatedMediaDto {
        val watermark =
            if (!sendWatermark) null else MediaWatermarkDto(if (secondSync) "2026-01-01 12:00:08" else "2026-01-01 12:00:04")
        if (updatedSince == null) {
            // The probe: one row and the collection's size.
            return PaginatedMediaDto(listOf(item(1, "2026-01-01 12:00:05")), 201, limit, offset, watermark = watermark)
        }
        if (secondSync) {
            check(updatedSince <= "2026-01-01 12:00:05") { "the second sync started after item 4's edit: $updatedSince" }
            val edited = listOf(item(4, "2026-01-01 12:00:05", "Four (edited)"), item(201, "2026-01-01 12:00:07"))
            return PaginatedMediaDto(edited, 2, limit, offset, watermark = watermark)
        }
        return if (updatedSinceId == null) {
            PaginatedMediaDto(
                (1L..200L).map { item(it, "2026-01-01 12:00:05") },
                201,
                limit,
                offset,
                nextCursor = MediaCursorDto("2026-01-01 12:00:05", 200),
                watermark = watermark,
            )
        } else {
            // The server's clock has moved on by page two, and so has its
            // watermark; only the first request's is safe to keep.
            PaginatedMediaDto(
                listOf(item(201, "2026-01-01 12:00:07")),
                1,
                limit,
                offset,
                nextCursor = MediaCursorDto("2026-01-01 12:00:07", 201),
                watermark = if (sendWatermark) MediaWatermarkDto("2026-01-01 12:00:07") else null,
            )
        }
    }

    private fun item(
        id: Long,
        updatedAt: String,
        title: String = "Item $id",
    ) = dto(id, title, "music", owner, updatedAt)
}
