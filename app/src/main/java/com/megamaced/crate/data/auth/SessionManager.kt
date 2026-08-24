package com.megamaced.crate.data.auth

import android.content.Context
import coil3.SingletonImageLoader
import com.megamaced.crate.data.db.CrateDatabase
import com.megamaced.crate.data.prefs.UserPreferences
import com.megamaced.crate.sync.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
        @ApplicationContext private val context: Context,
        private val tokenStore: TokenStore,
        private val userPreferences: UserPreferences,
        private val database: CrateDatabase,
        private val syncScheduler: SyncScheduler,
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
            _authState.value = AuthState.Unauthenticated
            // Stop the background sync too. Without this the periodic and
            // foreground workers keep firing against an unresolvable
            // placeholder host and burn exponential-backoff retries forever.
            syncScheduler.cancelSync()
            // Clear per-user sync state so logging in as a different
            // Nextcloud account doesn't reuse the previous user's delta
            // cursor (flagged by the Phase 16 audit). Theme + collection
            // view mode are pure UX preferences and survive logout.
            scope.launch {
                userPreferences.setLastSyncCursor(null)
                userPreferences.setLastSeenWipedAt(null)
                // Drop all cached collection data: delta sync only wipes the
                // local DB when the server reports a newer wipedAt, so without
                // this a login as a different (never-wiped) account would merge
                // the previous user's rows into the new one's collection.
                database.clearAllTables()
                // Coil caches are keyed by item id with no user/host scope, so
                // evict them too — otherwise account B could be served account
                // A's artwork/photos for a colliding id.
                val loader = SingletonImageLoader.get(context)
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
            }
        }

        /**
         * Persists the credentials the login flow returned. Returns false —
         * leaving the session unauthenticated — when [host] is not a usable
         * absolute URL, rather than storing a host every later request would
         * silently fail to reach.
         */
        fun onLoginSuccess(
            host: String,
            loginName: String,
            appPassword: String,
        ): Boolean {
            val parsed = host.toHttpUrlOrNull() ?: return false
            if (loginName.isBlank() || appPassword.isBlank()) return false
            // Keep any base path (subdirectory installs) but drop the trailing
            // slash HttpUrl always renders, so HostInterceptor can concatenate.
            tokenStore.saveCredentials(
                parsed.toString().trimEnd('/'),
                loginName,
                appPassword,
            )
            _authState.value = AuthState.Authenticated
            syncScheduler.ensurePeriodicSync()
            syncScheduler.syncNow()
            return true
        }

        fun onUnauthorised() {
            logout()
        }
    }
