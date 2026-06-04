package com.megamaced.crate.data.repository

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.CrateApiService
import com.megamaced.crate.data.api.apiCall
import com.megamaced.crate.data.db.dao.MediaItemDao
import com.megamaced.crate.data.mapper.MediaItemJsonCodec
import com.megamaced.crate.data.mapper.toDomain
import com.megamaced.crate.data.mapper.toEntity
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.RefreshableMarketValues
import com.megamaced.crate.domain.repository.EnrichmentRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnrichmentRepositoryImpl
    @Inject
    constructor(
        private val api: CrateApiService,
        private val dao: MediaItemDao,
        private val codec: MediaItemJsonCodec,
    ) : EnrichmentRepository {
        override suspend fun enrich(itemId: Long): ApiResult<MediaItem> =
            apiCall {
                val dto = api.enrich(itemId)
                dao.upsert(dto.toEntity(codec))
                dto.toDomain()
            }

        override suspend fun stripEnrichment(itemId: Long): ApiResult<MediaItem> =
            apiCall {
                val dto = api.stripEnrichment(itemId)
                dao.upsert(dto.toEntity(codec))
                dto.toDomain()
            }

        override suspend fun fetchMarketValue(itemId: Long): ApiResult<MediaItem> =
            apiCall {
                val dto = api.fetchMarketValue(itemId)
                dao.upsert(dto.toEntity(codec))
                dto.toDomain()
            }

        override suspend fun listRefreshableMarketValues(): ApiResult<RefreshableMarketValues> =
            apiCall { api.listRefreshableMarketValues().toDomain() }

        override suspend fun listUnenrichedItems(): ApiResult<List<Long>> =
            apiCall {
                val items = mutableListOf<Long>()
                var offset = 0
                val limit = 50
                while (true) {
                    val page = api.getMedia(limit = limit, offset = offset)
                    page.items
                        .filter { it.genres.isNullOrBlank() && it.artistBio.isNullOrBlank() }
                        .forEach { items.add(it.id) }
                    if (page.items.size < limit) break
                    offset += limit
                }
                items
            }
    }
