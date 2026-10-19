package com.megamaced.crate.ui.screen.settings

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.domain.model.MarketSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Market settings go to the server as a whole snapshot, which replaces every
 * field. Turning on auto-fetch and then, quickly, turning off auto-enrich used
 * to send two snapshots at once; landing in the other order, the older one put
 * auto-enrich back on while the switch still showed it off.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SnapshotSaverTest {
    private val base = MarketSettings(autoFetchMarketRates = false, marketCurrency = "GBP")

    /** The server's copy: each save replaces it whole. */
    private var server = base
    private val sent = mutableListOf<MarketSettings>()
    private val gates = ArrayDeque<CompletableDeferred<ApiResult<Unit>>>()
    private val failures = mutableListOf<MarketSettings?>()

    @Test
    fun `saves never overlap, so the newest choice is the one the server keeps`() =
        runTest {
            val saver = saver()
            val a = base.copy(autoFetchMarketRates = true, autoEnrichOnClick = true)
            val b = a.copy(autoEnrichOnClick = false)

            saver.submit(a)
            advanceUntilIdle()
            saver.submit(b)
            advanceUntilIdle()
            // A is out; B waits for it rather than racing it.
            assertEquals(listOf(a), sent)

            release(ApiResult.Success(Unit))
            release(ApiResult.Success(Unit))

            assertEquals(listOf(a, b), sent)
            assertEquals(b, server)
            assertEquals(b, saver.confirmed)
            assertFalse(saver.isSaving)
        }

    @Test
    fun `changes made while a save is out coalesce into one`() =
        runTest {
            val saver = saver()
            val a = base.copy(autoFetchMarketRates = true)
            val b = a.copy(marketCurrency = "USD")
            val c = b.copy(marketCurrency = "EUR")

            saver.submit(a)
            advanceUntilIdle()
            saver.submit(b)
            saver.submit(c)
            release(ApiResult.Success(Unit))
            release(ApiResult.Success(Unit))

            assertEquals(listOf(a, c), sent)
            assertEquals(c, server)
        }

    @Test
    fun `a failure with nothing newer queued reports what the server last accepted`() =
        runTest {
            val saver = saver().apply { confirmed = base }

            saver.submit(base.copy(autoFetchMarketRates = true))
            release(ApiResult.NetworkError)

            assertEquals(listOf<MarketSettings?>(base), failures)
            assertEquals(base, server)
        }

    @Test
    fun `a failure superseded by a newer choice is not reported`() =
        runTest {
            val saver = saver().apply { confirmed = base }
            val a = base.copy(autoFetchMarketRates = true)
            val b = a.copy(autoEnrichOnImport = false)

            saver.submit(a)
            advanceUntilIdle()
            saver.submit(b)
            release(ApiResult.NetworkError)
            release(ApiResult.Success(Unit))

            assertTrue(failures.isEmpty())
            assertEquals(b, server)
        }

    private fun TestScope.saver() =
        SnapshotSaver<MarketSettings>(
            scope = this,
            save = { settings ->
                sent += settings
                val outcome = CompletableDeferred<ApiResult<Unit>>().also(gates::addLast).await()
                if (outcome is ApiResult.Success) server = settings
                outcome
            },
            onFailure = { _, confirmed -> failures += confirmed },
        )

    /** Let the oldest outstanding save finish with [outcome]. */
    private fun TestScope.release(outcome: ApiResult<Unit>) {
        advanceUntilIdle()
        gates.removeFirst().complete(outcome)
        advanceUntilIdle()
    }
}
