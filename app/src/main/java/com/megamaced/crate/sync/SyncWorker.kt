package com.megamaced.crate.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.auth.TokenStore
import com.megamaced.crate.data.prefs.UserPreferences
import com.megamaced.crate.domain.repository.MediaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val mediaRepository: MediaRepository,
        private val userPreferences: UserPreferences,
        private val tokenStore: TokenStore,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            // Nothing to sync while logged out. Returning success (not retry)
            // matters: the host is unresolvable without credentials, so a retry
            // would spin in backoff until the constraints lapse.
            if (tokenStore.getCredentials() == null) {
                Timber.d("Sync skipped: not signed in")
                return Result.success()
            }
            // Must run before reading the cursor — it clears a stale one.
            // A DataStore read failure is transient, so retry rather than
            // letting the exception escape doWork.
            val repair = runCatching { userPreferences.consumeSyncRepair() }
                .getOrElse {
                    Timber.w(it, "Sync deferred: could not read sync-repair flag")
                    return Result.retry()
                }
            val prefs = runCatching { userPreferences.flow.first() }
                .getOrElse {
                    Timber.w(it, "Sync deferred: could not read preferences")
                    return Result.retry()
                }
            // A repair run deliberately ignores the stored cursor so the sweep
            // re-reads the whole collection and back-fills rows an earlier,
            // lossy pagination pass never delivered.
            val cursor = if (repair) null else prefs.lastSyncCursor
            if (repair) Timber.i("Sync repair: forcing full resync to back-fill missing rows")
            val seenWipedAt = prefs.lastSeenWipedAt
            return when (val result = mediaRepository.syncDelta(cursor, seenWipedAt)) {
                is ApiResult.Success -> {
                    val newCursor = result.value.cursor
                    val newWipedAt = result.value.wipedAt
                    if (newCursor != null && newCursor != cursor) {
                        userPreferences.setLastSyncCursor(newCursor)
                    }
                    if (newWipedAt != seenWipedAt) {
                        userPreferences.setLastSeenWipedAt(newWipedAt)
                    }
                    Timber.d("Sync ok (cursor %s -> %s, wipedAt %s -> %s)", cursor, newCursor, seenWipedAt, newWipedAt)
                    Result.success()
                }

                ApiResult.NetworkError -> {
                    Timber.w("Sync deferred: network unavailable")
                    Result.retry()
                }

                is ApiResult.HttpError -> {
                    Timber.w("Sync HTTP %d: %s", result.code, result.message)
                    // Only retry transient failures. Permanent 4xx
                    // (400/403/404/422, …) can never succeed on retry, so
                    // retrying just burns battery/network with WorkManager's
                    // exponential backoff until the constraints lapse. Code -1
                    // is apiCall's catch-all for a parse or unexpected failure
                    // — a truncated body, a captive-portal HTML page — which is
                    // transient, so it belongs with the retryable set.
                    if (result.code in 500..599 || result.code == 408 || result.code == 429 || result.code == -1) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }

                ApiResult.Unauthorised -> {
                    Timber.w("Sync aborted: unauthorised")
                    Result.success()
                }
            }
        }
    }
