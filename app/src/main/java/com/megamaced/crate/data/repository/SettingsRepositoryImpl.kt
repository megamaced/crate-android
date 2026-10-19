package com.megamaced.crate.data.repository

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.CrateApiService
import com.megamaced.crate.data.api.apiCall
import com.megamaced.crate.data.api.dto.HiddenCategoriesRequest
import com.megamaced.crate.data.api.dto.KeyRequest
import com.megamaced.crate.data.api.dto.OnlineRecommendationsRequest
import com.megamaced.crate.data.api.dto.TokenRequest
import com.megamaced.crate.data.auth.SessionGeneration
import com.megamaced.crate.data.mapper.toDomain
import com.megamaced.crate.data.mapper.toDto
import com.megamaced.crate.data.prefs.ProfilePrefs
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
        private val userPreferences: ProfilePrefs,
    ) : SettingsRepository {
        override suspend fun getMe(): ApiResult<UserProfile> =
            apiCall {
                val me = api.getMe().toDomain()
                // Mirror server-side hidden_categories into the local DataStore
                // cache so navigation / home / search can read it synchronously
                // next launch. The user id is what every "my collection" query
                // is scoped by, so this is the one authoritative place it gets
                // cached. Inside the call and session-bound: a /me that answers
                // after its account signed out must not name the next one's
                // user (see SessionGeneration).
                SessionGeneration.write {
                    userPreferences.setCurrentUserId(me.userId)
                    userPreferences.setHiddenCategories(me.hiddenCategories)
                    userPreferences.setOnlineRecommendations(me.onlineRecommendations)
                }
                me
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

        override suspend fun getCurrencies(): ApiResult<List<String>> = apiCall { api.getCurrencies() }

        override val hiddenCategoriesFlow: Flow<Set<Category>> = userPreferences.hiddenCategoriesFlow

        override suspend fun setHiddenCategories(categories: Set<Category>): ApiResult<Unit> {
            val payload = HiddenCategoriesRequest(categories.map { it.apiValue })
            return apiCall {
                api.setHiddenCategories(payload)
                SessionGeneration.write { userPreferences.setHiddenCategories(categories) }
            }
        }

        override val onlineRecommendationsFlow: Flow<Boolean> = userPreferences.onlineRecommendationsFlow

        override suspend fun setOnlineRecommendations(enabled: Boolean): ApiResult<Unit> =
            apiCall {
                api.setOnlineRecommendations(OnlineRecommendationsRequest(enabled))
                SessionGeneration.write { userPreferences.setOnlineRecommendations(enabled) }
            }
    }
