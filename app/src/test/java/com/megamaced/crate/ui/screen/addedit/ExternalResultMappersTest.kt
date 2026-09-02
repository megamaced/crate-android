package com.megamaced.crate.ui.screen.addedit

import com.megamaced.crate.data.api.dto.ComicVineSearchResultDto
import com.megamaced.crate.data.api.dto.DiscogsSearchResultDto
import com.megamaced.crate.data.api.dto.OpenLibraryResultDto
import com.megamaced.crate.data.api.dto.RawgSearchResultDto
import com.megamaced.crate.data.api.dto.TmdbSearchResultDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * These mappings were copied into main search, the add/edit sheet and barcode
 * scanning, and the copies drifted — main search lost every cover, and RAWG's
 * platform never reached the form. One shared mapper plus these assertions is
 * what stops that happening again.
 */
class ExternalResultMappersTest {
    @Test
    fun `every provider carries a cover through`() {
        assertEquals("d.jpg", DiscogsSearchResultDto(title = "A", thumb = "d.jpg").toResult().coverUrl)
        assertEquals("t.jpg", TmdbSearchResultDto(tmdbId = "1", title = "A", thumb = "t.jpg").toResult().coverUrl)
        assertEquals("r.jpg", RawgSearchResultDto(rawgId = "1", title = "A", thumb = "r.jpg").toResult().coverUrl)
        assertEquals("c.jpg", ComicVineSearchResultDto(comicVineId = "1", title = "A", thumb = "c.jpg").toResult().coverUrl)
        assertEquals("o.jpg", OpenLibraryResultDto(workKey = "k", title = "A", thumb = "o.jpg").toResult().coverUrl)
    }

    @Test
    fun `Open Library prefers its full artwork over the thumbnail`() {
        // coverUrl is both the row's thumbnail and the artwork the form hands
        // the server to cache, so the larger image wins where there is one.
        val result =
            OpenLibraryResultDto(workKey = "k", title = "A", thumb = "small.jpg", artworkUrl = "large.jpg").toResult()

        assertEquals("large.jpg", result.coverUrl)
    }

    @Test
    fun `a RAWG result keeps the platform it reports`() {
        // The server maps RAWG's platforms to a Crate format value and matches
        // on it when enriching, so dropping it empties Platform and degrades
        // the enrichment that follows.
        val result = RawgSearchResultDto(rawgId = "1", title = "Half-Life", format = "PC").toResult()

        assertEquals("PC", result.format)
    }

    @Test
    fun `an Open Library result with no title is refused rather than shown blank`() {
        assertNull(OpenLibraryResultDto(workKey = "k", title = " ").toResultOrNull())
        assertEquals("A", OpenLibraryResultDto(workKey = "k", title = "A").toResultOrNull()?.title)
    }
}
