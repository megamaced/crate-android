package com.macebox.crate.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.macebox.crate.data.db.entity.PlaylistEntity
import com.macebox.crate.data.db.entity.PlaylistItemCrossRef
import com.macebox.crate.data.db.entity.PlaylistWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PlaylistWithItems>>

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
