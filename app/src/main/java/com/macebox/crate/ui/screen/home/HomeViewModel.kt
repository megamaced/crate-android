package com.macebox.crate.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.domain.model.HomeFeed
import com.macebox.crate.domain.repository.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val feed: HomeFeed? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val homeRepository: HomeRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        init {
            load(initial = true)
        }

        fun refresh() = load(initial = false)

        fun dismissError() {
            _uiState.update { it.copy(errorMessage = null) }
        }

        private fun load(initial: Boolean) {
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isLoading = initial && it.feed == null,
                        isRefreshing = !initial,
                        errorMessage = null,
                    )
                }
                when (val result = homeRepository.fetch()) {
                    is ApiResult.Success ->
                        _uiState.update {
                            it.copy(
                                feed = result.value,
                                isLoading = false,
                                isRefreshing = false,
                            )
                        }
                    ApiResult.NetworkError ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = "Couldn't reach the server.",
                            )
                        }
                    is ApiResult.HttpError ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = result.message ?: "Server error (${result.code}).",
                            )
                        }
                    ApiResult.Unauthorised ->
                        _uiState.update {
                            it.copy(isLoading = false, isRefreshing = false)
                        }
                }
            }
        }
    }
