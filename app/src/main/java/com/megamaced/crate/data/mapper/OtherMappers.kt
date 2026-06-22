package com.megamaced.crate.data.mapper

import com.megamaced.crate.data.api.dto.CategoryFeedDto
import com.megamaced.crate.data.api.dto.CategoryShareDto
import com.megamaced.crate.data.api.dto.HomeFeedDto
import com.megamaced.crate.data.api.dto.LibraryShareDto
import com.megamaced.crate.data.api.dto.MarketSettingsDto
import com.megamaced.crate.data.api.dto.MeDto
import com.megamaced.crate.data.api.dto.RefreshAllDto
import com.megamaced.crate.data.api.dto.ShareDto
import com.megamaced.crate.data.api.dto.SharedWithMeDto
import com.megamaced.crate.data.api.dto.UserSearchResultDto
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.CategoryFeed
import com.megamaced.crate.domain.model.CategoryShare
import com.megamaced.crate.domain.model.HomeFeed
import com.megamaced.crate.domain.model.LibraryShare
import com.megamaced.crate.domain.model.MarketSettings
import com.megamaced.crate.domain.model.RefreshableMarketValues
import com.megamaced.crate.domain.model.Share
import com.megamaced.crate.domain.model.SharedWithMe
import com.megamaced.crate.domain.model.UserProfile
import com.megamaced.crate.domain.model.UserSearchResult

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
        hiddenCategories = hiddenCategories.mapNotNull { Category.fromApi(it) }.toSet(),
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
        ownerUserId = ownerUserId,
        targetUserId = targetUserId,
        targetDisplayName = targetDisplayName,
        resourceType = resourceType,
        resourceId = resourceId,
        shareableCategory = shareableCategory,
        createdAt = createdAt,
    )

fun LibraryShareDto.toDomain(): LibraryShare =
    LibraryShare(
        shareId = shareId,
        sharedByUser = sharedByUser,
        createdAt = createdAt,
        items = items.map { it.toDomain() },
    )

fun CategoryShareDto.toDomain(): CategoryShare =
    CategoryShare(
        shareId = shareId,
        sharedByUser = sharedByUser,
        category = Category.fromApi(category),
        createdAt = createdAt,
        items = items.map { it.toDomain() },
    )

fun SharedWithMeDto.toDomain(): SharedWithMe =
    SharedWithMe(
        albums = albums.map { it.toDomain() },
        playlists = playlists.map { it.toDomain() },
        libraries = libraries.map { it.toDomain() },
        categories = categories.map { it.toDomain() },
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
