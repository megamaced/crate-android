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
    version = 4,
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

        /**
         * Mirrors the Nextcloud-side Version0004 migration: a per-item
         * purchase-price pair (amount + ISO-4217 currency) so the user can
         * record what they paid alongside the current market value. Both
         * columns are nullable; existing rows resolve to null on upgrade.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN purchasePrice REAL")
                db.execSQL("ALTER TABLE media_items ADD COLUMN purchasePriceCurrency TEXT")
            }
        }

        /**
         * Mirrors the Nextcloud-side Version0005 migration: two extra
         * user-photo presence flags. Stored as INTEGER (0/1) per Room
         * convention for Boolean columns; default 0 so existing rows
         * report no photos until the user uploads one.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN hasPhoto1 INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE media_items ADD COLUMN hasPhoto2 INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
