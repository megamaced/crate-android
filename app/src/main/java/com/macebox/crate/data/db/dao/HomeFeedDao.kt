package com.macebox.crate.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.macebox.crate.data.db.entity.MediaItemEntity

/**
 * Read-only views over [MediaItemEntity] that approximate the server's
 * `/home` feed when the app is offline. The authoritative feed comes from
 * the API (date-seeded so `albumOfDay` matches across devices); these
 * queries are a graceful fallback only.
 */
@Dao
interface HomeFeedDao {
    @Query("SELECT * FROM media_items WHERE status = 'owned' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentOwned(limit: Int = 12): List<MediaItemEntity>

    @Query(
        """
        SELECT * FROM media_items
        WHERE status = 'owned' AND category = :category
        ORDER BY createdAt DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentByCategory(
        category: String,
        limit: Int = 6,
    ): List<MediaItemEntity>

    @Query(
        """
        SELECT * FROM media_items
        WHERE status = 'owned' AND marketValue IS NOT NULL
        ORDER BY marketValue DESC
        LIMIT :limit
        """,
    )
    suspend fun getMostValuable(limit: Int = 6): List<MediaItemEntity>

    @Query("SELECT DISTINCT category FROM media_items WHERE status = 'owned' AND category IS NOT NULL")
    suspend fun getOwnedCategories(): List<String>

    @Query("SELECT COUNT(*) FROM media_items WHERE status = 'owned' AND category = :category")
    suspend fun countByCategory(category: String): Int
}
