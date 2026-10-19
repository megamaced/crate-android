package com.megamaced.crate.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.megamaced.crate.data.auth.TokenStore
import com.megamaced.crate.data.auth.stampedWithSession
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val collectionSync: CollectionSync,
        private val tokenStore: TokenStore,
    ) : CoroutineWorker(appContext, params) {
        // The whole run belongs to the session current when it starts: its
        // cache writes, and the cursor it records, are refused once that
        // session has ended (see SessionGeneration).
        override suspend fun doWork(): Result = stampedWithSession { syncForCurrentSession() }

        private suspend fun syncForCurrentSession(): Result {
            // Nothing to sync while logged out. Returning success (not retry)
            // matters: the host is unresolvable without credentials, so a retry
            // would spin in backoff until the constraints lapse.
            if (tokenStore.getCredentials() == null) {
                Timber.d("Sync skipped: not signed in")
                return Result.success()
            }
            return when (collectionSync.run()) {
                CollectionSync.Outcome.Done -> Result.success()
                CollectionSync.Outcome.Retry -> Result.retry()
                CollectionSync.Outcome.Failed -> Result.failure()
            }
        }
    }
