package com.megamaced.crate.domain.repository

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.MarketSettings
import com.megamaced.crate.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

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

    /** Locally-cached hidden categories — observed by navigation, home, and search. */
    val hiddenCategoriesFlow: Flow<Set<Category>>

    /**
     * Persists a new hidden_categories set to both the server and the local
     * DataStore cache. The server validates that at least one category
     * remains visible; on success the cache mirrors the server state.
     */
    suspend fun setHiddenCategories(categories: Set<Category>): ApiResult<Unit>
}
