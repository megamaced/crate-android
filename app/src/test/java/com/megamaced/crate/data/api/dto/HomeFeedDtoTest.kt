package com.megamaced.crate.data.api.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `an empty PHP array for categories decodes as no categories`() {
        // PHP has one array type, so an empty associative array encodes as [].
        // A brand-new user with no owned items gets exactly this payload.
        val payload = """{"categories":[],"recentlyAdded":[],"mostValuable":[]}"""

        val feed = json.decodeFromString<HomeFeedDto>(payload)

        assertTrue(feed.categories.isEmpty())
    }

    @Test
    fun `a populated categories object still decodes as a map`() {
        val payload =
            """
            {"categories":{"music":{"count":2,"itemOfDay":null,"recentItems":[]}},
             "recentlyAdded":[],"mostValuable":[]}
            """.trimIndent()

        val feed = json.decodeFromString<HomeFeedDto>(payload)

        assertEquals(setOf("music"), feed.categories.keys)
        assertEquals(2, feed.categories.getValue("music").count)
    }
}
