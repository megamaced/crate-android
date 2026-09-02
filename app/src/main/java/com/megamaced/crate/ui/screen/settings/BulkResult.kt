package com.megamaced.crate.ui.screen.settings

import com.megamaced.crate.data.api.ApiResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * What a bulk run came to: how many items the server actually accepted, how
 * many it refused one at a time, and whether the run gave up part-way.
 */
data class BulkResult(
    val done: Int,
    val failed: Int,
    /** Null when the run got to the end of [ids]. */
    val abandonedBy: Abandon? = null,
) {
    /** Why a run stopped early. Both are conditions no later item would escape. */
    enum class Abandon { Network, Unauthorised }

    /** Items the run never got through, whether refused or never attempted. */
    fun untouched(total: Int): Int = total - done
}

/**
 * Runs [operation] over [ids], reporting progress through [onProgress].
 *
 * Progress counts attempts that resolved, and [BulkResult.done] counts only
 * the ones that succeeded. Treating every call as done — which is what
 * discarding the results amounted to — let a run whose second half failed
 * outright march to 100% and then vanish, telling the user the whole
 * collection had been updated.
 *
 * A network failure or a sign-out is not specific to one item: every remaining
 * call would fail the same way, so the run stops rather than working through
 * the rest of the collection to fail once per item.
 *
 * Extracted from [SettingsViewModel] so this is testable without a Context.
 */
suspend fun runBulk(
    ids: List<Long>,
    onProgress: (Int) -> Unit,
    operation: suspend (Long) -> ApiResult<*>,
): BulkResult {
    var done = 0
    var failed = 0
    for (id in ids) {
        // Cancellation still has to stop the run: the caller's scope is gone
        // the moment the user leaves Settings.
        currentCoroutineContext().ensureActive()
        when (operation(id)) {
            is ApiResult.Success -> {
                done++
            }

            // Per-item — a provider with no match for this one item, say. The
            // rest of the run can still succeed.
            is ApiResult.HttpError -> {
                failed++
            }

            ApiResult.NetworkError -> {
                return BulkResult(done, failed, BulkResult.Abandon.Network)
            }

            ApiResult.Unauthorised -> {
                return BulkResult(done, failed, BulkResult.Abandon.Unauthorised)
            }
        }
        onProgress(done + failed)
    }
    return BulkResult(done = done, failed = failed)
}
