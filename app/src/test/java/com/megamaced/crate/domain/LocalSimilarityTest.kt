package com.megamaced.crate.domain

import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.MarketValue
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.PurchasePrice
import com.megamaced.crate.domain.model.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ranking rules for the "More from your crate" row. These mirror
 * `LocalSimilarityScorerTest.php` in the crate server app — a deliberate
 * change to the weights belongs in both.
 */
class LocalSimilarityTest {
    @Test
    fun `same artist outranks genre overlap`() {
        val subject = item(1, artist = "Slint", genres = "Rock, Post-Rock, Math Rock")
        val sameBand = item(2, artist = "Slint")
        val sameGenre = item(3, artist = "Shellac", genres = "Rock, Post-Rock, Math Rock")

        assertTrue(
            LocalSimilarity.score(subject, sameBand) >
                LocalSimilarity.score(subject, sameGenre),
        )
    }

    @Test
    fun `genre overlap contribution is capped`() {
        val many = "Rock, Punk, Indie, Lo-Fi, Noise, Emo"
        val subject = item(1, artist = "A", genres = many)
        val other = item(2, artist = "B", genres = many)

        // Six shared tokens would be 60 uncapped; capped at 30 (+1 owned) it
        // stays below a same-artist match so genre spam can't outrank one.
        assertEquals(31, LocalSimilarity.score(subject, other))
    }

    @Test
    fun `unrelated items score zero and are omitted`() {
        val subject = item(1, artist = "Slint", genres = "Post-Rock")
        val other = item(2, artist = "Dolly Parton", genres = "Country")

        assertEquals(0, LocalSimilarity.score(subject, other))
        assertEquals(emptyList<MediaItem>(), LocalSimilarity.rank(subject, listOf(other)))
    }

    @Test
    fun `genre tokens match regardless of case and spacing`() {
        val subject = item(1, artist = "A", genres = "Post-Rock, Shoegaze")
        val other = item(2, artist = "B", genres = "  post-rock ,  SHOEGAZE ")

        assertEquals(21, LocalSimilarity.score(subject, other))
    }

    @Test
    fun `an item is never recommended to itself`() {
        val subject = item(1, artist = "Slint", genres = "Post-Rock")

        assertEquals(emptyList<MediaItem>(), LocalSimilarity.rank(subject, listOf(subject)))
    }

    @Test
    fun `other categories are never suggested`() {
        // Both tagged "Fantasy", but an album and a game sharing a token is a
        // coincidence rather than a recommendation.
        val subject = item(1, artist = "A", genres = "Fantasy", category = Category.Music)
        val game = item(2, artist = "B", genres = "Fantasy", category = Category.Games)

        assertEquals(emptyList<MediaItem>(), LocalSimilarity.rank(subject, listOf(game)))
    }

    @Test
    fun `format alone never promotes an item`() {
        // Everything in a record collection is "Vinyl"; on its own that says nothing.
        val subject = item(1, artist = "A", format = "Vinyl")
        val other = item(2, artist = "B", format = "Vinyl")

        assertEquals(0, LocalSimilarity.score(subject, other))
    }

    @Test
    fun `blank artists do not count as a match`() {
        val subject = item(1, artist = "", genres = "Jazz")
        val other = item(2, artist = "", genres = "Jazz")

        // Genre overlap plus the owned nudge — the two blanks must not add 50.
        assertEquals(11, LocalSimilarity.score(subject, other))
    }

    @Test
    fun `unenriched items still match on artist`() {
        // No genres, label or year: a hand-typed item that was never enriched.
        val subject = item(1, artist = "Slint")
        val other = item(2, artist = "Slint")

        assertEquals(listOf(2L), LocalSimilarity.rank(subject, listOf(other)).map { it.id })
    }

    @Test
    fun `year proximity is banded`() {
        val subject = item(1, artist = "A", genres = "Jazz", year = 1960)

        assertEquals(15, LocalSimilarity.score(subject, item(2, artist = "B", genres = "Jazz", year = 1963)))
        assertEquals(13, LocalSimilarity.score(subject, item(3, artist = "C", genres = "Jazz", year = 1969)))
        assertEquals(11, LocalSimilarity.score(subject, item(4, artist = "D", genres = "Jazz", year = 1999)))
    }

    @Test
    fun `owned items edge out wishlist items at equal score`() {
        val subject = item(1, artist = "A", genres = "Jazz")
        val owned = item(2, artist = "B", genres = "Jazz", status = Status.Owned)
        val wanted = item(3, artist = "C", genres = "Jazz", status = Status.Wanted)

        assertTrue(
            LocalSimilarity.score(subject, owned) > LocalSimilarity.score(subject, wanted),
        )
    }

    @Test
    fun `rank respects the limit`() {
        val subject = item(1, artist = "Slint")
        val candidates = (2L..12L).map { item(it, artist = "Slint") }

        assertEquals(6, LocalSimilarity.rank(subject, candidates).size)
        assertEquals(3, LocalSimilarity.rank(subject, candidates, limit = 3).size)
    }

    @Test
    fun `rank order is stable for equally scored items`() {
        val subject = item(1, artist = "Slint")
        val a = item(2, artist = "Slint", year = 1991)
        val b = item(3, artist = "Slint", year = 1989)

        // Newest first, and the same order whichever way they arrive.
        assertEquals(listOf(2L, 3L), LocalSimilarity.rank(subject, listOf(a, b)).map { it.id })
        assertEquals(listOf(2L, 3L), LocalSimilarity.rank(subject, listOf(b, a)).map { it.id })
    }

    private fun item(
        id: Long,
        artist: String? = "Artist $id",
        genres: String? = null,
        label: String? = null,
        year: Int? = null,
        format: String? = null,
        status: Status = Status.Owned,
        category: Category = Category.Music,
    ) = MediaItem(
        id = id,
        userId = null,
        title = "Title $id",
        artist = artist,
        format = format,
        year = year,
        barcode = null,
        notes = null,
        status = status,
        category = category,
        discogsId = null,
        artworkPath = null,
        label = label,
        country = null,
        genres = genres,
        tracklist = emptyList(),
        pressingNotes = null,
        discogsArtistId = null,
        artistBio = null,
        artistMembers = emptyList(),
        marketValue = MarketValue(null, null, null, null, null),
        purchasePrice = PurchasePrice(null, null),
        createdAt = null,
        updatedAt = null,
    )
}
