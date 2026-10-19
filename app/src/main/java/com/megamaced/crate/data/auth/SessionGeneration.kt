package com.megamaced.crate.data.auth

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Which signed-in session the process is serving, and the lock that keeps a
 * session's late work out of the next one's data.
 *
 * The generation moves on at every sign-in and sign-out. Work that will write
 * to the local cache is stamped with the generation it started under
 * ([stampedWithSession]; every apiCall does it), and each such write goes
 * through [write]: under one lock, and only while its stamp is still current.
 * The account wipe takes the same lock ([exclusive]), so a request that was in
 * flight when the user signed out either writes before the wipe — and is wiped
 * — or finds its session over and writes nothing. Without this, a response
 * that arrived after the wipe repopulated the next account's database.
 *
 * Process-wide, like the session itself: an `object`, so the lowest layers
 * (the DAO wrappers, apiCall) can reach it without a dependency on the
 * session machinery above them.
 */
object SessionGeneration {
    private val generation = AtomicInteger(0)
    private val writes = Mutex()

    fun current(): Int = generation.get()

    /** Starts a new session; returns its generation. */
    fun advance(): Int = generation.incrementAndGet()

    /** Runs [block] while no [write] is in progress — the account wipe. */
    suspend fun <T> exclusive(block: suspend () -> T): T = writes.withLock { block() }

    /**
     * Runs [block], a write to the local cache, on behalf of the session this
     * coroutine is stamped with. Unstamped work is not session-bound and is
     * only serialised with the wipe.
     *
     * @throws StaleSessionException when the stamped session has ended; [block] does not run
     */
    suspend fun <T> write(block: suspend () -> T): T {
        val stamp = currentCoroutineContext()[SessionStamp]?.generation
        return writes.withLock {
            if (stamp != null && stamp != generation.get()) throw StaleSessionException(stamp)
            block()
        }
    }
}

/** The session a piece of work was started for. */
class SessionStamp(
    val generation: Int,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<SessionStamp>
}

/** Runs [block] stamped with the session current now, unless it already carries a stamp. */
suspend fun <T> stampedWithSession(block: suspend () -> T): T =
    if (currentCoroutineContext()[SessionStamp] != null) {
        block()
    } else {
        withContext(SessionStamp(SessionGeneration.current())) { block() }
    }

/** A cache write refused because the session it was made for has ended. */
class StaleSessionException(
    generation: Int,
) : IllegalStateException("Session $generation has ended; its result was not kept")
