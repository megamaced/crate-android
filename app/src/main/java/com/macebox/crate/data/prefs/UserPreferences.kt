package com.macebox.crate.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "crate_prefs")

enum class ThemeMode {
    System,
    Light,
    Dark,
}

data class UserPrefs(
    val lastSyncCursor: String? = null,
    val themeMode: ThemeMode = ThemeMode.System,
)

@Singleton
class UserPreferences
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val ds get() = context.dataStore

        val flow: Flow<UserPrefs> =
            ds.data.map { prefs ->
                UserPrefs(
                    lastSyncCursor = prefs[Keys.LAST_SYNC_CURSOR],
                    themeMode = prefs[Keys.THEME_MODE]?.let(::parseThemeMode) ?: ThemeMode.System,
                )
            }

        suspend fun setLastSyncCursor(cursor: String?) {
            ds.edit { it.write(Keys.LAST_SYNC_CURSOR, cursor) }
        }

        suspend fun setThemeMode(mode: ThemeMode) {
            ds.edit { it[Keys.THEME_MODE] = mode.name }
        }

        private fun parseThemeMode(value: String): ThemeMode = runCatching { ThemeMode.valueOf(value) }.getOrDefault(ThemeMode.System)

        private fun <T> MutablePreferences.write(
            key: Preferences.Key<T>,
            value: T?,
        ) {
            if (value == null) remove(key) else set(key, value)
        }

        private object Keys {
            val LAST_SYNC_CURSOR = stringPreferencesKey("last_sync_cursor")
            val THEME_MODE = stringPreferencesKey("theme_mode")
        }
    }
