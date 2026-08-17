package com.megamaced.crate.ui.screen.collection

import com.megamaced.crate.domain.model.CollectionSort
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.SortDirection
import com.megamaced.crate.domain.model.SortField
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
 * True when [item] repeats the artist of the row above it ([previous]), so the
 * list can print the name once per run and let the rest read as one cluster.
 * Items with no artist never count as a repeat.
 */
internal fun isArtistRepeat(
    item: MediaItem,
    previous: MediaItem?,
): Boolean {
    val artist = item.artist.orEmpty().trim()
    if (artist.isEmpty() || previous == null) return false
    return artist.equals(previous.artist.orEmpty().trim(), ignoreCase = true)
}

/**
 * Format buckets (name + count) for the filter chips, sorted alphabetically.
 * Counts are computed over the full [items] list, not the filtered subset, so
 * toggling one chip doesn't reshuffle every other chip's number — mirrors
 * CollectionView.vue.
 */
internal fun formatBuckets(items: List<MediaItem>): List<FilterBucket> =
    items
        .mapNotNull { it.format?.takeIf { v -> v.isNotBlank() } }
        .groupingBy { it }
        .eachCount()
        .toSortedMap()
        .map { (fmt, count) -> FilterBucket(fmt, count) }

// -- Sort keys ---------------------------------------------------------------
//
// One accessor per axis so the primary comparator and the tiebreak chain below
// can't drift apart. Mirrored by crate/src/utils/sortItems.js.

// Artist strips a leading article so it sorts under the first real word ("The
// Beatles" → B), keeping the sort consistent with the article-stripped group
// headers. Title deliberately keeps its article, matching groupKeyFor().
private fun artistKey(item: MediaItem): String = stripArticle(item.artist.orEmpty().trim())

private fun titleKey(item: MediaItem): String = item.title.trim()

private fun formatKey(item: MediaItem): String = item.format.orEmpty().trim()

/** Absent and zero years both read as "unknown", as they do in [groupKeyFor]. */
private fun yearKey(item: MediaItem): Int = item.year?.takeIf { it != 0 } ?: Int.MIN_VALUE

private fun valueKey(item: MediaItem): Double = item.marketValue.main ?: Double.NEGATIVE_INFINITY

private val ByArtist: Comparator<MediaItem> = compareBy(String.CASE_INSENSITIVE_ORDER) { artistKey(it) }
private val ByTitle: Comparator<MediaItem> = compareBy(String.CASE_INSENSITIVE_ORDER) { titleKey(it) }
private val ByFormat: Comparator<MediaItem> = compareBy(String.CASE_INSENSITIVE_ORDER) { formatKey(it) }
private val ByYear: Comparator<MediaItem> = compareBy { yearKey(it) }
private val ByCreatedAt: Comparator<MediaItem> = compareBy { it.createdAt.orEmpty() }
private val ByValue: Comparator<MediaItem> = compareBy { valueKey(it) }

// Final tiebreak. Every key above can tie — a bulk import stamps one createdAt
// across hundreds of rows, and two pressings of one album share artist, title
// and year — so without this the order of tied rows is left to the sort's
// discretion and can differ between two renders of the same list.
private val ById: Comparator<MediaItem> = compareBy { it.id }

private fun primaryFor(axis: SortField): Comparator<MediaItem> =
    when (axis) {
        SortField.Artist -> ByArtist
        SortField.Title -> ByTitle
        SortField.Year -> ByYear
        SortField.CreatedAt -> ByCreatedAt
        SortField.Format -> ByFormat
        SortField.MarketValue -> ByValue
    }

/**
 * How rows that tie on the primary axis are ordered. Each chain omits its own
 * primary and ends on [ById] so the result is a total order.
 *
 * Artist leads with title, because within one artist the album name is what you
 * scan for; every other axis leads with artist then title, so a year, format or
 * value bucket reads as an alphabetical list rather than an arbitrary one.
 */
private fun tiebreaksFor(axis: SortField): Comparator<MediaItem> =
    when (axis) {
        SortField.Artist -> ByTitle.then(ByYear).then(ByFormat)
        SortField.Title -> ByArtist.then(ByYear).then(ByFormat)
        SortField.Year -> ByArtist.then(ByTitle).then(ByFormat)
        SortField.CreatedAt -> ByArtist.then(ByTitle).then(ByYear)
        SortField.Format -> ByArtist.then(ByTitle).then(ByYear)
        SortField.MarketValue -> ByArtist.then(ByTitle).then(ByYear)
    }.then(ById)

/**
 * Comparator for the active [sort], matching CollectionView.vue's sort order.
 *
 * Only the primary axis follows [CollectionSort.direction]; the tiebreaks stay
 * ascending. "Year (Newest)" therefore counts years down while keeping each
 * year's contents in A–Z order, which is how you'd read a shelf.
 */
internal fun comparatorForSort(sort: CollectionSort): Comparator<MediaItem> {
    val primary = primaryFor(sort.axis)
    val directed = if (sort.direction == SortDirection.Desc) primary.reversed() else primary
    return directed.then(tiebreaksFor(sort.axis))
}
