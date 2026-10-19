package com.megamaced.crate.ui.screen.settings

import com.megamaced.crate.data.api.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Saves whole snapshots of a group of settings, one at a time, always sending
 * the newest.
 *
 * The server replaces every field of the group with whatever snapshot arrives,
 * so two saves in flight at once can land in either order — and an older one
 * landing last reverts a newer choice on the server while the screen still
 * shows it. Here a save starts only when the previous one has finished, and
 * snapshots submitted in the meantime coalesce: only the latest is sent next.
 *
 * [submit] must be called on [scope]'s dispatcher, which serialises it with
 * the save loop.
 */
internal class SnapshotSaver<T : Any>(
    private val scope: CoroutineScope,
    private val save: suspend (T) -> ApiResult<Unit>,
    /**
     * A save failed and nothing newer is queued to supersede it. [confirmed]
     * is the snapshot the server last accepted, if one is known.
     */
    private val onFailure: (result: ApiResult<Unit>, confirmed: T?) -> Unit,
) {
    /** The snapshot the server last accepted; seeded from a load. */
    var confirmed: T? = null

    private var pending: T? = null
    private var running: Job? = null

    /** True while a save is outstanding, so a load does not overwrite a newer choice. */
    val isSaving: Boolean get() = running?.isActive == true

    fun submit(snapshot: T) {
        pending = snapshot
        if (isSaving) return
        running =
            scope.launch {
                while (true) {
                    val next = pending ?: break
                    pending = null
                    val result = save(next)
                    if (result is ApiResult.Success) {
                        confirmed = next
                    } else if (pending == null) {
                        onFailure(result, confirmed)
                    }
                }
            }
    }
}
