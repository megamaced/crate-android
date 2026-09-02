package com.megamaced.crate.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.megamaced.crate.data.db.entity.MediaItemEntity

/**
 * Read-only views over [MediaItemEntity] that approximate the server's
 * `/home` feed when the app is offline. The authoritative feed comes from
 * the API (date-seeded so `albumOfDay` matches across devices); these
 * queries are a graceful fallback only.
 *
 * Every query is scoped to the signed-in user: a shared item opened from
 * "Shared with me" is cached in the same table, and the offline Home feed is
 * a view of *your* collection. A null [MediaItemEntity.userId] is own; a null
 * `ownerId` means the current user isn't known yet and the filter fails open.
 */
@Dao
interface HomeFeedDao {
    @Query(
        """
        SELECT * FROM media_items
        WHERE status = 'owned'
          AND (:ownerId IS NULL OR userId IS NULL OR userId = :ownerId)
        ORDER BY createdAt DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentOwned(
        ownerId: String?,
        limit: Int = 12,
    ): List<MediaItemEntity>

    @Query(
        """
        SELECT * FROM media_items
        WHERE status = 'owned' AND category = :category
          AND (:ownerId IS NULL OR userId IS NULL OR userId = :ownerId)
        ORDER BY createdAt DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentByCategory(
        category: String,
        ownerId: String?,
        limit: Int = 6,
    ): List<MediaItemEntity>

    @Query(
        """
        SELECT * FROM media_items
        WHERE status = 'owned' AND marketValue IS NOT NULL
          AND (:ownerId IS NULL OR userId IS NULL OR userId = :ownerId)
        ORDER BY marketValue DESC
        LIMIT :limit
        """,
    )
    suspend fun getMostValuable(
        ownerId: String?,
        limit: Int = 6,
    ): List<MediaItemEntity>

    @Query(
        """
        SELECT DISTINCT category FROM media_items
        WHERE status = 'owned' AND category IS NOT NULL
          AND (:ownerId IS NULL OR userId IS NULL OR userId = :ownerId)
        """,
    )
    suspend fun getOwnedCategories(ownerId: String?): List<String>

    @Query(
        """
        SELECT COUNT(*) FROM media_items
        WHERE status = 'owned' AND category = :category
          AND (:ownerId IS NULL OR userId IS NULL OR userId = :ownerId)
        """,
    )
    suspend fun countByCategory(
        category: String,
        ownerId: String?,
    ): Int
}
