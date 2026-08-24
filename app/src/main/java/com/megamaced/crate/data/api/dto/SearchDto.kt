package com.megamaced.crate.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class TmdbSearchResultDto(
    val tmdbId: String,
    val title: String,
    val year: Int? = null,
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
    // RAWG reports platforms, which RawgService maps to a Crate format value.
    // Carried through so picking a game fills Platform in — enrichment matches
    // on format server-side, so leaving it empty also degrades enrichment.
    val format: String? = null,
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

/**
 * One provider-backed suggestion from `GET media/{id}/recommendations`.
 *
 * The server returns each suggestion in its own provider's search-result
 * shape, so exactly one of the id fields is populated, depending on the item's
 * category. That's deliberate: it means a suggestion can be handed straight to
 * the same add-from-search path as a manual lookup, instead of needing a
 * parallel conversion.
 */
@Serializable
data class SuggestionDto(
    val title: String = "",
    val artist: String? = null,
    val year: Int? = null,
    val thumb: String? = null,
    val artworkUrl: String? = null,
    val label: String? = null,
    val barcode: String? = null,
    val genres: String? = null,
    val format: String? = null,
    val discogsId: String? = null,
    val tmdbId: String? = null,
    val workKey: String? = null,
    val rawgId: String? = null,
    val comicVineId: String? = null,
)

@Serializable
data class RecommendationsDto(
    val online: List<SuggestionDto> = emptyList(),
    // Provider the suggestions came from, for the row's attribution line.
    val onlineSource: String? = null,
)
