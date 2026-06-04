package com.megamaced.crate.domain.repository

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.domain.model.HomeFeed

interface HomeRepository {
    /** One-shot fetch — the server's home feed is date-seeded so it doesn't need caching. */
    suspend fun fetch(): ApiResult<HomeFeed>
}
