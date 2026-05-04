package com.macebox.crate.ui.screen.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.domain.model.Category
import com.macebox.crate.domain.model.MarketValue
import com.macebox.crate.domain.model.MediaItem
import com.macebox.crate.domain.model.MediaItemDraft
import com.macebox.crate.domain.model.RefreshableMarketValues
import com.macebox.crate.domain.model.Status
import com.macebox.crate.domain.repository.EnrichmentRepository
import com.macebox.crate.domain.repository.MediaRepository
import com.macebox.crate.domain.repository.MediaRepository.RefreshResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ItemDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `enrich routes through enrichment repository`() =
        runTest {
            val media = FakeMediaRepository().apply { seed(listOf(item(7))) }
            val enrichment = FakeEnrichmentRepository()
            val vm = newViewModel(media, enrichment, itemId = 7)

            vm.enrich()
            advanceUntilIdle()

            assertEquals(listOf(7L), enrichment.enrichCalls)
        }

    @Test
    fun `delete sets the deleted flag and removes the item`() =
        runTest {
            val media = FakeMediaRepository().apply { seed(listOf(item(7))) }
            val vm = newViewModel(media, FakeEnrichmentRepository(), itemId = 7)

            vm.delete()
            advanceUntilIdle()

            vm.uiState.test {
                var current = awaitItem()
                while (!current.deleted) current = awaitItem()
                assertTrue(current.deleted)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(7L), media.deletedIds)
        }

    private fun newViewModel(
        media: MediaRepository,
        enrichment: EnrichmentRepository,
        itemId: Long,
    ): ItemDetailViewModel = ItemDetailViewModel(SavedStateHandle(mapOf("itemId" to itemId)), media, enrichment)
}

private class FakeMediaRepository : MediaRepository {
    private val items = MutableStateFlow<List<MediaItem>>(emptyList())
    val deletedIds = mutableListOf<Long>()

    fun seed(value: List<MediaItem>) {
        items.value = value
    }

    override fun observeAll(): Flow<List<MediaItem>> = items

    override fun observeByCategory(
        category: Category,
        status: Status?,
    ): Flow<List<MediaItem>> = items

    override fun observe(id: Long): Flow<MediaItem?> = items.map { it.firstOrNull { row -> row.id == id } }

    override suspend fun refresh(
        category: Category?,
        status: Status?,
        limit: Int,
        offset: Int,
    ): ApiResult<RefreshResult> = ApiResult.Success(RefreshResult(0, limit, offset, 0))

    override suspend fun refreshSingle(id: Long): ApiResult<MediaItem> {
        val match = items.value.firstOrNull { it.id == id } ?: return ApiResult.HttpError(404, "missing")
        return ApiResult.Success(match)
    }

    override suspend fun create(draft: MediaItemDraft): ApiResult<MediaItem> = error("not used")

    override suspend fun update(
        id: Long,
        draft: MediaItemDraft,
    ): ApiResult<MediaItem> = error("not used")

    override suspend fun delete(id: Long): ApiResult<Unit> {
        deletedIds += id
        items.value = items.value.filterNot { it.id == id }
        return ApiResult.Success(Unit)
    }

    override suspend fun deleteAll(): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun uploadArtwork(
        id: Long,
        bytes: ByteArray,
        mimeType: String,
    ): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun syncDelta(updatedSince: String?): ApiResult<String?> = ApiResult.Success(updatedSince)
}

private class FakeEnrichmentRepository : EnrichmentRepository {
    val enrichCalls = mutableListOf<Long>()
    val stripCalls = mutableListOf<Long>()
    val marketValueCalls = mutableListOf<Long>()

    override suspend fun enrich(itemId: Long): ApiResult<MediaItem> {
        enrichCalls += itemId
        return ApiResult.Success(item(itemId))
    }

    override suspend fun stripEnrichment(itemId: Long): ApiResult<MediaItem> {
        stripCalls += itemId
        return ApiResult.Success(item(itemId))
    }

    override suspend fun fetchMarketValue(itemId: Long): ApiResult<MediaItem> {
        marketValueCalls += itemId
        return ApiResult.Success(item(itemId))
    }

    override suspend fun listRefreshableMarketValues(): ApiResult<RefreshableMarketValues> = error("not used")
}

private fun item(id: Long) =
    MediaItem(
        id = id,
        userId = null,
        title = "Title",
        artist = "Artist",
        format = "LP",
        year = 2020,
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
        createdAt = null,
        updatedAt = null,
    )
