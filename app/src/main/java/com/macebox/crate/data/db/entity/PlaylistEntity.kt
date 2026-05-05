package com.macebox.crate.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val userId: String? = null,
    val itemCount: Int = 0,
    val coverId: Long? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "mediaItemId"],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["mediaItemId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaylistItemCrossRef(
    val playlistId: Long,
    val mediaItemId: Long,
    /** Position within the playlist (0-based) for ordering. */
    val position: Int = 0,
)

data class PlaylistWithItems(
    @androidx.room.Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy =
            androidx.room.Junction(
                value = PlaylistItemCrossRef::class,
                parentColumn = "playlistId",
                entityColumn = "mediaItemId",
            ),
    )
    val items: List<MediaItemEntity>,
)
