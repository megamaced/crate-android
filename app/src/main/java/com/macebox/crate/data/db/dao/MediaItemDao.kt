package com.macebox.crate.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.macebox.crate.data.db.entity.MediaItemEntity
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MediaItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
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
