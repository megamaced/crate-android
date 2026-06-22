package com.megamaced.crate.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.CrateApiService
import com.megamaced.crate.data.api.apiCall
import com.megamaced.crate.data.api.dto.ComicVineSearchResultDto
import com.megamaced.crate.data.api.dto.DiscogsSearchResultDto
import com.megamaced.crate.data.api.dto.OpenLibraryResultDto
import com.megamaced.crate.data.api.dto.RawgSearchResultDto
import com.megamaced.crate.data.api.dto.TmdbSearchResultDto
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.repository.MediaRepository
import com.megamaced.crate.domain.repository.SettingsRepository
import com.megamaced.crate.ui.screen.addedit.ExternalSearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchTab { Collection, External }

data class SearchUiState(
    val query: String = "",
    val tab: SearchTab = SearchTab.Collection,
    val externalCategory: Category = Category.Music,
    val collectionResults: List<MediaItem> = emptyList(),
    val externalResults: List<ExternalSearchResult> = emptyList(),
    val isExternalLoading: Boolean = false,
    val externalError: String? = null,
    val externalHasSearched: Boolean = false,
    val visibleCategories: List<Category> = Category.entries,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val api: CrateApiService,
        mediaRepository: MediaRepository,
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val query = MutableStateFlow("")
        private val tab = MutableStateFlow(SearchTab.Collection)
        private val externalCategory = MutableStateFlow(Category.Music)
        private val externalResults = MutableStateFlow<List<ExternalSearchResult>>(emptyList())
        private val isExternalLoading = MutableStateFlow(false)
        private val externalError = MutableStateFlow<String?>(null)
        private val externalHasSearched = MutableStateFlow(false)

        private val collectionResults: StateFlow<List<MediaItem>> =
            query
                .debounce(150)
                .flatMapLatest { q ->
                    if (q.isBlank()) {
                        flowOf(emptyList())
                    } else {
                        mediaRepository
                            .observeAll()
                            .map { items -> items.filter { it.matches(q) } }
                    }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = emptyList(),
                )

        private val hiddenCategories = settingsRepository.hiddenCategoriesFlow

        val uiState: StateFlow<SearchUiState> =
            combine(
                combine(query, tab, externalCategory) { q, t, c -> Triple(q, t, c) },
                collectionResults,
                externalResults,
                combine(isExternalLoading, externalError, externalHasSearched) { l, e, s -> Triple(l, e, s) },
                hiddenCategories,
            ) { qtc, coll, ext, lesTriple, hidden ->
                val (q, t, c) = qtc
                val (loading, err, searched) = lesTriple
                SearchUiState(
                    query = q,
                    tab = t,
                    externalCategory = c,
                    collectionResults = coll,
                    externalResults = ext,
                    isExternalLoading = loading,
                    externalError = err,
                    externalHasSearched = searched,
                    visibleCategories = Category.entries.filter { it !in hidden },
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SearchUiState(),
            )

        fun onQueryChange(value: String) {
            query.value = value
            if (value.isBlank()) {
                externalResults.value = emptyList()
                externalHasSearched.value = false
                externalError.value = null
            }
        }

        fun selectTab(value: SearchTab) {
            tab.value = value
        }

        fun selectCategory(value: Category) {
            if (externalCategory.value != value) {
                externalCategory.value = value
                externalResults.value = emptyList()
                externalHasSearched.value = false
                externalError.value = null
            }
        }

        fun runExternalSearch() {
            val q = query.value.trim()
            if (q.isBlank()) return
            val category = externalCategory.value
            viewModelScope.launch {
                isExternalLoading.value = true
                externalError.value = null
                val result =
                    apiCall {
                        when (category) {
                            Category.Music -> api.discogsSearch(q).map(DiscogsSearchResultDto::toResult)
                            Category.Films -> api.tmdbSearch(q).map(TmdbSearchResultDto::toResult)
                            Category.Books -> api.openLibrarySearch(q).map(OpenLibraryResultDto::toResult)
                            Category.Games -> api.rawgSearch(q).map(RawgSearchResultDto::toResult)
                            Category.Comics -> api.comicVineSearch(q).map(ComicVineSearchResultDto::toResult)
                        }
                    }
                when (result) {
                    is ApiResult.Success -> externalResults.value = result.value
                    ApiResult.NetworkError -> externalError.value = "Couldn't reach the server."
                    is ApiResult.HttpError -> {
                        externalError.value =
                            when (result.code) {
                                400 -> "Provider token missing. Add it in Settings."
                                else -> result.message ?: "Server error (${result.code})."
                            }
                    }
                    ApiResult.Unauthorised -> { /* SessionManager handles */ }
                }
                isExternalLoading.value = false
                externalHasSearched.value = true
            }
        }

        fun dismissExternalError() {
            externalError.value = null
        }
    }

private fun MediaItem.matches(query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return false
    return title.lowercase().contains(q) ||
        artist.orEmpty().lowercase().contains(q) ||
        format.orEmpty().lowercase().contains(q) ||
        label.orEmpty().lowercase().contains(q) ||
        notes.orEmpty().lowercase().contains(q)
}

private fun DiscogsSearchResultDto.toResult(): ExternalSearchResult =
    ExternalSearchResult(
        title = title.orEmpty(),
        artist = artist,
        format = format,
        year = year,
        barcode = barcode,
        label = label,
        country = country,
        discogsId = discogsId,
    )

private fun TmdbSearchResultDto.toResult(): ExternalSearchResult =
    ExternalSearchResult(
        title = title,
        year = year,
    )

private fun OpenLibraryResultDto.toResult(): ExternalSearchResult =
    ExternalSearchResult(
        title = title,
        artist = artist,
        year = year,
        barcode = barcode,
        label = label,
    )

private fun RawgSearchResultDto.toResult(): ExternalSearchResult =
    ExternalSearchResult(
        title = title,
        year = year,
        subtitle = genres,
    )

private fun ComicVineSearchResultDto.toResult(): ExternalSearchResult =
    ExternalSearchResult(
        title = title,
        year = year,
        label = label,
        subtitle = genres,
    )
