package com.megamaced.crate.data.repository

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.auth.SessionGeneration
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.MarketValue
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.PurchasePrice
import com.megamaced.crate.domain.model.SharedWithMe
import com.megamaced.crate.domain.model.Status
import com.megamaced.crate.domain.repository.ShareRepository
import com.megamaced.crate.testing.unimplemented
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared-content snapshot outlives screens, and used to be keyed by login
 * name alone. Signing out of `sam` on one server and into `sam` on another
 * served the first server's shares — titles, notes, prices — from the cache,
 * and a failed refresh kept them on screen; an answer that arrived after a
 * switch was published under the next account.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedContentStoreTest {
    private val answers = ArrayDeque<CompletableDeferred<ApiResult<SharedWithMe>>>()
    private var requests = 0

    private val store =
        SharedContentStore(
            object : ShareRepository by unimplemented<ShareRepository>() {
                override suspend fun sharedWithMe(): ApiResult<SharedWithMe> {
                    requests++
                    return CompletableDeferred<ApiResult<SharedWithMe>>().also(answers::addLast).await()
                }
            },
            FakeMediaItemDao(),
            FakeAccountPrefs(),
        )

    @Test
    fun `a new session never sees the last one's shares, even under the same login name`() =
        runTest {
            val serverA = shared("A's album")
            loadWith(ApiResult.Success(serverA))
            assertEquals(serverA, store.state.first().data)

            SessionGeneration.advance() // sam signs out of A and into B
            assertNull("hidden at once, before any reload", store.state.first().data)

            val pending = async { store.load() }
            advanceUntilIdle()
            assertEquals("B is fetched although A's snapshot was fresh", 2, requests)
            answers.removeFirst().complete(ApiResult.NetworkError)
            pending.await()

            assertNull("a failed refresh does not bring A's shares back", store.state.first().data)
        }

    @Test
    fun `an answer that arrives after a switch is not published as the next session's`() =
        runTest {
            val pending = async { store.load() }
            advanceUntilIdle()

            SessionGeneration.advance()
            answers.removeFirst().complete(ApiResult.Success(shared("A's album")))
            pending.await()
            assertNull(store.state.first().data)

            // B's own load still fetches rather than trusting A's answer.
            val next = async { store.load() }
            advanceUntilIdle()
            val serverB = shared("B's album")
            answers.removeFirst().complete(ApiResult.Success(serverB))
            next.await()
            assertEquals(2, requests)
            assertEquals(serverB, store.state.first().data)
        }

    @Test
    fun `a refresh keeps a payload only for the session it was fetched for`() {
        val sessionA = SharedContentStore.State(data = shared("A's album"), generation = 7)

        val forA = sessionA.refreshingFor(7)
        assertEquals("A's own refresh keeps A's shares on screen", sessionA.data, forA.data)
        assertTrue(forA.isLoading)

        // Re-stamping A's payload with B's generation is what would let the
        // session filter show it to B.
        val forB = sessionA.refreshingFor(8)
        assertNull(forB.data)
        assertEquals(8, forB.generation)
        assertTrue(forB.isLoading)
    }

    @Test
    fun `a switch during a refresh shows none of the old shares, pending or after failure`() =
        runTest {
            loadWith(ApiResult.Success(shared("A's album")))
            val refreshing = async { store.refresh() }
            advanceUntilIdle()

            SessionGeneration.advance()
            val seen = mutableListOf<SharedContentStore.State>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { store.state.toList(seen) }
            // B's first load queues behind A's refresh, then fetches for itself.
            val loading = async { store.load() }
            advanceUntilIdle()
            answers.removeFirst().complete(ApiResult.NetworkError)
            refreshing.await()
            advanceUntilIdle()
            answers.removeFirst().complete(ApiResult.NetworkError)
            loading.await()

            assertEquals(3, requests)
            assertTrue(seen.isNotEmpty())
            assertTrue("no state after the switch carries A's shares: $seen", seen.all { it.data == null })
            assertNotNull("B's own failure is reported", store.state.first().error)
        }

    private suspend fun TestScope.loadWith(result: ApiResult<SharedWithMe>) {
        val pending = async { store.load() }
        advanceUntilIdle()
        answers.removeFirst().complete(result)
        pending.await()
    }

    private fun shared(title: String) =
        SharedWithMe(
            albums = listOf(album(title)),
            playlists = emptyList(),
            libraries = emptyList(),
            categories = emptyList(),
        )

    private fun album(title: String) =
        MediaItem(
            id = title.hashCode().toLong(),
            userId = "owner",
            title = title,
            artist = null,
            format = null,
            year = null,
            barcode = null,
            notes = null,
            status = Status.Owned,
            category = Category.Music,
            discogsId = null,
            artworkPath = null,
            label = null,
            country = null,
            genres = null,
            tracklist = emptyList(),
            pressingNotes = null,
            discogsArtistId = null,
            artistBio = null,
            artistMembers = emptyList(),
            marketValue = MarketValue(null, null, null, null, null),
            purchasePrice = PurchasePrice(null, null),
            createdAt = null,
            updatedAt = null,
        )
}
