package com.megamaced.crate.ui.screen.collection

import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.MarketValue
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.PurchasePrice
import com.megamaced.crate.domain.model.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionFiltersTest {
    @Test
    fun `genres split on commas and drop blanks`() {
        val item = item(1, genres = "Rock,  Art Rock , ,Electronic")
        assertEquals(listOf("Rock", "Art Rock", "Electronic"), genreTokens(item))
        assertEquals(emptyList<String>(), genreTokens(item(2, genres = null)))
        assertEquals(emptyList<String>(), genreTokens(item(3, genres = " , ")))
    }

    @Test
    fun `a JSON array of genres tokenises instead of splitting as text`() {
        // Older enriched rows can carry the provider's array verbatim; split on
        // commas it renders chips like `["Rock` that never match a filter.
        val item = item(1, genres = """["Rock", "Art Rock"]""")
        assertEquals(listOf("Rock", "Art Rock"), genreTokens(item))
        assertTrue(hasGenre(item, "art rock"))
    }

    @Test
    fun `an empty JSON array yields no genres`() {
        assertEquals(emptyList<String>(), genreTokens(item(1, genres = "[]")))
    }

    @Test
    fun `a bracketed string that isn't valid JSON falls back to comma splitting`() {
        assertEquals(listOf("[Rock", "Art Rock"), genreTokens(item(1, genres = "[Rock, Art Rock")))
    }

    @Test
    fun `genre matching is case-insensitive and exact per token`() {
        val item = item(1, genres = "Alternative Rock, Art Rock")
        assertTrue(hasGenre(item, "alternative rock"))
        assertTrue(hasGenre(item, " Art Rock "))
        // A substring is not a match — "Rock" is its own genre elsewhere.
        assertFalse(hasGenre(item, "Rock"))
    }

    @Test
    fun `genre buckets count each item once and sort alphabetically`() {
        val items = listOf(
            item(1, genres = "Rock, Electronic"),
            // Same genre twice in one item still counts once.
            item(2, genres = "rock, Rock"),
            item(3, genres = "Ambient"),
        )
        assertEquals(
            listOf("Ambient" to 1, "Electronic" to 1, "Rock" to 2),
            genreBuckets(items).map { it.value to it.count },
        )
    }

    @Test
    fun `decade buckets group by ten years oldest first and skip missing years`() {
        val items = listOf(
            item(1, year = 1997),
            item(2, year = 1999),
            item(3, year = 2004),
            item(4, year = null),
            item(5, year = 0),
        )
        assertEquals(
            listOf("1990s" to 2, "2000s" to 1),
            decadeBuckets(items).map { it.value to it.count },
        )
    }

    @Test
    fun `status splits the collection into two lists`() {
        val items = listOf(
            item(1, status = Status.Owned),
            item(2, status = Status.Wanted),
            item(3, status = Status.Owned),
        )
        assertEquals(listOf(1L, 3L), filterByStatus(items, Status.Owned).map { it.id })
        assertEquals(listOf(2L), filterByStatus(items, Status.Wanted).map { it.id })
    }

    @Test
    fun `buckets built over one tab don't offer the other tab's values`() {
        // The order is what matters here: status narrows the list before the
        // option lists are built, so a genre or a decade that only the wanted
        // list carries is never offered on the owned tab, where selecting it
        // would empty the list.
        val items = listOf(
            item(1, genres = "Rock", year = 1997),
            item(2, genres = "Vaporwave", year = 2014, status = Status.Wanted),
        )
        val owned = filterByStatus(items, Status.Owned)
        assertEquals(listOf("Rock"), genreBuckets(owned).map { it.value })
        assertEquals(listOf("1990s"), decadeBuckets(owned).map { it.value })
        assertTrue(applyValueFilters(owned, "Vaporwave", null).isEmpty())
    }

    @Test
    fun `genre and decade filters combine`() {
        val items = listOf(
            item(1, genres = "Rock", year = 1997),
            item(2, genres = "Rock", year = 2004),
            item(3, genres = "Ambient", year = 1997),
        )
        assertEquals(listOf(1L), applyValueFilters(items, "Rock", "1990s").map { it.id })
        assertEquals(listOf(1L, 2L), applyValueFilters(items, "Rock", null).map { it.id })
        // Blank selections pass everything through.
        assertEquals(3, applyValueFilters(items, "", "").size)
    }

    private fun item(
        id: Long,
        genres: String? = "Rock",
        year: Int? = 2000,
        status: Status = Status.Owned,
    ) = MediaItem(
        id = id,
        userId = null,
        title = "Title $id",
        artist = "Artist",
        format = "LP",
        year = year,
        barcode = null,
        notes = null,
        status = status,
        category = Category.Music,
        discogsId = null,
        artworkPath = null,
        label = null,
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
