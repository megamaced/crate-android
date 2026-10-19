package com.megamaced.crate.data.repository

import com.megamaced.crate.R
import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.toUiText
import com.megamaced.crate.data.auth.SessionGeneration
import com.megamaced.crate.data.auth.SessionStamp
import com.megamaced.crate.data.auth.StaleSessionException
import com.megamaced.crate.data.db.dao.MediaItemDao
import com.megamaced.crate.data.prefs.AccountPrefs
import com.megamaced.crate.domain.model.SharedWithMe
import com.megamaced.crate.domain.model.allItems
import com.megamaced.crate.domain.repository.ShareRepository
import com.megamaced.crate.util.UiText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
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
    ) {
        data class State(
            val data: SharedWithMe? = null,
            val isLoading: Boolean = false,
            val error: UiText? = null,
            // The session this snapshot was fetched for (see SessionGeneration).
            internal val generation: Int? = null,
        )

        private val _state = MutableStateFlow(State())

        /**
         * The snapshot, hidden the moment the session it was fetched for
         * ends: a sign-out or sign-in empties it at once, rather than at the
         * next [load], so nothing on screen keeps the previous session's
         * shares while the next one's are fetched.
         */
        val state: Flow<State> =
            combine(_state, SessionGeneration.generations) { snapshot, generation ->
                if (snapshot.generation == null || snapshot.generation == generation) {
                    snapshot
                } else {
                    State(generation = generation)
                }
            }

        private val mutex = Mutex()

        // Wall-clock of the last successful fetch, so [load] can re-fetch a
        // stale snapshot instead of serving a cached one for the whole process
        // lifetime — otherwise a sharee keeps seeing revoked/downgraded shares
        // (and their now-invalid Add affordance) until a manual pull-to-refresh.
        private var lastLoadedAtMs: Long = 0L

        /** Fetch if nothing is cached yet, or the cached snapshot is stale. */
        suspend fun load() {
            mutex.withLock {
                val generation = SessionGeneration.current()
                discardOtherSessionsSnapshot(generation)
                val fresh =
                    _state.value.data != null &&
                        (System.currentTimeMillis() - lastLoadedAtMs) < CACHE_TTL_MS
                if (!fresh) refreshLocked(generation)
            }
        }

        /** Clear a surfaced error after the UI has shown it. */
        fun clearError() {
            _state.update { it.copy(error = null) }
        }

        suspend fun refresh() {
            mutex.withLock {
                val generation = SessionGeneration.current()
                discardOtherSessionsSnapshot(generation)
                refreshLocked(generation)
            }
        }

        // Caller must hold [mutex]. The payload carries whole items — titles,
        // notes, prices — not just counts, so a snapshot from another session
        // is dropped outright rather than left on screen until a refresh
        // returns, which on a failed refresh would be forever. Keyed by session
        // rather than login name: the same name on another server is another
        // account.
        private fun discardOtherSessionsSnapshot(generation: Int) {
            if (_state.value.generation != generation) {
                lastLoadedAtMs = 0L
                _state.value = State(generation = generation)
            }
        }

        // Caller must hold [mutex]. Keeping the check-and-fetch under one lock
        // acquisition also prevents two concurrent load() callers (landing +
        // subpage on first mount) from both issuing a full fetch.
        //
        // [generation] is read once per load() or refresh() and used for every
        // step: a session that ends part-way through must not have its answer
        // published as the next one's, nor its cached payload re-labelled as
        // the next one's while the request is out.
        private suspend fun refreshLocked(generation: Int) {
            _state.update { it.refreshingFor(generation) }
            val result = withContext(SessionStamp(generation)) { shareRepository.sharedWithMe() }
            if (generation != SessionGeneration.current()) return
            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(data = result.value, isLoading = false, error = null) }
                    lastLoadedAtMs = System.currentTimeMillis()
                    try {
                        withContext(SessionStamp(generation)) { dropRevokedShares(result.value) }
                    } catch (e: StaleSessionException) {
                        // Ended during the clean-up; the next session starts over.
                        Timber.i(e, "Revoked-share clean-up dropped: its session has ended")
                    }
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

/**
 * The state a refresh for [generation] starts from. The cached payload stays
 * on screen only when it is that session's own; any other is dropped, never
 * re-stamped, since the stamp is what decides who may see it.
 */
internal fun SharedContentStore.State.refreshingFor(generation: Int): SharedContentStore.State =
    if (this.generation == generation) {
        copy(isLoading = true, error = null)
    } else {
        SharedContentStore.State(isLoading = true, generation = generation)
    }
