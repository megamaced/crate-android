package com.megamaced.crate.ui.screen.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamaced.crate.data.auth.LoginFlowInitResponse
import com.megamaced.crate.data.auth.LoginFlowStatus
import com.megamaced.crate.data.auth.NextcloudLoginFlow
import com.megamaced.crate.data.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LoginUiState(
    val hostInput: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginUrl: String? = null,
    val isPolling: Boolean = false,
    val loginSuccess: Boolean = false,
)

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val loginFlow: NextcloudLoginFlow,
        private val sessionManager: SessionManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(LoginUiState())
        val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

        /**
         * The in-flight login attempt. Tracked so a fresh attempt cancels the
         * previous long-poll instead of leaving it running — two concurrent
         * flows would each be able to mint an app password.
         */
        private var flowJob: Job? = null

        fun onHostChanged(host: String) {
            _uiState.update { it.copy(hostInput = host, error = null) }
        }

        fun startLogin() {
            // The Go key on the keyboard is not gated on isLoading/isPolling
            // the way the button is, so guard the entry point itself.
            if (_uiState.value.isLoading || _uiState.value.isPolling) return
            val host = _uiState.value.hostInput.trim()
            if (host.isBlank()) {
                _uiState.update { it.copy(error = "Enter your Nextcloud server URL") }
                return
            }

            // Force HTTPS: the Login Flow v2 exchange carries the app password,
            // so never let a user-typed http:// scheme send it in cleartext.
            val normalisedHost =
                when {
                    host.startsWith("https://") -> host
                    host.startsWith("http://") -> "https://" + host.removePrefix("http://")
                    else -> "https://$host"
                }

            _uiState.update { it.copy(isLoading = true, error = null) }

            flowJob?.cancel()
            flowJob = viewModelScope.launch {
                val result = withContext(Dispatchers.IO) {
                    loginFlow.initiate(normalisedHost)
                }
                result.fold(
                    onSuccess = { initResponse -> onFlowInitiated(initResponse, normalisedHost) },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(isLoading = false, error = e.message ?: "Connection failed")
                        }
                    },
                )
            }
        }

        private fun onFlowInitiated(
            initResponse: LoginFlowInitResponse,
            normalisedHost: String,
        ) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    loginUrl = initResponse.login,
                    isPolling = true,
                )
            }

            flowJob = viewModelScope.launch {
                val status = withContext(Dispatchers.IO) {
                    loginFlow.poll(
                        endpoint = initResponse.poll.endpoint,
                        token = initResponse.poll.token,
                        expectedOrigin = normalisedHost,
                    )
                }
                when (status) {
                    is LoginFlowStatus.Success -> {
                        val stored = sessionManager.onLoginSuccess(
                            host = status.result.server,
                            loginName = status.result.loginName,
                            appPassword = status.result.appPassword,
                        )
                        if (stored) {
                            _uiState.update { it.copy(isPolling = false, loginSuccess = true) }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isPolling = false,
                                    error = "The server returned an address Crate can't use.",
                                )
                            }
                        }
                    }

                    is LoginFlowStatus.Error -> {
                        _uiState.update {
                            it.copy(isPolling = false, error = status.message)
                        }
                    }

                    LoginFlowStatus.Polling -> { /* unreachable from poll() return */ }
                }
            }
        }

        fun dismissError() {
            _uiState.update { it.copy(error = null) }
        }
    }
