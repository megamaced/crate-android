package com.megamaced.crate.data.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A sign-in has to leave nothing of the previous account behind before it is
 * announced — and a process that dies part-way must come back signed out, not
 * signed in on top of the old cache. A sign-in overtaken by a sign-out must not
 * announce itself at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountSequenceTest {
    private val steps = mutableListOf<String>()

    @Test
    fun `a sign-in wipes the previous account before its credentials exist`() =
        runTest {
            val sequence = AccountSequence(wipe = { steps += "wipe" })

            val signedIn = sequence.signIn(store = { steps += "store" }, undo = { steps += "undo" })

            assertTrue(signedIn)
            assertEquals(listOf("wipe", "store"), steps)
        }

    @Test
    fun `a sign-in that dies during the wipe has stored no credentials`() =
        runTest {
            val sequence = AccountSequence(wipe = { awaitCancellation() })

            val signIn = launch { sequence.signIn(store = { steps += "store" }, undo = { steps += "undo" }) }
            advanceUntilIdle()
            signIn.cancel() // the process is killed mid-wipe

            assertEquals(emptyList<String>(), steps)
        }

    @Test
    fun `a sign-in overtaken by a sign-out during the wipe announces nothing`() =
        runTest {
            val wipeDone = CompletableDeferred<Unit>()
            val sequence = AccountSequence(wipe = { wipeDone.await() })

            val signIn = async { sequence.signIn(store = { steps += "store" }, undo = { steps += "undo" }) }
            advanceUntilIdle()
            SessionGeneration.advance() // sign-out
            wipeDone.complete(Unit)

            assertFalse(signIn.await())
            assertEquals(emptyList<String>(), steps)
        }

    @Test
    fun `a sign-out that lands while credentials are stored undoes the sign-in`() =
        runTest {
            val sequence = AccountSequence(wipe = {})

            val signedIn =
                sequence.signIn(
                    store = {
                        steps += "store"
                        SessionGeneration.advance() // sign-out doesn't wait for the lock
                    },
                    undo = { steps += "undo" },
                )

            assertFalse(signedIn)
            assertEquals(listOf("store", "undo"), steps)
        }

    @Test
    fun `a wipe for a switch that has been overtaken does nothing`() =
        runTest {
            val sequence = AccountSequence(wipe = { steps += "wipe" })
            val stale = SessionGeneration.current()
            SessionGeneration.advance()

            assertFalse(sequence.forget(stale))
            assertEquals(emptyList<String>(), steps)
        }
}
