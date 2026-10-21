package com.megamaced.crate.ui.screen.collection

import com.megamaced.crate.R
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.util.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveFilterDescriptionTest {
    private val french =
        mapOf(
            R.string.format_name_hardcover to "Relié",
            R.string.format_name_paperback to "Broché",
            R.string.decade_label to "Années %1\$d",
        )

    private fun resolve(text: UiText): String =
        when (text) {
            is UiText.Res -> french.getValue(text.id).format(*text.args.toTypedArray())
            is UiText.Raw -> text.value
            is UiText.Plural -> error("no plural in a filter description")
        }

    @Test
    fun `the decade and formats read back as display text`() {
        val state =
            CollectionUiState(
                category = Category.Books,
                selectedDecade = "1990s",
                selectedGenre = "Fantasy",
                selectedFormats = setOf("Paperback", "Hardcover"),
            )

        assertEquals("Années 1990 Fantasy Relié Broché", activeFilterDescription(state, ::resolve))
    }

    @Test
    fun `a product name reads back as it is`() {
        val state = CollectionUiState(category = Category.Games, selectedFormats = setOf("PS5"))

        assertEquals("PS5", activeFilterDescription(state, ::resolve))
    }

    @Test
    fun `nothing filtered reads back nothing`() {
        assertNull(activeFilterDescription(CollectionUiState(), ::resolve))
    }
}
