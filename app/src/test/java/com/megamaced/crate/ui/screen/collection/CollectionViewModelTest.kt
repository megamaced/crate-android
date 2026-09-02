package com.megamaced.crate.ui.screen.collection

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.prefs.CollectionPrefs
import com.megamaced.crate.data.prefs.CollectionViewMode
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.CollectionSort
import com.megamaced.crate.domain.model.MarketValue
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.MediaItemDraft
import com.megamaced.crate.domain.model.PurchasePrice
import com.megamaced.crate.domain.model.SortDirection
import com.megamaced.crate.domain.model.SortField
import com.megamaced.crate.domain.model.Status
import com.megamaced.crate.domain.repository.MediaRepository
import com.megamaced.crate.domain.repository.MediaRepository.RefreshResult
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
            val vm = CollectionViewModel(SavedStateHandle(), repo, FakeCollectionPrefs(), StubSettingsRepository(), dispatcher)

            vm.uiState.test {
                // Skip the initial empty emission until items arrive.
                var current = awaitItem()
                while (current.items.isEmpty()) current = awaitItem()
                assertEquals(3, current.items.size)
                assertEquals(
                    listOf(FilterBucket("CD", 1), FilterBucket("LP", 2)),
                    current.availableFormats,
                )
                assertEquals(3, current.totalCount)
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
            val vm = CollectionViewModel(SavedStateHandle(), repo, FakeCollectionPrefs(), StubSettingsRepository(), dispatcher)

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
            val vm = CollectionViewModel(SavedStateHandle(), repo, FakeCollectionPrefs(), StubSettingsRepository(), dispatcher)
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
    fun `genre nav arg pre-filters the list and pins the category`() =
        runTest {
            val repo = FakeMediaRepository().apply {
                seed(
                    listOf(
                        item(1, "OK Computer", genres = "Alternative Rock, Art Rock"),
                        item(2, "Blue Lines", genres = "Trip Hop"),
                        item(3, "Kid A", genres = "art rock"),
                    ),
                )
            }
            val args = SavedStateHandle(mapOf("category" to "music", "genre" to "Art Rock"))
            val vm = CollectionViewModel(args, repo, FakeCollectionPrefs(), StubSettingsRepository(), dispatcher)

            vm.uiState.test {
                var current = awaitItem()
                while (current.selectedGenre == null) current = awaitItem()
                // Case-insensitive, and only the two items carrying that genre.
                assertEquals(listOf(1L, 3L), current.items.map { it.id }.sorted())
                assertEquals(Category.Music, current.category)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a genre that no longer exists falls back to showing everything`() =
        runTest {
            val repo = FakeMediaRepository().apply {
                seed(listOf(item(1, "OK Computer", genres = "Alternative Rock")))
            }
            val args = SavedStateHandle(mapOf("genre" to "Vaporwave"))
            val vm = CollectionViewModel(args, repo, FakeCollectionPrefs(), StubSettingsRepository(), dispatcher)

            vm.uiState.test {
                var current = awaitItem()
                while (current.items.isEmpty()) current = awaitItem()
                assertEquals(null, current.selectedGenre)
                assertEquals(1, current.items.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `format nav arg pre-filters the list`() =
        runTest {
            val repo = FakeMediaRepository().apply {
                seed(
                    listOf(
                        item(1, "OK Computer", format = "LP"),
                        item(2, "Kid A", format = "CD"),
                    ),
                )
            }
            val args = SavedStateHandle(mapOf("category" to "music", "format" to "LP"))
            val vm = CollectionViewModel(args, repo, FakeCollectionPrefs(), StubSettingsRepository(), dispatcher)

            vm.uiState.test {
                var current = awaitItem()
                while (current.selectedFormats.isEmpty()) current = awaitItem()
                assertEquals(setOf("LP"), current.selectedFormats)
                assertEquals(listOf(1L), current.items.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `decade nav arg filters by decade rather than by the exact year tapped`() =
        runTest {
            val repo = FakeMediaRepository().apply {
                seed(
                    listOf(
                        item(1, "OK Computer", year = 1997),
                        item(2, "Pablo Honey", year = 1993),
                        item(3, "Kid A", year = 2000),
                    ),
                )
            }
            val args = SavedStateHandle(mapOf("category" to "music", "decade" to "1990s"))
            val vm = CollectionViewModel(args, repo, FakeCollectionPrefs(), StubSettingsRepository(), dispatcher)

            vm.uiState.test {
                var current = awaitItem()
                while (current.selectedDecade == null) current = awaitItem()
                assertEquals("1990s", current.selectedDecade)
                // Tapping 1997 brings back everything from that decade, not
                // only the items released in 1997.
                assertEquals(listOf(1L, 2L), current.items.map { it.id }.sorted())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `owned is the default status and wanted items stay out of it`() =
        runTest {
            val repo = FakeMediaRepository().apply {
                seed(
                    listOf(
                        item(1, "OK Computer", format = "LP"),
                        item(2, "Kid A", format = "CD"),
                        item(3, "Amnesiac", format = "Cassette", status = Status.Wanted),
                    ),
                )
            }
            val vm = CollectionViewModel(SavedStateHandle(), repo, FakeCollectionPrefs(), StubSettingsRepository(), dispatcher)

            vm.uiState.test {
                var current = awaitItem()
                while (current.items.isEmpty()) current = awaitItem()
                assertEquals(Status.Owned, current.status)
                assertEquals(listOf(1L, 2L), current.items.map { it.id }.sorted())
                assertEquals(2, current.totalCount)
                // The chips describe the list on screen, not the whole category:
                // Cassette is only on the wanted list, so offering it here would
                // be a filter that matches nothing.
                assertEquals(listOf("CD", "LP"), current.availableFormats.map { it.value })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `switching status swaps the list and drops the value filters`() =
        runTest {
            val repo = FakeMediaRepository().apply {
                seed(
                    listOf(
                        item(1, "OK Computer", format = "LP"),
                        item(2, "Kid A", format = "CD"),
                        item(3, "Amnesiac", format = "LP", status = Status.Wanted),
                        item(4, "In Rainbows", format = "CD", status = Status.Wanted),
                    ),
                )
            }
            val vm = CollectionViewModel(SavedStateHandle(), repo, FakeCollectionPrefs(), StubSettingsRepository(), dispatcher)

            vm.uiState.test {
                var current = awaitItem()
                while (current.items.isEmpty()) current = awaitItem()

                vm.toggleFormat("LP")
                while (current.selectedFormats.isEmpty()) current = awaitItem()
                assertEquals(listOf(1L), current.items.map { it.id })

                vm.selectStatus(Status.Wanted)
                while (current.status != Status.Wanted) current = awaitItem()
                // The status switch and the cleared filters can arrive as
                // separate emissions; settle on the one carrying both. LP
                // exists on both lists, so a filter that survived would show
                // item 3 alone.
                while (current.selectedFormats.isNotEmpty()) current = awaitItem()
                assertEquals(listOf(3L, 4L), current.items.map { it.id }.sorted())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a wanted status nav arg opens the list on the wanted items`() =
        runTest {
            val repo = FakeMediaRepository().apply {
                seed(
                    listOf(
                        item(1, "OK Computer", genres = "Art Rock"),
                        item(2, "Amnesiac", genres = "Art Rock", status = Status.Wanted),
                    ),
                )
            }
            val args = SavedStateHandle(mapOf("genre" to "Art Rock", "status" to "wanted"))
            val vm = CollectionViewModel(args, repo, FakeCollectionPrefs(), StubSettingsRepository(), dispatcher)

            vm.uiState.test {
                var current = awaitItem()
                while (current.items.isEmpty()) current = awaitItem()
                assertEquals(Status.Wanted, current.status)
                // The tapped item's own list, not the owned copy of that genre.
                assertEquals(listOf(2L), current.items.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `changing category clears format filter and triggers refresh`() =
        runTest {
            val repo = FakeMediaRepository().apply {
                seed(listOf(item(1, "OK Computer", format = "LP", category = Category.Music)))
            }
            val vm = CollectionViewModel(SavedStateHandle(), repo, FakeCollectionPrefs(), StubSettingsRepository(), dispatcher)
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

    override suspend fun uploadArtwork(
        id: Long,
        bytes: ByteArray,
        mimeType: String,
    ): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun deleteArtwork(id: Long): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun uploadPhoto(
        id: Long,
        slot: Int,
        bytes: ByteArray,
        mimeType: String,
    ): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun deletePhoto(
        id: Long,
        slot: Int,
    ): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun syncDelta(
        updatedSince: String?,
        cursorId: Long?,
        lastSeenWipedAt: String?,
    ): ApiResult<MediaRepository.SyncResult> =
        ApiResult.Success(MediaRepository.SyncResult(cursor = updatedSince, cursorId = cursorId, wipedAt = lastSeenWipedAt))

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
    genres: String? = null,
    status: Status = Status.Owned,
) = MediaItem(
    id = id,
    userId = null,
    title = title,
    artist = artist,
    format = format,
    year = year,
    barcode = null,
    notes = null,
    status = status,
    category = category,
    discogsId = null,
    artworkPath = null,
    label = null,
    country = null,
    genres = genres,
    tracklist = emptyList(),
    pressingNotes = null,
    discogsArtistId = null,
    artistBio = null,
    artistMembers = emptyList(),
    marketValue = MarketValue(null, null, null, null, null),
    purchasePrice = PurchasePrice(null, null),
    createdAt = updatedAt,
    updatedAt = updatedAt,
)

private class FakeCollectionPrefs : CollectionPrefs {
    private val mode = MutableStateFlow(CollectionViewMode.Card)
    private val lastCategory = MutableStateFlow<Category?>(null)

    override val collectionViewModeFlow: Flow<CollectionViewMode> = mode

    override suspend fun setCollectionViewMode(mode: CollectionViewMode) {
        this.mode.value = mode
    }

    override val lastCategoryFlow: Flow<Category?> = lastCategory

    override suspend fun setLastCategory(category: Category) {
        this.lastCategory.value = category
    }
}

// Minimal SettingsRepository stub for CollectionViewModelTest — only the
// hidden_categories surface is exercised; everything else explodes if touched.
private class StubSettingsRepository : com.megamaced.crate.domain.repository.SettingsRepository {
    override val hiddenCategoriesFlow: Flow<Set<com.megamaced.crate.domain.model.Category>> =
        kotlinx.coroutines.flow.flowOf(emptySet())

    override suspend fun setHiddenCategories(categories: Set<com.megamaced.crate.domain.model.Category>) =
        com.megamaced.crate.data.api.ApiResult
            .Success(Unit)

    override val onlineRecommendationsFlow: Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)

    override suspend fun setOnlineRecommendations(enabled: Boolean) =
        com.megamaced.crate.data.api.ApiResult
            .Success(Unit)

    override suspend fun getMe() = error("not used")

    override suspend fun hasDiscogsToken() = error("not used")

    override suspend fun setDiscogsToken(token: String) = error("not used")

    override suspend fun hasTmdbToken() = error("not used")

    override suspend fun setTmdbToken(token: String) = error("not used")

    override suspend fun hasRawgKey() = error("not used")

    override suspend fun setRawgKey(key: String) = error("not used")

    override suspend fun hasComicVineKey() = error("not used")

    override suspend fun setComicVineKey(key: String) = error("not used")

    override suspend fun hasPriceChartingToken() = error("not used")

    override suspend fun setPriceChartingToken(token: String) = error("not used")

    override suspend fun getMarketSettings() = error("not used")

    override suspend fun setMarketSettings(settings: com.megamaced.crate.domain.model.MarketSettings) = error("not used")

    override suspend fun getCurrencies() = error("not used")
}
