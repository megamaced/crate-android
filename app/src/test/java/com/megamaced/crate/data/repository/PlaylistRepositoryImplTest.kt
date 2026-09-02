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
    private val repo = PlaylistRepositoryImpl(api, dao, mediaItemDao, codec)

    @Test
    fun `refresh drops playlists that no longer exist on the server`() =
        runTest {
            // Upserting alone can never notice a deletion: a playlist deleted on
            // the web simply stops being listed, and the local row then sits
            // there un-openable forever.
            dao.seedPlaylists(
                listOf(
                    PlaylistEntity(id = 1, name = "Kept"),
                    PlaylistEntity(id = 2, name = "Deleted on the web"),
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
            dao.seedPlaylists(listOf(PlaylistEntity(id = 1, name = "Already gone")))
            api.deletePlaylistCode = 404

            val result = repo.delete(1)

            assertTrue(result is ApiResult.Success)
            assertEquals(emptyList<Long>(), dao.snapshot().map { it.id })
        }

    @Test
    fun `a failure that isn't a 404 keeps the local row`() =
        runTest {
            dao.seedPlaylists(listOf(PlaylistEntity(id = 1, name = "Still there")))
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

    private fun playlistDto(
        id: Long,
        name: String,
        items: List<MediaItemDto>? = null,
    ) = PlaylistDto(id = id, name = name, items = items)

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
