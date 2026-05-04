package com.macebox.crate.ui.screen.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.domain.model.Share
import com.macebox.crate.domain.model.UserSearchResult
import com.macebox.crate.domain.repository.ShareRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ShareTarget {
    Album,
    Playlist,
}

data class ShareSheetUiState(
    val target: ShareTarget = ShareTarget.Album,
    val resourceId: Long = 0,
    val isLoadingShares: Boolean = false,
    val isSearching: Boolean = false,
    val isWorking: Boolean = false,
    val query: String = "",
    val results: List<UserSearchResult> = emptyList(),
    val existingShares: List<Share> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class ShareSheetViewModel
    @Inject
    constructor(
        private val shareRepository: ShareRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ShareSheetUiState())
        val state: StateFlow<ShareSheetUiState> = _state.asStateFlow()

        private var searchJob: Job? = null
        private var bound: Pair<ShareTarget, Long>? = null

        fun bind(
            target: ShareTarget,
            resourceId: Long,
        ) {
            if (bound == target to resourceId) return
            bound = target to resourceId
            _state.value = ShareSheetUiState(target = target, resourceId = resourceId, isLoadingShares = true)
            refresh()
        }

        fun refresh() {
            val current = _state.value
            viewModelScope.launch {
                _state.update { it.copy(isLoadingShares = true, errorMessage = null) }
                val result =
                    when (current.target) {
                        ShareTarget.Album -> shareRepository.listAlbumShares(current.resourceId)
                        ShareTarget.Playlist -> shareRepository.listPlaylistShares(current.resourceId)
                    }
                when (result) {
                    is ApiResult.Success ->
                        _state.update { it.copy(existingShares = result.value, isLoadingShares = false) }
                    ApiResult.NetworkError ->
                        _state.update { it.copy(isLoadingShares = false, errorMessage = "Couldn't reach the server.") }
                    is ApiResult.HttpError ->
                        _state.update {
                            it.copy(
                                isLoadingShares = false,
                                errorMessage = result.message ?: "Server error (${result.code}).",
                            )
                        }
                    ApiResult.Unauthorised ->
                        _state.update { it.copy(isLoadingShares = false) }
                }
            }
        }

        fun onQueryChange(value: String) {
            _state.update { it.copy(query = value) }
            searchJob?.cancel()
            if (value.trim().length < 2) {
                _state.update { it.copy(results = emptyList(), isSearching = false) }
                return
            }
            searchJob =
                viewModelScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    _state.update { it.copy(isSearching = true, errorMessage = null) }
                    when (val result = shareRepository.searchUsers(value.trim())) {
                        is ApiResult.Success ->
                            _state.update { it.copy(results = result.value, isSearching = false) }
                        ApiResult.NetworkError ->
                            _state.update {
                                it.copy(isSearching = false, errorMessage = "Couldn't reach the server.")
                            }
                        is ApiResult.HttpError ->
                            _state.update {
                                it.copy(
                                    isSearching = false,
                                    errorMessage = result.message ?: "Server error (${result.code}).",
                                )
                            }
                        ApiResult.Unauthorised ->
                            _state.update { it.copy(isSearching = false) }
                    }
                }
        }

        fun share(targetUserId: String) {
            val current = _state.value
            viewModelScope.launch {
                _state.update { it.copy(isWorking = true, errorMessage = null) }
                val result =
                    when (current.target) {
                        ShareTarget.Album -> shareRepository.shareAlbum(current.resourceId, targetUserId)
                        ShareTarget.Playlist -> shareRepository.sharePlaylist(current.resourceId, targetUserId)
                    }
                when (result) {
                    is ApiResult.Success ->
                        _state.update {
                            it.copy(
                                isWorking = false,
                                query = "",
                                results = emptyList(),
                                existingShares = it.existingShares + result.value,
                            )
                        }
                    ApiResult.NetworkError ->
                        _state.update { it.copy(isWorking = false, errorMessage = "Couldn't reach the server.") }
                    is ApiResult.HttpError ->
                        _state.update {
                            it.copy(
                                isWorking = false,
                                errorMessage = result.message ?: "Server error (${result.code}).",
                            )
                        }
                    ApiResult.Unauthorised ->
                        _state.update { it.copy(isWorking = false) }
                }
            }
        }

        fun revoke(shareId: Long) {
            viewModelScope.launch {
                _state.update { it.copy(isWorking = true, errorMessage = null) }
                when (val result = shareRepository.removeShare(shareId)) {
                    is ApiResult.Success ->
                        _state.update {
                            it.copy(
                                isWorking = false,
                                existingShares = it.existingShares.filterNot { share -> share.id == shareId },
                            )
                        }
                    ApiResult.NetworkError ->
                        _state.update { it.copy(isWorking = false, errorMessage = "Couldn't reach the server.") }
                    is ApiResult.HttpError ->
                        _state.update {
                            it.copy(
                                isWorking = false,
                                errorMessage = result.message ?: "Server error (${result.code}).",
                            )
                        }
                    ApiResult.Unauthorised ->
                        _state.update { it.copy(isWorking = false) }
                }
            }
        }

        fun dismissError() {
            _state.update { it.copy(errorMessage = null) }
        }

        companion object {
            private const val SEARCH_DEBOUNCE_MS = 300L
        }
    }
