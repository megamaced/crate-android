package com.macebox.crate.ui.screen.addedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.data.api.CrateApiService
import com.macebox.crate.data.api.apiCall
import com.macebox.crate.data.api.dto.ComicVineSearchResultDto
import com.macebox.crate.data.api.dto.DiscogsSearchResultDto
import com.macebox.crate.data.api.dto.OpenLibraryResultDto
import com.macebox.crate.data.api.dto.RawgSearchResultDto
import com.macebox.crate.data.api.dto.TmdbSearchResultDto
import com.macebox.crate.domain.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExternalSearchState(
    val category: Category = Category.Music,
    val query: String = "",
    val results: List<ExternalSearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ExternalSearchViewModel
    @Inject
    constructor(
        private val api: CrateApiService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ExternalSearchState())
        val state: StateFlow<ExternalSearchState> = _state.asStateFlow()

        fun setCategory(value: Category) {
            if (value != _state.value.category) {
                _state.value =
                    ExternalSearchState(
                        category = value,
                        query = _state.value.query,
                    )
            }
        }

        fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

        fun search() {
            val current = _state.value
            val q = current.query.trim()
            if (q.isBlank()) return
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, errorMessage = null) }
                val result =
                    apiCall {
                        when (current.category) {
                            Category.Music -> api.discogsSearch(q).map(DiscogsSearchResultDto::toResult)
                            Category.Films -> api.tmdbSearch(q).map(TmdbSearchResultDto::toResult)
                            Category.Books -> api.openLibrarySearch(q).map(OpenLibraryResultDto::toResult)
                            Category.Games -> api.rawgSearch(q).map(RawgSearchResultDto::toResult)
                            Category.Comics -> api.comicVineSearch(q).map(ComicVineSearchResultDto::toResult)
                        }
                    }
                when (result) {
                    is ApiResult.Success ->
                        _state.update {
                            it.copy(
                                results = result.value,
                                isLoading = false,
                                hasSearched = true,
                                errorMessage = null,
                            )
                        }
                    ApiResult.NetworkError ->
                        _state.update {
                            it.copy(isLoading = false, hasSearched = true, errorMessage = "Couldn't reach the server.")
                        }
                    is ApiResult.HttpError -> {
                        val msg =
                            when (result.code) {
                                400 -> "Provider token missing. Add it in Settings."
                                else -> result.message ?: "Server error (${result.code})."
                            }
                        _state.update { it.copy(isLoading = false, hasSearched = true, errorMessage = msg) }
                    }
                    ApiResult.Unauthorised ->
                        _state.update { it.copy(isLoading = false) }
                }
            }
        }
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
