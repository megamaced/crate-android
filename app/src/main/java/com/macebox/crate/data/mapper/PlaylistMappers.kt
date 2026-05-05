package com.macebox.crate.data.mapper

import com.macebox.crate.data.api.dto.PlaylistDto
import com.macebox.crate.data.db.entity.PlaylistEntity
import com.macebox.crate.data.db.entity.PlaylistWithItems
import com.macebox.crate.domain.model.Playlist

fun PlaylistDto.toDomain(): Playlist =
    Playlist(
        id = id,
        name = name,
        userId = userId,
        items = items.orEmpty().map { it.toDomain() },
        itemCount = itemCount ?: items?.size ?: 0,
        createdAt = createdAt,
        updatedAt = updatedAt,
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

fun PlaylistWithItems.toDomain(codec: MediaItemJsonCodec): Playlist =
    Playlist(
        id = playlist.id,
        name = playlist.name,
        userId = playlist.userId,
        items = items.map { it.toDomain(codec) },
        itemCount = if (items.isNotEmpty()) items.size else playlist.itemCount,
        createdAt = playlist.createdAt,
        updatedAt = playlist.updatedAt,
    )

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
