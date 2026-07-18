package com.megamaced.crate.ui.screen.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.auth.CurrentSession
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.Playlist
import com.megamaced.crate.domain.repository.MediaRepository
import com.megamaced.crate.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailUiState(
    val playlistId: Long = 0,
    val playlist: Playlist? = null,
    val candidates: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val deleted: Boolean = false,
    // True for our own playlist — grants delete + re-share.
    val isOwner: Boolean = false,
    // True when we may rename/add/remove tracks — own playlist OR a
    // read/write share. Delete + re-share stay owner-only (see [isOwner]).
    val canWrite: Boolean = false,
)

@HiltViewModel
class PlaylistDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val playlistRepository: PlaylistRepository,
        mediaRepository: MediaRepository,
        currentSession: CurrentSession,
    ) : ViewModel() {
        private val playlistId: Long =
            checkNotNull(savedStateHandle["playlistId"]) {
                "Playlist detail route requires a playlistId argument"
            }

        private val currentLoginName: String? = currentSession.loginName()

        private val errorMessage = MutableStateFlow<String?>(null)
        private val deleted = MutableStateFlow(false)

        // Write permission for a shared playlist, learned from the network
        // refresh (not persisted through Room). Own playlists don't rely on it.
        private val sharedCanWrite = MutableStateFlow(false)

        val uiState: StateFlow<PlaylistDetailUiState> =
            combine(
                playlistRepository.observe(playlistId),
                mediaRepository.observeAll(),
                errorMessage,
                deleted,
                sharedCanWrite,
            ) { playlist, allItems, err, isDeleted, canWriteShare ->
                val playlistItemIds = playlist
                    ?.items
                    ?.map { it.id }
                    ?.toSet()
                    .orEmpty()
                val owner =
                    playlist != null &&
                        (playlist.userId == null || currentLoginName == null || playlist.userId == currentLoginName)
                PlaylistDetailUiState(
                    playlistId = playlistId,
                    playlist = playlist,
                    candidates = allItems.filterNot { it.id in playlistItemIds },
                    isLoading = playlist == null && !isDeleted,
                    errorMessage = err,
                    deleted = isDeleted,
                    isOwner = owner,
                    canWrite = owner || canWriteShare,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = PlaylistDetailUiState(playlistId = playlistId),
            )

        init {
            viewModelScope.launch {
                val result = playlistRepository.refresh(playlistId)
                if (result is ApiResult.Success) {
                    sharedCanWrite.value = result.value.canWrite
                }
                handle(result)
            }
        }

        fun rename(name: String) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch { handle(playlistRepository.rename(playlistId, trimmed)) }
        }

        fun delete() {
            viewModelScope.launch {
                when (val result = playlistRepository.delete(playlistId)) {
                    is ApiResult.Success -> deleted.value = true
                    ApiResult.NetworkError -> errorMessage.value = "Couldn't reach the server."
                    is ApiResult.HttpError -> errorMessage.value = result.message ?: "Server error (${result.code})."
                    ApiResult.Unauthorised -> { /* SessionManager handles */ }
                }
            }
        }

        fun addItem(mediaItemId: Long) {
            viewModelScope.launch { handle(playlistRepository.addItem(playlistId, mediaItemId)) }
        }

        fun removeItem(mediaItemId: Long) {
            viewModelScope.launch { handle(playlistRepository.removeItem(playlistId, mediaItemId)) }
        }

        fun dismissError() {
            errorMessage.value = null
        }

        private fun <T> handle(result: ApiResult<T>) {
            when (result) {
                is ApiResult.Success -> { /* Repository writes through to Room */ }
                ApiResult.NetworkError -> errorMessage.value = "Couldn't reach the server."
                is ApiResult.HttpError -> errorMessage.value = result.message ?: "Server error (${result.code})."
                ApiResult.Unauthorised -> { /* SessionManager handles */ }
            }
        }
    }
