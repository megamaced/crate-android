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
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun PlaylistDto.toEntity(): PlaylistEntity =
    PlaylistEntity(
        id = id,
        name = name,
        userId = userId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun PlaylistWithItems.toDomain(codec: MediaItemJsonCodec): Playlist =
    Playlist(
        id = playlist.id,
        name = playlist.name,
        userId = playlist.userId,
        items = items.map { it.toDomain(codec) },
        createdAt = playlist.createdAt,
        updatedAt = playlist.updatedAt,
    )

fun PlaylistEntity.toDomain(): Playlist =
    Playlist(
        id = id,
        name = name,
        userId = userId,
        items = emptyList(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
