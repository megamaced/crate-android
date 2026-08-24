package com.megamaced.crate.domain.model

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
