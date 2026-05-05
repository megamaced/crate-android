package com.macebox.crate.domain.model

enum class SortField(
    val apiKey: String,
) {
    CreatedAt("createdAt"),
    Artist("artist"),
    Title("title"),
    Year("year"),
    Format("format"),
    MarketValue("marketValue"),
}

enum class SortDirection(
    val apiKey: String,
) {
    Asc("asc"),
    Desc("desc"),
}

/**
 * Mirror of crate/src/components/CollectionView.vue per-category sort config.
 * The web UI hides Format / MarketValue options for categories that don't
 * support them — Android does the same via [supportsFormat] / [supportsValue].
 */
data class CollectionSort(
    val axis: SortField,
    val direction: SortDirection,
) {
    val key: String get() = "${axis.apiKey}-${direction.apiKey}"

    companion object {
        val Default = CollectionSort(SortField.Artist, SortDirection.Asc)
    }
}

data class CategorySortConfig(
    val artistLabel: String,
    val titleLabel: String,
    val supportsFormat: Boolean,
    val supportsValue: Boolean,
) {
    companion object {
        fun forCategory(category: Category): CategorySortConfig =
            when (category) {
                Category.Music -> CategorySortConfig("Artist", "Album", supportsFormat = false, supportsValue = true)
                Category.Films -> CategorySortConfig("Director", "Film", supportsFormat = false, supportsValue = false)
                Category.Books -> CategorySortConfig("Author", "Title", supportsFormat = false, supportsValue = false)
                Category.Games -> CategorySortConfig("Developer", "Game", supportsFormat = true, supportsValue = true)
                Category.Comics -> CategorySortConfig("Writer", "Volume", supportsFormat = false, supportsValue = true)
            }
    }

    fun supports(field: SortField): Boolean =
        when (field) {
            SortField.Format -> supportsFormat
            SortField.MarketValue -> supportsValue
            else -> true
        }
}

/**
 * Build the ordered list of (sort, label) pairs the UI should show for the
 * given category. Order mirrors the Vue `<select>` so the experience is
 * consistent across web and mobile.
 */
fun sortOptionsFor(category: Category): List<Pair<CollectionSort, String>> {
    val cfg = CategorySortConfig.forCategory(category)
    val opts = mutableListOf<Pair<CollectionSort, String>>()
    opts += CollectionSort(SortField.CreatedAt, SortDirection.Desc) to "Newest First"
    opts += CollectionSort(SortField.CreatedAt, SortDirection.Asc) to "Oldest First"
    opts += CollectionSort(SortField.Artist, SortDirection.Asc) to "${cfg.artistLabel} A–Z"
    opts += CollectionSort(SortField.Artist, SortDirection.Desc) to "${cfg.artistLabel} Z–A"
    opts += CollectionSort(SortField.Title, SortDirection.Asc) to "${cfg.titleLabel} A–Z"
    opts += CollectionSort(SortField.Title, SortDirection.Desc) to "${cfg.titleLabel} Z–A"
    opts += CollectionSort(SortField.Year, SortDirection.Asc) to "Year (Oldest)"
    opts += CollectionSort(SortField.Year, SortDirection.Desc) to "Year (Newest)"
    if (cfg.supportsFormat) {
        opts += CollectionSort(SortField.Format, SortDirection.Asc) to "Format A–Z"
        opts += CollectionSort(SortField.Format, SortDirection.Desc) to "Format Z–A"
    }
    if (cfg.supportsValue) {
        opts += CollectionSort(SortField.MarketValue, SortDirection.Desc) to "Value (Highest)"
        opts += CollectionSort(SortField.MarketValue, SortDirection.Asc) to "Value (Lowest)"
    }
    return opts
}
