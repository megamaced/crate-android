package com.macebox.crate.data.repository

import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.data.api.CrateApiService
import com.macebox.crate.data.api.apiCall
import com.macebox.crate.data.db.dao.HomeFeedDao
import com.macebox.crate.data.mapper.MediaItemJsonCodec
import com.macebox.crate.data.mapper.toDomain
import com.macebox.crate.domain.model.Category
import com.macebox.crate.domain.model.CategoryFeed
import com.macebox.crate.domain.model.HomeFeed
import com.macebox.crate.domain.repository.HomeRepository
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
            // Fallback to local DB for offline use
            if (result is ApiResult.NetworkError) {
                return ApiResult.Success(buildOfflineFeed())
            }
            return result
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
