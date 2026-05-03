package com.macebox.crate.domain.repository

import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.domain.model.MarketSettings
import com.macebox.crate.domain.model.UserProfile

/**
 * Wraps the `/me` profile and the various external-provider settings.
 *
 * Token getters report only whether a token is set (never expose the value
 * to the UI for Discogs); for TMDB / RAWG / ComicVine / PriceCharting the
 * API returns the value itself for the editor.
 */
interface SettingsRepository {
    suspend fun getMe(): ApiResult<UserProfile>

    // Discogs (presence only — server never returns the value)
    suspend fun hasDiscogsToken(): ApiResult<Boolean>

    suspend fun setDiscogsToken(token: String): ApiResult<Unit>

    // Editable tokens (server returns the value when set)
    suspend fun getTmdbToken(): ApiResult<String?>

    suspend fun setTmdbToken(token: String): ApiResult<Unit>

    suspend fun getRawgKey(): ApiResult<String?>

    suspend fun setRawgKey(key: String): ApiResult<Unit>

    suspend fun getComicVineKey(): ApiResult<String?>

    suspend fun setComicVineKey(key: String): ApiResult<Unit>

    suspend fun getPriceChartingToken(): ApiResult<String?>

    suspend fun setPriceChartingToken(token: String): ApiResult<Unit>

    // Market settings
    suspend fun getMarketSettings(): ApiResult<MarketSettings>

    suspend fun setMarketSettings(settings: MarketSettings): ApiResult<Unit>

    suspend fun setCurrency(currency: String): ApiResult<String>

    suspend fun getCurrencies(): ApiResult<List<String>>
}
