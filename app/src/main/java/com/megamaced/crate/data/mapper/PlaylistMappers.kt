package com.megamaced.crate.data.mapper

import com.megamaced.crate.data.api.dto.PlaylistDto
import com.megamaced.crate.data.db.entity.PlaylistEntity
import com.megamaced.crate.data.db.entity.PlaylistWithItems
import com.megamaced.crate.domain.model.Playlist

fun PlaylistDto.toDomain(): Playlist =
    Playlist(
        id = id,
        name = name,
        userId = userId,
        items = items.orEmpty().map { it.toDomain() },
        itemCount = itemCount ?: items?.size ?: 0,
        createdAt = createdAt,
        updatedAt = updatedAt,
        canWrite = resolveCanWrite(canWrite, permission),
    )

fun PlaylistDto.toEntity(): PlaylistEntity =
    PlaylistEntity(
        id = id,
        name = name,
        userId = userId,
        itemCount = itemCount ?: items?.size ?: 0,
        coverId = coverId ?: items?.firstOrNull()?.id,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun PlaylistWithItems.toDomain(codec: MediaItemJsonCodec): Playlist {
    // The API returns a playlist in the user's chosen order and
    // replacePlaylistItems stores that as PlaylistItemCrossRef.position. Room
    // gives no ordering guarantee of its own, so sorting here is what makes the
    // phone agree with the web client.
    val items = itemRefs.sortedBy { it.ref.position }.mapNotNull { it.item }
    return Playlist(
        id = playlist.id,
        name = playlist.name,
        userId = playlist.userId,
        items = items.map { it.toDomain(codec) },
        itemCount = if (items.isNotEmpty()) items.size else playlist.itemCount,
        createdAt = playlist.createdAt,
        updatedAt = playlist.updatedAt,
    )
}

fun PlaylistEntity.toDomain(): Playlist =
    Playlist(
        id = id,
        name = name,
        userId = userId,
        items = emptyList(),
        itemCount = itemCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
