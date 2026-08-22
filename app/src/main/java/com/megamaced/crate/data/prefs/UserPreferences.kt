package com.megamaced.crate.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    /** Last category the user opened the Collection view on, persisted across launches. */
    val lastCategoryFlow: Flow<Category?>

    suspend fun setLastCategory(category: Category)
}

data class UserPrefs(
    val lastSyncCursor: String? = null,
    val lastSeenWipedAt: String? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val collectionViewMode: CollectionViewMode = CollectionViewMode.Card,
    val hiddenCategories: Set<Category> = emptySet(),
    val lastCategory: Category? = null,
    // Off by default: when on, opening an item asks the server for
    // provider-backed suggestions, which the server turns into an outbound
    // call. The local "more from your crate" row needs no opt-in.
    val onlineRecommendations: Boolean = false,
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
                    lastCategory = prefs[Keys.LAST_CATEGORY]?.let(Category::fromApi),
                    onlineRecommendations = prefs[Keys.ONLINE_RECOMMENDATIONS] ?: false,
                )
            }

        val hiddenCategoriesFlow: Flow<Set<Category>> = flow.map { it.hiddenCategories }

        suspend fun setHiddenCategories(categories: Set<Category>) {
            ds.edit { it[Keys.HIDDEN_CATEGORIES] = categories.map { c -> c.apiValue }.toSet() }
        }

        val onlineRecommendationsFlow: Flow<Boolean> = flow.map { it.onlineRecommendations }

        suspend fun setOnlineRecommendations(enabled: Boolean) {
            ds.edit { it[Keys.ONLINE_RECOMMENDATIONS] = enabled }
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

        override val lastCategoryFlow: Flow<Category?> = flow.map { it.lastCategory }

        override suspend fun setLastCategory(category: Category) {
            ds.edit { it[Keys.LAST_CATEGORY] = category.apiValue }
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

        /**
         * One-shot hook for healing a local collection that a past sync bug left
         * incomplete. Returns true the first time it runs after
         * [CURRENT_SYNC_REPAIR_VERSION] is raised, and marks the repair done in
         * the same edit so a crash mid-sync retries rather than skips it.
         *
         * Callers should treat a true return as "ignore the stored cursor and do
         * a full sweep this run". A full sweep only upserts, so it costs one
         * extra pass over the collection and never drops local rows.
         */
        suspend fun consumeSyncRepair(): Boolean {
            var repair = false
            ds.edit { prefs ->
                val done = prefs[Keys.SYNC_REPAIR_VERSION] ?: 0
                if (done < CURRENT_SYNC_REPAIR_VERSION) {
                    repair = true
                    prefs[Keys.SYNC_REPAIR_VERSION] = CURRENT_SYNC_REPAIR_VERSION
                    prefs.remove(Keys.LAST_SYNC_CURSOR)
                }
            }
            return repair
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
            val ONLINE_RECOMMENDATIONS = booleanPreferencesKey("online_recommendations")
            val LAST_CATEGORY = stringPreferencesKey("last_category")
            val UPDATE_LAST_CHECKED_AT = longPreferencesKey("update_last_checked_at")
            val UPDATE_LAST_NOTIFIED_VERSION = stringPreferencesKey("update_last_notified_version")
            val SYNC_REPAIR_VERSION = intPreferencesKey("sync_repair_version")
        }

        companion object {
            /**
             * Raise this to force every existing install into one full resync on
             * the next sync. Currently 1: the server paginated on `created_at`
             * alone, which is not unique — bulk imports stamp hundreds of rows
             * with the same second — so LIMIT/OFFSET silently skipped rows that
             * the local DB then never learned about. The delta cursor had already
             * advanced past them, so no amount of pull-to-refresh recovered them.
             */
            const val CURRENT_SYNC_REPAIR_VERSION = 1
        }
    }

data class UpdateCheckState(
    val lastCheckedAt: Long,
    val lastNotifiedVersion: String?,
)
