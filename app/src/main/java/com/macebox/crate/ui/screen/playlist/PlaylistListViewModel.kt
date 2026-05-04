package com.macebox.crate.ui.screen.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.domain.model.Playlist
import com.macebox.crate.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistListUiState(
    val playlists: List<Playlist> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class PlaylistListViewModel
    @Inject
    constructor(
        private val playlistRepository: PlaylistRepository,
    ) : ViewModel() {
        private val isRefreshing = MutableStateFlow(false)
        private val errorMessage = MutableStateFlow<String?>(null)

        val uiState: StateFlow<PlaylistListUiState> =
            combine(
                playlistRepository.observeAll(),
                isRefreshing,
                errorMessage,
            ) { playlists, refreshing, err ->
                PlaylistListUiState(
                    playlists = playlists.sortedByDescending { it.updatedAt.orEmpty() },
                    isRefreshing = refreshing,
                    errorMessage = err,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = PlaylistListUiState(),
            )

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                isRefreshing.value = true
                errorMessage.value = null
                handle(playlistRepository.refresh()) {}
                isRefreshing.value = false
            }
        }

        fun create(name: String) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch { handle(playlistRepository.create(trimmed)) {} }
        }

        fun rename(
            id: Long,
            name: String,
        ) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch { handle(playlistRepository.rename(id, trimmed)) {} }
        }

        fun delete(id: Long) {
            viewModelScope.launch { handle(playlistRepository.delete(id)) {} }
        }

        fun dismissError() {
            errorMessage.value = null
        }

        private inline fun <T> handle(
            result: ApiResult<T>,
            onSuccess: (T) -> Unit,
        ) {
            when (result) {
                is ApiResult.Success -> onSuccess(result.value)
                ApiResult.NetworkError -> errorMessage.value = "Couldn't reach the server."
                is ApiResult.HttpError -> errorMessage.value = result.message ?: "Server error (${result.code})."
                ApiResult.Unauthorised -> { /* SessionManager handles */ }
            }
        }
    }
