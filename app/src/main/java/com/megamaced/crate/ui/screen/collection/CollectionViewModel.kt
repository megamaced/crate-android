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
import com.megamaced.crate.domain.repository.MediaRepository
import com.megamaced.crate.domain.repository.SettingsRepository
import com.megamaced.crate.ui.components.FormatBucket
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
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
    val groups: List<ItemGroup> = emptyList(),
    val availableFormats: List<FormatBucket> = emptyList(),
    val totalCount: Int = 0,
    val viewMode: CollectionViewMode = CollectionViewMode.Card,
    val visibleCategories: List<Category> = Category.entries,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

// An ordered section of the collection list. `header` is the section label
// (first letter, decade, date bucket, format …) or null for ungrouped axes.
// Grouping logic lives in CollectionGrouping.kt so the shared-category view can
// reuse it verbatim.
data class ItemGroup(
    val header: String?,
    val items: List<MediaItem>,
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
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        // category is initialised optimistically to Music — the init block
        // resolves the persisted last category (if any) and updates it before
        // the first user interaction. If the persisted category is now hidden
        // we fall back to the first visible category instead.
        private val category = MutableStateFlow(Category.Music)
        private val sort = MutableStateFlow(CollectionSort.Default)
        private val selectedFormats = MutableStateFlow<Set<String>>(emptySet())
        private val isRefreshing = MutableStateFlow(false)
        private val errorMessage = MutableStateFlow<String?>(null)
        private var initialCategoryRestored = false

        private val filters = combine(category, sort, selectedFormats, ::Filters)
        private val itemsForCategory = category.flatMapLatest { mediaRepository.observeByCategory(it) }
        private val viewMode = collectionPrefs.collectionViewModeFlow
        private val hiddenCategories = settingsRepository.hiddenCategoriesFlow

        val uiState: StateFlow<CollectionUiState> =
            combine(
                filters,
                itemsForCategory,
                viewMode,
                isRefreshing,
                errorMessage,
                hiddenCategories,
            ) { args ->
                @Suppress("UNCHECKED_CAST")
                val f = args[0] as Filters

                @Suppress("UNCHECKED_CAST")
                val items = args[1] as List<MediaItem>
                val mode = args[2] as CollectionViewMode
                val refreshing = args[3] as Boolean

                @Suppress("UNCHECKED_CAST")
                val err = args[4] as String?

                @Suppress("UNCHECKED_CAST")
                val hidden = args[5] as Set<Category>
                val buckets = formatBuckets(items)
                val availableSet = buckets.map { it.format }.toSet()
                val activeFormats = f.selectedFormats.intersect(availableSet)
                val filtered =
                    if (activeFormats.isEmpty()) items else items.filter { it.format in activeFormats }
                val sorted = filtered.sortedWith(comparatorForSort(f.sort))
                val groups = groupItemsForSort(sorted, f.sort.axis)

                CollectionUiState(
                    category = f.category,
                    sort = f.sort,
                    selectedFormats = activeFormats,
                    items = sorted,
                    groups = groups,
                    availableFormats = buckets,
                    totalCount = items.size,
                    viewMode = mode,
                    visibleCategories = Category.entries.filter { it !in hidden },
                    isRefreshing = refreshing,
                    errorMessage = err,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = CollectionUiState(),
            )

        init {
            viewModelScope.launch {
                restoreInitialCategory()
                refresh()
            }
        }

        /**
         * Reads the persisted last-used category from DataStore (Phase A27).
         * If the persisted value is now hidden — or never set — falls back to
         * the first visible category. Only runs once per ViewModel instance.
         */
        private suspend fun restoreInitialCategory() {
            if (initialCategoryRestored) return
            initialCategoryRestored = true
            // Capture the construction-time default so we don't clobber a
            // selectCategory() the user made before our init coroutine was
            // dispatched — the StandardTestDispatcher in unit tests can defer
            // this far enough that this race actually matters.
            val initial = category.value
            val persisted = collectionPrefs.lastCategoryFlow.firstOrNull()
            val hidden = settingsRepository.hiddenCategoriesFlow.firstOrNull().orEmpty()
            val visible = Category.entries.filter { it !in hidden }
            if (visible.isEmpty()) return // server enforces non-empty; defensive guard
            val resolved =
                when {
                    persisted != null && persisted in visible -> persisted
                    Category.Music in visible -> Category.Music
                    else -> visible.first()
                }
            if (category.value != initial) return // user interacted while we were awaiting
            if (resolved != category.value) {
                category.value = resolved
                val cfg = CategorySortConfig.forCategory(resolved)
                if (!cfg.supports(sort.value.axis)) {
                    sort.value = CollectionSort.Default
                }
            }
        }

        fun selectCategory(value: Category) {
            // User picked their own category — short-circuit any pending init
            // restoration so it can't overwrite this choice when its async
            // DataStore read completes.
            initialCategoryRestored = true
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
                // Persist for next launch (Phase A27).
                viewModelScope.launch { collectionPrefs.setLastCategory(value) }
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
