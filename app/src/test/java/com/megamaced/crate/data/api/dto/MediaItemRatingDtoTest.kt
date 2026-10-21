package com.megamaced.crate.data.api.dto

import com.megamaced.crate.data.mapper.toRequest
import com.megamaced.crate.di.NetworkModule
import com.megamaced.crate.domain.model.MediaItemDraft
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** The rating on the wire, read and written with the app's own Json config. */
class MediaItemRatingDtoTest {
    private val json = NetworkModule.provideJson()

    @Test
    fun `an item's rating is read, and a server without ratings reads as unrated`() {
        val rated = """{"id":1,"title":"Low","status":"owned","category":"music","rating":4}"""
        val older = """{"id":2,"title":"Low","status":"owned","category":"music"}"""

        assertEquals(4, json.decodeFromString<MediaItemDto>(rated).rating)
        assertNull(json.decodeFromString<MediaItemDto>(older).rating)
    }

    @Test
    fun `a draft without a rating leaves it out of the request, and a cleared one sends 0`() {
        val untouched = MediaItemDraft(title = "Low", artist = "David Bowie", format = "Vinyl")
        val cleared = untouched.copy(rating = 0)

        val untouchedBody = json.parseToJsonElement(json.encodeToString(untouched.toRequest())).jsonObject
        val clearedBody = json.parseToJsonElement(json.encodeToString(cleared.toRequest())).jsonObject

        assertFalse("rating" in untouchedBody)
        assertEquals("0", clearedBody["rating"].toString())
    }
}
