package com.megamaced.crate.ui.screen.collection

import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.MarketValue
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.PurchasePrice
import com.megamaced.crate.domain.model.SortField
import com.megamaced.crate.domain.model.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `created at axis uses relative date buckets`() {
        val items = listOf(
            item(1, "A", createdAt = "2026-07-19 10:00:00"),
            item(2, "B", createdAt = "2026-07-18 10:00:00"),
        )
        val groups = groupItemsForSort(items, SortField.CreatedAt, today)
        assertEquals(listOf("Today", "Yesterday"), groups.map { it.header })
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
