package com.megamaced.crate.data.api

import com.megamaced.crate.data.auth.SessionManager
import com.megamaced.crate.data.auth.TokenStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import okio.ByteString.Companion.encodeUtf8
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches Nextcloud Basic Auth and OCS headers to requests addressed to the
 * user's Nextcloud instance. Runs after [HostInterceptor], so by this point
 * any placeholder URL has already been rewritten to the user's host.
 *
 * Requests bound for any other host — third-party image URLs (Discogs, TMDB,
 * RAWG, ComicVine, Open Library) that Coil fetches directly through the
 * shared client — pass through unauthenticated so the user's credentials do
 * not leak to external services.
 */
@Singleton
class AuthInterceptor
    @Inject
    constructor(
        private val tokenStore: TokenStore,
        private val sessionManager: SessionManager,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val credentials = tokenStore.getCredentials()
            // Match the full origin (scheme + host + port), not just the
            // hostname — the credential is bound to a specific origin, so the
            // header should never ride along to the same host on a different
            // scheme/port. HostInterceptor has already rewritten API requests
            // to exactly this origin by the time we run.
            val target = credentials?.host?.toHttpUrlOrNull()
            val isUserHost =
                target != null &&
                    original.url.host == target.host &&
                    original.url.port == target.port &&
                    original.url.scheme == target.scheme

            val request = if (credentials != null && isUserHost) {
                val basic = "${credentials.loginName}:${credentials.appPassword}"
                    .encodeUtf8()
                    .base64()
                val builder = original
                    .newBuilder()
                    .header("Authorization", "Basic $basic")
                    .header("OCS-APIRequest", "true")
                // OCS endpoints reply with XML by default; ask for JSON
                // explicitly. Skip for binary endpoints (artwork, export)
                // and for any caller that already set an Accept header
                // (e.g. Coil → image/*).
                if (original.url.encodedPath.contains("/ocs/v2.php/") &&
                    original.header("Accept") == null
                ) {
                    builder.header("Accept", "application/json")
                }
                builder.build()
            } else {
                original
            }

            val response = chain.proceed(request)

            if (isUserHost && response.code == 401) {
                sessionManager.onUnauthorised()
            }

            return response
        }
    }
