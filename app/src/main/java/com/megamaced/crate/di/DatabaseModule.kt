package com.megamaced.crate.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.megamaced.crate.data.db.CrateDatabase
import com.megamaced.crate.data.db.dao.HomeFeedDao
import com.megamaced.crate.data.db.dao.MediaItemDao
import com.megamaced.crate.data.db.dao.PlaylistDao
import com.megamaced.crate.data.prefs.UserPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        userPreferences: UserPreferences,
    ): CrateDatabase {
        // Application-scoped: only used to fire a one-shot DataStore reset from
        // the destructive-migration callback below.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return Room
            .databaseBuilder(context, CrateDatabase::class.java, CrateDatabase.NAME)
            .addMigrations(
                CrateDatabase.MIGRATION_1_2,
                CrateDatabase.MIGRATION_2_3,
                CrateDatabase.MIGRATION_3_4,
            ).fallbackToDestructiveMigration(dropAllTables = true)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        // Room found no migration path to the current schema
                        // version and dropped every table, so this device just
                        // lost its whole local collection. Nothing here can get
                        // it back — the only defence is a missing migration
                        // being impossible to overlook in a log or bug report.
                        // CrateDatabaseMigrationTest is what stops it reaching
                        // a device in the first place.
                        Timber.e(
                            "DESTRUCTIVE MIGRATION of %s at v%d: every local row dropped, a migration is missing",
                            CrateDatabase.NAME,
                            db.version,
                        )
                        // The DB was just recreated empty, but the delta-sync
                        // cursor in DataStore still points at the pre-wipe
                        // state. Left alone, the next sync would only fetch
                        // rows newer than that cursor and never re-import the
                        // dropped rows, leaving a permanently partial
                        // collection. Reset the cursor to force a full resync.
                        scope.launch {
                            userPreferences.setLastSyncCursor(null)
                            userPreferences.setLastSeenWipedAt(null)
                        }
                    }
                },
            ).build()
    }

    @Provides
    fun provideMediaItemDao(db: CrateDatabase): MediaItemDao = db.mediaItemDao()

    @Provides
    fun providePlaylistDao(db: CrateDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideHomeFeedDao(db: CrateDatabase): HomeFeedDao = db.homeFeedDao()
}
