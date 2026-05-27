package com.macebox.crate.data.api

import com.macebox.crate.data.auth.TokenStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewrites outgoing requests addressed to the Retrofit placeholder host so
 * they actually hit the user's Nextcloud instance. Retrofit needs a static
 * base URL at construction time, so we point it at [PLACEHOLDER_HOST] and
 * patch the real host on every request.
 *
 * Requests addressed to any other host — most notably third-party image URLs
 * that Coil fetches directly via the shared OkHttp client (Discogs thumbnails,
 * TMDB posters, RAWG and ComicVine artwork, Open Library covers) — pass
 * through unchanged.
 */
const val PLACEHOLDER_HOST = "placeholder.invalid"

@Singleton
class HostInterceptor
    @Inject
    constructor(
        private val tokenStore: TokenStore,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            if (original.url.host != PLACEHOLDER_HOST) {
                return chain.proceed(original)
            }
            val credentials = tokenStore.getCredentials() ?: return chain.proceed(original)
            val target = credentials.host.toHttpUrlOrNull() ?: return chain.proceed(original)

            val rewritten = original.url
                .newBuilder()
                .scheme(target.scheme)
                .host(target.host)
                .port(target.port)
                .build()

            return chain.proceed(
                original.newBuilder().url(rewritten).build(),
            )
        }
    }
