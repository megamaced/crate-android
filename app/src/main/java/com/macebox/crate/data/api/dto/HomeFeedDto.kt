package com.macebox.crate.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class HomeFeedDto(
    val albumOfDay: MediaItemDto? = null,
    val recentItems: List<MediaItemDto> = emptyList(),
    val formatRows: List<FormatRowDto> = emptyList(),
    val mostValuable: List<MediaItemDto> = emptyList(),
)

@Serializable
data class FormatRowDto(
    val format: String,
    val label: String,
    val items: List<MediaItemDto> = emptyList(),
)
