package com.macebox.crate.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.data.prefs.UserPreferences
import com.macebox.crate.domain.repository.MediaRepository
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
                    Result.retry()
                }
                ApiResult.Unauthorised -> {
                    Timber.w("Sync aborted: unauthorised")
                    Result.success()
                }
            }
        }
    }
