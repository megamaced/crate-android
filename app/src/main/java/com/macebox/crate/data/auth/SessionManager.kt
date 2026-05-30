package com.macebox.crate.data.auth

import com.macebox.crate.data.prefs.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthState {
    data object Unknown : AuthState

    data object Authenticated : AuthState

    data object Unauthenticated : AuthState
}

@Singleton
class SessionManager
    @Inject
    constructor(
        private val tokenStore: TokenStore,
        private val userPreferences: UserPreferences,
    ) {
        private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
        val authState: StateFlow<AuthState> = _authState.asStateFlow()

        // Application-scoped — SessionManager is @Singleton so this lives
        // for the process lifetime. Used to fire-and-forget the DataStore
        // wipe on logout from synchronous callers (AuthInterceptor).
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        init {
            refreshState()
        }

        fun refreshState() {
            _authState.value = if (tokenStore.getCredentials() != null) {
                AuthState.Authenticated
            } else {
                AuthState.Unauthenticated
            }
        }

        fun logout() {
            tokenStore.clear()
            // Clear per-user sync state so logging in as a different
            // Nextcloud account doesn't reuse the previous user's delta
            // cursor (flagged by the Phase 16 audit). Theme + collection
            // view mode are pure UX preferences and survive logout.
            scope.launch {
                userPreferences.setLastSyncCursor(null)
                userPreferences.setLastSeenWipedAt(null)
            }
            _authState.value = AuthState.Unauthenticated
        }

        fun onLoginSuccess(
            host: String,
            loginName: String,
            appPassword: String,
        ) {
            tokenStore.saveCredentials(host, loginName, appPassword)
            _authState.value = AuthState.Authenticated
        }

        fun onUnauthorised() {
            logout()
        }
    }
