package com.megamaced.crate.domain.repository

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.RefreshableMarketValues

interface EnrichmentRepository {
    suspend fun enrich(itemId: Long): ApiResult<MediaItem>

    suspend fun stripEnrichment(itemId: Long): ApiResult<MediaItem>

    suspend fun fetchMarketValue(itemId: Long): ApiResult<MediaItem>

    suspend fun listRefreshableMarketValues(): ApiResult<RefreshableMarketValues>

    suspend fun listUnenrichedItems(): ApiResult<List<Long>>
}
