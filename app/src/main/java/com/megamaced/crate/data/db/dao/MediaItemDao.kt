package com.megamaced.crate.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.megamaced.crate.data.db.entity.MediaItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {
    // -- Collection reads -----------------------------------------------------
    //
    // Every list the user thinks of as "my collection" is scoped to the signed-in
    // user. Opening a shared item (or a shared playlist) caches the owner's row
    // in this same table, and without the predicate those rows show up in
    // Collection, Search and the offline Home feed as if they were the user's
    // own. A null userId predates the column being carried through the mapper
    // and is treated as own; a null :ownerId means we don't yet know who we are,
    // and filtering then would hide the whole collection — so it fails open.

    @Query(
        """
        SELECT * FROM media_items
        WHERE (:ownerId IS NULL OR userId IS NULL OR userId = :ownerId)
        ORDER BY updatedAt DESC
        """,
    )
    fun observeAll(ownerId: String?): Flow<List<MediaItemEntity>>

    @Query(
        """
        SELECT * FROM media_items
        WHERE category = :category
          AND (:status IS NULL OR status = :status)
          AND (:ownerId IS NULL OR userId IS NULL OR userId = :ownerId)
        ORDER BY updatedAt DESC
        """,
    )
    fun observeByCategory(
        category: String,
        status: String? = null,
        ownerId: String? = null,
    ): Flow<List<MediaItemEntity>>

    // Unscoped by design: the detail screen opens shared items too, and it is
    // the one place another user's row is meant to be readable.
    @Query("SELECT * FROM media_items WHERE id = :id")
    fun observe(id: Long): Flow<MediaItemEntity?>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun get(id: Long): MediaItemEntity?

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

    // -- Reconciliation against a full server sweep ---------------------------
    //
    // Both queries scope to rows the signed-in user owns. Opening a shared item's
    // detail view caches the owner's row here too, and a sweep of *your* items
    // says nothing about whether that row still exists — so it must be neither
    // counted nor pruned. A null userId predates the column being carried
    // through the mapper and is treated as own.

    @Query("SELECT COUNT(*) FROM media_items WHERE userId IS NULL OR userId = :ownerId")
    suspend fun countOwnedBy(ownerId: String): Int

    @Query("SELECT id FROM media_items WHERE userId IS NULL OR userId = :ownerId")
    suspend fun idsOwnedBy(ownerId: String): List<Long>

    @Query("DELETE FROM media_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Ids of cached rows belonging to *other* users — i.e. reached via a share. */
    @Query("SELECT id FROM media_items WHERE userId IS NOT NULL AND userId != :ownerId")
    suspend fun foreignIds(ownerId: String): List<Long>

    /**
     * Drops the local rows for the selected categories only — see
     * `wipeCollection`. Scoped to the signed-in user for the same reason the
     * sweep is: a wipe of your collection deletes nothing of someone else's.
     */
    @Query(
        """
        DELETE FROM media_items
        WHERE category IN (:categories)
          AND (:ownerId IS NULL OR userId IS NULL OR userId = :ownerId)
        """,
    )
    suspend fun deleteByCategories(
        categories: List<String>,
        ownerId: String?,
    )

    @Query("DELETE FROM media_items")
    suspend fun deleteAll()
}
