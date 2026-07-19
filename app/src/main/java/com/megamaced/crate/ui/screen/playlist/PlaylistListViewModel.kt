package com.megamaced.crate.ui.screen.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.domain.model.Playlist
import com.megamaced.crate.domain.repository.PlaylistRepository
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

        // Guards create/rename/delete against double-submit from rapid taps.
        // Mutations launch on the main dispatcher, so a plain flag is enough.
        private var isMutating = false

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
            launchMutation { handle(playlistRepository.create(trimmed)) {} }
        }

        fun rename(
            id: Long,
            name: String,
        ) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return
            launchMutation { handle(playlistRepository.rename(id, trimmed)) {} }
        }

        fun delete(id: Long) {
            launchMutation { handle(playlistRepository.delete(id)) {} }
        }

        private fun launchMutation(block: suspend () -> Unit) {
            if (isMutating) return
            isMutating = true
            viewModelScope.launch {
                try {
                    block()
                } finally {
                    isMutating = false
                }
            }
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
