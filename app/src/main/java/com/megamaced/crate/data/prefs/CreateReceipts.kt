package com.megamaced.crate.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.createReceiptsStore by preferencesDataStore(name = "create_receipts")

/**
 * The items new-item forms have created, kept on disk and keyed by form.
 *
 * A form parks its state in SavedStateHandle, but Android writes that out only
 * when the activity saves its instance state. Background the app while a create
 * is out, and the snapshot it keeps predates the create; whatever the form
 * records after that is lost if the process dies before the app returns. The
 * form restored from that snapshot would look new, and saving it again would
 * create a second copy. A receipt is written before any of the work that
 * follows the create, so the restored form can find what it already made.
 *
 * An interface so the form can be unit-tested without a Context.
 */
interface CreateReceipts {
    /** Records that form [formId] created [itemId]; returns once it is on disk. */
    suspend fun record(
        formId: String,
        itemId: Long,
    )

    /** The item form [formId] created, or null if it created none. */
    suspend fun find(formId: String): Long?

    /** Drops form [formId]'s receipt once the form can no longer be restored. Returns at once. */
    fun forget(formId: String)
}

@Singleton
class DataStoreCreateReceipts
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : CreateReceipts {
        private val ds get() = context.createReceiptsStore

        // Application-scoped, for [forget]: the form that calls it is being
        // torn down and has no scope left to run the write in.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        override suspend fun record(
            formId: String,
            itemId: Long,
        ) {
            val now = System.currentTimeMillis()
            ds.edit { prefs ->
                // A receipt is normally forgotten when its form closes. One
                // whose process died and never came back is dropped here once
                // it is older than any form that could still be restored.
                prefs
                    .asMap()
                    .filterValues { value ->
                        val at = (value as? String)?.substringAfter(SEPARATOR, "")?.toLongOrNull()
                        at == null || now - at > MAX_AGE_MS
                    }.keys
                    .forEach { prefs.remove(it) }
                prefs[key(formId)] = "$itemId$SEPARATOR$now"
            }
        }

        override suspend fun find(formId: String): Long? =
            ds.data
                .first()[key(formId)]
                ?.substringBefore(SEPARATOR)
                ?.toLongOrNull()

        override fun forget(formId: String) {
            scope.launch {
                try {
                    ds.edit { it.remove(key(formId)) }
                } catch (e: IOException) {
                    // Left for the age limit in [record] to clear.
                    Timber.w(e, "Couldn't drop a create receipt")
                }
            }
        }

        private fun key(formId: String) = stringPreferencesKey(formId)

        private companion object {
            const val SEPARATOR = "@"
            const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        }
    }
