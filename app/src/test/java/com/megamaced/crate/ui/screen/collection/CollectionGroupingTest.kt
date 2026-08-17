package com.megamaced.crate.ui.screen.collection

import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.CollectionSort
import com.megamaced.crate.domain.model.MarketValue
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.PurchasePrice
import com.megamaced.crate.domain.model.SortDirection
import com.megamaced.crate.domain.model.SortField
import com.megamaced.crate.domain.model.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CollectionGroupingTest {
    private val today = LocalDate.of(2026, 7, 19)

    @Test
    fun `artist axis buckets by first letter and strips leading articles`() {
        val items = listOf(
            item(1, "OK Computer", artist = "Radiohead"),
            item(2, "Wish You Were Here", artist = "Pink Floyd"),
            item(3, "Definitely Maybe", artist = "The Beatles"),
            item(4, "Untitled", artist = "808 State"),
        )
        val groups = groupItemsForSort(items, SortField.Artist, today)
        val headers = groups.map { it.header }
        // "The Beatles" files under B, "808 State" under '#'.
        assertEquals(listOf("R", "P", "B", "#"), headers)
    }

    @Test
    fun `title axis does not strip articles`() {
        val items = listOf(item(1, "The Wall", artist = "Pink Floyd"))
        val groups = groupItemsForSort(items, SortField.Title, today)
        assertEquals(listOf("T"), groups.map { it.header })
    }

    @Test
    fun `year axis buckets by decade with unknown fallback`() {
        val items = listOf(
            item(1, "A", year = 1997),
            item(2, "B", year = 1991),
            item(3, "C", year = 2004),
            item(4, "D", year = null),
        )
        val groups = groupItemsForSort(items, SortField.Year, today)
        assertEquals(listOf("1990s", "2000s", "Unknown"), groups.map { it.header })
        assertEquals(2, groups.first { it.header == "1990s" }.items.size)
    }

    @Test
    fun `format axis buckets by format name`() {
        val items = listOf(
            item(1, "A", format = "LP"),
            item(2, "B", format = "CD"),
            item(3, "C", format = "LP"),
            item(4, "D", format = null),
        )
        val groups = groupItemsForSort(items, SortField.Format, today)
        assertEquals(listOf("LP", "CD", "Unknown"), groups.map { it.header })
    }

    @Test
    fun `market value axis is a single header-less group`() {
        val items = listOf(item(1, "A"), item(2, "B"))
        val groups = groupItemsForSort(items, SortField.MarketValue, today)
        assertEquals(1, groups.size)
        assertNull(groups.single().header)
        assertEquals(2, groups.single().items.size)
    }

    @Test
    fun `artist sort and grouping agree on article stripping`() {
        val items = listOf(
            item(1, "X", artist = "The Beatles"),
            item(2, "Y", artist = "Adele"),
            item(3, "Z", artist = "Cardigans"),
        )
        val sorted = items.sortedWith(comparatorForSort(CollectionSort(SortField.Artist, SortDirection.Asc)))
        // "The Beatles" sorts under B, between Adele and Cardigans.
        assertEquals(listOf("Adele", "The Beatles", "Cardigans"), sorted.map { it.artist })
        val groups = groupItemsForSort(sorted, SortField.Artist, today)
        assertEquals(listOf("A", "B", "C"), groups.map { it.header })
    }

    @Test
    fun `created at axis uses relative date buckets`() {
        val items = listOf(
            item(1, "A", createdAt = "2026-07-19 10:00:00"),
            item(2, "B", createdAt = "2026-07-18 10:00:00"),
        )
        val groups = groupItemsForSort(items, SortField.CreatedAt, today)
        assertEquals(listOf("Today", "Yesterday"), groups.map { it.header })
    }

    @Test
    fun `repeat detection ignores case and surrounding whitespace`() {
        val first = item(1, "OK Computer", artist = "Radiohead")
        val second = item(2, "Kid A", artist = " radiohead ")
        val other = item(3, "Blue Lines", artist = "Massive Attack")
        assertTrue(isArtistRepeat(second, first))
        assertFalse(isArtistRepeat(other, second))
    }

    @Test
    fun `first row of a group and blank artists are never repeats`() {
        val blank = item(1, "Untitled", artist = " ")
        val named = item(2, "OK Computer", artist = "Radiohead")
        assertFalse(isArtistRepeat(named, null))
        // Two artist-less items in a row still both render their (absent) name
        // rather than collapsing into a nameless cluster.
        assertFalse(isArtistRepeat(blank, item(3, "Other", artist = null)))
    }

    @Test
    fun `artist sort orders titles within each artist`() {
        val items = listOf(
            item(1, "Wish You Were Here", artist = "Pink Floyd"),
            item(2, "OK Computer", artist = "Radiohead"),
            item(3, "Animals", artist = "Pink Floyd"),
            item(4, "Kid A", artist = "Radiohead"),
        )
        val sorted = items.sortedWith(comparatorForSort(CollectionSort(SortField.Artist, SortDirection.Asc)))
        assertEquals(
            listOf("Animals", "Wish You Were Here", "Kid A", "OK Computer"),
            sorted.map { it.title },
        )
    }

    @Test
    fun `year sort orders artist then title within each year`() {
        val items = listOf(
            item(1, "Zoolook", artist = "Jarre", year = 1984),
            item(2, "Hounds of Love", artist = "Kate Bush", year = 1985),
            item(3, "Automatic", artist = "Jarre", year = 1984),
            item(4, "Alpha", artist = "Bush", year = 1984),
        )
        val sorted = items.sortedWith(comparatorForSort(CollectionSort(SortField.Year, SortDirection.Asc)))
        // 1984 first, and inside it Bush before Jarre, then Jarre's two by title.
        assertEquals(listOf("Alpha", "Automatic", "Zoolook", "Hounds of Love"), sorted.map { it.title })
    }

    @Test
    fun `descending flips only the primary axis and leaves tiebreaks ascending`() {
        val items = listOf(
            item(1, "Wish", artist = "Pink Floyd", year = 1975),
            item(2, "Alpha", artist = "Zappa", year = 1975),
            item(3, "Beta", artist = "Zappa", year = 1975),
            item(4, "Newer", artist = "Someone", year = 1990),
        )
        val sorted = items.sortedWith(comparatorForSort(CollectionSort(SortField.Year, SortDirection.Desc)))
        // 1990 leads because years count down, but 1975 still reads A–Z inside.
        assertEquals(listOf("Newer", "Wish", "Alpha", "Beta"), sorted.map { it.title })
    }

    @Test
    fun `items sharing a bulk-import timestamp fall back to artist and title`() {
        val stamp = "2026-05-10 13:33:07"
        val items = listOf(
            item(1, "Zoo", artist = "Radiohead", createdAt = stamp),
            item(2, "Amnesiac", artist = "Radiohead", createdAt = stamp),
            item(3, "Blue Lines", artist = "Massive Attack", createdAt = stamp),
        )
        val sorted = items.sortedWith(comparatorForSort(CollectionSort(SortField.CreatedAt, SortDirection.Asc)))
        assertEquals(listOf("Blue Lines", "Amnesiac", "Zoo"), sorted.map { it.title })
    }

    @Test
    fun `two pressings of one album keep a stable order`() {
        // Same artist, title, year and format — only the id differs, as with two
        // copies of one record. Ties must not reorder between renders.
        val a = item(3674, "The Back Room", artist = "Editors", year = 2005)
        val b = item(3499, "The Back Room", artist = "Editors", year = 2005)
        val sort = CollectionSort(SortField.Artist, SortDirection.Asc)
        assertEquals(listOf(3499L, 3674L), listOf(a, b).sortedWith(comparatorForSort(sort)).map { it.id })
        assertEquals(listOf(3499L, 3674L), listOf(b, a).sortedWith(comparatorForSort(sort)).map { it.id })
    }

    @Test
    fun `unknown years sort together ahead of known ones`() {
        val items = listOf(
            item(1, "Known", artist = "A", year = 1999),
            item(2, "NoYear", artist = "B", year = null),
            item(3, "ZeroYear", artist = "C", year = 0),
        )
        val sorted = items.sortedWith(comparatorForSort(CollectionSort(SortField.Year, SortDirection.Asc)))
        assertEquals(listOf("NoYear", "ZeroYear", "Known"), sorted.map { it.title })
    }

    private fun item(
        id: Long,
        title: String,
        artist: String? = "Test",
        format: String? = "LP",
        year: Int? = 2000,
        createdAt: String? = "2025-01-01 00:00:00",
    ) = MediaItem(
        id = id,
        userId = null,
        title = title,
        artist = artist,
        format = format,
        year = year,
        barcode = null,
        notes = null,
        status = Status.Owned,
        category = Category.Music,
        discogsId = null,
        artworkPath = null,
        label = null,
        country = null,
        genres = null,
        tracklist = emptyList(),
        pressingNotes = null,
        discogsArtistId = null,
        artistBio = null,
        artistMembers = emptyList(),
        marketValue = MarketValue(null, null, null, null, null),
        purchasePrice = PurchasePrice(null, null),
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
