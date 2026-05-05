package com.macebox.crate.data.repository

import com.macebox.crate.data.api.CrateApiService
import com.macebox.crate.data.api.dto.AddPlaylistItemRequest
import com.macebox.crate.data.api.dto.ComicVineSearchResultDto
import com.macebox.crate.data.api.dto.ComicVineVolumeDto
import com.macebox.crate.data.api.dto.CreateMediaItemRequest
import com.macebox.crate.data.api.dto.CreatePlaylistRequest
import com.macebox.crate.data.api.dto.CurrencyRequest
import com.macebox.crate.data.api.dto.CurrencyResponseDto
import com.macebox.crate.data.api.dto.DiscogsSearchResultDto
import com.macebox.crate.data.api.dto.HasKeyDto
import com.macebox.crate.data.api.dto.HasTokenDto
import com.macebox.crate.data.api.dto.HomeFeedDto
import com.macebox.crate.data.api.dto.KeyRequest
import com.macebox.crate.data.api.dto.MarketSettingsDto
import com.macebox.crate.data.api.dto.MeDto
import com.macebox.crate.data.api.dto.MediaItemDto
import com.macebox.crate.data.api.dto.OpenLibraryResultDto
import com.macebox.crate.data.api.dto.PaginatedMediaDto
import com.macebox.crate.data.api.dto.PlaylistDto
import com.macebox.crate.data.api.dto.RawgGameDto
import com.macebox.crate.data.api.dto.RawgSearchResultDto
import com.macebox.crate.data.api.dto.RefreshAllDto
import com.macebox.crate.data.api.dto.ShareDto
import com.macebox.crate.data.api.dto.ShareRequest
import com.macebox.crate.data.api.dto.SharedWithMeDto
import com.macebox.crate.data.api.dto.TmdbMovieDto
import com.macebox.crate.data.api.dto.TmdbSearchResultDto
import com.macebox.crate.data.api.dto.TokenRequest
import com.macebox.crate.data.api.dto.UserSearchResultDto

/**
 * Test double — every method that a test doesn't override throws, so unused
 * surface area can't accidentally pass. Tests assign `nextX` mailbox fields
 * (or read `xCalls`) for the methods they exercise.
 */
class FakeCrateApiService : CrateApiService {
    var nextPage: PaginatedMediaDto? = null
    var nextCreated: MediaItemDto? = null
    var nextUpdated: MediaItemDto? = null
    var nextItem: MediaItemDto? = null
    val deletedIds = mutableListOf<Long>()

    override suspend fun getMedia(
        status: String?,
        category: String?,
        updatedSince: String?,
        limit: Int,
        offset: Int,
        paginated: Boolean,
    ): PaginatedMediaDto = requireNotNull(nextPage) { "FakeCrateApiService.nextPage was not set" }

    override suspend fun createMedia(body: CreateMediaItemRequest): MediaItemDto =
        requireNotNull(nextCreated) { "FakeCrateApiService.nextCreated was not set" }

    override suspend fun updateMediaItem(
        id: Long,
        body: CreateMediaItemRequest,
    ): MediaItemDto = requireNotNull(nextUpdated) { "FakeCrateApiService.nextUpdated was not set" }

    override suspend fun getMediaItem(id: Long): MediaItemDto = requireNotNull(nextItem) { "FakeCrateApiService.nextItem was not set" }

    override suspend fun deleteMediaItem(id: Long) {
        deletedIds += id
    }

    override suspend fun deleteAllMedia(scopes: String?) = unsupported()

    // -- Everything else: not exercised by current tests ----------------------

    override suspend fun getMe(): MeDto = unsupported()

    override suspend fun getDiscogsToken(): HasTokenDto = unsupported()

    override suspend fun setDiscogsToken(body: TokenRequest) = unsupported()

    override suspend fun getTmdbToken(): HasTokenDto = unsupported()

    override suspend fun setTmdbToken(body: TokenRequest) = unsupported()

    override suspend fun getRawgKey(): HasKeyDto = unsupported()

    override suspend fun setRawgKey(body: KeyRequest) = unsupported()

    override suspend fun getComicVineKey(): HasKeyDto = unsupported()

    override suspend fun setComicVineKey(body: KeyRequest) = unsupported()

    override suspend fun getPriceChartingToken(): HasTokenDto = unsupported()

    override suspend fun setPriceChartingToken(body: TokenRequest) = unsupported()

    override suspend fun getMarketSettings(): MarketSettingsDto = unsupported()

    override suspend fun setMarketSettings(body: MarketSettingsDto) = unsupported()

    override suspend fun setCurrency(body: CurrencyRequest): CurrencyResponseDto = unsupported()

    override suspend fun getCurrencies(): List<String> = unsupported()

    override suspend fun enrich(id: Long): MediaItemDto = unsupported()

    override suspend fun stripEnrichment(id: Long): MediaItemDto = unsupported()

    override suspend fun fetchMarketValue(id: Long): MediaItemDto = unsupported()

    override suspend fun listRefreshableMarketValues(): RefreshAllDto = unsupported()

    override suspend fun getHome(): HomeFeedDto = unsupported()

    override suspend fun discogsSearch(query: String): List<DiscogsSearchResultDto> = unsupported()

    override suspend fun discogsBarcode(barcode: String): List<DiscogsSearchResultDto> = unsupported()

    override suspend fun discogsRelease(id: String): DiscogsSearchResultDto = unsupported()

    override suspend fun discogsArtist(id: String): DiscogsSearchResultDto = unsupported()

    override suspend fun tmdbSearch(query: String): List<TmdbSearchResultDto> = unsupported()

    override suspend fun tmdbMovie(id: String): TmdbMovieDto = unsupported()

    override suspend fun openLibrarySearch(query: String): List<OpenLibraryResultDto> = unsupported()

    override suspend fun openLibraryWork(id: String): OpenLibraryResultDto = unsupported()

    override suspend fun openLibraryIsbn(isbn: String): OpenLibraryResultDto = unsupported()

    override suspend fun rawgSearch(query: String): List<RawgSearchResultDto> = unsupported()

    override suspend fun rawgGame(id: String): RawgGameDto = unsupported()

    override suspend fun comicVineSearch(query: String): List<ComicVineSearchResultDto> = unsupported()

    override suspend fun comicVineVolume(id: String): ComicVineVolumeDto = unsupported()

    override suspend fun listPlaylists(): List<PlaylistDto> = unsupported()

    override suspend fun createPlaylist(body: CreatePlaylistRequest): PlaylistDto = unsupported()

    override suspend fun getPlaylist(id: Long): PlaylistDto = unsupported()

    override suspend fun updatePlaylist(
        id: Long,
        body: CreatePlaylistRequest,
    ): PlaylistDto = unsupported()

    override suspend fun deletePlaylist(id: Long) = unsupported()

    override suspend fun addPlaylistItem(
        id: Long,
        body: AddPlaylistItemRequest,
    ): PlaylistDto = unsupported()

    override suspend fun removePlaylistItem(
        id: Long,
        mediaItemId: Long,
    ): PlaylistDto = unsupported()

    override suspend fun searchUsers(query: String): List<UserSearchResultDto> = unsupported()

    override suspend fun shareAlbum(
        id: Long,
        body: ShareRequest,
    ): ShareDto = unsupported()

    override suspend fun listAlbumShares(id: Long): List<ShareDto> = unsupported()

    override suspend fun sharePlaylist(
        id: Long,
        body: ShareRequest,
    ): ShareDto = unsupported()

    override suspend fun listPlaylistShares(id: Long): List<ShareDto> = unsupported()

    override suspend fun sharedWithMe(): SharedWithMeDto = unsupported()

    override suspend fun removeShare(id: Long) = unsupported()

    private fun unsupported(): Nothing = error("FakeCrateApiService method not stubbed for this test")
}
