package com.megamaced.crate.data.auth

import com.megamaced.crate.R
import com.megamaced.crate.util.UiText
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.security.cert.CertificateException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

@Serializable
data class LoginFlowInitResponse(
    val poll: PollEndpoint,
    val login: String,
)

@Serializable
data class PollEndpoint(
    val token: String,
    val endpoint: String,
)

@Serializable
data class LoginFlowResult(
    val server: String,
    val loginName: String,
    val appPassword: String,
)

sealed interface LoginFlowStatus {
    data object Polling : LoginFlowStatus

    data class Success(
        val result: LoginFlowResult,
    ) : LoginFlowStatus

    data class Error(
        val reason: UiText,
    ) : LoginFlowStatus
}

@Singleton
class NextcloudLoginFlow
    @Inject
    constructor() {
        // Redirects are followed by hand (executeWithinOrigin), never by
        // OkHttp: a 307 or 308 re-sends the POST body, and the poll's body is
        // the flow token, which can be exchanged once for an app password.
        private val client =
            OkHttpClient
                .Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        private val json = Json { ignoreUnknownKeys = true }

        fun initiate(host: String): Result<LoginFlowInitResponse> {
            val url = "${host.trimEnd('/')}/index.php/login/v2"
            val origin = url.toHttpUrlOrNull()
                ?: return Result.failure(LoginFlowException(UiText.Res(R.string.login_error_invalid_address)))
            val request = Request
                .Builder()
                .url(url)
                .post(FormBody.Builder().build())
                .header("User-Agent", USER_AGENT)
                .build()

            return try {
                // use{}: only a consumed body closes itself, so an unsuccessful
                // response left unread holds its connection until the pool
                // evicts it. The poll below repeats this up to 60 times.
                val body = executeWithinOrigin(request, origin).use { response ->
                    if (!response.isSuccessful) {
                        return Result.failure(
                            LoginFlowException(
                                UiText.Res(R.string.login_error_server_returned, listOf(response.code)),
                            ),
                        )
                    }
                    response.body?.string()
                } ?: return Result.failure(LoginFlowException(UiText.Res(R.string.login_error_empty_response)))
                val initResponse = json.decodeFromString<LoginFlowInitResponse>(body)
                // Both URLs come from the response body, so a hostile or
                // compromised server could point them at an origin the user
                // never typed — relaying the flow token to a third party, or
                // steering the browser somewhere else entirely. Neither is
                // used unless it belongs to the origin we just POSTed to.
                if (!isSameOrigin(origin, initResponse.poll.endpoint) ||
                    !isSameOrigin(origin, initResponse.login)
                ) {
                    return Result.failure(
                        LoginFlowException(UiText.Res(R.string.login_error_foreign_login_url)),
                    )
                }
                Result.success(initResponse)
            } catch (e: ForeignRedirectException) {
                Timber.w(e, "Login flow initiation redirected off its origin")
                Result.failure(LoginFlowException(UiText.Res(R.string.login_error_foreign_login_url), e))
            } catch (e: Exception) {
                Timber.e(e, "Login flow initiation failed")
                Result.failure(LoginFlowException(connectFailureReason(e), e))
            }
        }

        /**
         * Polls until the browser half of the flow completes. [expectedOrigin]
         * is the address the user typed; the returned credentials are refused
         * unless the `server` they are scoped to is that same origin, so a
         * server cannot hand back an app password bound to somewhere else.
         */
        suspend fun poll(
            endpoint: String,
            token: String,
            expectedOrigin: String,
        ): LoginFlowStatus {
            val origin = expectedOrigin.toHttpUrlOrNull()
                ?: return LoginFlowStatus.Error(UiText.Res(R.string.login_error_invalid_address))
            if (!isSameOrigin(origin, endpoint)) {
                return LoginFlowStatus.Error(UiText.Res(R.string.login_error_foreign_login_url))
            }
            val body = FormBody
                .Builder()
                .add("token", token)
                .build()
            val request = Request
                .Builder()
                .url(endpoint)
                .post(body)
                .header("User-Agent", USER_AGENT)
                .build()

            repeat(MAX_POLL_ATTEMPTS) {
                try {
                    // Every attempt is closed, including the 404 that means
                    // "not authorised yet" — the normal case, and the one that
                    // repeats. Leaking those exhausts connections and file
                    // descriptors over a single login.
                    executeWithinOrigin(request, origin).use { response ->
                        when (response.code) {
                            200 -> {
                                val responseBody = response.body?.string()
                                    ?: return LoginFlowStatus.Error(UiText.Res(R.string.login_error_empty_response))
                                val result = json.decodeFromString<LoginFlowResult>(responseBody)
                                // The app password is about to be bound to whatever
                                // `server` says. Refuse an origin the user never
                                // typed: otherwise a hostile server can point the
                                // whole session — every later API call, with the
                                // Basic header attached — somewhere else.
                                if (!isSameOrigin(origin, result.server)) {
                                    return LoginFlowStatus.Error(
                                        UiText.Res(R.string.login_error_foreign_credentials),
                                    )
                                }
                                return LoginFlowStatus.Success(result)
                            }

                            404 -> {
                                // Not yet authorized, keep polling
                            }

                            else -> {
                                return LoginFlowStatus.Error(
                                    UiText.Res(R.string.login_error_server_returned, listOf(response.code)),
                                )
                            }
                        }
                    }
                } catch (e: ForeignRedirectException) {
                    // Refused before anything was sent there; asking again
                    // would only be redirected again.
                    Timber.w(e, "Poll redirected off its origin")
                    return LoginFlowStatus.Error(UiText.Res(R.string.login_error_foreign_login_url))
                } catch (e: Exception) {
                    Timber.w(e, "Poll attempt failed")
                }
                delay(POLL_INTERVAL_MS)
            }
            return LoginFlowStatus.Error(UiText.Res(R.string.login_error_timed_out))
        }

        /**
         * Executes [request], following a redirect only while it stays on
         * [origin]: same scheme, host and port, checked before the next request
         * is made. A 307 or 308 keeps the method and body — the poll's body is
         * the flow token — so a redirect elsewhere is refused rather than
         * followed; a 301, 302 or 303 continues as a GET without a body, as
         * OkHttp itself would.
         *
         * @throws ForeignRedirectException when a redirect leaves [origin]
         */
        private fun executeWithinOrigin(
            request: Request,
            origin: HttpUrl,
        ): Response {
            var current = request
            repeat(MAX_REDIRECTS + 1) {
                val response = client.newCall(current).execute()
                if (!response.isRedirect) return response
                val target = response.header("Location")?.let { current.url.resolve(it) }
                val code = response.code
                response.close()
                if (target == null || !isSameOrigin(origin, target.toString())) {
                    throw ForeignRedirectException(current.url.redact(), target?.redact())
                }
                current =
                    if (code == HTTP_TEMPORARY_REDIRECT || code == HTTP_PERMANENT_REDIRECT) {
                        current.newBuilder().url(target).build()
                    } else {
                        current
                            .newBuilder()
                            .url(target)
                            .get()
                            .build()
                    }
            }
            throw IOException("Too many redirects from ${request.url.redact()}")
        }

        /**
         * True when [candidate] parses and shares [expected]'s scheme, host and
         * port. Path is deliberately not compared — a subdirectory install
         * returns its own base path in these URLs.
         */
        private fun isSameOrigin(
            expected: HttpUrl,
            candidate: String,
        ): Boolean {
            val url = candidate.toHttpUrlOrNull() ?: return false
            return url.scheme == expected.scheme &&
                url.host == expected.host &&
                url.port == expected.port
        }

        companion object {
            private const val USER_AGENT = "Crate Android"
            private const val POLL_INTERVAL_MS = 5_000L
            private const val MAX_POLL_ATTEMPTS = 60 // 5 minutes total
            private const val MAX_REDIRECTS = 5
            private const val HTTP_TEMPORARY_REDIRECT = 307
            private const val HTTP_PERMANENT_REDIRECT = 308
        }
    }

/** A login request redirected off the origin the user typed; nothing was sent there. */
class ForeignRedirectException(
    from: String,
    to: String?,
) : IOException("Redirect from $from to ${to ?: "an unreadable location"} leaves the login origin")

/**
 * What the login screen says when the server can't be reached. Certificate
 * failures get their own wording: the raw exception text ("Trust anchor for
 * certification path not found") doesn't tell a self-hosted user that the
 * fix is their certificate, or installing it on the device.
 *
 * An untrusted chain surfaces as an [SSLHandshakeException] caused by a
 * [CertificateException]; other handshake failures (no shared protocol or
 * cipher) keep the generic message. A trusted certificate that doesn't name
 * the host is OkHttp's [SSLPeerUnverifiedException], which a self-signed
 * certificate for an IP address hits when the IP is only in its CN.
 */
internal fun connectFailureReason(e: Exception): UiText =
    when {
        e is SSLPeerUnverifiedException -> {
            UiText.Res(R.string.login_error_certificate_hostname)
        }

        e is SSLHandshakeException &&
            generateSequence<Throwable>(e) { it.cause }.any { it is CertificateException } -> {
            UiText.Res(R.string.login_error_untrusted_certificate)
        }

        else -> {
            UiText.Res(R.string.login_error_connect_failed, listOf(e.message.toString()))
        }
    }

/**
 * A login attempt that could not complete. [reason] is what the login screen
 * shows: the flow runs without a Context, so it names the wording rather than
 * spelling it out.
 */
class LoginFlowException(
    val reason: UiText,
    cause: Throwable? = null,
) : Exception(cause)
