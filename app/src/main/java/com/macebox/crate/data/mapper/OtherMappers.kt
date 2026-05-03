package com.macebox.crate.data.mapper

import com.macebox.crate.data.api.dto.FormatRowDto
import com.macebox.crate.data.api.dto.HomeFeedDto
import com.macebox.crate.data.api.dto.MarketSettingsDto
import com.macebox.crate.data.api.dto.MeDto
import com.macebox.crate.data.api.dto.RefreshAllDto
import com.macebox.crate.data.api.dto.ShareDto
import com.macebox.crate.data.api.dto.SharedWithMeDto
import com.macebox.crate.data.api.dto.UserSearchResultDto
import com.macebox.crate.domain.model.FormatRow
import com.macebox.crate.domain.model.HomeFeed
import com.macebox.crate.domain.model.MarketSettings
import com.macebox.crate.domain.model.RefreshableMarketValues
import com.macebox.crate.domain.model.Share
import com.macebox.crate.domain.model.SharedWithMe
import com.macebox.crate.domain.model.UserProfile
import com.macebox.crate.domain.model.UserSearchResult

fun HomeFeedDto.toDomain(): HomeFeed =
    HomeFeed(
        albumOfDay = albumOfDay?.toDomain(),
        recentItems = recentItems.map { it.toDomain() },
        formatRows = formatRows.map(FormatRowDto::toDomain),
        mostValuable = mostValuable.map { it.toDomain() },
    )

fun FormatRowDto.toDomain(): FormatRow =
    FormatRow(
        format = format,
        label = label,
        items = items.map { it.toDomain() },
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
    )

fun MarketSettingsDto.toDomain(): MarketSettings =
    MarketSettings(
        autoFetchMarketRates = autoFetchMarketRates,
        marketCurrency = marketCurrency,
    )

fun MarketSettings.toDto(): MarketSettingsDto =
    MarketSettingsDto(
        autoFetchMarketRates = autoFetchMarketRates,
        marketCurrency = marketCurrency,
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
