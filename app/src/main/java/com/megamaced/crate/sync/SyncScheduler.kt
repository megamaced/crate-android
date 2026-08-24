package com.megamaced.crate.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.megamaced.crate.data.auth.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val workManager get() = WorkManager.getInstance(context)

        private val connectedConstraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        /** Schedules the recurring 6-hour sync. Idempotent — safe to call on every start-up. */
        fun ensurePeriodicSync() {
            val periodic =
                PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS)
                    .setConstraints(connectedConstraints)
                    .build()
            // UPDATE rather than KEEP: with KEEP, a later change to the
            // interval or the constraints would never reach installs that
            // already have the work enqueued.
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodic,
            )
        }

        /** Fires a one-shot sync, e.g. when the app comes to the foreground. */
        fun syncNow() {
            val oneShot =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(connectedConstraints)
                    .build()
            // REPLACE rather than KEEP: KEEP silently discards this request
            // whenever an earlier one is still sitting in exponential backoff
            // after a failure, so a user returning to a working connection
            // could wait minutes for fresh data with no way to force a sync.
            workManager.enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                oneShot,
            )
        }

        /** Cancels all sync work. Called on logout — see [SessionManager]. */
        fun cancelSync() {
            workManager.cancelUniqueWork(PERIODIC_NAME)
            workManager.cancelUniqueWork(ONE_SHOT_NAME)
        }

        companion object {
            private const val PERIODIC_NAME = "crate-sync-periodic"
            private const val ONE_SHOT_NAME = "crate-sync-now"
            private const val PERIODIC_INTERVAL_HOURS = 6L
        }
    }
