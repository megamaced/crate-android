package com.macebox.crate.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class TmdbSearchResultDto(
    val tmdbId: String,
    val title: String,
    val year: Int? = null,
    val thumb: String? = null,
)

@Serializable
data class TmdbMovieDto(
    val tmdbId: String,
    val title: String,
    val artist: String? = null,
    val directorId: String? = null,
    val year: Int? = null,
    val genres: String? = null,
    val label: String? = null,
    val country: String? = null,
    val overview: String? = null,
    val artworkUrl: String? = null,
    val thumb: String? = null,
)

@Serializable
data class OpenLibraryResultDto(
    val workKey: String,
    val title: String,
    val artist: String? = null,
    val year: Int? = null,
    val thumb: String? = null,
    val label: String? = null,
    val barcode: String? = null,
    val genres: String? = null,
    val artworkUrl: String? = null,
    val authorKey: String? = null,
    val authorBio: String? = null,
    val overview: String? = null,
)

@Serializable
data class RawgSearchResultDto(
    val rawgId: String,
    val title: String,
    val year: Int? = null,
    val thumb: String? = null,
    val genres: String? = null,
)

@Serializable
data class RawgGameDto(
    val rawgId: String,
    val title: String,
    val artist: String? = null,
    val year: Int? = null,
    val label: String? = null,
    val genres: String? = null,
    val overview: String? = null,
    val artworkUrl: String? = null,
    val thumb: String? = null,
)

@Serializable
data class ComicVineSearchResultDto(
    val comicVineId: String,
    val title: String,
    val year: Int? = null,
    val label: String? = null,
    val genres: String? = null,
    val thumb: String? = null,
)

@Serializable
data class ComicVineVolumeDto(
    val comicVineId: String,
    val title: String,
    val year: Int? = null,
    val label: String? = null,
    val genres: String? = null,
    val overview: String? = null,
    val artworkUrl: String? = null,
    val thumb: String? = null,
)

@Serializable
data class DiscogsSearchResultDto(
    val discogsId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val format: String? = null,
    val year: Int? = null,
    val label: String? = null,
    val country: String? = null,
    val barcode: String? = null,
    val thumb: String? = null,
)
