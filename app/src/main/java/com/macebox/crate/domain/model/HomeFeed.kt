package com.macebox.crate.domain.model

data class HomeFeed(
    val categoryFeeds: List<CategoryFeed>,
    val recentlyAdded: List<MediaItem>,
    val mostValuable: List<MediaItem>,
)

data class CategoryFeed(
    val category: Category,
    val count: Int,
    val itemOfDay: MediaItem?,
    val recentItems: List<MediaItem>,
)

data class FormatRow(
    val format: String,
    val label: String,
    val items: List<MediaItem>,
)
