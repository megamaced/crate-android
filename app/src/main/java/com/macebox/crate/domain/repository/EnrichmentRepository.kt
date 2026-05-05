package com.macebox.crate.domain.repository

import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.domain.model.MediaItem
import com.macebox.crate.domain.model.RefreshableMarketValues

interface EnrichmentRepository {
    suspend fun enrich(itemId: Long): ApiResult<MediaItem>

    suspend fun stripEnrichment(itemId: Long): ApiResult<MediaItem>

    suspend fun fetchMarketValue(itemId: Long): ApiResult<MediaItem>

    suspend fun listRefreshableMarketValues(): ApiResult<RefreshableMarketValues>

    suspend fun listUnenrichedItems(): ApiResult<List<Long>>
}
