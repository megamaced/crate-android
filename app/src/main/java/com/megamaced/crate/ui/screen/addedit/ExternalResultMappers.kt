package com.megamaced.crate.ui.screen.addedit

import com.megamaced.crate.data.api.dto.ComicVineSearchResultDto
import com.megamaced.crate.data.api.dto.DiscogsSearchResultDto
import com.megamaced.crate.data.api.dto.OpenLibraryResultDto
import com.megamaced.crate.data.api.dto.RawgSearchResultDto
import com.megamaced.crate.data.api.dto.SuggestionDto
import com.megamaced.crate.data.api.dto.TmdbSearchResultDto

/**
 * The one place a provider search result becomes an [ExternalSearchResult].
 *
 * Main search, the add/edit sheet and barcode scanning all show the same rows
 * and feed the same form, and each used to carry its own copy of these
 * mappings. The copies drifted: main search dropped every cover, and both it
 * and the add/edit sheet dropped RAWG's platform, so picking a game lost the
 * Format the server enriches on.
 *
 * `coverUrl` is doing two jobs — the thumbnail on the row, and the artwork the
 * form hands the server to cache — so where a provider offers both sizes the
 * larger one wins.
 */
fun DiscogsSearchResultDto.toResult(): ExternalSearchResult =
    ExternalSearchResult(
        title = title.orEmpty(),
        artist = artist,
        format = format,
        year = year,
        barcode = barcode,
        label = label,
        country = country,
        discogsId = discogsId,
        coverUrl = thumb,
    )

fun TmdbSearchResultDto.toResult(): ExternalSearchResult =
    ExternalSearchResult(
        title = title,
        year = year,
        coverUrl = thumb,
    )

fun OpenLibraryResultDto.toResult(): ExternalSearchResult =
    ExternalSearchResult(
        title = title,
        artist = artist,
        year = year,
        barcode = barcode,
        label = label,
        coverUrl = artworkUrl ?: thumb,
    )

/** Null for a result with no title — Open Library's ISBN lookup can return one. */
fun OpenLibraryResultDto.toResultOrNull(): ExternalSearchResult? = if (title.isBlank()) null else toResult()

fun RawgSearchResultDto.toResult(): ExternalSearchResult =
    ExternalSearchResult(
        title = title,
        // RAWG reports platforms, which the server maps to a Crate format
        // value. Enrichment matches on format server-side, so dropping it here
        // both empties the Platform field and degrades the later enrichment.
        format = format,
        year = year,
        subtitle = genres,
        coverUrl = thumb,
    )

fun ComicVineSearchResultDto.toResult(): ExternalSearchResult =
    ExternalSearchResult(
        title = title,
        year = year,
        label = label,
        subtitle = genres,
        coverUrl = thumb,
    )

/**
 * A provider suggestion in the shape the add form already understands. The
 * server returns each suggestion in its provider's search-result shape, so
 * exactly one id field is set; they all land in `discogsId`, which is the
 * generic enrichment-id field on both the form and the server's schema.
 */
fun SuggestionDto.toExternalSearchResult(): ExternalSearchResult =
    ExternalSearchResult(
        title = title,
        artist = artist,
        format = format,
        year = year,
        barcode = barcode,
        label = label,
        discogsId = discogsId ?: tmdbId ?: workKey ?: rawgId ?: comicVineId,
        subtitle = genres,
        coverUrl = thumb ?: artworkUrl,
    )
