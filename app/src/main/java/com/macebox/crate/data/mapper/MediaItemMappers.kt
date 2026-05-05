package com.macebox.crate.data.mapper

import com.macebox.crate.data.api.dto.ArtistMemberDto
import com.macebox.crate.data.api.dto.CreateMediaItemRequest
import com.macebox.crate.data.api.dto.MediaItemDto
import com.macebox.crate.data.api.dto.TrackDto
import com.macebox.crate.data.db.entity.MediaItemEntity
import com.macebox.crate.domain.model.ArtistMember
import com.macebox.crate.domain.model.Category
import com.macebox.crate.domain.model.MarketValue
import com.macebox.crate.domain.model.MediaItem
import com.macebox.crate.domain.model.MediaItemDraft
import com.macebox.crate.domain.model.Status
import com.macebox.crate.domain.model.Track
import kotlinx.serialization.json.Json

/**
 * Encodes/decodes `tracklist` and `artistMembers` for the [MediaItemEntity]
 * JSON columns. Allowed to throw on programmer error (malformed local rows
 * indicate a Room migration bug, not a network condition).
 */
class MediaItemJsonCodec(
    private val json: Json,
) {
    fun encodeTracks(tracks: List<TrackDto>?): String? = tracks?.let { json.encodeToString(it) }

    fun decodeTracks(raw: String?): List<TrackDto> = raw?.let { json.decodeFromString<List<TrackDto>>(it) } ?: emptyList()

    fun encodeMembers(members: List<ArtistMemberDto>?): String? = members?.let { json.encodeToString(it) }

    fun decodeMembers(raw: String?): List<ArtistMemberDto> = raw?.let { json.decodeFromString<List<ArtistMemberDto>>(it) } ?: emptyList()
}

// -- DTO -> Domain ----------------------------------------------------------

fun MediaItemDto.toDomain(): MediaItem =
    MediaItem(
        id = id,
        userId = userId,
        title = title,
        artist = artist,
        format = format,
        year = year,
        barcode = barcode,
        notes = notes,
        status = Status.fromApi(status) ?: Status.Owned,
        category = Category.fromApi(category) ?: Category.Music,
        discogsId = discogsId,
        artworkPath = artworkPath,
        label = label,
        country = country,
        genres = genres,
        tracklist = tracklist.orEmpty().map(TrackDto::toDomain),
        pressingNotes = pressingNotes,
        discogsArtistId = discogsArtistId,
        artistBio = artistBio,
        artistMembers = artistMembers.orEmpty().map(ArtistMemberDto::toDomain),
        marketValue =
            MarketValue(
                currency = marketValueCurrency,
                main = marketValue,
                loose = marketValueLoose,
                new = marketValueNew,
                fetchedAt = marketValueFetchedAt,
            ),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun TrackDto.toDomain(): Track = Track(position = position, title = title, duration = duration)

fun ArtistMemberDto.toDomain(): ArtistMember = ArtistMember(name = name, active = active)

// -- DTO -> Entity ----------------------------------------------------------

fun MediaItemDto.toEntity(codec: MediaItemJsonCodec): MediaItemEntity =
    MediaItemEntity(
        id = id,
        userId = userId,
        title = title,
        artist = artist,
        format = format,
        year = year,
        barcode = barcode,
        notes = notes,
        status = status,
        category = category,
        discogsId = discogsId,
        artworkPath = artworkPath,
        label = label,
        country = country,
        genres = genres,
        tracklistJson = codec.encodeTracks(tracklist),
        pressingNotes = pressingNotes,
        discogsArtistId = discogsArtistId,
        artistBio = artistBio,
        artistMembersJson = codec.encodeMembers(artistMembers),
        marketValue = marketValue,
        marketValueLoose = marketValueLoose,
        marketValueNew = marketValueNew,
        marketValueCurrency = marketValueCurrency,
        marketValueFetchedAt = marketValueFetchedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

// -- Entity -> Domain -------------------------------------------------------

fun MediaItemEntity.toDomain(codec: MediaItemJsonCodec): MediaItem =
    MediaItem(
        id = id,
        userId = userId,
        title = title,
        artist = artist,
        format = format,
        year = year,
        barcode = barcode,
        notes = notes,
        status = Status.fromApi(status) ?: Status.Owned,
        category = Category.fromApi(category) ?: Category.Music,
        discogsId = discogsId,
        artworkPath = artworkPath,
        label = label,
        country = country,
        genres = genres,
        tracklist = codec.decodeTracks(tracklistJson).map(TrackDto::toDomain),
        pressingNotes = pressingNotes,
        discogsArtistId = discogsArtistId,
        artistBio = artistBio,
        artistMembers = codec.decodeMembers(artistMembersJson).map(ArtistMemberDto::toDomain),
        marketValue =
            MarketValue(
                currency = marketValueCurrency,
                main = marketValue,
                loose = marketValueLoose,
                new = marketValueNew,
                fetchedAt = marketValueFetchedAt,
            ),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

// -- Draft -> Request -------------------------------------------------------

fun MediaItemDraft.toRequest(): CreateMediaItemRequest =
    CreateMediaItemRequest(
        title = title,
        artist = artist,
        mediaFormat = format,
        year = year,
        barcode = barcode,
        notes = notes,
        status = status?.apiValue,
        discogsId = discogsId,
        artworkPath = artworkPath,
        label = label,
        country = country,
        category = category?.apiValue,
    )
