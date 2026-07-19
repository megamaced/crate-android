package com.megamaced.crate.data.repository

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.domain.model.SharedWithMe
import com.megamaced.crate.domain.repository.ShareRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide cache of the "Shared with me" payload, so the landing and every
 * per-category subpage read one consistent snapshot — mirroring the module-level
 * singleton in crate/src/composables/useSharedContent.js. Any screen can call
 * [load] (fetch once) or [refresh] (re-fetch, e.g. after adding an item into a
 * shared category).
 */
@Singleton
class SharedContentStore
    @Inject
    constructor(
        private val shareRepository: ShareRepository,
    ) {
        data class State(
            val data: SharedWithMe? = null,
            val isLoading: Boolean = false,
            val error: String? = null,
        )

        private val _state = MutableStateFlow(State())
        val state: StateFlow<State> = _state.asStateFlow()

        private val mutex = Mutex()

        /** Fetch only if nothing is cached yet. */
        suspend fun load() {
            if (_state.value.data != null) return
            refresh()
        }

        /** Clear a surfaced error after the UI has shown it. */
        fun clearError() {
            _state.update { it.copy(error = null) }
        }

        suspend fun refresh() {
            mutex.withLock {
                _state.update { it.copy(isLoading = true, error = null) }
                when (val result = shareRepository.sharedWithMe()) {
                    is ApiResult.Success ->
                        _state.update { it.copy(data = result.value, isLoading = false, error = null) }
                    ApiResult.NetworkError ->
                        _state.update { it.copy(isLoading = false, error = "Couldn't reach the server.") }
                    is ApiResult.HttpError ->
                        _state.update {
                            it.copy(isLoading = false, error = result.message ?: "Server error (${result.code}).")
                        }
                    ApiResult.Unauthorised ->
                        _state.update { it.copy(isLoading = false) }
                }
            }
        }
    }
