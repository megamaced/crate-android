package com.megamaced.crate.ui.screen.settings

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.domain.model.Category
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HiddenCategoryEditorTest {
    @Test
    fun `rapid double toggle hides both categories`() =
        runTest {
            val writes = mutableListOf<Set<Category>>()
            val editor = HiddenCategoryEditor { target ->
                writes += target
                ApiResult.Success(Unit)
            }
            // The projection the screen reads only moves once a write has round
            // tripped, so it still reports "nothing hidden" for the second tap.
            val laggingProjection = emptySet<Category>()

            val first = editor.stage(laggingProjection, Category.Films, visible = false)
            val second = editor.stage(laggingProjection, Category.Books, visible = false)
            editor.commit(requireNotNull(first), Category.Films)
            editor.commit(requireNotNull(second), Category.Books)

            assertEquals(setOf(Category.Films), writes[0])
            assertEquals(setOf(Category.Films, Category.Books), writes[1])
            assertEquals(setOf(Category.Films, Category.Books), editor.pending.value)
        }

    @Test
    fun `staging marks the category busy until the write completes`() =
        runTest {
            val editor = HiddenCategoryEditor { ApiResult.Success(Unit) }

            val target = requireNotNull(editor.stage(emptySet(), Category.Films, visible = false))
            assertEquals(setOf(Category.Films), editor.busy.value)

            editor.commit(target, Category.Films)
            assertTrue(editor.busy.value.isEmpty())
        }

    @Test
    fun `hiding the last visible category is refused`() {
        val editor = HiddenCategoryEditor { ApiResult.Success(Unit) }
        val allButOneHidden = Category.entries.drop(1).toSet()

        assertNull(editor.stage(allButOneHidden, Category.entries.first(), visible = false))
        assertNull(editor.pending.value)
    }

    @Test
    fun `a failed write drops the pending set so the server state wins`() =
        runTest {
            val editor = HiddenCategoryEditor { ApiResult.HttpError(500, "boom") }

            val target = requireNotNull(editor.stage(emptySet(), Category.Films, visible = false))
            editor.commit(target, Category.Films)

            assertNull(editor.pending.value)
        }
}
