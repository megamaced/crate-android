package com.macebox.crate.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = true,
)
abstract class CrateDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun homeFeedDao(): HomeFeedDao

    companion object {
        const val NAME = "crate.db"
    }
}
