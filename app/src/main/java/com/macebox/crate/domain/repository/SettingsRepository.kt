package com.macebox.crate.domain.repository

import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.domain.model.MarketSettings
import com.macebox.crate.domain.model.UserProfile

/**
 * Wraps the `/me` profile and the various external-provider settings.
 *
 * All token getters report only whether a token is set; the server never
 * returns saved values to the client.
 */
interface SettingsRepository {
    suspend fun getMe(): ApiResult<UserProfile>

    suspend fun hasDiscogsToken(): ApiResult<Boolean>

    suspend fun setDiscogsToken(token: String): ApiResult<Unit>

    suspend fun hasTmdbToken(): ApiResult<Boolean>

    suspend fun setTmdbToken(token: String): ApiResult<Unit>

    suspend fun hasRawgKey(): ApiResult<Boolean>

    suspend fun setRawgKey(key: String): ApiResult<Unit>

    suspend fun hasComicVineKey(): ApiResult<Boolean>

    suspend fun setComicVineKey(key: String): ApiResult<Unit>

    suspend fun hasPriceChartingToken(): ApiResult<Boolean>

    suspend fun setPriceChartingToken(token: String): ApiResult<Unit>

    // Market settings
    suspend fun getMarketSettings(): ApiResult<MarketSettings>

    suspend fun setMarketSettings(settings: MarketSettings): ApiResult<Unit>

    suspend fun setCurrency(currency: String): ApiResult<String>

    suspend fun getCurrencies(): ApiResult<List<String>>
}
