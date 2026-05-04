package com.macebox.crate.ui.screen.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.domain.model.SharedWithMe
import com.macebox.crate.domain.repository.ShareRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SharedWithMeUiState(
    val data: SharedWithMe? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class SharedWithMeViewModel
    @Inject
    constructor(
        private val shareRepository: ShareRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SharedWithMeUiState())
        val state: StateFlow<SharedWithMeUiState> = _state.asStateFlow()

        init {
            load(initial = true)
        }

        fun refresh() = load(initial = false)

        fun dismissError() {
            _state.update { it.copy(errorMessage = null) }
        }

        private fun load(initial: Boolean) {
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        isLoading = initial && it.data == null,
                        isRefreshing = !initial,
                        errorMessage = null,
                    )
                }
                when (val result = shareRepository.sharedWithMe()) {
                    is ApiResult.Success ->
                        _state.update {
                            it.copy(data = result.value, isLoading = false, isRefreshing = false)
                        }
                    ApiResult.NetworkError ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = "Couldn't reach the server.",
                            )
                        }
                    is ApiResult.HttpError ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = result.message ?: "Server error (${result.code}).",
                            )
                        }
                    ApiResult.Unauthorised ->
                        _state.update { it.copy(isLoading = false, isRefreshing = false) }
                }
            }
        }
    }
