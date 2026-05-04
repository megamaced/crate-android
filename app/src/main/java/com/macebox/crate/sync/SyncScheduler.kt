package com.macebox.crate.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic,
            )
        }

        /** Fires a one-shot sync, e.g. when the app comes to the foreground. */
        fun syncNow() {
            val oneShot =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(connectedConstraints)
                    .build()
            workManager.enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.KEEP,
                oneShot,
            )
        }

        companion object {
            private const val PERIODIC_NAME = "crate-sync-periodic"
            private const val ONE_SHOT_NAME = "crate-sync-now"
            private const val PERIODIC_INTERVAL_HOURS = 6L
        }
    }
