package com.macebox.crate.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.macebox.crate.data.db.entity.PlaylistEntity
import com.macebox.crate.data.db.entity.PlaylistItemCrossRef
import com.macebox.crate.data.db.entity.PlaylistWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeWithItems(id: Long): Flow<PlaylistWithItems?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
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
