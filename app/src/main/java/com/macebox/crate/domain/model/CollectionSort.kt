package com.macebox.crate.domain.model

enum class CollectionSort(
    val label: String,
) {
    RecentlyAdded("Recently added"),
    Title("Title (A–Z)"),
    Artist("Artist (A–Z)"),
    Year("Year (newest)"),
}
