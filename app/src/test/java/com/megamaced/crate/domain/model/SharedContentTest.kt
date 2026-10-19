package com.megamaced.crate.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * An empty collection shared read/write used to produce no category at all —
 * the tiles came from categories holding items — so the recipient had write
 * access and no route to the Add on the category page.
 */
class SharedContentTest {
    @Test
    fun `an empty category shared read-write still has a summary to add from`() {
        val shared = shared(categories = listOf(categoryShare(Category.Music, canWrite = true)))

        val summaries = shared.sharedCategories()

        assertEquals(listOf(Category.Music), summaries.map { it.category })
        assertEquals(0, summaries.single().count)
        assertEquals(listOf("alice"), summaries.single().writeOwners)
    }

    @Test
    fun `an empty library shared read-write covers every category`() {
        val shared = shared(libraries = listOf(LibraryShare(1, "alice", null, canWrite = true, items = emptyList())))

        assertEquals(CATEGORY_ORDER, shared.sharedCategories().map { it.category })
    }

    @Test
    fun `read-only shares still list only categories with items`() {
        val shared =
            shared(
                categories = listOf(categoryShare(Category.Films, canWrite = false)),
                libraries = listOf(LibraryShare(2, "bob", null, canWrite = false, items = listOf(item(5, Category.Books)))),
            )

        val summaries = shared.sharedCategories()

        assertEquals(listOf(Category.Books), summaries.map { it.category })
        assertEquals(emptyList<String>(), summaries.single().writeOwners)
    }

    private fun shared(
        categories: List<CategoryShare> = emptyList(),
        libraries: List<LibraryShare> = emptyList(),
    ) = SharedWithMe(albums = emptyList(), playlists = emptyList(), libraries = libraries, categories = categories)

    private fun categoryShare(
        category: Category,
        canWrite: Boolean,
    ) = CategoryShare(shareId = 1, sharedByUser = "alice", category = category, createdAt = null, canWrite = canWrite, items = emptyList())

    private fun item(
        id: Long,
        category: Category,
    ) = MediaItem(
        id = id,
        userId = "bob",
        title = "Title $id",
        artist = null,
        format = null,
        year = null,
        barcode = null,
        notes = null,
        status = Status.Owned,
        category = category,
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
        createdAt = null,
        updatedAt = null,
    )
}
