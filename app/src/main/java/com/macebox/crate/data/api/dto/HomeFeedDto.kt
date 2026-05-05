package com.macebox.crate.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class HomeFeedDto(
    val categories: Map<String, CategoryFeedDto> = emptyMap(),
    val recentlyAdded: List<MediaItemDto> = emptyList(),
    val mostValuable: List<MediaItemDto> = emptyList(),
)

@Serializable
data class CategoryFeedDto(
    val count: Int = 0,
    val itemOfDay: MediaItemDto? = null,
    val recentItems: List<MediaItemDto> = emptyList(),
)

@Serializable
data class FormatRowDto(
    val format: String,
    val label: String,
    val items: List<MediaItemDto> = emptyList(),
)
