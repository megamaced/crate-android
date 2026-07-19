package com.megamaced.crate.ui.screen.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamaced.crate.data.repository.SharedContentStore
import com.megamaced.crate.domain.model.Playlist
import com.megamaced.crate.domain.model.SharedCategorySummary
import com.megamaced.crate.domain.model.sharedCategories
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SharedLandingUiState(
    val categories: List<SharedCategorySummary> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = categories.isEmpty() && playlists.isEmpty()
}

/**
 * Backs the "Shared with me" landing — a home-style overview of everything
 * shared with the viewer, grouped into per-category tiles plus a shared-playlist
 * section. Reads the shared [SharedContentStore] snapshot so the landing and the
 * per-category subpages stay in sync.
 */
@HiltViewModel
class SharedWithMeViewModel
    @Inject
    constructor(
        private val store: SharedContentStore,
    ) : ViewModel() {
        private val isRefreshing = MutableStateFlow(false)

        val state: StateFlow<SharedLandingUiState> =
            combine(store.state, isRefreshing) { storeState, refreshing ->
                SharedLandingUiState(
                    categories = storeState.data?.sharedCategories() ?: emptyList(),
                    playlists = storeState.data?.playlists ?: emptyList(),
                    isLoading = storeState.data == null && storeState.isLoading,
                    isRefreshing = refreshing,
                    errorMessage = storeState.error,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SharedLandingUiState(),
            )

        init {
            viewModelScope.launch { store.load() }
        }

        fun refresh() {
            viewModelScope.launch {
                isRefreshing.value = true
                store.refresh()
                isRefreshing.value = false
            }
        }

        fun dismissError() {
            store.clearError()
        }
    }
