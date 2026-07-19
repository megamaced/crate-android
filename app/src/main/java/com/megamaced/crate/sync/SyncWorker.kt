package com.megamaced.crate.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.megamaced.crate.data.api.ApiResult
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
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val prefs = userPreferences.flow.first()
            val cursor = prefs.lastSyncCursor
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
                    // Only retry transient server-side failures. Permanent 4xx
                    // (400/403/404/422, …) can never succeed on retry, so
                    // retrying just burns battery/network with WorkManager's
                    // exponential backoff until the constraints lapse.
                    if (result.code in 500..599 || result.code == 408 || result.code == 429) {
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
