package com.macebox.crate.data.api

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
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for all Crate OCS endpoints.
 * Methods annotated with [OcsResponse] use the [OcsConverterFactory] to unwrap
 * the `{ocs: {data: T}}` envelope automatically.
 */
@Suppress("TooManyFunctions")
interface CrateApiService {
    // -- User & Settings ------------------------------------------------------

    @OcsResponse
    @GET(API_BASE + "me")
    suspend fun getMe(): MeDto

    @OcsResponse
    @GET(API_BASE + "settings/discogs-token")
    suspend fun getDiscogsToken(): HasTokenDto

    @OcsResponse
    @POST(API_BASE + "settings/discogs-token")
    suspend fun setDiscogsToken(
        @Body body: TokenRequest,
    )

    @OcsResponse
    @GET(API_BASE + "settings/tmdb-token")
    suspend fun getTmdbToken(): HasTokenDto

    @OcsResponse
    @POST(API_BASE + "settings/tmdb-token")
    suspend fun setTmdbToken(
        @Body body: TokenRequest,
    )

    @OcsResponse
    @GET(API_BASE + "settings/rawg-key")
    suspend fun getRawgKey(): HasKeyDto

    @OcsResponse
    @POST(API_BASE + "settings/rawg-key")
    suspend fun setRawgKey(
        @Body body: KeyRequest,
    )

    @OcsResponse
    @GET(API_BASE + "settings/comicvine-key")
    suspend fun getComicVineKey(): HasKeyDto

    @OcsResponse
    @POST(API_BASE + "settings/comicvine-key")
    suspend fun setComicVineKey(
        @Body body: KeyRequest,
    )

    @OcsResponse
    @GET(API_BASE + "settings/pricecharting-token")
    suspend fun getPriceChartingToken(): HasTokenDto

    @OcsResponse
    @POST(API_BASE + "settings/pricecharting-token")
    suspend fun setPriceChartingToken(
        @Body body: TokenRequest,
    )

    @OcsResponse
    @GET(API_BASE + "settings/market")
    suspend fun getMarketSettings(): MarketSettingsDto

    @OcsResponse
    @POST(API_BASE + "settings/market")
    suspend fun setMarketSettings(
        @Body body: MarketSettingsDto,
    )

    @OcsResponse
    @PUT(API_BASE + "settings/currency")
    suspend fun setCurrency(
        @Body body: CurrencyRequest,
    ): CurrencyResponseDto

    @OcsResponse
    @GET(API_BASE + "settings/currencies")
    suspend fun getCurrencies(): List<String>

    // -- Media (always paginated; pass updatedSince for delta sync) -----------

    @OcsResponse
    @GET(API_BASE + "media")
    suspend fun getMedia(
        @Query("status") status: String? = null,
        @Query("category") category: String? = null,
        @Query("updatedSince") updatedSince: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("paginated") paginated: Boolean = true,
    ): PaginatedMediaDto

    @OcsResponse
    @POST(API_BASE + "media")
    suspend fun createMedia(
        @Body body: CreateMediaItemRequest,
    ): MediaItemDto

    @OcsResponse
    @GET(API_BASE + "media/{id}")
    suspend fun getMediaItem(
        @Path("id") id: Long,
    ): MediaItemDto

    @OcsResponse
    @PUT(API_BASE + "media/{id}")
    suspend fun updateMediaItem(
        @Path("id") id: Long,
        @Body body: CreateMediaItemRequest,
    ): MediaItemDto

    @OcsResponse
    @DELETE(API_BASE + "media/{id}")
    suspend fun deleteMediaItem(
        @Path("id") id: Long,
    )

    @OcsResponse
    @DELETE(API_BASE + "media")
    suspend fun deleteAllMedia(
        @Query("scopes") scopes: String? = null,
    )

    // -- Enrichment & Market --------------------------------------------------

    @OcsResponse
    @POST(API_BASE + "media/{id}/enrich")
    suspend fun enrich(
        @Path("id") id: Long,
    ): MediaItemDto

    @OcsResponse
    @DELETE(API_BASE + "media/{id}/enrich")
    suspend fun stripEnrichment(
        @Path("id") id: Long,
    ): MediaItemDto

    @OcsResponse
    @POST(API_BASE + "media/{id}/market-value")
    suspend fun fetchMarketValue(
        @Path("id") id: Long,
    ): MediaItemDto

    @OcsResponse
    @POST(API_BASE + "market-value/refresh-all")
    suspend fun listRefreshableMarketValues(): RefreshAllDto

    // -- Home -----------------------------------------------------------------

    @OcsResponse
    @GET(API_BASE + "home")
    suspend fun getHome(): HomeFeedDto

    // -- Discogs --------------------------------------------------------------

    @OcsResponse
    @GET(API_BASE + "discogs/search")
    suspend fun discogsSearch(
        @Query("q") query: String,
    ): List<DiscogsSearchResultDto>

    @OcsResponse
    @GET(API_BASE + "discogs/barcode/{barcode}")
    suspend fun discogsBarcode(
        @Path("barcode") barcode: String,
    ): List<DiscogsSearchResultDto>

    @OcsResponse
    @GET(API_BASE + "discogs/release/{id}")
    suspend fun discogsRelease(
        @Path("id") id: String,
    ): DiscogsSearchResultDto

    @OcsResponse
    @GET(API_BASE + "discogs/artist/{id}")
    suspend fun discogsArtist(
        @Path("id") id: String,
    ): DiscogsSearchResultDto

    // -- TMDB -----------------------------------------------------------------

    @OcsResponse
    @GET(API_BASE + "tmdb/search")
    suspend fun tmdbSearch(
        @Query("q") query: String,
    ): List<TmdbSearchResultDto>

    @OcsResponse
    @GET(API_BASE + "tmdb/movie/{id}")
    suspend fun tmdbMovie(
        @Path("id") id: String,
    ): TmdbMovieDto

    // -- Open Library ---------------------------------------------------------

    @OcsResponse
    @GET(API_BASE + "openlibrary/search")
    suspend fun openLibrarySearch(
        @Query("q") query: String,
    ): List<OpenLibraryResultDto>

    @OcsResponse
    @GET(API_BASE + "openlibrary/work/{id}")
    suspend fun openLibraryWork(
        @Path("id") id: String,
    ): OpenLibraryResultDto

    @OcsResponse
    @GET(API_BASE + "openlibrary/isbn/{isbn}")
    suspend fun openLibraryIsbn(
        @Path("isbn") isbn: String,
    ): OpenLibraryResultDto

    // -- RAWG -----------------------------------------------------------------

    @OcsResponse
    @GET(API_BASE + "rawg/search")
    suspend fun rawgSearch(
        @Query("q") query: String,
    ): List<RawgSearchResultDto>

    @OcsResponse
    @GET(API_BASE + "rawg/game/{id}")
    suspend fun rawgGame(
        @Path("id") id: String,
    ): RawgGameDto

    // -- ComicVine ------------------------------------------------------------

    @OcsResponse
    @GET(API_BASE + "comicvine/search")
    suspend fun comicVineSearch(
        @Query("q") query: String,
    ): List<ComicVineSearchResultDto>

    @OcsResponse
    @GET(API_BASE + "comicvine/volume/{id}")
    suspend fun comicVineVolume(
        @Path("id") id: String,
    ): ComicVineVolumeDto

    // -- Playlists ------------------------------------------------------------

    @OcsResponse
    @GET(API_BASE + "playlists")
    suspend fun listPlaylists(): List<PlaylistDto>

    @OcsResponse
    @POST(API_BASE + "playlists")
    suspend fun createPlaylist(
        @Body body: CreatePlaylistRequest,
    ): PlaylistDto

    @OcsResponse
    @GET(API_BASE + "playlists/{id}")
    suspend fun getPlaylist(
        @Path("id") id: Long,
    ): PlaylistDto

    @OcsResponse
    @PUT(API_BASE + "playlists/{id}")
    suspend fun updatePlaylist(
        @Path("id") id: Long,
        @Body body: CreatePlaylistRequest,
    ): PlaylistDto

    @OcsResponse
    @DELETE(API_BASE + "playlists/{id}")
    suspend fun deletePlaylist(
        @Path("id") id: Long,
    )

    @OcsResponse
    @POST(API_BASE + "playlists/{id}/items")
    suspend fun addPlaylistItem(
        @Path("id") id: Long,
        @Body body: AddPlaylistItemRequest,
    ): PlaylistDto

    @OcsResponse
    @DELETE(API_BASE + "playlists/{id}/items/{mediaItemId}")
    suspend fun removePlaylistItem(
        @Path("id") id: Long,
        @Path("mediaItemId") mediaItemId: Long,
    ): PlaylistDto

    // -- Sharing --------------------------------------------------------------

    @OcsResponse
    @GET(API_BASE + "users/search")
    suspend fun searchUsers(
        @Query("q") query: String,
    ): List<UserSearchResultDto>

    @OcsResponse
    @POST(API_BASE + "share/album/{id}")
    suspend fun shareAlbum(
        @Path("id") id: Long,
        @Body body: ShareRequest,
    ): ShareDto

    @OcsResponse
    @GET(API_BASE + "share/album/{id}")
    suspend fun listAlbumShares(
        @Path("id") id: Long,
    ): List<ShareDto>

    @OcsResponse
    @POST(API_BASE + "share/playlist/{id}")
    suspend fun sharePlaylist(
        @Path("id") id: Long,
        @Body body: ShareRequest,
    ): ShareDto

    @OcsResponse
    @GET(API_BASE + "share/playlist/{id}")
    suspend fun listPlaylistShares(
        @Path("id") id: Long,
    ): List<ShareDto>

    @OcsResponse
    @GET(API_BASE + "share/with-me")
    suspend fun sharedWithMe(): SharedWithMeDto

    @OcsResponse
    @DELETE(API_BASE + "share/{id}")
    suspend fun removeShare(
        @Path("id") id: Long,
    )

    companion object {
        const val API_BASE = "ocs/v2.php/apps/crate/api/v1/"
    }
}
