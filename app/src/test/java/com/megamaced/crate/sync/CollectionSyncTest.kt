package com.megamaced.crate.sync

import com.megamaced.crate.data.api.CrateBinaryService
import com.megamaced.crate.data.api.dto.MediaItemDto
import com.megamaced.crate.data.api.dto.MediaWatermarkDto
import com.megamaced.crate.data.api.dto.PaginatedMediaDto
import com.megamaced.crate.data.db.entity.MediaItemEntity
import com.megamaced.crate.data.mapper.MediaItemJsonCodec
import com.megamaced.crate.data.prefs.SyncStatePrefs
import com.megamaced.crate.data.prefs.UserPrefs
import com.megamaced.crate.data.repository.FakeAccountPrefs
import com.megamaced.crate.data.repository.FakeCrateApiService
import com.megamaced.crate.data.repository.FakeFullSweepPrefs
import com.megamaced.crate.data.repository.FakeMediaItemDao
import com.megamaced.crate.data.repository.FakePlaylistDao
import com.megamaced.crate.data.repository.MediaRepositoryImpl
import com.megamaced.crate.data.repository.meDto
import com.megamaced.crate.domain.model.Status
import com.megamaced.crate.testing.unimplemented
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The periodic and the on-demand sync are separate unique work, so two runs
 * can overlap. Two full sweeps after a server wipe used to share one owed-sweep
 * marker and one cursor: the run that finished first paid the debt, and the
 * other, cut off after clearing the database, left rows missing with nothing
 * saying a full sweep was still due.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionSyncTest {
    private val api = FakeCrateApiService().apply { nextMe = meDto(OWNER) }
    private val dao = FakeMediaItemDao()
    private val sweepPrefs = FakeFullSweepPrefs()
    private val prefs = FakeSyncStatePrefs()
    private val sync =
        CollectionSync(
            MediaRepositoryImpl(
                api,
                unimplemented<CrateBinaryService>(),
                dao,
                FakePlaylistDao(),
                FakeAccountPrefs(),
                sweepPrefs,
                MediaItemJsonCodec(Json),
            ),
            prefs,
        )

    @Test
    fun `a second run waits for the first to commit, then starts from what it left`() =
        runTest {
            // The server was wiped, so the first run is a full sweep; its page
            // is held, as though slow, until the second run has been started.
            val page = CompletableDeferred<Unit>()
            var holdPage = true
            api.getMediaHandler = { since, _, limit, offset ->
                val rows = (1L..3L).map(::item).filter { since == null || it.updatedAt!! >= since }
                if (since != null && holdPage) {
                    holdPage = false
                    page.await()
                }
                PaginatedMediaDto(
                    items = if (since == null) rows.take(1) else rows,
                    total = 3,
                    limit = limit,
                    offset = offset,
                    wipedAt = WIPED,
                    watermark = MediaWatermarkDto(WATERMARK),
                )
            }

            val first = async { sync.run() }
            advanceUntilIdle()
            val second = async { sync.run() }
            advanceUntilIdle()
            assertEquals("the second run hasn't so much as probed", 2, api.getMediaCursors.size)

            page.complete(Unit)
            assertEquals(CollectionSync.Outcome.Done, first.await())
            assertEquals(CollectionSync.Outcome.Done, second.await())

            assertEquals(listOf(1L, 2L, 3L), dao.snapshot().map { it.id }.sorted())
            assertFalse(sweepPrefs.owed)
            assertEquals(WIPED, prefs.state.value.lastSeenWipedAt)
            // A delta from the first run's commit, not a second full sweep
            // over a wipe the first had already handled.
            assertEquals("2026-10-02 11:59:59" to null, api.getMediaCursors.last())
            assertEquals(WATERMARK, prefs.state.value.lastSyncCursor)
        }

    @Test
    fun `the sync repair brings down a rating the delta cursor has already passed`() =
        runTest {
            seedRatedOnServerUnratedHere()
            prefs.repairPending = true

            assertEquals(CollectionSync.Outcome.Done, sync.run())

            assertEquals(4, dao.snapshot().single().rating)
        }

    @Test
    fun `without the repair a delta sync never revisits that row`() =
        runTest {
            seedRatedOnServerUnratedHere()

            assertEquals(CollectionSync.Outcome.Done, sync.run())

            assertNull(dao.snapshot().single().rating)
        }

    /**
     * A row cached unrated by a version that ignored the field, which the
     * server has at 4 stars, set before the stored cursor.
     */
    private fun seedRatedOnServerUnratedHere() {
        dao.seed(
            listOf(
                MediaItemEntity(
                    id = 1,
                    userId = OWNER,
                    title = "Item 1",
                    artist = "test",
                    format = "LP",
                    status = Status.Owned.apiValue,
                    category = "music",
                ),
            ),
        )
        prefs.state.value = UserPrefs(lastSyncCursor = WATERMARK, lastSeenWipedAt = WIPED)
        api.getMediaHandler = { since, _, limit, offset ->
            PaginatedMediaDto(
                items = listOf(item(1, rating = 4)).filter { since == null || it.updatedAt!! >= since },
                total = 1,
                limit = limit,
                offset = offset,
                wipedAt = WIPED,
                watermark = MediaWatermarkDto(WATERMARK),
            )
        }
    }

    private fun item(
        id: Long,
        rating: Int? = null,
    ) = MediaItemDto(
        id = id,
        userId = OWNER,
        title = "Item $id",
        artist = "test",
        format = "LP",
        status = Status.Owned.apiValue,
        category = "music",
        updatedAt = "2026-10-02 10:00:00",
        rating = rating,
    )

    private companion object {
        const val OWNER = "owner"
        const val WIPED = "2026-10-01 00:00:00"
        const val WATERMARK = "2026-10-02 12:00:00"
    }
}

private class FakeSyncStatePrefs : SyncStatePrefs {
    val state = MutableStateFlow(UserPrefs())

    override val flow: Flow<UserPrefs> = state

    /** Set to make the next run a repair, as raising the repair version does. */
    var repairPending = false

    override suspend fun consumeSyncRepair(): Boolean {
        if (!repairPending) return false
        repairPending = false
        state.update { it.copy(lastSyncCursor = null) }
        return true
    }

    override suspend fun setLastSyncCursor(cursor: String?) {
        state.update { it.copy(lastSyncCursor = cursor) }
    }

    override suspend fun setLastSeenWipedAt(wipedAt: String?) {
        state.update { it.copy(lastSeenWipedAt = wipedAt) }
    }
}
