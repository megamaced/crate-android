package com.macebox.crate.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.macebox.crate.data.db.entity.MediaItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Read-only views over [MediaItemEntity] that approximate the server's
 * `/home` feed when the app is offline. The authoritative feed comes from
 * the API (date-seeded so `albumOfDay` matches across devices); these
 * queries are a graceful fallback only.
 */
@Dao
interface HomeFeedDao {
    @Query("SELECT * FROM media_items WHERE status = 'owned' ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentOwned(limit: Int = 6): Flow<List<MediaItemEntity>>

    @Query(
        """
        SELECT * FROM media_items
        WHERE status = 'owned' AND format = :format
        ORDER BY createdAt DESC
        LIMIT :limit
        """,
    )
    fun observeRecentByFormat(
        format: String,
        limit: Int = 6,
    ): Flow<List<MediaItemEntity>>

    @Query(
        """
        SELECT * FROM media_items
        WHERE status = 'owned' AND marketValue IS NOT NULL
        ORDER BY marketValue DESC
        LIMIT :limit
        """,
    )
    fun observeMostValuable(limit: Int = 6): Flow<List<MediaItemEntity>>

    @Query("SELECT DISTINCT format FROM media_items WHERE status = 'owned' AND format IS NOT NULL")
    fun observeOwnedFormats(): Flow<List<String>>
}
