package com.megamaced.crate.sync

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.auth.SessionGeneration
import com.megamaced.crate.data.auth.StaleSessionException
import com.megamaced.crate.data.prefs.SyncStatePrefs
import com.megamaced.crate.domain.repository.MediaRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One sync of the signed-in user's collection: the sync-repair check, a delta
 * or full sweep, and the cursor and wipe marker it leaves for the next.
 *
 * Runs one at a time in the process. The periodic and the on-demand sync are
 * separate unique work, so two can overlap, and each step here reads or
 * writes state the other relies on. Take two full sweeps after a server
 * wipe: one clears the database and pages, while the other finishes first and
 * pays the owed full sweep. If the first is then cut short, rows are missing
 * and nothing says a full sweep is still due. So a run holds the lock from
 * reading the cursor to committing the next one.
 */
@Singleton
class CollectionSync
    @Inject
    constructor(
        private val mediaRepository: MediaRepository,
        private val prefs: SyncStatePrefs,
    ) {
        /** What a run came to, in WorkManager's terms. */
        enum class Outcome { Done, Retry, Failed }

        private val runs = Mutex()

        suspend fun run(): Outcome = runs.withLock { runLocked() }

        private suspend fun runLocked(): Outcome {
            // Must run before reading the cursor — it clears a stale one.
            // A DataStore read failure is transient, so retry rather than
            // letting the exception escape the worker.
            val repair =
                try {
                    // It clears the cursor, which belongs to this run's session.
                    SessionGeneration.write { prefs.consumeSyncRepair() }
                } catch (e: StaleSessionException) {
                    Timber.i(e, "Sync skipped: its session has ended")
                    return Outcome.Done
                } catch (e: Exception) {
                    Timber.w(e, "Sync deferred: could not read sync-repair flag")
                    return Outcome.Retry
                }
            val state = runCatching { prefs.flow.first() }
                .getOrElse {
                    Timber.w(it, "Sync deferred: could not read preferences")
                    return Outcome.Retry
                }
            // A repair run deliberately ignores the stored cursor so the sweep
            // re-reads the whole collection and back-fills rows an earlier,
            // lossy pagination pass never delivered.
            val cursor = if (repair) null else state.lastSyncCursor
            if (repair) Timber.i("Sync repair: forcing full resync to back-fill missing rows")
            val seenWipedAt = state.lastSeenWipedAt
            return when (val result = mediaRepository.syncDelta(cursor, seenWipedAt)) {
                is ApiResult.Success -> {
                    val newCursor = result.value.cursor
                    val newWipedAt = result.value.wipedAt
                    try {
                        SessionGeneration.write {
                            // An unchanged cursor — which is what an incomplete
                            // sweep deliberately returns — writes nothing.
                            if (newCursor != null && newCursor != cursor) {
                                prefs.setLastSyncCursor(newCursor)
                            }
                            if (newWipedAt != seenWipedAt) {
                                prefs.setLastSeenWipedAt(newWipedAt)
                            }
                        }
                    } catch (e: StaleSessionException) {
                        Timber.i(e, "Sync result dropped: its session has ended")
                        return Outcome.Done
                    }
                    Timber.d(
                        "Sync ok (cursor %s -> %s, wipedAt %s -> %s)",
                        cursor,
                        newCursor,
                        seenWipedAt,
                        newWipedAt,
                    )
                    // An incomplete sweep held its cursor and pruned nothing;
                    // retry with backoff rather than wait for the next period.
                    if (result.value.complete) Outcome.Done else Outcome.Retry
                }

                ApiResult.NetworkError -> {
                    Timber.w("Sync deferred: network unavailable")
                    Outcome.Retry
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
                        Outcome.Retry
                    } else {
                        Outcome.Failed
                    }
                }

                ApiResult.Unauthorised -> {
                    Timber.w("Sync aborted: unauthorised")
                    Outcome.Done
                }
            }
        }
    }
