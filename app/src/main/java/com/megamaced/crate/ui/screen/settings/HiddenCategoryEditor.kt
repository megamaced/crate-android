package com.megamaced.crate.ui.screen.settings

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.domain.model.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds the authoritative hidden-category set while writes are in flight.
 *
 * The set the UI renders is a projection of the preference cache, which only
 * moves once a write has round-tripped through the server. Deriving each edit
 * from that projection means a second toggle reads a pre-first-write value and
 * silently un-hides the first category, so the pending set is kept here and
 * [stage] always builds on it. The writes themselves are serialised, so they
 * also reach the server in the order the user made them.
 */
internal class HiddenCategoryEditor(
    private val write: suspend (Set<Category>) -> ApiResult<Unit>,
) {
    private val _pending = MutableStateFlow<Set<Category>?>(null)

    /** The edit awaiting a round trip, or null when the cache is authoritative. */
    val pending: StateFlow<Set<Category>?> = _pending.asStateFlow()

    private val _busy = MutableStateFlow<Set<Category>>(emptySet())

    /** Categories with a write outstanding; their switches stay disabled. */
    val busy: StateFlow<Set<Category>> = _busy.asStateFlow()

    private val mutex = Mutex()

    /**
     * Records the intended next hidden set and marks [category] busy, all
     * synchronously so consecutive taps accumulate rather than race.
     *
     * Returns null when the change is refused because it would hide every
     * category — the server enforces the same rule.
     */
    fun stage(
        persisted: Set<Category>,
        category: Category,
        visible: Boolean,
    ): Set<Category>? {
        val current = _pending.value ?: persisted
        val next =
            if (visible) {
                current - category
            } else {
                if (current.size >= Category.entries.size - 1) return null
                current + category
            }
        _pending.value = next
        _busy.update { it + category }
        return next
    }

    /**
     * Writes a set produced by [stage]. On failure the pending set is dropped
     * so the UI falls back to whatever the server actually holds.
     */
    suspend fun commit(
        target: Set<Category>,
        category: Category,
    ): ApiResult<Unit> {
        try {
            val result = mutex.withLock { write(target) }
            if (result !is ApiResult.Success) _pending.value = null
            return result
        } finally {
            _busy.update { it - category }
        }
    }
}
