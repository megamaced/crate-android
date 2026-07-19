package com.megamaced.crate.ui.screen.collection

import com.megamaced.crate.domain.model.CollectionSort
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.SortDirection
import com.megamaced.crate.domain.model.SortField
import com.megamaced.crate.ui.components.FormatBucket
import java.time.LocalDate

// Group headers for the collection views, mirroring getGroupKey(item, field) in
// crate/src/components/CollectionView.vue so web and mobile bucket identically.
//
//   Artist / Title  → first letter (A–Z), '#' for anything else. Artist strips a
//                     leading article ("The Beatles" files under B) for the key
//                     only, never for display.
//   Year            → decade ("1990s"), or "Unknown" when absent.
//   CreatedAt       → relative date bucket (Today / Last week / 2023 …) via
//                     [DateBucket].
//   Format          → the format name, or "Unknown".
//   MarketValue     → ungrouped: the web UI shows no header for value sorts, so
//                     we emit a single header-less group.

private val ARTICLE_REGEX = Regex("^(the |a |an )\\s*", RegexOption.IGNORE_CASE)

private fun stripArticle(value: String): String = value.replace(ARTICLE_REGEX, "")

private fun alphaKey(value: String): String {
    val first = value.trim().firstOrNull()?.uppercaseChar()
    return if (first != null && first in 'A'..'Z') first.toString() else "#"
}

/**
 * The header label [item] falls under for the active [axis], or null when the
 * axis is intentionally ungrouped (MarketValue).
 */
internal fun groupKeyFor(
    item: MediaItem,
    axis: SortField,
    today: LocalDate,
): String? =
    when (axis) {
        SortField.Artist -> alphaKey(stripArticle(item.artist.orEmpty()))
        SortField.Title -> alphaKey(item.title)
        SortField.Year -> item.year?.takeIf { it != 0 }?.let { "${(it / 10) * 10}s" } ?: "Unknown"
        SortField.CreatedAt -> DateBucket.labelFor(item.createdAt, today)
        SortField.Format -> item.format?.takeIf { it.isNotBlank() } ?: "Unknown"
        SortField.MarketValue -> null
    }

/**
 * Split an already-sorted [items] list into ordered (header, items) groups,
 * preserving the incoming order. MarketValue sorts yield a single header-less
 * group so the View renders them without section dividers.
 */
internal fun groupItemsForSort(
    items: List<MediaItem>,
    axis: SortField,
    today: LocalDate = LocalDate.now(),
): List<ItemGroup> {
    if (items.isEmpty()) return emptyList()
    if (axis == SortField.MarketValue) return listOf(ItemGroup(header = null, items = items))

    val groups = mutableListOf<ItemGroup>()
    val seen = HashMap<String, MutableList<MediaItem>>()
    for (item in items) {
        val key = groupKeyFor(item, axis, today) ?: ""
        val bucket = seen[key]
        if (bucket == null) {
            val list = mutableListOf(item)
            seen[key] = list
            groups.add(ItemGroup(header = key, items = list))
        } else {
            bucket.add(item)
        }
    }
    return groups
}

/**
 * Format buckets (name + count) for the filter chips, sorted alphabetically.
 * Counts are computed over the full [items] list, not the filtered subset, so
 * toggling one chip doesn't reshuffle every other chip's number — mirrors
 * CollectionView.vue.
 */
internal fun formatBuckets(items: List<MediaItem>): List<FormatBucket> =
    items
        .mapNotNull { it.format?.takeIf { v -> v.isNotBlank() } }
        .groupingBy { it }
        .eachCount()
        .toSortedMap()
        .map { (fmt, count) -> FormatBucket(fmt, count) }

/** Comparator for the active [sort], matching CollectionView.vue's sort order. */
internal fun comparatorForSort(sort: CollectionSort): Comparator<MediaItem> {
    val base: Comparator<MediaItem> =
        when (sort.axis) {
            SortField.CreatedAt -> compareBy { it.createdAt.orEmpty() }
            SortField.Artist -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist.orEmpty() }
            SortField.Title -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            SortField.Year -> compareBy { it.year ?: Int.MIN_VALUE }
            SortField.Format -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.format.orEmpty() }
            SortField.MarketValue -> compareBy { it.marketValue.main ?: Double.NEGATIVE_INFINITY }
        }
    return if (sort.direction == SortDirection.Desc) base.reversed() else base
}
