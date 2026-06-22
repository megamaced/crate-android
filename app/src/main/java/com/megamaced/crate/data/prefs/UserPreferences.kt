package com.megamaced.crate.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.megamaced.crate.domain.model.Category
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "crate_prefs")

enum class ThemeMode {
    System,
    Light,
    Dark,
}

enum class CollectionViewMode {
    Card,
    List,
}

/**
 * Narrow view of [UserPreferences] surfaced to the collection screen so the
 * ViewModel can be exercised without the DataStore-backed singleton — handy
 * for unit tests, which otherwise need a Context.
 */
interface CollectionPrefs {
    val collectionViewModeFlow: Flow<CollectionViewMode>

    suspend fun setCollectionViewMode(mode: CollectionViewMode)
}

data class UserPrefs(
    val lastSyncCursor: String? = null,
    val lastSeenWipedAt: String? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val collectionViewMode: CollectionViewMode = CollectionViewMode.Card,
    val hiddenCategories: Set<Category> = emptySet(),
)

@Singleton
class UserPreferences
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : CollectionPrefs {
        private val ds get() = context.dataStore

        val flow: Flow<UserPrefs> =
            ds.data.map { prefs ->
                UserPrefs(
                    lastSyncCursor = prefs[Keys.LAST_SYNC_CURSOR],
                    lastSeenWipedAt = prefs[Keys.LAST_SEEN_WIPED_AT],
                    themeMode = prefs[Keys.THEME_MODE]?.let(::parseThemeMode) ?: ThemeMode.System,
                    collectionViewMode =
                        prefs[Keys.COLLECTION_VIEW_MODE]?.let(::parseCollectionViewMode)
                            ?: CollectionViewMode.Card,
                    hiddenCategories =
                        prefs[Keys.HIDDEN_CATEGORIES]
                            ?.mapNotNull(Category::fromApi)
                            ?.toSet()
                            .orEmpty(),
                )
            }

        val hiddenCategoriesFlow: Flow<Set<Category>> = flow.map { it.hiddenCategories }

        suspend fun setHiddenCategories(categories: Set<Category>) {
            ds.edit { it[Keys.HIDDEN_CATEGORIES] = categories.map { c -> c.apiValue }.toSet() }
        }

        suspend fun setLastSyncCursor(cursor: String?) {
            ds.edit { it.write(Keys.LAST_SYNC_CURSOR, cursor) }
        }

        suspend fun setLastSeenWipedAt(wipedAt: String?) {
            ds.edit { it.write(Keys.LAST_SEEN_WIPED_AT, wipedAt) }
        }

        suspend fun setThemeMode(mode: ThemeMode) {
            ds.edit { it[Keys.THEME_MODE] = mode.name }
        }

        override val collectionViewModeFlow: Flow<CollectionViewMode> =
            flow.map { it.collectionViewMode }

        override suspend fun setCollectionViewMode(mode: CollectionViewMode) {
            ds.edit { it[Keys.COLLECTION_VIEW_MODE] = mode.name }
        }

        suspend fun getUpdateState(): UpdateCheckState {
            val prefs = ds.data.first()
            return UpdateCheckState(
                lastCheckedAt = prefs[Keys.UPDATE_LAST_CHECKED_AT] ?: 0L,
                lastNotifiedVersion = prefs[Keys.UPDATE_LAST_NOTIFIED_VERSION],
            )
        }

        suspend fun setUpdateLastCheckedAt(epochMillis: Long) {
            ds.edit { it[Keys.UPDATE_LAST_CHECKED_AT] = epochMillis }
        }

        suspend fun setUpdateLastNotifiedVersion(version: String) {
            ds.edit { it[Keys.UPDATE_LAST_NOTIFIED_VERSION] = version }
        }

        private fun parseThemeMode(value: String): ThemeMode = runCatching { ThemeMode.valueOf(value) }.getOrDefault(ThemeMode.System)

        private fun parseCollectionViewMode(value: String): CollectionViewMode =
            runCatching { CollectionViewMode.valueOf(value) }.getOrDefault(CollectionViewMode.Card)

        private fun <T> MutablePreferences.write(
            key: Preferences.Key<T>,
            value: T?,
        ) {
            if (value == null) remove(key) else set(key, value)
        }

        private object Keys {
            val LAST_SYNC_CURSOR = stringPreferencesKey("last_sync_cursor")
            val LAST_SEEN_WIPED_AT = stringPreferencesKey("last_seen_wiped_at")
            val THEME_MODE = stringPreferencesKey("theme_mode")
            val COLLECTION_VIEW_MODE = stringPreferencesKey("collection_view_mode")
            val HIDDEN_CATEGORIES = stringSetPreferencesKey("hidden_categories")
            val UPDATE_LAST_CHECKED_AT = longPreferencesKey("update_last_checked_at")
            val UPDATE_LAST_NOTIFIED_VERSION = stringPreferencesKey("update_last_notified_version")
        }
    }

data class UpdateCheckState(
    val lastCheckedAt: Long,
    val lastNotifiedVersion: String?,
)
