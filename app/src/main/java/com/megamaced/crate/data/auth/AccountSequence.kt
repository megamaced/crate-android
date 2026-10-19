package com.megamaced.crate.data.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The order an account switch happens in, apart from the Android pieces that
 * carry it out (credential storage, Room, Coil), so it can be checked on its
 * own.
 *
 * Every step is tied to the generation the switch claimed. A wipe that a newer
 * switch has overtaken does nothing, since the data it would delete now
 * belongs to whoever is signed in. A sign-in wipes the previous account's data
 * before its credentials exist, and only publishes them while it still owns
 * the session.
 */
internal class AccountSequence(
    private val wipe: suspend () -> Unit,
) {
    // Serialises the transitions against each other.
    private val transition = Mutex()

    /**
     * Wipe the previous account's data on behalf of [generation]. False when a
     * newer switch has taken over, in which case nothing is wiped.
     */
    suspend fun forget(generation: Int): Boolean =
        transition.withLock {
            if (SessionGeneration.current() != generation) return@withLock false
            // Exclusive with every session-bound cache write, so one from the
            // outgoing session either lands before the wipe (and is wiped) or
            // finds its session ended and writes nothing.
            SessionGeneration.exclusive { wipe() }
            true
        }

    /**
     * A sign-in: claim a generation, wipe what the previous account left, then
     * [store] the new credentials and announce the session — in that order, so
     * a process that dies part-way comes back signed out rather than signed in
     * over the old cache. False, with nothing stored, when a sign-out or
     * another sign-in took over meanwhile. Sign-out doesn't take the lock, so
     * a generation that moves during [store] means it raced in: [undo] puts the
     * signed-out state back.
     */
    suspend fun signIn(
        store: () -> Unit,
        undo: () -> Unit,
    ): Boolean {
        val generation = SessionGeneration.advance()
        if (!forget(generation)) return false
        return transition.withLock {
            if (SessionGeneration.current() != generation) return@withLock false
            store()
            if (SessionGeneration.current() != generation) {
                undo()
                return@withLock false
            }
            true
        }
    }
}
