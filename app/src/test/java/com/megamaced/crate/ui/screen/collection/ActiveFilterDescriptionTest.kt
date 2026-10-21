package com.megamaced.crate.ui.screen.collection

import com.megamaced.crate.R
import com.megamaced.crate.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveFilterDescriptionTest {
    private val french =
        mapOf(
            R.string.format_name_hardcover to "Relié",
            R.string.format_name_paperback to "Broché",
        )

    @Test
    fun `formats read back by their display names`() {
        val state =
            CollectionUiState(
                category = Category.Books,
                selectedDecade = "1990s",
                selectedGenre = "Fantasy",
                selectedFormats = setOf("Paperback", "Hardcover"),
            )

        assertEquals("1990s Fantasy Relié Broché", activeFilterDescription(state, french::getValue))
    }

    @Test
    fun `a product name reads back as it is`() {
        val state = CollectionUiState(category = Category.Games, selectedFormats = setOf("PS5"))

        assertEquals("PS5", activeFilterDescription(state, french::getValue))
    }

    @Test
    fun `nothing filtered reads back nothing`() {
        assertNull(activeFilterDescription(CollectionUiState(), french::getValue))
    }
}
