package com.megamaced.crate.ui.screen.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.prefs.CollectionPrefs
import com.megamaced.crate.data.prefs.CollectionViewMode
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.CategorySortConfig
import com.megamaced.crate.domain.model.CollectionSort
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.SortDirection
import com.megamaced.crate.domain.model.SortField
import com.megamaced.crate.domain.repository.MediaRepository
import com.megamaced.crate.ui.components.FormatBucket
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectionUiState(
    val category: Category = Category.Music,
    val sort: CollectionSort = CollectionSort.Default,
    val selectedFormats: Set<String> = emptySet(),
    val items: List<MediaItem> = emptyList(),
    val availableFormats: List<FormatBucket> = emptyList(),
    val totalCount: Int = 0,
    val viewMode: CollectionViewMode = CollectionViewMode.Card,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

private data class Filters(
    val category: Category,
    val sort: CollectionSort,
    val selectedFormats: Set<String>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CollectionViewModel
    @Inject
    constructor(
        private val mediaRepository: MediaRepository,
        private val collectionPrefs: CollectionPrefs,
    ) : ViewModel() {
        private val category = MutableStateFlow(Category.Music)
        private val sort = MutableStateFlow(CollectionSort.Default)
        private val selectedFormats = MutableStateFlow<Set<String>>(emptySet())
        private val isRefreshing = MutableStateFlow(false)
        private val errorMessage = MutableStateFlow<String?>(null)

        private val filters = combine(category, sort, selectedFormats, ::Filters)
        private val itemsForCategory = category.flatMapLatest { mediaRepository.observeByCategory(it) }
        private val viewMode = collectionPrefs.collectionViewModeFlow

        val uiState: StateFlow<CollectionUiState> =
            combine(filters, itemsForCategory, viewMode, isRefreshing, errorMessage) { f, items, mode, refreshing, err ->
                // Counts are computed against the full category list, not the
                // currently-filtered subset, so toggling one chip doesn't reshuffle
                // every other chip's number — mirrors CollectionView.vue.
                val buckets = items
                    .mapNotNull { it.format?.takeIf { v -> v.isNotBlank() } }
                    .groupingBy { it }
                    .eachCount()
                    .toSortedMap()
                    .map { (fmt, count) -> FormatBucket(fmt, count) }
                val availableSet = buckets.map { it.format }.toSet()
                val activeFormats = f.selectedFormats.intersect(availableSet)
                val filtered =
                    if (activeFormats.isEmpty()) items else items.filter { it.format in activeFormats }
                val sorted = filtered.sortedWith(comparatorFor(f.sort))

                CollectionUiState(
                    category = f.category,
                    sort = f.sort,
                    selectedFormats = activeFormats,
                    items = sorted,
                    availableFormats = buckets,
                    totalCount = items.size,
                    viewMode = mode,
                    isRefreshing = refreshing,
                    errorMessage = err,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = CollectionUiState(),
            )

        init {
            refresh()
        }

        fun selectCategory(value: Category) {
            if (value != category.value) {
                category.value = value
                selectedFormats.value = emptySet()
                // If the active sort axis isn't supported by the new category
                // (e.g. coming from Games' "Format" axis to Music), fall back
                // to the default — mirrors CollectionView.vue's reset logic.
                val cfg = CategorySortConfig.forCategory(value)
                if (!cfg.supports(sort.value.axis)) {
                    sort.value = CollectionSort.Default
                }
                refresh()
            }
        }

        fun toggleFormat(format: String) {
            selectedFormats.update { current ->
                if (format in current) current - format else current + format
            }
        }

        fun clearFormats() {
            selectedFormats.value = emptySet()
        }

        fun selectSort(value: CollectionSort) {
            sort.value = value
        }

        fun setViewMode(mode: CollectionViewMode) {
            viewModelScope.launch { collectionPrefs.setCollectionViewMode(mode) }
        }

        fun refresh() {
            viewModelScope.launch {
                isRefreshing.value = true
                errorMessage.value = null
                when (val result = mediaRepository.refresh(category = category.value, limit = 200)) {
                    is ApiResult.Success -> { /* DAO updates flow downstream */ }
                    ApiResult.NetworkError -> errorMessage.value = "Couldn't reach the server."
                    is ApiResult.HttpError -> errorMessage.value = result.message ?: "Server error (${result.code})."
                    ApiResult.Unauthorised -> { /* SessionManager already triggered logout */ }
                }
                isRefreshing.value = false
            }
        }

        fun dismissError() {
            errorMessage.value = null
        }
    }

private fun comparatorFor(sort: CollectionSort): Comparator<MediaItem> {
    val base: Comparator<MediaItem> = when (sort.axis) {
        SortField.CreatedAt -> compareBy { it.createdAt.orEmpty() }
        SortField.Artist -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist.orEmpty() }
        SortField.Title -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        SortField.Year -> compareBy { it.year ?: Int.MIN_VALUE }
        SortField.Format -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.format.orEmpty() }
        SortField.MarketValue -> compareBy { it.marketValue.main ?: Double.NEGATIVE_INFINITY }
    }
    return if (sort.direction == SortDirection.Desc) base.reversed() else base
}
