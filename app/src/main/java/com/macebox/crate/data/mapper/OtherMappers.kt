package com.macebox.crate.data.mapper

import com.macebox.crate.data.api.dto.CategoryFeedDto
import com.macebox.crate.data.api.dto.HomeFeedDto
import com.macebox.crate.data.api.dto.MarketSettingsDto
import com.macebox.crate.data.api.dto.MeDto
import com.macebox.crate.data.api.dto.RefreshAllDto
import com.macebox.crate.data.api.dto.ShareDto
import com.macebox.crate.data.api.dto.SharedWithMeDto
import com.macebox.crate.data.api.dto.UserSearchResultDto
import com.macebox.crate.domain.model.Category
import com.macebox.crate.domain.model.CategoryFeed
import com.macebox.crate.domain.model.HomeFeed
import com.macebox.crate.domain.model.MarketSettings
import com.macebox.crate.domain.model.RefreshableMarketValues
import com.macebox.crate.domain.model.Share
import com.macebox.crate.domain.model.SharedWithMe
import com.macebox.crate.domain.model.UserProfile
import com.macebox.crate.domain.model.UserSearchResult

fun HomeFeedDto.toDomain(): HomeFeed {
    val categoryFeeds = categories.mapNotNull { (key, dto) ->
        val category = Category.fromApi(key) ?: return@mapNotNull null
        dto.toDomain(category)
    }
    return HomeFeed(
        categoryFeeds = categoryFeeds,
        recentlyAdded = recentlyAdded.map { it.toDomain() },
        mostValuable = mostValuable.map { it.toDomain() },
    )
}

fun CategoryFeedDto.toDomain(category: Category): CategoryFeed =
    CategoryFeed(
        category = category,
        count = count,
        itemOfDay = itemOfDay?.toDomain(),
        recentItems = recentItems.map { it.toDomain() },
    )

fun MeDto.toDomain(): UserProfile =
    UserProfile(
        userId = userId,
        displayName = displayName,
        avatarUrl = avatarUrl,
        hasDiscogsToken = hasDiscogsToken,
        marketCurrency = marketCurrency,
        autoFetchMarketRates = autoFetchMarketRates,
        autoEnrichOnClick = autoEnrichOnClick,
        autoEnrichOnImport = autoEnrichOnImport,
        crateVersion = crateVersion,
    )

fun MarketSettingsDto.toDomain(): MarketSettings =
    MarketSettings(
        autoFetchMarketRates = autoFetchMarketRates,
        marketCurrency = marketCurrency,
        autoEnrichOnClick = autoEnrichOnClick,
        autoEnrichOnImport = autoEnrichOnImport,
    )

fun MarketSettings.toDto(): MarketSettingsDto =
    MarketSettingsDto(
        autoFetchMarketRates = autoFetchMarketRates,
        marketCurrency = marketCurrency,
        autoEnrichOnClick = autoEnrichOnClick,
        autoEnrichOnImport = autoEnrichOnImport,
    )

fun ShareDto.toDomain(): Share =
    Share(
        id = id,
        targetUserId = targetUserId,
        targetDisplayName = targetDisplayName,
        resourceType = resourceType,
        resourceId = resourceId,
        createdAt = createdAt,
    )

fun SharedWithMeDto.toDomain(): SharedWithMe =
    SharedWithMe(
        albums = albums.map { it.toDomain() },
        playlists = playlists.map { it.toDomain() },
    )

fun UserSearchResultDto.toDomain(): UserSearchResult =
    UserSearchResult(
        userId = userId,
        displayName = displayName,
        avatarUrl = avatarUrl,
    )

fun RefreshAllDto.toDomain(): RefreshableMarketValues =
    RefreshableMarketValues(
        currency = currency,
        itemIds = itemIds,
    )
