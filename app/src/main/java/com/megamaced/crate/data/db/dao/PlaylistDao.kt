package com.megamaced.crate.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.megamaced.crate.data.db.entity.PlaylistEntity
import com.megamaced.crate.data.db.entity.PlaylistItemCrossRef
import com.megamaced.crate.data.db.entity.PlaylistWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    // "My Playlists" is the signed-in user's own list. Opening a playlist
    // shared with you caches the owner's row in this same table, so without the
    // predicate it joins your own list and stays there offline. Same fail-open
    // null case as MediaItemDao: an unknown current user filters nothing.
    @Transaction
    @Query(
        """
        SELECT * FROM playlists
        WHERE (:ownerId IS NULL OR userId IS NULL OR userId = :ownerId)
        ORDER BY updatedAt DESC
        """,
    )
    fun observeAll(ownerId: String?): Flow<List<PlaylistWithItems>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeWithItems(id: Long): Flow<PlaylistWithItems?>

    // @Upsert (INSERT-or-UPDATE) is used instead of @Insert(REPLACE) so that
    // refreshing the playlist list — which doesn't include items — doesn't
    // cascade-delete the playlist_items cross-refs that the detail endpoint
    // populated. With REPLACE, Room deletes-then-inserts the row, which fires
    // the FK CASCADE on playlist_items and wipes the cached membership.
    @Upsert
    suspend fun upsert(playlist: PlaylistEntity)

    @Upsert
    suspend fun upsertAll(playlists: List<PlaylistEntity>)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM playlists")
    suspend fun deleteAll()

    @Query("SELECT id FROM playlists WHERE userId IS NULL OR userId = :ownerId")
    suspend fun idsOwnedBy(ownerId: String): List<Long>

    @Query("SELECT id FROM playlists")
    suspend fun allIds(): List<Long>

    @Query("DELETE FROM playlists WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * Reconciles the local table against an authoritative server list.
     *
     * Upserting alone can never notice a deletion — a playlist deleted on the
     * web simply stops being listed — so it would linger on the phone forever,
     * un-openable and un-deletable. One transaction so a list refresh is never
     * observed half-applied.
     *
     * [ownerId] scopes what may be deleted. The listing enumerates the user's
     * own playlists, so it says nothing about a playlist shared with them —
     * pruning those would drop the cache the shared views read. A null owner
     * (no `/me` yet) means nothing can be classified, so nothing is pruned.
     */
    @Transaction
    suspend fun replaceAll(
        playlists: List<PlaylistEntity>,
        ownerId: String?,
    ) {
        if (ownerId != null) {
            val serverIds = playlists.mapTo(mutableSetOf()) { it.id }
            val stale = idsOwnedBy(ownerId).filterNot { it in serverIds }
            if (stale.isNotEmpty()) deleteByIds(stale)
        }
        upsertAll(playlists)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCrossRefs(refs: List<PlaylistItemCrossRef>)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylistItems(playlistId: Long)

    @Transaction
    suspend fun replacePlaylistItems(
        playlistId: Long,
        mediaItemIds: List<Long>,
    ) {
        clearPlaylistItems(playlistId)
        upsertCrossRefs(
            mediaItemIds.mapIndexed { index, mediaItemId ->
                PlaylistItemCrossRef(
                    playlistId = playlistId,
                    mediaItemId = mediaItemId,
                    position = index,
                )
            },
        )
    }
}
