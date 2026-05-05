package com.macebox.crate.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.macebox.crate.data.db.dao.HomeFeedDao
import com.macebox.crate.data.db.dao.MediaItemDao
import com.macebox.crate.data.db.dao.PlaylistDao
import com.macebox.crate.data.db.entity.MediaItemEntity
import com.macebox.crate.data.db.entity.PlaylistEntity
import com.macebox.crate.data.db.entity.PlaylistItemCrossRef

@Database(
    entities = [
        MediaItemEntity::class,
        PlaylistEntity::class,
        PlaylistItemCrossRef::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class CrateDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun homeFeedDao(): HomeFeedDao

    companion object {
        const val NAME = "crate.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN itemCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE playlists ADD COLUMN coverId INTEGER")
            }
        }
    }
}
