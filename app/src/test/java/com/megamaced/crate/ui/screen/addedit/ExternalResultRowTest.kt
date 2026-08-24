package com.megamaced.crate.ui.screen.addedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ExternalResultRowTest {
    @Test
    fun `two results with the same title and year get distinct list keys`() {
        // Open Library returns one row per edition of a work, so title, author
        // and year are all identical — identityKey alone collides here.
        val results = listOf(
            ExternalSearchResult(title = "1984", artist = "George Orwell", year = 1949),
            ExternalSearchResult(title = "1984", artist = "George Orwell", year = 1949),
        )

        assertEquals(results[0].identityKey(), results[1].identityKey())
        assertNotEquals(results[0].listKey(0), results[1].listKey(1))
    }

    @Test
    fun `barcode candidates all sharing the scanned barcode get distinct list keys`() {
        val scanned = "5099902987521"
        val results = listOf(
            ExternalSearchResult(title = "Pressing A", barcode = scanned),
            ExternalSearchResult(title = "Pressing B", barcode = scanned),
            ExternalSearchResult(title = "Pressing C", barcode = scanned),
        )

        val keys = results.mapIndexed { index, result -> result.listKey(index) }

        assertEquals(3, keys.toSet().size)
    }

    @Test
    fun `a discogs id still leads the key`() {
        val result = ExternalSearchResult(title = "OK Computer", discogsId = "12345")

        assertEquals("12345", result.identityKey())
        assertEquals("0-12345", result.listKey(0))
    }
}
