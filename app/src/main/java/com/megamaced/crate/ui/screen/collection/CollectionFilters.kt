package com.megamaced.crate.ui.screen.collection

import com.megamaced.crate.domain.model.MediaItem

// Genre / decade filtering for the collection views, mirroring
// crate/src/utils/genres.js so web and mobile bucket identically.
//
// Providers store genres as one comma-separated string ("Rock, Art Rock"), so
// anything that filters or lists them has to split first. Matching is
// case-insensitive; the first casing seen wins for display.

/** One selectable filter value and how many items carry it. */
data class FilterBucket(
    val value: String,
    val count: Int,
)

/** An item's genres, split and trimmed. Empty when it has none. */
internal fun genreTokens(item: MediaItem): List<String> =
    item.genres
        .orEmpty()
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/** True when [item] carries [genre] (case-insensitive). */
internal fun hasGenre(
    item: MediaItem,
    genre: String,
): Boolean {
    val needle = genre.trim()
    if (needle.isEmpty()) return true
    return genreTokens(item).any { it.equals(needle, ignoreCase = true) }
}

/** The decade an item files under — 1997 -> "1990s" — or null without a year. */
internal fun decadeOf(item: MediaItem): String? = item.year?.takeIf { it != 0 }?.let { "${(it / 10) * 10}s" }

/** Distinct genres across [items] with counts, alphabetical. */
internal fun genreBuckets(items: List<MediaItem>): List<FilterBucket> {
    val counts = LinkedHashMap<String, FilterBucket>()
    for (item in items) {
        // One item can name a genre twice ("Rock, rock"); count it once.
        val seen = HashSet<String>()
        for (genre in genreTokens(item)) {
            val key = genre.lowercase()
            if (!seen.add(key)) continue
            val existing = counts[key]
            counts[key] = existing?.copy(count = existing.count + 1) ?: FilterBucket(genre, 1)
        }
    }
    return counts.values.sortedBy { it.value.lowercase() }
}

/** Distinct decades across [items] with counts, oldest first. */
internal fun decadeBuckets(items: List<MediaItem>): List<FilterBucket> =
    items
        .mapNotNull { decadeOf(it) }
        .groupingBy { it }
        .eachCount()
        .map { (decade, count) -> FilterBucket(decade, count) }
        .sortedBy { it.value.dropLast(1).toIntOrNull() ?: 0 }

/**
 * Apply the genre / decade selections to [items]. Empty selections pass
 * everything through; a selection that no longer matches anything is the
 * caller's problem to reset (see the view models' availability intersect).
 */
internal fun applyValueFilters(
    items: List<MediaItem>,
    genre: String?,
    decade: String?,
): List<MediaItem> {
    var result = items
    if (!genre.isNullOrBlank()) result = result.filter { hasGenre(it, genre) }
    if (!decade.isNullOrBlank()) result = result.filter { decadeOf(it) == decade }
    return result
}
