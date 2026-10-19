package com.megamaced.crate.data.repository

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.CrateApiService
import com.megamaced.crate.data.api.CrateBinaryService
import com.megamaced.crate.data.api.dto.MediaItemDto
import com.megamaced.crate.data.auth.SessionGeneration
import com.megamaced.crate.data.auth.SessionStamp
import com.megamaced.crate.data.auth.StaleSessionException
import com.megamaced.crate.data.db.dao.SessionGuardedMediaItemDao
import com.megamaced.crate.data.db.dao.SessionGuardedPlaylistDao
import com.megamaced.crate.data.db.entity.PlaylistEntity
import com.megamaced.crate.data.mapper.MediaItemJsonCodec
import com.megamaced.crate.data.mapper.toEntity
import com.megamaced.crate.testing.unimplemented
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A request still in flight when the user signs out can succeed afterwards.
 * Its response used to be written into the database regardless — after the
 * wipe, so straight into the next account's cache, where item detail reads
 * it with no owner filter. Writes now belong to the session that started them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionGuardTest {
    private val rows = FakeMediaItemDao()
    private val playlists = FakePlaylistDao()
    private val response = CompletableDeferred<MediaItemDto>()
    private val deletion = CompletableDeferred<Unit>()

    private val api =
        object : CrateApiService by unimplemented<CrateApiService>() {
            override suspend fun getMediaItem(id: Long): MediaItemDto = response.await()

            override suspend fun deletePlaylist(id: Long) = deletion.await()
        }

    private val repo =
        MediaRepositoryImpl(
            api,
            unimplemented<CrateBinaryService>(),
            SessionGuardedMediaItemDao(rows),
            SessionGuardedPlaylistDao(playlists),
            FakeAccountPrefs(),
            FakeFullSweepPrefs(),
            MediaItemJsonCodec(Json),
        )

    @Test
    fun `a response that lands after sign-out is not cached for the next account`() =
        runTest {
            val call = async { repo.refreshSingle(7) }
            advanceUntilIdle()

            SessionGeneration.advance() // sign-out, then the next sign-in
            response.complete(dto(7))

            assertEquals(ApiResult.Unauthorised, call.await())
            assertTrue(rows.snapshot().isEmpty())
        }

    @Test
    fun `a response within its own session is cached`() =
        runTest {
            response.complete(dto(7))

            assertTrue(repo.refreshSingle(7) is ApiResult.Success)
            assertEquals(listOf(7L), rows.snapshot().map { it.id })
        }

    @Test
    fun `a write waits out the wipe and then finds its session over`() =
        runTest {
            // Stamped by the outgoing session, before the wipe begins.
            val outgoing = SessionStamp(SessionGeneration.current())
            val wipeRunning = CompletableDeferred<Unit>()
            val finishWipe = CompletableDeferred<Unit>()
            launch {
                SessionGeneration.exclusive {
                    wipeRunning.complete(Unit)
                    SessionGeneration.advance()
                    finishWipe.await()
                }
            }
            wipeRunning.await()
            val write = async(outgoing) { runCatching { SessionGeneration.write { rows.upsert(entity(7)) } } }
            advanceUntilIdle()
            assertFalse("the write must wait for the wipe", write.isCompleted)

            finishWipe.complete(Unit)

            assertTrue(write.await().exceptionOrNull() is StaleSessionException)
            assertTrue(rows.snapshot().isEmpty())
        }

    @Test
    fun `a playlist deleted for an ended session leaves the next one's row alone`() =
        runTest {
            playlists.seedPlaylists(listOf(PlaylistEntity(id = 1, name = "Theirs", userId = "next")))
            val playlistRepo =
                PlaylistRepositoryImpl(
                    api,
                    SessionGuardedPlaylistDao(playlists),
                    SessionGuardedMediaItemDao(rows),
                    FakeAccountPrefs(),
                    MediaItemJsonCodec(Json),
                )
            val call = async { playlistRepo.delete(1) }
            advanceUntilIdle()

            SessionGeneration.advance()
            deletion.complete(Unit)

            assertEquals(ApiResult.Unauthorised, call.await())
            assertEquals(listOf(1L), playlists.snapshot().map { it.id })
        }

    private fun dto(id: Long) = MediaItemDto(id = id, title = "Item $id", status = "owned", category = "music")

    private fun entity(id: Long) = dto(id).toEntity(MediaItemJsonCodec(Json))
}
