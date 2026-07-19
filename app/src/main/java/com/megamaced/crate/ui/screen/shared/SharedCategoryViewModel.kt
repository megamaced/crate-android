package com.megamaced.crate.ui.screen.shared

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamaced.crate.data.prefs.CollectionPrefs
import com.megamaced.crate.data.prefs.CollectionViewMode
import com.megamaced.crate.data.repository.SharedContentStore
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.CollectionSort
import com.megamaced.crate.domain.model.SharedCategorySummary
import com.megamaced.crate.domain.model.sharedCategories
import com.megamaced.crate.ui.components.FormatBucket
import com.megamaced.crate.ui.screen.collection.ItemGroup
import com.megamaced.crate.ui.screen.collection.comparatorForSort
import com.megamaced.crate.ui.screen.collection.formatBuckets
import com.megamaced.crate.ui.screen.collection.groupItemsForSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SharedCategoryUiState(
    val category: Category,
    val label: String,
    val sort: CollectionSort = CollectionSort.Default,
    val selectedFormats: Set<String> = emptySet(),
    val groups: List<ItemGroup> = emptyList(),
    val availableFormats: List<FormatBucket> = emptyList(),
    val totalCount: Int = 0,
    val viewMode: CollectionViewMode = CollectionViewMode.Card,
    // First owner who granted write access covering this category — the target
    // for the Add affordance. Null = read-only (no Add).
    val writeOwner: String? = null,
    // Toolbar subtitle giving provenance: "Shared by alice" for a single owner,
    // "Shared by 2 people" when the category spans multiple owners. Null when
    // there are no items yet.
    val ownerCaption: String? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

/**
 * Backs a per-owner-agnostic shared category subpage. Reads the process-wide
 * [SharedContentStore] snapshot, filters to one category, and applies the same
 * format-filter / sort / grouping pipeline as [com.megamaced.crate.ui.screen.collection.CollectionViewModel]
 * so the subpage renders identically to a primary category view.
 */
@HiltViewModel
class SharedCategoryViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val store: SharedContentStore,
        private val collectionPrefs: CollectionPrefs,
    ) : ViewModel() {
        private val category: Category =
            Category.fromApi(savedStateHandle.get<String>("category")) ?: Category.Music

        private val sort = MutableStateFlow(CollectionSort.Default)
        private val selectedFormats = MutableStateFlow<Set<String>>(emptySet())
        private val isRefreshing = MutableStateFlow(false)

        val uiState: StateFlow<SharedCategoryUiState> =
            combine(
                store.state,
                sort,
                selectedFormats,
                collectionPrefs.collectionViewModeFlow,
                isRefreshing,
            ) { storeState, sortValue, formats, mode, refreshing ->
                val summary: SharedCategorySummary? =
                    storeState.data?.sharedCategories()?.firstOrNull { it.category == category }
                val items = summary?.items ?: emptyList()
                val buckets = formatBuckets(items)
                val availableSet = buckets.map { it.format }.toSet()
                val activeFormats = formats.intersect(availableSet)
                val filtered =
                    if (activeFormats.isEmpty()) items else items.filter { it.format in activeFormats }
                val sorted = filtered.sortedWith(comparatorForSort(sortValue))
                val owners = summary?.owners.orEmpty()
                SharedCategoryUiState(
                    category = category,
                    label = category.label,
                    sort = sortValue,
                    selectedFormats = activeFormats,
                    groups = groupItemsForSort(sorted, sortValue.axis),
                    availableFormats = buckets,
                    totalCount = items.size,
                    viewMode = mode,
                    writeOwner = summary?.writeOwners?.firstOrNull(),
                    ownerCaption = ownerCaptionFor(owners),
                    isLoading = storeState.data == null && storeState.isLoading,
                    isRefreshing = refreshing,
                    error = storeState.error,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SharedCategoryUiState(category = category, label = category.label),
            )

        init {
            viewModelScope.launch { store.load() }
        }

        fun selectSort(value: CollectionSort) {
            sort.value = value
        }

        fun toggleFormat(format: String) {
            selectedFormats.update { if (format in it) it - format else it + format }
        }

        fun clearFormats() {
            selectedFormats.value = emptySet()
        }

        fun setViewMode(mode: CollectionViewMode) {
            viewModelScope.launch { collectionPrefs.setCollectionViewMode(mode) }
        }

        fun refresh() {
            viewModelScope.launch {
                isRefreshing.value = true
                store.refresh()
                isRefreshing.value = false
            }
        }
    }

private fun ownerCaptionFor(owners: List<String>): String? =
    when (owners.size) {
        0 -> null
        1 -> "Shared by ${owners.first()}"
        else -> "Shared by ${owners.size} people"
    }
