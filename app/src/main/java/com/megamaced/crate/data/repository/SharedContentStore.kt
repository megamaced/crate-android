package com.megamaced.crate.data.repository

import com.megamaced.crate.R
import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.toUiText
import com.megamaced.crate.data.auth.CurrentSession
import com.megamaced.crate.data.db.dao.MediaItemDao
import com.megamaced.crate.data.prefs.AccountPrefs
import com.megamaced.crate.domain.model.SharedWithMe
import com.megamaced.crate.domain.model.allItems
import com.megamaced.crate.domain.repository.ShareRepository
import com.megamaced.crate.util.UiText
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
        private val mediaItemDao: MediaItemDao,
        private val accountPrefs: AccountPrefs,
        private val currentSession: CurrentSession,
    ) {
        data class State(
            val data: SharedWithMe? = null,
            val isLoading: Boolean = false,
            val error: UiText? = null,
        )

        private val _state = MutableStateFlow(State())
        val state: StateFlow<State> = _state.asStateFlow()

        private val mutex = Mutex()

        // Wall-clock of the last successful fetch, so [load] can re-fetch a
        // stale snapshot instead of serving a cached one for the whole process
        // lifetime — otherwise a sharee keeps seeing revoked/downgraded shares
        // (and their now-invalid Add affordance) until a manual pull-to-refresh.
        private var lastLoadedAtMs: Long = 0L

        // The account the cached snapshot belongs to. The payload carries whole
        // items — titles, notes, prices — not just counts, so serving it to the
        // next person to sign in on this device would show them the previous
        // user's shares. Null before the first fetch.
        private var snapshotAccount: String? = null

        /** Fetch if nothing is cached yet, or the cached snapshot is stale. */
        suspend fun load() {
            mutex.withLock {
                discardOtherAccountsSnapshot()
                val fresh =
                    _state.value.data != null &&
                        (System.currentTimeMillis() - lastLoadedAtMs) < CACHE_TTL_MS
                if (!fresh) refreshLocked()
            }
        }

        /** Clear a surfaced error after the UI has shown it. */
        fun clearError() {
            _state.update { it.copy(error = null) }
        }

        suspend fun refresh() {
            mutex.withLock {
                discardOtherAccountsSnapshot()
                refreshLocked()
            }
        }

        // Caller must hold [mutex]. A snapshot from another account is dropped
        // outright rather than left on screen until a refresh returns — which
        // on a failed refresh would be forever.
        private fun discardOtherAccountsSnapshot() {
            val account = currentSession.loginName()
            if (account != snapshotAccount) {
                snapshotAccount = account
                lastLoadedAtMs = 0L
                _state.value = State()
            }
        }

        // Caller must hold [mutex]. Keeping the check-and-fetch under one lock
        // acquisition also prevents two concurrent load() callers (landing +
        // subpage on first mount) from both issuing a full fetch.
        private suspend fun refreshLocked() {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = shareRepository.sharedWithMe()) {
                is ApiResult.Success -> {
                    _state.update { it.copy(data = result.value, isLoading = false, error = null) }
                    lastLoadedAtMs = System.currentTimeMillis()
                    snapshotAccount = currentSession.loginName()
                    dropRevokedShares(result.value)
                }

                ApiResult.NetworkError -> {
                    _state.update { it.copy(isLoading = false, error = UiText.Res(R.string.error_network)) }
                }

                is ApiResult.HttpError -> {
                    _state.update {
                        it.copy(isLoading = false, error = result.toUiText())
                    }
                }

                ApiResult.Unauthorised -> {
                    _state.update { it.copy(isLoading = false) }
                }
            }
        }

        /**
         * Forgets locally cached rows belonging to other users that this
         * payload no longer lists.
         *
         * Opening a shared item caches the owner's row in `media_items`, and a
         * sweep of your own collection deliberately never prunes it. This
         * snapshot is the authoritative statement of what is still shared with
         * you, so it is the only thing that can clear a revoked share — without
         * it the owner's title, notes, prices and artwork stay on the device
         * indefinitely.
         */
        private suspend fun dropRevokedShares(payload: SharedWithMe) {
            // "Foreign" is defined relative to the signed-in user, so an id we
            // don't have — the state the account transition leaves behind until
            // /me lands — means no row can be classified and nothing is dropped.
            val ownerId = accountPrefs.currentUserId() ?: return
            val stillShared = payload.allItems().mapTo(mutableSetOf()) { it.id }
            val revoked = mediaItemDao.foreignIds(ownerId).filterNot { it in stillShared }
            if (revoked.isNotEmpty()) mediaItemDao.deleteByIds(revoked)
        }

        companion object {
            private const val CACHE_TTL_MS = 30_000L
        }
    }
