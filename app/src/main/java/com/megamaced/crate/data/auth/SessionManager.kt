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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.atomic.AtomicInteger
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

        // Serialises the account transition against itself: a second logout
        // (or a login) can't interleave with a clean-up still in flight.
        private val transition = Mutex()

        /**
         * Bumped on every sign-in and sign-out.
         *
         * The clean-up on logout is asynchronous and a request in flight when
         * it happens can answer 401 long afterwards, so both need a way to ask
         * "is the session I belong to still the current one?". Without it a
         * stale 401 signs out — and wipes the database of — whoever is signed
         * in by the time it lands.
         */
        private val epoch = AtomicInteger(0)

        /** Snapshot for a caller that will act on the result later. See [onUnauthorised]. */
        fun currentEpoch(): Int = epoch.get()

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
            val myEpoch = epoch.incrementAndGet()
            tokenStore.clear()
            _authState.value = AuthState.Unauthenticated
            // Stop the background sync too. Without this the periodic and
            // foreground workers keep firing against an unresolvable
            // placeholder host and burn exponential-backoff retries forever.
            syncScheduler.cancelSync()
            scope.launch { forgetAccountData(myEpoch) }
        }

        /**
         * Erases everything the signed-out account left on the device.
         *
         * Runs under [transition] so two transitions can't interleave, and
         * gives up if [forEpoch] is no longer the live session — every step
         * here destroys data, and after a newer sign-in that data belongs to
         * whoever is signed in now. The same work runs again from
         * [onLoginSuccess], so losing this race costs nothing: the incoming
         * session clears the outgoing one's leftovers before it syncs.
         */
        private suspend fun forgetAccountData(forEpoch: Int) {
            transition.withLock {
                if (epoch.get() != forEpoch) return@withLock
                // Forgets the sync cursor and wipe marker, and the server
                // profile mirrored locally (hidden categories, the online-
                // recommendations opt-in, the last category) — all of it
                // belongs to the account being left behind.
                userPreferences.clearAccountScoped()
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
            // Claim a new epoch before the credentials land, so a clean-up
            // still queued from the previous session aborts instead of wiping
            // this one's database, and any 401 issued under the old session is
            // ignored when it arrives.
            val myEpoch = epoch.incrementAndGet()
            // Keep any base path (subdirectory installs) but drop the trailing
            // slash HttpUrl always renders, so HostInterceptor can concatenate.
            tokenStore.saveCredentials(
                parsed.toString().trimEnd('/'),
                loginName,
                appPassword,
            )
            _authState.value = AuthState.Authenticated
            // Clear the previous account's data from this side too, and only
            // then start syncing. Logout does the same work, but it does it
            // asynchronously: doing it here as well means the incoming session
            // never starts on top of leftovers, whichever ran first, and the
            // first sync can't race the wipe that would delete its rows.
            scope.launch {
                forgetAccountData(myEpoch)
                syncScheduler.ensurePeriodicSync()
                syncScheduler.syncNow()
            }
            return true
        }

        /**
         * Signs out in response to a 401 — but only if [requestEpoch], captured
         * when the request was issued, is still the live session. A response
         * that belongs to a session the user has already left says nothing
         * about the one they are in now.
         */
        fun onUnauthorised(requestEpoch: Int) {
            if (epoch.get() != requestEpoch) {
                return
            }
            logout()
        }
    }
