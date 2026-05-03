package com.macebox.crate.domain.model

data class HomeFeed(
    val albumOfDay: MediaItem?,
    val recentItems: List<MediaItem>,
    val formatRows: List<FormatRow>,
    val mostValuable: List<MediaItem>,
)

data class FormatRow(
    val format: String,
    val label: String,
    val items: List<MediaItem>,
)
