package com.megamaced.crate.domain.model

import androidx.annotation.StringRes
import com.megamaced.crate.R

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
    @param:StringRes val artistLabelRes: Int,
    @param:StringRes val titleLabelRes: Int,
    val supportsFormat: Boolean,
    val supportsValue: Boolean,
) {
    companion object {
        fun forCategory(category: Category): CategorySortConfig =
            when (category) {
                Category.Music -> {
                    CategorySortConfig(
                        R.string.field_artist_music,
                        R.string.sort_noun_title_music,
                        supportsFormat = false,
                        supportsValue = true,
                    )
                }

                Category.Films -> {
                    CategorySortConfig(
                        R.string.field_artist_films,
                        R.string.sort_noun_title_films,
                        supportsFormat = false,
                        supportsValue = false,
                    )
                }

                Category.Books -> {
                    CategorySortConfig(
                        R.string.field_artist_books,
                        R.string.sort_noun_title_books,
                        supportsFormat = false,
                        supportsValue = false,
                    )
                }

                Category.Games -> {
                    CategorySortConfig(
                        R.string.field_artist_games,
                        R.string.sort_noun_title_games,
                        supportsFormat = true,
                        supportsValue = true,
                    )
                }

                Category.Comics -> {
                    CategorySortConfig(
                        R.string.field_artist_comics,
                        R.string.sort_noun_title_comics,
                        supportsFormat = false,
                        supportsValue = true,
                    )
                }
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
 * One entry in the sort menu. [nounRes], when set, names the field the label
 * interpolates — "Director A–Z" for films, "Artist A–Z" for music — so the
 * ordering of noun and direction stays the translator's choice.
 */
data class SortOption(
    val sort: CollectionSort,
    @param:StringRes val labelRes: Int,
    @param:StringRes val nounRes: Int? = null,
)

/**
 * Build the ordered list of options the UI should show for the given category.
 * Order mirrors the Vue `<select>` so the experience is consistent across web
 * and mobile.
 */
fun sortOptionsFor(category: Category): List<SortOption> {
    val cfg = CategorySortConfig.forCategory(category)
    val opts = mutableListOf<SortOption>()
    opts += SortOption(CollectionSort(SortField.CreatedAt, SortDirection.Desc), R.string.sort_newest_first)
    opts += SortOption(CollectionSort(SortField.CreatedAt, SortDirection.Asc), R.string.sort_oldest_first)
    opts += SortOption(CollectionSort(SortField.Artist, SortDirection.Asc), R.string.sort_a_to_z, cfg.artistLabelRes)
    opts += SortOption(CollectionSort(SortField.Artist, SortDirection.Desc), R.string.sort_z_to_a, cfg.artistLabelRes)
    opts += SortOption(CollectionSort(SortField.Title, SortDirection.Asc), R.string.sort_a_to_z, cfg.titleLabelRes)
    opts += SortOption(CollectionSort(SortField.Title, SortDirection.Desc), R.string.sort_z_to_a, cfg.titleLabelRes)
    opts += SortOption(CollectionSort(SortField.Year, SortDirection.Asc), R.string.sort_year_oldest)
    opts += SortOption(CollectionSort(SortField.Year, SortDirection.Desc), R.string.sort_year_newest)
    if (cfg.supportsFormat) {
        opts += SortOption(CollectionSort(SortField.Format, SortDirection.Asc), R.string.sort_a_to_z, R.string.sort_noun_format)
        opts += SortOption(CollectionSort(SortField.Format, SortDirection.Desc), R.string.sort_z_to_a, R.string.sort_noun_format)
    }
    if (cfg.supportsValue) {
        opts += SortOption(CollectionSort(SortField.MarketValue, SortDirection.Desc), R.string.sort_value_highest)
        opts += SortOption(CollectionSort(SortField.MarketValue, SortDirection.Asc), R.string.sort_value_lowest)
    }
    return opts
}
