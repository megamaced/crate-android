package com.megamaced.crate.domain

import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.Status

/**
 * Ranks the user's own items by how much they resemble a given item — the
 * "More from your crate" row on the detail screen.
 *
 * Deliberately a pure function over already-cached items rather than a server
 * call, which is what lets this row work with no connection: Room holds the
 * whole collection, so the suggestions are available offline while the
 * provider-backed row is not.
 *
 * These weights mirror `LocalSimilarityScorer.php` in the crate server app,
 * which computes the same row for the web client. The two rankings don't have
 * to agree item-for-item, but a deliberate change to the rules belongs in
 * both.
 */
object LocalSimilarity {
    /** Same artist / author / director / developer — by far the strongest signal. */
    private const val WEIGHT_SAME_ARTIST = 50

    /** Per shared genre / style / subject token. */
    private const val WEIGHT_GENRE_TOKEN = 10

    /** Cap on the genre contribution, so a long subject list can't outrank an artist match. */
    private const val MAX_GENRE_SCORE = 30

    /** Same label / publisher / studio. */
    private const val WEIGHT_SAME_LABEL = 6
    private const val WEIGHT_YEAR_CLOSE = 4
    private const val WEIGHT_YEAR_NEAR = 2
    private const val WEIGHT_SAME_FORMAT = 2

    /** Nudge owned items above wishlist ones at equal score. */
    private const val WEIGHT_OWNED = 1

    /**
     * Best matches for [item] drawn from [candidates], most similar first.
     *
     * [candidates] may contain [item] itself and items of other categories;
     * both are filtered out. Items with nothing in common are omitted rather
     * than used as padding — a row of unrelated records reads as a bug.
     */
    fun rank(
        item: MediaItem,
        candidates: List<MediaItem>,
        limit: Int = 6,
    ): List<MediaItem> =
        candidates
            .asSequence()
            .filter { it.id != item.id && it.category == item.category }
            .map { it to score(item, it) }
            .filter { (_, score) -> score > 0 }
            // Score, then newest, then id — a total order, so the row doesn't
            // reshuffle between recompositions.
            .sortedWith(
                compareByDescending<Pair<MediaItem, Int>> { it.second }
                    .thenByDescending { it.first.year ?: 0 }
                    .thenByDescending { it.first.id },
            ).take(limit)
            .map { it.first }
            .toList()

    /** Similarity score between two items; 0 means "nothing in common". */
    fun score(
        item: MediaItem,
        candidate: MediaItem,
    ): Int {
        var score = 0

        val artist = item.artist.normalise()
        if (artist.isNotEmpty() && artist == candidate.artist.normalise()) {
            score += WEIGHT_SAME_ARTIST
        }

        val shared = item.genres.tokens() intersect candidate.genres.tokens()
        if (shared.isNotEmpty()) {
            score += minOf(shared.size * WEIGHT_GENRE_TOKEN, MAX_GENRE_SCORE)
        }

        val label = item.label.normalise()
        if (label.isNotEmpty() && label == candidate.label.normalise()) {
            score += WEIGHT_SAME_LABEL
        }

        val year = item.year
        val candidateYear = candidate.year
        if (year != null && candidateYear != null) {
            val gap = kotlin.math.abs(year - candidateYear)
            score +=
                when {
                    gap <= 5 -> WEIGHT_YEAR_CLOSE
                    gap <= 10 -> WEIGHT_YEAR_NEAR
                    else -> 0
                }
        }

        // Only meaningful alongside another signal — everything in a record
        // collection is "Vinyl", so format alone must never promote an item.
        if (score > 0) {
            val format = item.format.normalise()
            if (format.isNotEmpty() && format == candidate.format.normalise()) {
                score += WEIGHT_SAME_FORMAT
            }
            if (candidate.status == Status.Owned) {
                score += WEIGHT_OWNED
            }
        }

        return score
    }

    /**
     * Split a stored `genres` value into comparable tokens. The field holds a
     * comma-separated blend of Discogs genres + styles, TMDB genres, RAWG
     * genres and Open Library subjects, so casing and spacing vary by source.
     */
    private fun String?.tokens(): Set<String> =
        this
            ?.split(',')
            ?.map { it.normalise() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

    private fun String?.normalise(): String = this?.trim()?.lowercase() ?: ""
}
