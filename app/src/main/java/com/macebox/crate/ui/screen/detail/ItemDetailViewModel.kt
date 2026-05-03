package com.macebox.crate.ui.screen.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.domain.model.MediaItem
import com.macebox.crate.domain.repository.EnrichmentRepository
import com.macebox.crate.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemDetailUiState(
    val itemId: Long = 0,
    val item: MediaItem? = null,
    val isLoading: Boolean = true,
    val activeAction: DetailAction? = null,
    val errorMessage: String? = null,
    val deleted: Boolean = false,
)

enum class DetailAction {
    Enrich,
    Strip,
    FetchMarketValue,
    Delete,
}

@HiltViewModel
class ItemDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val mediaRepository: MediaRepository,
        private val enrichmentRepository: EnrichmentRepository,
    ) : ViewModel() {
        private val itemId: Long = checkNotNull(savedStateHandle["itemId"]) {
            "Detail route requires an itemId argument"
        }

        private val activeAction = MutableStateFlow<DetailAction?>(null)
        private val errorMessage = MutableStateFlow<String?>(null)
        private val deleted = MutableStateFlow(false)

        val uiState: StateFlow<ItemDetailUiState> =
            combine(
                mediaRepository.observe(itemId),
                activeAction,
                errorMessage,
                deleted,
            ) { item, action, err, isDeleted ->
                ItemDetailUiState(
                    itemId = itemId,
                    item = item,
                    isLoading = item == null && !isDeleted,
                    activeAction = action,
                    errorMessage = err,
                    deleted = isDeleted,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ItemDetailUiState(itemId = itemId),
            )

        init {
            viewModelScope.launch { mediaRepository.refreshSingle(itemId) }
        }

        fun enrich() {
            run(DetailAction.Enrich) { enrichmentRepository.enrich(itemId) }
        }

        fun stripEnrichment() {
            run(DetailAction.Strip) { enrichmentRepository.stripEnrichment(itemId) }
        }

        fun fetchMarketValue() {
            run(DetailAction.FetchMarketValue) { enrichmentRepository.fetchMarketValue(itemId) }
        }

        fun delete() {
            viewModelScope.launch {
                activeAction.value = DetailAction.Delete
                errorMessage.value = null
                when (val result = mediaRepository.delete(itemId)) {
                    is ApiResult.Success -> deleted.value = true
                    ApiResult.NetworkError -> errorMessage.value = "Couldn't reach the server."
                    is ApiResult.HttpError -> errorMessage.value = result.message ?: "Server error (${result.code})."
                    ApiResult.Unauthorised -> { /* SessionManager already handled */ }
                }
                activeAction.value = null
            }
        }

        fun dismissError() {
            errorMessage.value = null
        }

        private fun run(
            action: DetailAction,
            block: suspend () -> ApiResult<MediaItem>,
        ) {
            viewModelScope.launch {
                activeAction.value = action
                errorMessage.value = null
                when (val result = block()) {
                    is ApiResult.Success -> { /* Repository writes through to Room */ }
                    ApiResult.NetworkError -> errorMessage.value = "Couldn't reach the server."
                    is ApiResult.HttpError -> errorMessage.value = result.message ?: "Server error (${result.code})."
                    ApiResult.Unauthorised -> { /* SessionManager already handled */ }
                }
                activeAction.value = null
            }
        }
    }
