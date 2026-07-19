package com.megamaced.crate.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.megamaced.crate.data.db.entity.MediaItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MediaItemEntity>>

    @Query(
        """
        SELECT * FROM media_items
        WHERE category = :category
          AND (:status IS NULL OR status = :status)
        ORDER BY updatedAt DESC
        """,
    )
    fun observeByCategory(
        category: String,
        status: String? = null,
    ): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    fun observe(id: Long): Flow<MediaItemEntity?>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun get(id: Long): MediaItemEntity?

    @Query("SELECT MAX(updatedAt) FROM media_items")
    suspend fun maxUpdatedAt(): String?

    // @Upsert (INSERT-or-UPDATE), NOT @Insert(REPLACE): with REPLACE, Room
    // emits INSERT OR REPLACE, which deletes-then-inserts a conflicting row and
    // fires the ON DELETE CASCADE on playlist_items — so routinely re-syncing an
    // existing item (SyncWorker runs on every app foreground) would silently
    // drop it from every playlist it belongs to. @Upsert updates in place and
    // leaves the cross-refs intact. Mirrors the same choice in PlaylistDao.
    @Upsert
    suspend fun upsert(item: MediaItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<MediaItemEntity>)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM media_items")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(items: List<MediaItemEntity>) {
        deleteAll()
        upsertAll(items)
    }
}
