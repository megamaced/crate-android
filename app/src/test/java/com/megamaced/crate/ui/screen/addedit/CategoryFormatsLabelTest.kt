package com.megamaced.crate.ui.screen.addedit

import com.megamaced.crate.R
import com.megamaced.crate.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A format's display name is translated while the value stored stays English,
 * so the English display name must be the stored value exactly, and only
 * formats named with ordinary words get one.
 */
class CategoryFormatsLabelTest {
    private val allFormats = Category.entries
        .flatMap { CategoryFormats.groupsFor(it) }
        .flatMap { it.formats }
        .toSet()

    @Test
    fun `a format named with ordinary words has a display name`() {
        assertEquals(R.string.format_name_trade_paperback, CategoryFormats.labelRes("Trade Paperback"))
        assertEquals(R.string.format_name_7_inch_single, CategoryFormats.labelRes("7\" Single"))
    }

    @Test
    fun `product names and custom formats show as stored`() {
        assertNull(CategoryFormats.labelRes("PS5"))
        assertNull(CategoryFormats.labelRes("VHS"))
        assertNull(CategoryFormats.labelRes("My bootleg tape"))
    }

    @Test
    fun `every display name belongs to a known format and reads as it in English`() {
        val english = englishStrings()
        val named = allFormats.mapNotNull { format -> CategoryFormats.labelRes(format)?.let { format to it } }
        assertEquals(27, named.size)
        val namesById = R.string::class.java.fields.associate { it.getInt(null) to it.name }
        named.forEach { (format, id) ->
            assertEquals(format, english[namesById.getValue(id)])
        }
        val withNames = english.keys.filter { it.startsWith("format_name_") }
        assertTrue(withNames.all { name -> named.any { namesById[it.second] == name } })
    }

    private fun englishStrings(): Map<String, String> {
        val xml = File("src/main/res/values/strings.xml").readText()
        return Regex("""<string name="([^"]+)"[^>]*>([^<]*)</string>""")
            .findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\\"", "\"").replace("\\'", "'") }
    }
}
