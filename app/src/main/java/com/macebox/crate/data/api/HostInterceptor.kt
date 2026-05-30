package com.macebox.crate.data.api

import com.macebox.crate.data.auth.TokenStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retrofit needs a static base URL at construction time, so the client is
 * pointed at [PlaceholderHost.HOST] and every outgoing request matching that
 * host is rewritten to the user's real Nextcloud instance by
 * [HostInterceptor]. Image URLs (Coil → third-party CDNs) and any other
 * absolute URLs pass through unchanged.
 */
object PlaceholderHost {
    const val HOST = "placeholder.invalid"
    const val BASE_URL = "https://$HOST/"

    fun urlPath(path: String): String = "$BASE_URL${path.trimStart('/')}"
}

@Singleton
class HostInterceptor
    @Inject
    constructor(
        private val tokenStore: TokenStore,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            if (original.url.host != PlaceholderHost.HOST) {
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
