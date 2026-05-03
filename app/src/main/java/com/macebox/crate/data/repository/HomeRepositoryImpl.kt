package com.macebox.crate.data.repository

import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.data.api.CrateApiService
import com.macebox.crate.data.api.apiCall
import com.macebox.crate.data.mapper.toDomain
import com.macebox.crate.domain.model.HomeFeed
import com.macebox.crate.domain.repository.HomeRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepositoryImpl
    @Inject
    constructor(
        private val api: CrateApiService,
    ) : HomeRepository {
        override suspend fun fetch(): ApiResult<HomeFeed> = apiCall { api.getHome().toDomain() }
    }
