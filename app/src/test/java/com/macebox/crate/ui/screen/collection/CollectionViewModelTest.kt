package com.macebox.crate.ui.screen.collection

import app.cash.turbine.test
import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.domain.model.Category
import com.macebox.crate.domain.model.CollectionSort
import com.macebox.crate.domain.model.MarketValue
import com.macebox.crate.domain.model.MediaItem
import com.macebox.crate.domain.model.MediaItemDraft
import com.macebox.crate.domain.model.SortDirection
import com.macebox.crate.domain.model.SortField
import com.macebox.crate.domain.model.Status
import com.macebox.crate.domain.repository.MediaRepository
import com.macebox.crate.domain.repository.MediaRepository.RefreshResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModelTest {
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
    fun `state surfaces items, available formats, and default sort`() =
        runTest {
            val repo = FakeMediaRepository().apply {
                seed(
                    listOf(
                        item(1, "OK Computer", artist = "Radiohead", format = "LP", year = 1997),
                        item(2, "Kid A", artist = "Radiohead", format = "CD", year = 2000),
                        item(3, "Pablo Honey", artist = "Radiohead", format = "LP", year = 1993),
                    ),
                )
            }
            val vm = CollectionViewModel(repo)

            vm.uiState.test {
                // Skip the initial empty emission until items arrive.
                var current = awaitItem()
                while (current.items.isEmpty()) current = awaitItem()
                assertEquals(3, current.items.size)
                assertEquals(listOf("CD", "LP"), current.availableFormats)
                assertEquals(CollectionSort.Default, current.sort)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `toggling a format chip filters items`() =
        runTest {
            val repo = FakeMediaRepository().apply {
                seed(
                    listOf(
                        item(1, "OK Computer", format = "LP"),
                        item(2, "Kid A", format = "CD"),
                    ),
                )
            }
            val vm = CollectionViewModel(repo)

            vm.toggleFormat("LP")

            vm.uiState.test {
                var current = awaitItem()
                while (current.selectedFormats.isEmpty() || current.items.size != 1) {
                    current = awaitItem()
                }
                assertEquals(setOf("LP"), current.selectedFormats)
                assertEquals(listOf(1L), current.items.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `selecting Title sort orders alphabetically`() =
        runTest {
            val repo = FakeMediaRepository().apply {
                seed(
                    listOf(
                        item(1, "Bee", updatedAt = "2025-01-03"),
                        item(2, "Apple", updatedAt = "2025-01-02"),
                        item(3, "Cherry", updatedAt = "2025-01-01"),
                    ),
                )
            }
            val vm = CollectionViewModel(repo)
            vm.selectSort(CollectionSort(SortField.Title, SortDirection.Asc))

            vm.uiState.test {
                var current = awaitItem()
                while (current.items.size != 3 || current.sort != CollectionSort(SortField.Title, SortDirection.Asc)) {
                    current = awaitItem()
                }
                assertEquals(listOf("Apple", "Bee", "Cherry"), current.items.map { it.title })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `changing category clears format filter and triggers refresh`() =
        runTest {
            val repo = FakeMediaRepository().apply {
                seed(listOf(item(1, "OK Computer", format = "LP", category = Category.Music)))
            }
            val vm = CollectionViewModel(repo)
            vm.toggleFormat("LP")

            repo.seed(
                listOf(
                    item(2, "Inception", format = "Blu-ray", category = Category.Films),
                ),
            )
            vm.selectCategory(Category.Films)

            vm.uiState.test {
                var current = awaitItem()
                while (current.category != Category.Films || current.items.isEmpty()) {
                    current = awaitItem()
                }
                assertEquals(Category.Films, current.category)
                assertTrue(current.selectedFormats.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(repo.refreshCalls.any { it.first == Category.Films })
        }
}

private class FakeMediaRepository : MediaRepository {
    private val items = MutableStateFlow<List<MediaItem>>(emptyList())
    val refreshCalls = mutableListOf<Pair<Category?, Status?>>()

    fun seed(value: List<MediaItem>) {
        items.value = value
    }

    override fun observeAll(): Flow<List<MediaItem>> = items

    override fun observeByCategory(
        category: Category,
        status: Status?,
    ): Flow<List<MediaItem>> = items.map { list -> list.filter { it.category == category } }

    override fun observe(id: Long): Flow<MediaItem?> = items.map { it.firstOrNull { row -> row.id == id } }

    override suspend fun refresh(
        category: Category?,
        status: Status?,
        limit: Int,
        offset: Int,
    ): ApiResult<RefreshResult> {
        refreshCalls += category to status
        return ApiResult.Success(RefreshResult(items.value.size, limit, offset, items.value.size))
    }

    override suspend fun refreshSingle(id: Long): ApiResult<MediaItem> = error("not used")

    override suspend fun create(draft: MediaItemDraft): ApiResult<MediaItem> = error("not used")

    override suspend fun update(
        id: Long,
        draft: MediaItemDraft,
    ): ApiResult<MediaItem> = error("not used")

    override suspend fun delete(id: Long): ApiResult<Unit> {
        items.value = items.value.filterNot { it.id == id }
        return ApiResult.Success(Unit)
    }

    override suspend fun deleteAll(): ApiResult<Unit> {
        items.value = emptyList()
        return ApiResult.Success(Unit)
    }

    override suspend fun uploadArtwork(
        id: Long,
        bytes: ByteArray,
        mimeType: String,
    ): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun deleteArtwork(id: Long): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun syncDelta(updatedSince: String?): ApiResult<String?> = ApiResult.Success(updatedSince)

    override suspend fun wipeCollection(scopes: List<String>): ApiResult<Unit> = ApiResult.Success(Unit)
}

private fun item(
    id: Long,
    title: String,
    artist: String? = "Test",
    format: String? = "LP",
    year: Int? = 2000,
    updatedAt: String? = "2025-01-0$id",
    category: Category = Category.Music,
) = MediaItem(
    id = id,
    userId = null,
    title = title,
    artist = artist,
    format = format,
    year = year,
    barcode = null,
    notes = null,
    status = Status.Owned,
    category = category,
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
    createdAt = updatedAt,
    updatedAt = updatedAt,
)
