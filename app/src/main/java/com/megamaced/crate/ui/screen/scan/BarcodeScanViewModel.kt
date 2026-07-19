package com.megamaced.crate.ui.screen.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.CrateApiService
import com.megamaced.crate.data.api.apiCall
import com.megamaced.crate.data.api.dto.DiscogsSearchResultDto
import com.megamaced.crate.data.api.dto.OpenLibraryResultDto
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.ui.screen.addedit.ExternalSearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BarcodeScanUiState(
    val category: Category? = null,
    val barcode: String? = null,
    val isLooking: Boolean = false,
    val candidates: List<ExternalSearchResult> = emptyList(),
    val errorMessage: String? = null,
    val sheetOpen: Boolean = false,
)

@HiltViewModel
class BarcodeScanViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val api: CrateApiService,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                BarcodeScanUiState(
                    category = savedStateHandle.get<String>("category")?.let { Category.fromApi(it) },
                ),
            )
        val uiState: StateFlow<BarcodeScanUiState> = _uiState.asStateFlow()

        private var lookupJob: Job? = null

        fun onBarcodeDetected(raw: String) {
            val current = _uiState.value
            if (current.isLooking || current.barcode == raw) return
            _uiState.update {
                it.copy(barcode = raw, isLooking = true, sheetOpen = true, errorMessage = null)
            }
            lookupJob = viewModelScope.launch { lookup(raw, current.category) }
        }

        fun dismissSheet() {
            // Cancel any in-flight lookup and clear isLooking; otherwise the
            // onBarcodeDetected guard would keep rejecting new scans until the
            // orphaned lookup completed, and its result would land in the
            // now-dismissed sheet. (apiCall propagates cancellation, so the
            // cancelled lookup won't post stale state.)
            lookupJob?.cancel()
            lookupJob = null
            _uiState.update {
                it.copy(
                    sheetOpen = false,
                    isLooking = false,
                    candidates = emptyList(),
                    barcode = null,
                    errorMessage = null,
                )
            }
        }

        fun manualOverride(): ExternalSearchResult? = _uiState.value.barcode?.let { ExternalSearchResult(title = "", barcode = it) }

        private suspend fun lookup(
            barcode: String,
            category: Category?,
        ) {
            val result =
                apiCall {
                    when (category) {
                        Category.Books -> listOfNotNull(api.openLibraryIsbn(barcode).toResult())
                        Category.Music, null -> api.discogsBarcode(barcode).map(DiscogsSearchResultDto::toResult)
                        else -> api.discogsBarcode(barcode).map(DiscogsSearchResultDto::toResult)
                    }
                }
            when (result) {
                is ApiResult.Success ->
                    _uiState.update {
                        it.copy(isLooking = false, candidates = result.value, errorMessage = null)
                    }
                ApiResult.NetworkError ->
                    _uiState.update {
                        it.copy(isLooking = false, errorMessage = "Couldn't reach the server.")
                    }
                is ApiResult.HttpError -> {
                    val msg =
                        when (result.code) {
                            400 -> "Provider token missing. Add it in Settings."
                            404 -> "No matches for that barcode."
                            else -> result.message ?: "Server error (${result.code})."
                        }
                    _uiState.update { it.copy(isLooking = false, errorMessage = msg) }
                }
                ApiResult.Unauthorised ->
                    _uiState.update { it.copy(isLooking = false) }
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
        coverUrl = thumb,
    )

private fun OpenLibraryResultDto.toResult(): ExternalSearchResult? =
    if (title.isBlank()) {
        null
    } else {
        ExternalSearchResult(
            title = title,
            artist = artist,
            year = year,
            barcode = barcode,
            label = label,
            coverUrl = artworkUrl ?: thumb,
        )
    }
