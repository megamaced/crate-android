package com.megamaced.crate.di

import android.content.Context
import androidx.room.Room
import com.megamaced.crate.data.db.CrateDatabase
import com.megamaced.crate.data.db.dao.HomeFeedDao
import com.megamaced.crate.data.db.dao.MediaItemDao
import com.megamaced.crate.data.db.dao.PlaylistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): CrateDatabase =
        Room
            .databaseBuilder(context, CrateDatabase::class.java, CrateDatabase.NAME)
            .addMigrations(
                CrateDatabase.MIGRATION_1_2,
                CrateDatabase.MIGRATION_2_3,
                CrateDatabase.MIGRATION_3_4,
            ).fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideMediaItemDao(db: CrateDatabase): MediaItemDao = db.mediaItemDao()

    @Provides
    fun providePlaylistDao(db: CrateDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideHomeFeedDao(db: CrateDatabase): HomeFeedDao = db.homeFeedDao()
}
