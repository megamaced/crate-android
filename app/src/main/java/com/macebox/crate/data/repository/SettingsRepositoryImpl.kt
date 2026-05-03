package com.macebox.crate.data.repository

import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.data.api.CrateApiService
import com.macebox.crate.data.api.apiCall
import com.macebox.crate.data.api.dto.CurrencyRequest
import com.macebox.crate.data.api.dto.KeyRequest
import com.macebox.crate.data.api.dto.TokenRequest
import com.macebox.crate.data.mapper.toDomain
import com.macebox.crate.data.mapper.toDto
import com.macebox.crate.domain.model.MarketSettings
import com.macebox.crate.domain.model.UserProfile
import com.macebox.crate.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl
    @Inject
    constructor(
        private val api: CrateApiService,
    ) : SettingsRepository {
        override suspend fun getMe(): ApiResult<UserProfile> = apiCall { api.getMe().toDomain() }

        override suspend fun hasDiscogsToken(): ApiResult<Boolean> = apiCall { api.getDiscogsToken().hasToken }

        override suspend fun setDiscogsToken(token: String): ApiResult<Unit> = apiCall { api.setDiscogsToken(TokenRequest(token)) }

        override suspend fun getTmdbToken(): ApiResult<String?> = apiCall { api.getTmdbToken().token }

        override suspend fun setTmdbToken(token: String): ApiResult<Unit> = apiCall { api.setTmdbToken(TokenRequest(token)) }

        override suspend fun getRawgKey(): ApiResult<String?> = apiCall { api.getRawgKey().key }

        override suspend fun setRawgKey(key: String): ApiResult<Unit> = apiCall { api.setRawgKey(KeyRequest(key)) }

        override suspend fun getComicVineKey(): ApiResult<String?> = apiCall { api.getComicVineKey().key }

        override suspend fun setComicVineKey(key: String): ApiResult<Unit> = apiCall { api.setComicVineKey(KeyRequest(key)) }

        override suspend fun getPriceChartingToken(): ApiResult<String?> = apiCall { api.getPriceChartingToken().token }

        override suspend fun setPriceChartingToken(token: String): ApiResult<Unit> =
            apiCall { api.setPriceChartingToken(TokenRequest(token)) }

        override suspend fun getMarketSettings(): ApiResult<MarketSettings> = apiCall { api.getMarketSettings().toDomain() }

        override suspend fun setMarketSettings(settings: MarketSettings): ApiResult<Unit> =
            apiCall { api.setMarketSettings(settings.toDto()) }

        override suspend fun setCurrency(currency: String): ApiResult<String> =
            apiCall { api.setCurrency(CurrencyRequest(currency)).marketCurrency }

        override suspend fun getCurrencies(): ApiResult<List<String>> = apiCall { api.getCurrencies() }
    }
