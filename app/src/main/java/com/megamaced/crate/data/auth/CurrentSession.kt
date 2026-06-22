package com.megamaced.crate.data.auth

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Narrow read-only view of the active session — exposes the login name only.
 * Used by ViewModels that need to compare item ownership (`MediaItem.userId`)
 * against the logged-in user without dragging the full TokenStore into the
 * unit-test boundary.
 *
 * The default implementation reads from [TokenStore]; tests can swap in a
 * lambda-or-class fake without needing Android's EncryptedSharedPreferences.
 */
interface CurrentSession {
    fun loginName(): String?
}

@Singleton
class TokenStoreCurrentSession
    @Inject
    constructor(
        private val tokenStore: TokenStore,
    ) : CurrentSession {
        override fun loginName(): String? = tokenStore.getCredentials()?.loginName
    }
