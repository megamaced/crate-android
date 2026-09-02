package com.megamaced.crate.data.db.entity

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

/**
 * A cross-ref row together with the media item it points at.
 *
 * The obvious shape — a `@Relation`/`@Junction` straight from playlist to
 * media item — throws [PlaylistItemCrossRef.position] away: Room's generated
 * junction query selects neither the position column nor an ORDER BY, so rows
 * come back in the junction index's order (by media-item id) and the app
 * disagrees with the server and the web client for any playlist not already in
 * id order. Relating through the cross-ref keeps the position, and the mapper
 * sorts on it.
 *
 * [item] is nullable only because Room models a one-to-one relation that way;
 * the FK cascade means a cross-ref can never outlive its media item.
 */
data class PlaylistItemWithMedia(
    @androidx.room.Embedded val ref: PlaylistItemCrossRef,
    @Relation(parentColumn = "mediaItemId", entityColumn = "id")
    val item: MediaItemEntity?,
)

data class PlaylistWithItems(
    @androidx.room.Embedded val playlist: PlaylistEntity,
    @Relation(
        entity = PlaylistItemCrossRef::class,
        parentColumn = "id",
        entityColumn = "playlistId",
    )
    val itemRefs: List<PlaylistItemWithMedia>,
)
