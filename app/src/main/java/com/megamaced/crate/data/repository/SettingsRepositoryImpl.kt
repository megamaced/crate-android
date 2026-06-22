package com.megamaced.crate.data.repository

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.CrateApiService
import com.megamaced.crate.data.api.apiCall
import com.megamaced.crate.data.api.dto.CurrencyRequest
import com.megamaced.crate.data.api.dto.HiddenCategoriesRequest
import com.megamaced.crate.data.api.dto.KeyRequest
import com.megamaced.crate.data.api.dto.TokenRequest
import com.megamaced.crate.data.mapper.toDomain
import com.megamaced.crate.data.mapper.toDto
import com.megamaced.crate.data.prefs.UserPreferences
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.MarketSettings
import com.megamaced.crate.domain.model.UserProfile
import com.megamaced.crate.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl
    @Inject
    constructor(
        private val api: CrateApiService,
        private val userPreferences: UserPreferences,
    ) : SettingsRepository {
        override suspend fun getMe(): ApiResult<UserProfile> {
            val result = apiCall { api.getMe().toDomain() }
            // Mirror server-side hidden_categories into the local DataStore cache
            // so navigation / home / search can read it synchronously next launch.
            if (result is ApiResult.Success) {
                userPreferences.setHiddenCategories(result.value.hiddenCategories)
            }
            return result
        }

        override suspend fun hasDiscogsToken(): ApiResult<Boolean> = apiCall { api.getDiscogsToken().hasToken }

        override suspend fun setDiscogsToken(token: String): ApiResult<Unit> = apiCall { api.setDiscogsToken(TokenRequest(token)) }

        override suspend fun hasTmdbToken(): ApiResult<Boolean> = apiCall { api.getTmdbToken().hasToken }

        override suspend fun setTmdbToken(token: String): ApiResult<Unit> = apiCall { api.setTmdbToken(TokenRequest(token)) }

        override suspend fun hasRawgKey(): ApiResult<Boolean> = apiCall { api.getRawgKey().hasKey }

        override suspend fun setRawgKey(key: String): ApiResult<Unit> = apiCall { api.setRawgKey(KeyRequest(key)) }

        override suspend fun hasComicVineKey(): ApiResult<Boolean> = apiCall { api.getComicVineKey().hasKey }

        override suspend fun setComicVineKey(key: String): ApiResult<Unit> = apiCall { api.setComicVineKey(KeyRequest(key)) }

        override suspend fun hasPriceChartingToken(): ApiResult<Boolean> = apiCall { api.getPriceChartingToken().hasToken }

        override suspend fun setPriceChartingToken(token: String): ApiResult<Unit> =
            apiCall { api.setPriceChartingToken(TokenRequest(token)) }

        override suspend fun getMarketSettings(): ApiResult<MarketSettings> = apiCall { api.getMarketSettings().toDomain() }

        override suspend fun setMarketSettings(settings: MarketSettings): ApiResult<Unit> =
            apiCall { api.setMarketSettings(settings.toDto()) }

        override suspend fun setCurrency(currency: String): ApiResult<String> =
            apiCall { api.setCurrency(CurrencyRequest(currency)).marketCurrency }

        override suspend fun getCurrencies(): ApiResult<List<String>> = apiCall { api.getCurrencies() }

        override val hiddenCategoriesFlow: Flow<Set<Category>> = userPreferences.hiddenCategoriesFlow

        override suspend fun setHiddenCategories(categories: Set<Category>): ApiResult<Unit> {
            val payload = HiddenCategoriesRequest(categories.map { it.apiValue })
            val result =
                apiCall {
                    api.setHiddenCategories(payload)
                    Unit
                }
            if (result is ApiResult.Success) {
                userPreferences.setHiddenCategories(categories)
            }
            return result
        }
    }
