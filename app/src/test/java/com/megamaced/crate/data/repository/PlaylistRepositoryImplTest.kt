package com.megamaced.crate.data.repository

import app.cash.turbine.test
import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.dto.MediaItemDto
import com.megamaced.crate.data.api.dto.PlaylistDto
import com.megamaced.crate.data.db.entity.PlaylistEntity
import com.megamaced.crate.data.mapper.MediaItemJsonCodec
import com.megamaced.crate.data.mapper.toEntity
import com.megamaced.crate.domain.model.Status
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistRepositoryImplTest {
    private val codec = MediaItemJsonCodec(Json)
    private val api = FakeCrateApiService()
    private val dao = FakePlaylistDao()
    private val mediaItemDao = FakeMediaItemDao()
    private val accountPrefs = FakeAccountPrefs(OWNER)
    private val repo = PlaylistRepositoryImpl(api, dao, mediaItemDao, accountPrefs, codec)

    @Test
    fun `refresh drops playlists that no longer exist on the server`() =
        runTest {
            // Upserting alone can never notice a deletion: a playlist deleted on
            // the web simply stops being listed, and the local row then sits
            // there un-openable forever.
            dao.seedPlaylists(
                listOf(
                    PlaylistEntity(id = 1, name = "Kept", userId = OWNER),
                    PlaylistEntity(id = 2, name = "Deleted on the web", userId = OWNER),
                ),
            )
            api.nextPlaylists = listOf(playlistDto(1, "Kept"))

            val result = repo.refresh()

            assertTrue(result is ApiResult.Success)
            assertEquals(listOf(1L), dao.snapshot().map { it.id })
        }

    @Test
    fun `deleting a playlist the server already dropped still clears the local row`() =
        runTest {
            // Deleting on the web then deleting on the phone answers 404. Giving
            // up there left the row on the device with no way to remove it.
            dao.seedPlaylists(listOf(PlaylistEntity(id = 1, name = "Already gone", userId = OWNER)))
            api.deletePlaylistCode = 404

            val result = repo.delete(1)

            assertTrue(result is ApiResult.Success)
            assertEquals(emptyList<Long>(), dao.snapshot().map { it.id })
        }

    @Test
    fun `a failure that isn't a 404 keeps the local row`() =
        runTest {
            dao.seedPlaylists(listOf(PlaylistEntity(id = 1, name = "Still there", userId = OWNER)))
            api.deletePlaylistCode = 500

            val result = repo.delete(1)

            assertTrue(result is ApiResult.HttpError)
            assertEquals(listOf(1L), dao.snapshot().map { it.id })
        }

    @Test
    fun `playlist items come back in the order the server sent them`() =
        runTest {
            // Ids deliberately descending against playlist order: a read path
            // that leans on Room's row order rather than the stored position
            // returns them backwards.
            api.nextPlaylists =
                listOf(
                    playlistDto(
                        id = 1,
                        name = "Sunday",
                        items = listOf(mediaDto(30, "First"), mediaDto(20, "Second"), mediaDto(10, "Third")),
                    ),
                )
            dao.seedMediaItems(
                api.nextPlaylists
                    .single()
                    .items!!
                    .map { it.toEntity(codec) },
            )

            repo.refresh()

            // Positions were stored...
            assertEquals(listOf(0, 1, 2), dao.crossRefsFor(1).sortedBy { it.position }.map { it.position })
            // ...and the read path honours them.
            repo.observe(1).test {
                assertEquals(listOf("First", "Second", "Third"), awaitItem()?.items?.map { it.title })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a playlist shared with you stays out of My Playlists`() =
        runTest {
            // Opening a shared playlist caches the owner's row in the same
            // table the owned list reads, where it looked like one of yours and
            // stayed until a successful owned-list refresh removed it.
            dao.seedPlaylists(
                listOf(
                    PlaylistEntity(id = 1, name = "Mine", userId = OWNER),
                    PlaylistEntity(id = 2, name = "Pre-userId row"),
                    PlaylistEntity(id = 9, name = "Shared with me", userId = "someone@else"),
                ),
            )

            repo.observeAll().test {
                assertEquals(listOf(1L, 2L), awaitItem().map { it.id }.sorted())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `reconciling the owned list leaves a cached shared playlist alone`() =
        runTest {
            // The listing enumerates the user's own playlists, so it says
            // nothing about one shared with them — pruning that row would drop
            // the cache the shared views read.
            dao.seedPlaylists(
                listOf(
                    PlaylistEntity(id = 1, name = "Mine", userId = OWNER),
                    PlaylistEntity(id = 9, name = "Shared with me", userId = "someone@else"),
                ),
            )
            api.nextPlaylists = listOf(playlistDto(1, "Mine"))

            repo.refresh()

            assertEquals(listOf(1L, 9L), dao.snapshot().map { it.id }.sorted())
        }

    private fun playlistDto(
        id: Long,
        name: String,
        items: List<MediaItemDto>? = null,
        userId: String? = OWNER,
    ) = PlaylistDto(id = id, name = name, userId = userId, items = items)

    private companion object {
        const val OWNER = "david@macemail.co.uk"
    }

    private fun mediaDto(
        id: Long,
        title: String,
    ) = MediaItemDto(
        id = id,
        title = title,
        artist = "test",
        format = "LP",
        status = Status.Owned.apiValue,
        category = "music",
    )
}
