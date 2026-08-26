package com.megamaced.crate.data.repository

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.CrateApiService
import com.megamaced.crate.data.api.NON_HTTP_FAILURE_CODES
import com.megamaced.crate.data.api.apiCall
import com.megamaced.crate.data.db.dao.HomeFeedDao
import com.megamaced.crate.data.mapper.MediaItemJsonCodec
import com.megamaced.crate.data.mapper.toDomain
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.CategoryFeed
import com.megamaced.crate.domain.model.HomeFeed
import com.megamaced.crate.domain.repository.HomeRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepositoryImpl
    @Inject
    constructor(
        private val api: CrateApiService,
        private val dao: HomeFeedDao,
        private val codec: MediaItemJsonCodec,
    ) : HomeRepository {
        override suspend fun fetch(): ApiResult<HomeFeed> {
            val result = apiCall { api.getHome().toDomain() }
            if (result is ApiResult.Success) return result
            // Serve the Room feed whenever the payload never arrived intact:
            // no connection, or a response the DTO contract couldn't read. A
            // Home screen built from the cache is far better than a hard error
            // for either cause, and the cache is what offline use relies on.
            val serveCache =
                result is ApiResult.NetworkError ||
                    (result is ApiResult.HttpError && result.code in NON_HTTP_FAILURE_CODES)
            return if (serveCache) ApiResult.Success(buildOfflineFeed()) else result
        }

        private suspend fun buildOfflineFeed(): HomeFeed {
            val categories = dao.getOwnedCategories().mapNotNull { Category.fromApi(it) }
            val seed = LocalDate.now().toEpochDay().toInt()
            val categoryFeeds = categories.map { category ->
                val items = dao.getRecentByCategory(category.apiValue)
                val count = dao.countByCategory(category.apiValue)
                val itemOfDay = if (items.isNotEmpty()) items[seed % items.size] else null
                CategoryFeed(
                    category = category,
                    count = count,
                    itemOfDay = itemOfDay?.toDomain(codec),
                    recentItems = items.map { it.toDomain(codec) },
                )
            }
            val recentlyAdded = dao.getRecentOwned().map { it.toDomain(codec) }
            val mostValuable = dao.getMostValuable().map { it.toDomain(codec) }
            return HomeFeed(
                categoryFeeds = categoryFeeds,
                recentlyAdded = recentlyAdded,
                mostValuable = mostValuable,
            )
        }
    }
