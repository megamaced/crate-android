package com.megamaced.crate.domain.repository

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.RefreshableMarketValues

interface EnrichmentRepository {
    suspend fun enrich(itemId: Long): ApiResult<MediaItem>

    suspend fun stripEnrichment(itemId: Long): ApiResult<MediaItem>

    /**
     * Prices [itemId] in [currency]. The server defaults to GBP when no
     * currency is sent, so callers pass the user's own market currency —
     * otherwise a collection ends up with mixed currencies depending on which
     * client last refreshed each item.
     */
    suspend fun fetchMarketValue(
        itemId: Long,
        currency: String,
    ): ApiResult<MediaItem>

    suspend fun listRefreshableMarketValues(): ApiResult<RefreshableMarketValues>

    suspend fun listUnenrichedItems(): ApiResult<List<Long>>
}
