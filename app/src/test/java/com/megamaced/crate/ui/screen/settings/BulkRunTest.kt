package com.megamaced.crate.ui.screen.settings

import com.megamaced.crate.data.api.ApiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BulkRunTest {
    @Test
    fun `only the calls that succeeded are counted as done`() =
        runTest {
            // The old loop incremented on every attempt, so a run where the
            // server refused half the items still reported every one updated.
            val result =
                runBulk(
                    ids = listOf(1, 2, 3, 4),
                    onProgress = {},
                    operation = { id ->
                        if (id % 2 == 0L) ApiResult.HttpError(422, "no match") else ApiResult.Success(Unit)
                    },
                )

            assertEquals(2, result.done)
            assertEquals(2, result.failed)
            assertEquals(2, result.untouched(total = 4))
            assertNull(result.abandonedBy)
        }

    @Test
    fun `progress still advances past an item the server refused`() =
        runTest {
            // A per-item refusal is not a reason to stall the bar: the run
            // carries on, and the summary at the end says what was missed.
            val seen = mutableListOf<Int>()
            runBulk(
                ids = listOf(1, 2, 3),
                onProgress = { seen += it },
                operation = { id -> if (id == 2L) ApiResult.HttpError(500, null) else ApiResult.Success(Unit) },
            )

            assertEquals(listOf(1, 2, 3), seen)
        }

    @Test
    fun `a network failure abandons the run instead of failing once per item`() =
        runTest {
            val attempted = mutableListOf<Long>()
            val result =
                runBulk(
                    ids = listOf(1, 2, 3, 4, 5),
                    onProgress = {},
                    operation = { id ->
                        attempted += id
                        if (id >= 3) ApiResult.NetworkError else ApiResult.Success(Unit)
                    },
                )

            // Stopped at the first item that proved the connection was gone.
            assertEquals(listOf(1L, 2L, 3L), attempted)
            assertEquals(2, result.done)
            assertEquals(BulkResult.Abandon.Network, result.abandonedBy)
        }

    @Test
    fun `a sign-out mid-run is reported separately from a network failure`() =
        runTest {
            val result =
                runBulk(
                    ids = listOf(1, 2),
                    onProgress = {},
                    operation = { ApiResult.Unauthorised },
                )

            assertEquals(0, result.done)
            assertEquals(BulkResult.Abandon.Unauthorised, result.abandonedBy)
        }
}
