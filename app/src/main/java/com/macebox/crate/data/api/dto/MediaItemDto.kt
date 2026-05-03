package com.macebox.crate.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class MediaItemDto(
    val id: Long,
    val userId: String? = null,
    val title: String,
    val artist: String? = null,
    val format: String? = null,
    val year: Int? = null,
    val barcode: String? = null,
    val notes: String? = null,
    val status: String,
    val category: String,
    val discogsId: String? = null,
    val artworkPath: String? = null,
    val label: String? = null,
    val country: String? = null,
    val genres: String? = null,
    val tracklist: List<TrackDto>? = null,
    val pressingNotes: String? = null,
    val discogsArtistId: String? = null,
    val artistBio: String? = null,
    val artistMembers: List<ArtistMemberDto>? = null,
    val marketValue: Double? = null,
    val marketValueLoose: Double? = null,
    val marketValueNew: Double? = null,
    val marketValueCurrency: String? = null,
    val marketValueFetchedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class TrackDto(
    val position: String? = null,
    val title: String? = null,
    val duration: String? = null,
)

@Serializable
data class ArtistMemberDto(
    val name: String,
    val active: Boolean? = null,
)

@Serializable
data class PaginatedMediaDto(
    val items: List<MediaItemDto>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)

@Serializable
data class CreateMediaItemRequest(
    val title: String,
    val artist: String,
    val format: String,
    val year: Int? = null,
    val barcode: String? = null,
    val notes: String? = null,
    val status: String? = null,
    val discogsId: String? = null,
    val artworkPath: String? = null,
    val label: String? = null,
    val country: String? = null,
    val category: String? = null,
)
