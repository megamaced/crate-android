package com.macebox.crate.data.api

import com.macebox.crate.data.auth.SessionManager
import com.macebox.crate.data.auth.TokenStore
import okhttp3.Interceptor
import okhttp3.Response
import okio.ByteString.Companion.encodeUtf8
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor
    @Inject
    constructor(
        private val tokenStore: TokenStore,
        private val sessionManager: SessionManager,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val credentials = tokenStore.getCredentials()
            val request = if (credentials != null) {
                val basic = "${credentials.loginName}:${credentials.appPassword}"
                    .encodeUtf8()
                    .base64()
                chain
                    .request()
                    .newBuilder()
                    .header("Authorization", "Basic $basic")
                    .header("OCS-APIRequest", "true")
                    .build()
            } else {
                chain.request()
            }

            val response = chain.proceed(request)

            if (response.code == 401) {
                sessionManager.onUnauthorised()
            }

            return response
        }
    }
